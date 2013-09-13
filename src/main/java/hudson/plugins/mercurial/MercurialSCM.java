package hudson.plugins.mercurial;

import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.common.StandardUsernameCredentials;
import com.cloudbees.plugins.credentials.common.StandardUsernameListBoxModel;
import com.cloudbees.plugins.credentials.common.StandardUsernamePasswordCredentials;
import com.cloudbees.plugins.credentials.domains.URIRequirementBuilder;
import static java.util.logging.Level.FINE;
import hudson.AbortException;
import hudson.EnvVars;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.Util;
import hudson.matrix.MatrixRun;
import hudson.model.*;
import hudson.plugins.mercurial.browser.HgBrowser;
import hudson.plugins.mercurial.browser.HgWeb;
import hudson.scm.ChangeLogParser;
import hudson.scm.PollingResult;
import hudson.scm.PollingResult.Change;
import hudson.scm.SCMDescriptor;
import hudson.scm.SCMRevisionState;
import hudson.scm.SCM;
import hudson.util.ArgumentListBuilder;
import hudson.util.ForkOutputStream;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.io.Serializable;
import java.net.MalformedURLException;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.sf.json.JSONObject;

import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.SuppressWarnings;
import hudson.util.ListBoxModel;
import java.util.List;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.QueryParameter;

/**
 * Mercurial SCM.
 */
public class MercurialSCM extends SCM implements Serializable {
    // old fields are left so that old config data can be read in, but
    // they are deprecated. transient so that they won't show up in XML
    // when writing back
    @Deprecated
    private transient boolean forest;

    /**
     * Name of selected installation, if any.
     */
    private final String installation;

    /**
     * Source repository URL from which we pull.
     */
    private final String source;

    /**
     * Prefixes of files within the repository which we're dependent on.
     * Storing as member variable so as to only parse the dependencies string once.
     * Will be either null (use whole repo), or nonempty list of subdir names.
     */
    private transient Set<String> _modules;
    // Same thing, but not parsed for jelly.
    private final String modules;

    /**
     * In-repository branch to follow. Null indicates "default".
     */
    private final String branch;

    /** Slash-separated subdirectory of the workspace in which the repository will be kept; null for top level. */
    private final String subdir;

    private final boolean clean;

    private HgBrowser browser;

    private final String credentialsId;

    @Deprecated
    public MercurialSCM(String installation, String source, String branch, String modules, String subdir, HgBrowser browser, boolean clean) {
        this(installation, source, branch, modules, subdir, browser, clean, null);
    }

    @DataBoundConstructor
    public MercurialSCM(String installation, String source, String branch, String modules, String subdir, HgBrowser browser, boolean clean, String credentialsId) {
        this.installation = installation;
        this.source = Util.fixEmptyAndTrim(source);
        this.modules = Util.fixNull(modules);
        this.subdir = Util.fixEmptyAndTrim(subdir);
        this.clean = clean;
        parseModules();
        branch = Util.fixEmpty(branch);
        if (branch != null && branch.equals("default")) {
            branch = null;
        }
        this.branch = branch;
        this.browser = browser;
        this.credentialsId = credentialsId;
    }

    private void parseModules() {
        if (modules.trim().length() > 0) {
            _modules = new HashSet<String>();
            // split by commas and whitespace, except "\ "
            for (String r : modules.split("(?<!\\\\)[ \\r\\n,]+")) {
                if (r.length() == 0) { // initial spaces should be ignored
                    continue;
                }
                // now replace "\ " to " ".
                r = r.replaceAll("\\\\ ", " ");
                // Strip leading slashes
                while (r.startsWith("/")) {
                    r = r.substring(1);
                }
                // Use unix file path separators
                r = r.replace('\\', '/');
                _modules.add(r);
            }
        } else {
            _modules = null;
        }
    }

    private Object readResolve() {
        parseModules();
        return this;
    }

    public String getInstallation() {
        return installation;
    }

    /**
     * Gets the source repository path.
     * Either URL or local file path.
     */
    public String getSource() {
        return source;
    }

    public String getCredentialsId() {
        return credentialsId;
    }

    @CheckForNull StandardUsernameCredentials getCredentials(AbstractProject<?,?> owner) {
        if (credentialsId != null) {
            for (StandardUsernameCredentials c : availableCredentials(owner, source)) {
                if (c.getId().equals(credentialsId)) {
                    return c;
                }
            }
        }
        return null;
    }

    /**
     * In-repository branch to follow. Never null.
     */
    public String getBranch() {
        return branch == null ? "default" : branch;
    }

    /**
     * Same as {@link #getBranch()} but with <em>default</em> values of parameters expanded.
     */
    private String getBranchExpanded(AbstractProject<?,?> project) {
        EnvVars env = new EnvVars();
        ParametersDefinitionProperty params = project.getProperty(ParametersDefinitionProperty.class);
        if (params != null) {
            for (ParameterDefinition param : params.getParameterDefinitions()) {
                if (param instanceof StringParameterDefinition) {
                    String dflt = ((StringParameterDefinition) param).getDefaultValue();
                    if (dflt != null) {
                        env.put(param.getName(), dflt);
                    }
                }
            }
        }
        return getBranch(env);
    }

    private String getBranch(EnvVars env) {
        return branch == null ? "default" : env.expand(branch);
    }

    public String getSubdir() {
        return subdir;
    }

    private FilePath workspace2Repo(FilePath workspace) {
        return subdir != null ? workspace.child(subdir) : workspace;
    }

    @Override
    @SuppressWarnings("DLS_DEAD_LOCAL_STORE")
    public HgBrowser getBrowser() {
        if (browser == null) {
            try {
                return new HgWeb(source); // #2406
            } catch (MalformedURLException x) {
                // forget it
            }
        }
        return browser;
    }

    /**
     * True if we want clean check out each time. This means deleting everything in the repository checkout
     * (except <tt>.hg</tt>)
     */
    public boolean isClean() {
        return clean;
    }

    @Override
    public SCMRevisionState calcRevisionsFromBuild(AbstractBuild<?, ?> build, Launcher launcher, TaskListener listener)
            throws IOException, InterruptedException {
        // tag action is added during checkout, so this shouldn't be called, but just in case.
        HgExe hg = new HgExe(findInstallation(getInstallation()), getCredentials(build.getProject()), launcher, build.getBuiltOn(), listener, build.getEnvironment(listener));
        String tip = hg.tip(workspace2Repo(build.getWorkspace()), null);
        String rev = hg.tipNumber(workspace2Repo(build.getWorkspace()), null);
        return tip != null && rev != null ? new MercurialTagAction(tip, rev, subdir) : null;
    }

    @Override
    public boolean requiresWorkspaceForPolling() {
        MercurialInstallation mercurialInstallation = findInstallation(installation);
        return mercurialInstallation == null || !(mercurialInstallation.isUseCaches() || mercurialInstallation.isUseSharing() );
    }

    @Override
    protected PollingResult compareRemoteRevisionWith(AbstractProject<?, ?> project, Launcher launcher, FilePath workspace,
            TaskListener listener, SCMRevisionState _baseline) throws IOException, InterruptedException {
        MercurialTagAction baseline = (MercurialTagAction)_baseline;

        PrintStream output = listener.getLogger();
        StandardUsernameCredentials credentials = getCredentials(project);

        if (!requiresWorkspaceForPolling()) {
            launcher = Hudson.getInstance().createLauncher(listener);
            CachedRepo possiblyCachedRepo = cachedSource(Hudson.getInstance(), launcher, listener, true, credentials);
            if (possiblyCachedRepo == null) {
                throw new IOException("Could not use cache to poll for changes. See error messages above for more details");
            }
            FilePath repositoryCache = new FilePath(new File(possiblyCachedRepo.getRepoLocation()));
            return compare(launcher, listener, baseline, output, Hudson.getInstance(), repositoryCache, project);
        }
        // TODO do canUpdate check similar to in checkout, and possibly return INCOMPARABLE

        try {
            // Get the list of changed files.
            Node node = project.getLastBuiltOn(); // JENKINS-5984: ugly but matches what AbstractProject.poll uses; though compare JENKINS-14247
            FilePath repository = workspace2Repo(workspace);

            pull(launcher, repository, listener, node, getBranchExpanded(project), credentials);

            return compare(launcher, listener, baseline, output, node, repository, project);
        } catch(IOException e) {
            if (causedByMissingHg(e)) {
                listener.error(Messages.MercurialSCM_failed_to_compare_with_remote_repository());
                throw new AbortException("Failed to compare with remote repository");
            }
            IOException ex = new IOException("Failed to compare with remote repository");
            ex.initCause(e);
            throw ex;
        }
    }

    private PollingResult compare(Launcher launcher, TaskListener listener, MercurialTagAction baseline, PrintStream output, Node node, FilePath repository, AbstractProject<?,?> project) throws IOException, InterruptedException {
        HgExe hg = new HgExe(findInstallation(getInstallation()), getCredentials(project), launcher, node, listener, /*TODO*/new EnvVars());
        String _branch = getBranchExpanded(project);
        String remote = hg.tip(repository, _branch);
        String rev = hg.tipNumber(repository, _branch);
        if (remote == null) {
            throw new IOException("failed to find ID of branch head");
        }
        if (rev == null) {
            throw new IOException("failed to find revision of branch head");
        }
        if (remote.equals(baseline.id)) { // shortcut
            return new PollingResult(baseline, new MercurialTagAction(remote, rev, subdir), Change.NONE);
        }
        Set<String> changedFileNames = parseStatus(hg.popen(repository, listener, false, new ArgumentListBuilder("status", "--rev", baseline.id, "--rev", remote)));

        MercurialTagAction cur = new MercurialTagAction(remote, rev, subdir);
        return new PollingResult(baseline,cur,computeDegreeOfChanges(changedFileNames,output));
    }

    static Set<String> parseStatus(String status) {
        Set<String> result = new HashSet<String>();
        Matcher m = Pattern.compile("(?m)^[ARM] (.+)").matcher(status);
        while (m.find()) {
            result.add(m.group(1));
        }
        return result;
    }

    private void pull(Launcher launcher, FilePath repository, TaskListener listener, Node node, String branch, StandardUsernameCredentials credentials) throws IOException, InterruptedException {
        HgExe hg = new HgExe(findInstallation(getInstallation()), credentials, launcher, node, listener, /* TODO */new EnvVars());
        ArgumentListBuilder cmd = hg.seed(true);
        cmd.add("pull");
        cmd.add("--rev", branch);
        CachedRepo cachedSource = cachedSource(node, launcher, listener, true, credentials);
        if (cachedSource != null) {
            cmd.add(cachedSource.getRepoLocation());
        }
        HgExe.joinWithPossibleTimeout(
                hg.launch(cmd).pwd(repository),
                true, listener);
    }

    private Change computeDegreeOfChanges(Set<String> changedFileNames, PrintStream output) {
        LOGGER.log(FINE, "Changed file names: {0}", changedFileNames);

        if (changedFileNames.isEmpty()) {
            return Change.NONE;
        }

        Set<String> depchanges = dependentChanges(changedFileNames);
        LOGGER.log(FINE, "Dependent changed file names: {0}", depchanges);

        if (depchanges.isEmpty()) {
            output.println(Messages.MercurialSCM_non_dependent_changes_detected());
            return Change.INSIGNIFICANT;
        }

        output.println(Messages.MercurialSCM_dependent_changes_detected());
        return Change.SIGNIFICANT;
    }

    /**
     * Filter out the given file name list by picking up changes that are in the modules we care about.
     */
    private Set<String> dependentChanges(Set<String> changedFileNames) {
        Set<String> affecting = new HashSet<String>();

        for (String changedFile : changedFileNames) {
            if (changedFile.matches("[.]hg(ignore|tags)")) {
                continue;
            }
            if (_modules == null) {
                affecting.add(changedFile);
                continue;
            }
            String unixChangedFile = changedFile.replace('\\', '/');
            for (String dependency : _modules) {
                if (unixChangedFile.startsWith(dependency)) {
                    affecting.add(changedFile);
                    break;
                }
            }
        }

        return affecting;
    }

    public static @CheckForNull MercurialInstallation findInstallation(String name) {
        for (MercurialInstallation inst : MercurialInstallation.allInstallations()) {
            if (inst.getName().equals(name)) {
                return inst;
            }
        }
        return null;
    }

    @Override
    public boolean checkout(AbstractBuild<?,?> build, Launcher launcher, FilePath workspace, final BuildListener listener, File changelogFile)
            throws IOException, InterruptedException {

        MercurialInstallation mercurialInstallation = findInstallation(installation);
        final boolean jobShouldUseSharing = mercurialInstallation != null && mercurialInstallation.isUseSharing();

        FilePath repository = workspace2Repo(workspace);
        boolean canReuseExistingWorkspace;
        try {
            canReuseExistingWorkspace = canReuseWorkspace(repository, jobShouldUseSharing, build, launcher, listener);
        } catch(IOException e) {
            if (causedByMissingHg(e)) {
                listener.error("Failed to determine whether workspace can be reused because hg could not be found;" +
                        " check that you've properly configured your Mercurial installation");
            } else {
                e.printStackTrace(listener.error("Failed to determine whether workspace can be reused"));
            }
            throw new AbortException("Failed to determine whether workspace can be reused");
        }

        String revToBuild = getRevToBuild(build, build.getEnvironment(listener));
        StandardUsernameCredentials credentials = getCredentials(build.getProject());
        if (canReuseExistingWorkspace) {
            update(build, launcher, repository, listener, revToBuild, credentials);
        } else {
            clone(build, launcher, repository, listener, revToBuild, credentials);
        }

        try {
            determineChanges(build, launcher, listener, changelogFile, repository, revToBuild);
        } catch (IOException e) {
            listener.error("Failed to capture change log");
            e.printStackTrace(listener.getLogger());
            throw new AbortException("Failed to capture change log");
        }
        return true;
    }
    
    private boolean canReuseWorkspace(FilePath repo,
            boolean jobShouldUseSharing, AbstractBuild<?,?> build,
            Launcher launcher, BuildListener listener)
                throws IOException, InterruptedException {
        if (!new FilePath(repo, ".hg/hgrc").exists()) {
            return false;
        }

        boolean jobUsesSharing = new FilePath(repo, ".hg/sharedpath").exists();
        if (jobShouldUseSharing != jobUsesSharing) {
            return false;
        }
        
        HgExe hg = new HgExe(findInstallation(getInstallation()), getCredentials(build.getProject()), launcher, build.getBuiltOn(), listener, build.getEnvironment(listener));
        String upstream = hg.config(repo, "paths.default");
        if (upstream == null) {
            return false;
        }
        if (HgExe.pathEquals(source, upstream)) {
            return true;
        }
        listener.error(
                "Workspace reports paths.default as " + upstream +
                "\nwhich looks different than " + source +
                "\nso falling back to fresh clone rather than incremental update");
        return false;
    }

    private void determineChanges(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener, File changelogFile, FilePath repository, String revToBuild) throws IOException, InterruptedException {
        AbstractBuild<?, ?> previousBuild = build.getPreviousBuild();
        MercurialTagAction prevTag = previousBuild != null ? findTag(previousBuild) : null;
        if (prevTag == null) {
            listener.getLogger().println("WARN: Revision data for previous build unavailable; unable to determine change log");
            createEmptyChangeLog(changelogFile, listener, "changelog");
            return;
        }
        EnvVars env = build.getEnvironment(listener);
        MercurialInstallation inst = findInstallation(getInstallation());
        StandardUsernameCredentials credentials = getCredentials(build.getProject());
        HgExe hg = new HgExe(inst, credentials, launcher, build.getBuiltOn(), listener, env);

        ArgumentListBuilder logCommand = hg.seed(true).add("log", "--rev", prevTag.getId());
        int exitCode = hg.launch(logCommand).pwd(repository).join();
        if(exitCode != 0) {
            listener.error("Previously built revision " + prevTag.getId() + " is not known in this clone; unable to determine change log");
            createEmptyChangeLog(changelogFile, listener, "changelog");
            return;
        }
        
        // calc changelog
        final FileOutputStream os = new FileOutputStream(changelogFile);
        try {
            os.write("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n".getBytes("UTF-8"));
            try {
                os.write("<changesets>\n".getBytes("UTF-8"));
                ArgumentListBuilder args = hg.seed(false);
                args.add("log");
                args.add("--template", MercurialChangeSet.CHANGELOG_TEMPLATE);
                args.add("--rev", revToBuild + ":0");
                args.add("--follow");
                args.add("--prune", prevTag.getId());
                args.add("--encoding", "UTF-8");
                args.add("--encodingmode", "replace");

                ByteArrayOutputStream errorLog = new ByteArrayOutputStream();

                int r = hg.launch(args).stdout(new ForkOutputStream(os, errorLog)).pwd(repository).join();
                if(r!=0) {
                    Util.copyStream(new ByteArrayInputStream(errorLog.toByteArray()), listener.getLogger());
                    throw new IOException("Failure detected while running hg log to determine change log");
                }
            } finally {
                os.write("</changesets>".getBytes("UTF-8"));
            }
        } finally {
            os.close();
        }
    }

    private void update(AbstractBuild<?, ?> build, Launcher launcher, FilePath repository, BuildListener listener, String toRevision, StandardUsernameCredentials credentials)
            throws IOException, InterruptedException {
        HgExe hg = new HgExe(findInstallation(getInstallation()), credentials, launcher, build.getBuiltOn(), listener, build.getEnvironment(listener));
        Node node = Computer.currentComputer().getNode(); // TODO why not build.getBuiltOn()?
        try {
            pull(launcher, repository, listener, node, toRevision, credentials);
        } catch (IOException e) {
            if (causedByMissingHg(e)) {
                listener.error("Failed to pull because hg could not be found;" +
                        " check that you've properly configured your Mercurial installation");
            } else {
                e.printStackTrace(listener.error("Failed to pull"));
            }
            throw new AbortException("Failed to pull");
        }

        int updateExitCode;
        try {
            updateExitCode = hg.run("update", "--clean", "--rev", toRevision).pwd(repository).join();
        } catch (IOException e) {
            listener.error("Failed to update");
            e.printStackTrace(listener.getLogger());
            throw new AbortException("Failed to update");
        }
        if (updateExitCode != 0) {
            listener.error("Failed to update");
            throw new AbortException("Failed to update");
        }
        if (build.getNumber() % 100 == 0) {
            CachedRepo cachedSource = cachedSource(node, launcher, listener, true, credentials);
            if (cachedSource != null && !cachedSource.isUseSharing()) {
                // Periodically recreate hardlinks to the cache to save disk space.
                hg.run("--config", "extensions.relink=", "relink", cachedSource.getRepoLocation()).pwd(repository).join(); // ignore failures
            }
        }

        if(clean) {
            if (hg.cleanAll().pwd(repository).join() != 0) {
                listener.error("Failed to clean unversioned files");
                throw new AbortException("Failed to clean unversioned files");
            }
        }

        String tip = hg.tip(repository, null);
        String rev = hg.tipNumber(repository, null);
        if (tip != null && rev != null) {
            build.addAction(new MercurialTagAction(tip, rev, subdir));
        }
    }

    private String getRevToBuild(AbstractBuild<?, ?> build, EnvVars env) {
        String revToBuild = getBranch(env);
        if (build instanceof MatrixRun) {
            MatrixRun matrixRun = (MatrixRun) build;
            MercurialTagAction parentRevision = matrixRun.getParentBuild().getAction(MercurialTagAction.class);
            if (parentRevision != null && parentRevision.getId() != null) {
                revToBuild = parentRevision.getId();
            }
        }
        return revToBuild;
    }

    /**
     * Start from scratch and clone the whole repository.
     */
    private void clone(AbstractBuild<?, ?> build, Launcher launcher, FilePath repository, BuildListener listener, String toRevision, StandardUsernameCredentials credentials)
            throws InterruptedException, IOException {
        try {
            repository.deleteRecursive();
        } catch (IOException e) {
            e.printStackTrace(listener.error("Failed to clean the repository checkout"));
            throw new AbortException("Failed to clean the repository checkout");
        }

        EnvVars env = build.getEnvironment(listener);
        HgExe hg = new HgExe(findInstallation(getInstallation()), credentials, launcher,build.getBuiltOn(),listener,env);

        ArgumentListBuilder args = hg.seed(true);
        CachedRepo cachedSource = cachedSource(build.getBuiltOn(), launcher, listener, false, credentials);
        if (cachedSource != null) {
            if (cachedSource.isUseSharing()) {
                args.add("--config", "extensions.share=");
                args.add("share");
                args.add("--noupdate");
                args.add(cachedSource.getRepoLocation());
            } else {
                args.add("clone");
                args.add("--rev", toRevision);
                args.add("--noupdate");
                args.add(cachedSource.getRepoLocation());
            }
        } else {
            args.add("clone");
            args.add("--rev", toRevision);
            args.add("--noupdate");
            args.add(source);
        }
        args.add(repository.getRemote());
        int cloneExitCode;
        try {
            cloneExitCode = hg.launch(args).join();
        } catch (IOException e) {
            if (causedByMissingHg(e)) {
                listener.error("Failed to clone " + source + " because hg could not be found;" +
                        " check that you've properly configured your Mercurial installation");
            } else {
                e.printStackTrace(listener.error(Messages.MercurialSCM_failed_to_clone(source)));
            }
            throw new AbortException(Messages.MercurialSCM_failed_to_clone(source));
        }
        if(cloneExitCode!=0) {
            listener.error(Messages.MercurialSCM_failed_to_clone(source));
            throw new AbortException(Messages.MercurialSCM_failed_to_clone(source));
        }

        if (cachedSource != null && !cachedSource.isUseSharing()) {
            FilePath hgrc = repository.child(".hg/hgrc");
            if (hgrc.exists()) {
                String hgrcText = hgrc.readToString();
                if (!hgrcText.contains(cachedSource.getRepoLocation())) {
                    listener.error(".hg/hgrc did not contain " + cachedSource.getRepoLocation() + " as expected:\n" + hgrcText);
                    throw new AbortException(".hg/hgrc did not contain " + cachedSource.getRepoLocation() + " as expected:\n" + hgrcText);
                }
                hgrc.write(hgrcText.replace(cachedSource.getRepoLocation(), source), null);
            }
            // Passing --rev disables hardlinks, so we need to recreate them:
            hg.run("--config", "extensions.relink=", "relink", cachedSource.getRepoLocation())
                    .pwd(repository).join(); // ignore failures
        }

        ArgumentListBuilder upArgs = hg.seed(true);
        upArgs.add("update");
        upArgs.add("--rev", toRevision);
        if (hg.launch(upArgs).pwd(repository).join() != 0) {
            throw new AbortException("Failed to update " + source + " to rev " + toRevision);
        }

        String tip = hg.tip(repository, null);
        String rev = hg.tipNumber(repository, null);
        if (tip != null && rev != null) {
            build.addAction(new MercurialTagAction(tip, rev, subdir));
        }
    }

    @Override
    public void buildEnvVars(AbstractBuild<?,?> build, Map<String, String> env) {
        buildEnvVarsFromActionable(build, env);
    }

    void buildEnvVarsFromActionable(Actionable build, Map<String, String> env) {
        MercurialTagAction a = findTag(build);
        if (a != null) {
            env.put("MERCURIAL_REVISION", a.id);
            env.put("MERCURIAL_REVISION_SHORT", a.getShortId());
            env.put("MERCURIAL_REVISION_NUMBER", a.rev);
        }
    }

    private MercurialTagAction findTag(Actionable build) {
        for (Action action : build.getActions()) {
            if (action instanceof MercurialTagAction) {
                MercurialTagAction tag = (MercurialTagAction) action;
                // JENKINS-12162: differentiate plugins in different subdirs
                if ((subdir == null && tag.subdir == null) || (subdir != null && subdir.equals(tag.subdir))) {
                    return tag;
                }
            }
        }
        return null;
    }

    @Override
    public ChangeLogParser createChangeLogParser() {
        return new MercurialChangeLogParser(_modules);
    }

    @Override public FilePath getModuleRoot(FilePath workspace, AbstractBuild build) {
        return workspace2Repo(workspace);
    }

    @Override
    public DescriptorImpl getDescriptor() {
        return (DescriptorImpl) super.getDescriptor();
    }

    public String getModules() {
        return modules;
    }

    private boolean causedByMissingHg(IOException e) {
        String message = e.getMessage();
        return message != null && message.startsWith("Cannot run program") && message.endsWith("No such file or directory");
    }

    static boolean CACHE_LOCAL_REPOS = false;
    private @CheckForNull CachedRepo cachedSource(Node node, Launcher launcher, TaskListener listener, boolean useTimeout, StandardUsernameCredentials credentials) {
        if (!CACHE_LOCAL_REPOS && source.matches("(file:|[/\\\\]).+")) {
            return null;
        }
        MercurialInstallation inst = findInstallation(installation);
        if (inst == null || !inst.isUseCaches()) {
            return null;
        }
        try {
            FilePath cache = Cache.fromURL(source, credentials).repositoryCache(inst, node, launcher, listener, useTimeout);
            if (cache != null) {
                return new CachedRepo(cache.getRemote(), inst.isUseSharing());
            } else {
                listener.error("Failed to use repository cache for " + source);
                return null;
            }
        } catch (Exception x) {
            x.printStackTrace(listener.error("Failed to use repository cache for " + source));
            return null;
        }
    }

    private static class CachedRepo {
        private final String repoLocation;
        private final boolean useSharing;

        private CachedRepo(String repoLocation, boolean useSharing) {
            this.repoLocation = repoLocation;
            this.useSharing = useSharing;
        }

        public String getRepoLocation() {
            return repoLocation;
        }

        public boolean isUseSharing() {
            return useSharing;
        }

    }

    private static List<? extends StandardUsernameCredentials> availableCredentials(AbstractProject<?,?> owner, String source) {
        // TODO implement support for SSHUserPrivateKey
        return CredentialsProvider.lookupCredentials(StandardUsernamePasswordCredentials.class, owner, null, URIRequirementBuilder.fromUri(source).build());
    }

    @Extension
    public static final class DescriptorImpl extends SCMDescriptor<MercurialSCM> {

        private String hgExe;

        public DescriptorImpl() {
            super(HgBrowser.class);
            load();
        }

        public String getDisplayName() {
            return "Mercurial";
        }

        /**
         * Path to mercurial executable.
         */
        public String getHgExe() {
            if (hgExe == null) {
                return "hg";
            }
            return hgExe;
        }

        @Override
        public SCM newInstance(StaplerRequest req, JSONObject formData) throws FormException {
            return super.newInstance(req, formData);
        }

        @Override
        public boolean configure(StaplerRequest req, JSONObject json) throws FormException {
            hgExe = req.getParameter("mercurial.hgExe");
            save();
            return true;
        }

        public ListBoxModel doFillCredentialsIdItems(@AncestorInPath AbstractProject<?,?> owner, @QueryParameter String source) {
            return new StandardUsernameListBoxModel()
                    .withEmptySelection()
                    .withAll(availableCredentials(owner, source));
        }

    }

    private static final long serialVersionUID = 1L;

    private static final Logger LOGGER = Logger.getLogger(MercurialSCM.class.getName());
}
