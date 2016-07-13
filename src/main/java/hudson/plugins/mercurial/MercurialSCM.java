package hudson.plugins.mercurial;

import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.common.StandardUsernameCredentials;
import com.cloudbees.plugins.credentials.common.StandardUsernameListBoxModel;
import com.cloudbees.plugins.credentials.domains.URIRequirementBuilder;
import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.AbortException;
import hudson.EnvVars;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.Util;
import hudson.matrix.MatrixRun;
import hudson.model.AbstractBuild;
import hudson.model.Action;
import hudson.model.Actionable;
import hudson.model.Computer;
import hudson.model.Item;
import hudson.model.Job;
import hudson.model.Node;
import hudson.model.ParameterDefinition;
import hudson.model.ParametersDefinitionProperty;
import hudson.model.Run;
import hudson.model.StringParameterDefinition;
import hudson.model.TaskListener;
import hudson.plugins.mercurial.browser.HgBrowser;
import hudson.plugins.mercurial.browser.HgWeb;
import hudson.scm.ChangeLogParser;
import hudson.scm.PollingResult;
import hudson.scm.PollingResult.Change;
import hudson.scm.RepositoryBrowser;
import hudson.scm.SCM;
import hudson.scm.SCMDescriptor;
import hudson.scm.SCMRevisionState;
import hudson.util.ArgumentListBuilder;
import hudson.util.ForkOutputStream;
import hudson.util.ListBoxModel;
import hudson.util.LogTaskListener;
import hudson.util.VersionNumber;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.io.Serializable;
import java.net.MalformedURLException;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import static java.util.logging.Level.FINE;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.annotation.Nonnull;
import jenkins.model.Jenkins;
import net.sf.json.JSONObject;
import org.jenkinsci.plugins.multiplescms.MultiSCMRevisionState;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;

/**
 * Mercurial SCM.
 */
public class MercurialSCM extends SCM implements Serializable {

    // Environment vars names to be exposed
    private static final String ENV_MERCURIAL_REVISION = "MERCURIAL_REVISION";
    private static final String ENV_MERCURIAL_REVISION_SHORT = "MERCURIAL_REVISION_SHORT";
    private static final String ENV_MERCURIAL_REVISION_NUMBER = "MERCURIAL_REVISION_NUMBER";
    private static final String ENV_MERCURIAL_REVISION_BRANCH = "MERCURIAL_REVISION_BRANCH";
    private static final String ENV_MERCURIAL_REPOSITORY_URL = "MERCURIAL_REPOSITORY_URL";

    // old fields are left so that old config data can be read in, but
    // they are deprecated. transient so that they won't show up in XML
    // when writing back
    @Deprecated
    private transient boolean forest;

    /**
     * Name of selected installation, if any.
     */
    private String installation;

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
    private String modules = "";

    public enum RevisionType {
        BRANCH() {
            @Override public String getDisplayName() {
                return "Branch";
            }
        },
        TAG() {
            @Override public String getDisplayName() {
                return "Tag";
            }
        },
        CHANGESET() {
            @Override public String getDisplayName() {
                return "Changeset";
            }
        };
        public abstract String getDisplayName();
    }

    private RevisionType revisionType = RevisionType.BRANCH;

    /**
     * Revision to follow.
     */
    private String revision = "default";
    
    @Deprecated
    private String branch;

    /** Slash-separated subdirectory of the workspace in which the repository will be kept; null for top level. */
    private String subdir;

    private boolean clean;

    private HgBrowser browser;

    private String credentialsId;

    private boolean disableChangeLog;

    @DataBoundConstructor public MercurialSCM(String source) {
        this.source = Util.fixEmptyAndTrim(source);
    }

    @Deprecated
    public MercurialSCM(String installation, String source, String branch, String modules, String subdir, HgBrowser browser, boolean clean) {
        this(installation, source, branch, modules, subdir, browser, clean, null);
    }

    @Deprecated
    public MercurialSCM(String installation, String source, String branch, String modules, String subdir, HgBrowser browser, boolean clean, String credentialsId) {
        this(installation, source, RevisionType.BRANCH, branch, modules, subdir, browser, clean, credentialsId);
    }

    @Deprecated
    public MercurialSCM(String installation, String source, @NonNull RevisionType revisionType, @NonNull String revision, String modules, String subdir, HgBrowser browser, boolean clean, String credentialsId) {
      this(installation, source, revisionType, revision, modules, subdir, browser, clean, credentialsId, false);
    }

    @Deprecated
    public MercurialSCM(String installation, String source, @NonNull RevisionType revisionType, @NonNull String revision, String modules, String subdir, HgBrowser browser, boolean clean, String credentialsId, boolean disableChangeLog) {
        this(source);
        setInstallation(installation);
        setModules(modules);
        setSubdir(subdir);
        setClean(clean);
        setRevisionType(revisionType);
        setRevision(revision);
        setBrowser(browser);
        setCredentialsId(credentialsId);
        setDisableChangeLog(disableChangeLog);
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
        if (revisionType == null) {
            revisionType = RevisionType.BRANCH;
            assert revision == null;
            revision = branch == null ? "default" : branch;
            branch = null;
        }
        parseModules();
        return this;
    }

    public String getInstallation() {
        return installation;
    }

    @DataBoundSetter public final void setInstallation(String installation) {
        this.installation = installation;
    }

    /**
     * Gets the source repository path.
     * Either URL or local file path.
     */
    public String getSource() {
        return source;
    }
    private String getSource(EnvVars env) {
        return env.expand(source);
    }

    @Override public String getKey() {
        String base = "hg " + getSource(new EnvVars());
        if (revisionType == RevisionType.CHANGESET) {
            return base;
        } else {
            return base + " " + revision;
        }
    }

    public String getCredentialsId() {
        return credentialsId;
    }

    @DataBoundSetter public final void setCredentialsId(String credentialsId) {
        this.credentialsId = credentialsId;
    }

    public boolean isDisableChangeLog() {
        return disableChangeLog;
    }
    
    @DataBoundSetter public final void setDisableChangeLog(boolean disableChangeLog) {
        this.disableChangeLog = disableChangeLog;
    }

    @CheckForNull StandardUsernameCredentials getCredentials(Job<?,?> owner, EnvVars env) {
        if (credentialsId != null) {
            for (StandardUsernameCredentials c : availableCredentials(owner, getSource(env))) {
                if (c.getId().equals(credentialsId)) {
                    return c;
                }
            }
        }
        return null;
    }

    public @NonNull RevisionType getRevisionType() {
        return revisionType;
    }

    @DataBoundSetter public final void setRevisionType(@NonNull RevisionType revisionType) {
        this.revisionType = revisionType;
    }

    public @NonNull String getRevision() {
        return revision;
    }

    @DataBoundSetter public final void setRevision(@NonNull String revision) {
        this.revision = Util.fixEmpty(revision) == null ? "default" : revision;
    }

    @Deprecated
    public String getBranch() {
        if (revisionType != RevisionType.BRANCH) {
            throw new IllegalStateException();
        }
        return revision;
    }

    /**
     * Same as {@link #getRevision()} but with <em>default</em> values of parameters expanded.
     */
    private String getRevisionExpanded(Job<?,?> project, EnvVars env) {
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
        return getRevision(env);
    }

    private String getRevision(EnvVars env) {
        return env.expand(revision);
    }
    
    public String getSubdir() {
        return subdir;
    }

    @DataBoundSetter public final void setSubdir(String subdir) {
        this.subdir = Util.fixEmptyAndTrim(subdir);
    }

    private String getSubdir(EnvVars env) {
        return env.expand(subdir);
    }    

    private FilePath workspace2Repo(FilePath workspace, EnvVars env) {
        return subdir != null ? workspace.child(env.expand(subdir)) : workspace;
    }

    public HgBrowser getBrowser() {
        return browser;
    }

    @DataBoundSetter public final void setBrowser(HgBrowser browser) {
        this.browser = browser;
    }

    @Override public RepositoryBrowser<?> guessBrowser() {
        try {
            return new HgWeb(getSource(new EnvVars()));
        } catch (MalformedURLException x) {
            LOGGER.log(Level.FINE, null, x); // OK, could just be a local directory path
            return null;
        }
    }

    /**
     * True if we want clean check out each time. This means deleting everything in the repository checkout
     * (except <tt>.hg</tt>)
     */
    public boolean isClean() {
        return clean;
    }

    @DataBoundSetter public final void setClean(boolean clean) {
        this.clean = clean;
    }

    @Override
    public SCMRevisionState calcRevisionsFromBuild(Run<?, ?> build, FilePath workspace, Launcher launcher, TaskListener listener)
            throws IOException, InterruptedException {
        // tag action is added during checkout, so this shouldn't be called, but just in case.
        EnvVars env = build.getEnvironment(listener);
        
        //TODO: fall-back to the master's workspace?
        if (workspace == null) {
            throw new IOException("Workspace is not specified");
        }
        
        final Node nodeWithTheWorkspace = workspaceToNode(workspace);
        if (nodeWithTheWorkspace == null) {
            throw new IOException("Cannot find a node for the specified workspace");
        }
        
        HgExe hg = new HgExe(findInstallation(getInstallation()), getCredentials(build.getParent(), env), launcher, nodeWithTheWorkspace, listener, env);
        try {
        String tip = hg.tip(workspace2Repo(workspace, env), null);
        String rev = hg.tipNumber(workspace2Repo(workspace, env), null);
        String branch = revisionType != RevisionType.BRANCH ? hg.branch(workspace2Repo(workspace, env), null) : null;
        return tip != null && rev != null ? new MercurialTagAction(tip, rev, getSubdir(env), branch) : null;
        } finally {
            hg.close();
        }
    }

    @Override
    public boolean requiresWorkspaceForPolling() {
        MercurialInstallation mercurialInstallation = findInstallation(installation);
        return mercurialInstallation == null || !(mercurialInstallation.isUseCaches() || mercurialInstallation.isUseSharing() );
    }

    @Override
    public PollingResult compareRemoteRevisionWith(Job<?, ?> project, Launcher launcher, FilePath workspace,
            TaskListener listener, SCMRevisionState _baseline) throws IOException, InterruptedException {
        
        final Jenkins jenkins = Jenkins.getInstance();
        if (jenkins == null) {
            throw new IOException("Jenkins instance is not ready");
        }
        
        if (!(_baseline instanceof MercurialTagAction)) {
            throw new IOException("SCM revision state is not a Mercurial one");
        }
        MercurialTagAction baseline = (MercurialTagAction)_baseline;

        PrintStream output = listener.getLogger();
        EnvVars env = project.getEnvironment(jenkins, listener);
        StandardUsernameCredentials credentials = getCredentials(project, env);

        if (!requiresWorkspaceForPolling()) {
            launcher = jenkins.createLauncher(listener);
            CachedRepo possiblyCachedRepo = cachedSource(Jenkins.getInstance(), env, launcher, listener, true, credentials);
            if (possiblyCachedRepo == null) {
                throw new IOException("Could not use cache to poll for changes. See error messages above for more details");
            }
            FilePath repositoryCache = new FilePath(new File(possiblyCachedRepo.getRepoLocation()));
            return compare(launcher, listener, baseline, output, jenkins, repositoryCache, project);
        }
        // TODO do canUpdate check similar to in checkout, and possibly return INCOMPARABLE

        try {
            // Get the list of changed files.
            Node node = workspaceToNode(workspace);
            FilePath repository = workspace2Repo(workspace, env);

            pull(launcher, repository, listener, node, getRevisionExpanded(project, env), credentials, env);

            return compare(launcher, listener, baseline, output, node, repository, project);
        } catch(IOException e) {
            if (causedByMissingHg(e)) {
                listener.error(Messages.MercurialSCM_failed_to_compare_with_remote_repository());
                throw new AbortException("Failed to compare with remote repository");
            }
            throw new IOException("Failed to compare with remote repository", e);
        }
    }

    PollingResult compare(Launcher launcher, TaskListener listener, MercurialTagAction baseline, PrintStream output, Node node, FilePath repository, Job<?,?> project) throws IOException, InterruptedException {
        Change change = null;
        for (ChangeComparator s : ChangeComparator.all()) {
            Change c = s.compare(this, launcher, listener, baseline, output, node, repository, project);
            if (c != null) {
                if (change == null || c.compareTo(change) > 0) {
                    change = c;
                }
            }
        }
        if (change != null) {
            return new PollingResult(change);
        }
        EnvVars env = project.getEnvironment(node, listener);
        HgExe hg = new HgExe(findInstallation(getInstallation()), getCredentials(project, env), launcher, node, listener, env);
        try {
        String _revision = getRevisionExpanded(project, env);
        String remote = hg.tip(repository, _revision);
        String rev = hg.tipNumber(repository, _revision);
        String branch = revisionType != RevisionType.BRANCH ? hg.branch(repository, _revision) : null;

        if (remote == null) {
            throw new IOException("failed to find ID of branch head");
        }
        if (rev == null) {
            throw new IOException("failed to find revision of branch head");
        }
        if (remote.equals(baseline.id)) { // shortcut
            return new PollingResult(baseline, new MercurialTagAction(remote, rev, getSubdir(env), branch), Change.NONE);
        }
        Set<String> changedFileNames = parseStatus(hg.popen(repository, listener, false, new ArgumentListBuilder("status", "--rev", baseline.id, "--rev", remote)));

        MercurialTagAction cur = new MercurialTagAction(remote, rev, getSubdir(env), branch);
        return new PollingResult(baseline,cur,computeDegreeOfChanges(changedFileNames,output));
        } finally {
            hg.close();
        }
    }

    static Set<String> parseStatus(String status) {
        Set<String> result = new HashSet<String>();
        Matcher m = Pattern.compile("(?m)^[ARM] (.+)").matcher(status);
        while (m.find()) {
            result.add(m.group(1));
        }
        return result;
    }

    private int pull(Launcher launcher, FilePath repository, TaskListener listener, Node node, String revision, StandardUsernameCredentials credentials, EnvVars env) throws IOException, InterruptedException {
        HgExe hg = new HgExe(findInstallation(getInstallation()), credentials, launcher, node, listener, env);
        try {
        ArgumentListBuilder cmd = hg.seed(true);
        cmd.add("pull");
        if (revisionType == RevisionType.BRANCH || revisionType == RevisionType.CHANGESET) { // does not work for tags
            cmd.add("--rev", revision);
        }
        CachedRepo cachedSource = cachedSource(node, env, launcher, listener, true, credentials);
        if (cachedSource != null) {
            cmd.add(cachedSource.getRepoLocation());
        }
        return HgExe.joinWithPossibleTimeout(
                hg.launch(cmd).pwd(repository),
                true, listener);
        } finally {
            hg.close();
        }
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
    public void checkout(Run<?, ?> build, Launcher launcher, FilePath workspace, final TaskListener listener, File changelogFile, SCMRevisionState baseline)
            throws IOException, InterruptedException {

        MercurialInstallation mercurialInstallation = findInstallation(installation);
        final boolean jobShouldUseSharing = mercurialInstallation != null && mercurialInstallation.isUseSharing();

        Node node = workspaceToNode(workspace);
        FilePath repository = workspace2Repo(workspace, build.getEnvironment(listener));
        boolean canReuseExistingWorkspace;
        try {
            canReuseExistingWorkspace = canReuseWorkspace(repository, node, jobShouldUseSharing, build, launcher, listener);
        } catch(IOException e) {
            if (causedByMissingHg(e)) {
                listener.error("Failed to determine whether workspace can be reused because hg could not be found;" +
                        " check that you've properly configured your Mercurial installation");
            } else {
                e.printStackTrace(listener.error("Failed to determine whether workspace can be reused"));
            }
            throw new AbortException("Failed to determine whether workspace can be reused");
        }

        String revToBuild = getRevToBuild(build, workspace, build.getEnvironment(listener));
        StandardUsernameCredentials credentials = getCredentials(build.getParent(), build.getEnvironment(listener));
        if (canReuseExistingWorkspace) {
            update(build, launcher, repository, node, listener, revToBuild, credentials);
        } else {
            clone(build, launcher, repository, node, listener, revToBuild, credentials);
        }

        if (changelogFile != null) {
        try {
            determineChanges(build, launcher, listener, changelogFile, repository, node, revToBuild, baseline);
        } catch (IOException e) {
            listener.error("Failed to capture change log");
            e.printStackTrace(listener.getLogger());
            throw new AbortException("Failed to capture change log");
        }
        }
    }
    
    private boolean canReuseWorkspace(FilePath repo, Node node,
            boolean jobShouldUseSharing, Run<?,?> build,
            Launcher launcher, TaskListener listener)
                throws IOException, InterruptedException {

        boolean jobUsesSharing = new FilePath(repo, ".hg/sharedpath").exists();
        if (jobShouldUseSharing != jobUsesSharing) {
            return false;
        } else if(jobUsesSharing) {
            return true;
        }
        
        if (!new FilePath(repo, ".hg/hgrc").exists()) {
            return false;
        }

        HgExe hg = new HgExe(findInstallation(getInstallation()), getCredentials(build.getParent(), build.getEnvironment(listener)), launcher, node, listener, build.getEnvironment(listener));
        try {
        String upstream = hg.config(repo, "paths.default");
        EnvVars env = build.getEnvironment(listener);
        if (HgExe.pathEquals(getSource(env), upstream)) {
            return true;
        }
        listener.error(
                "Workspace reports paths.default as " + upstream +
                "\nwhich looks different than " + getSource(env) +
                "\nso falling back to fresh clone rather than incremental update");
        return false;
        } finally {
            hg.close();
        }
    }

    private void determineChanges(Run<?, ?> build, Launcher launcher, TaskListener listener, @Nonnull File changelogFile, FilePath repository, Node node, String revToBuild, SCMRevisionState baseline) throws IOException, InterruptedException {
        if (isDisableChangeLog()) {
            createEmptyChangeLog(changelogFile, listener, "changelog");
            return;
        }

        MercurialTagAction prevTag = (MercurialTagAction) baseline;
        if (prevTag == null) {
            listener.getLogger().println("WARN: Revision data for previous build unavailable; unable to determine change log");
            createEmptyChangeLog(changelogFile, listener, "changelog");
            return;
        }
        EnvVars env = build.getEnvironment(listener);
        MercurialInstallation inst = findInstallation(getInstallation());
        StandardUsernameCredentials credentials = getCredentials(build.getParent(), env);
        HgExe hg = new HgExe(inst, credentials, launcher, node, listener, env);
        try {

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
                args.add("--rev", "ancestors('" + revToBuild.replace("'", "\\'") + "') and not ancestors(" + prevTag.getId() + ")");
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
        } finally {
            hg.close();
        }
    }

    private void update(Run<?, ?> build, Launcher launcher, FilePath repository, Node node, TaskListener listener, String toRevision, StandardUsernameCredentials credentials)
            throws IOException, InterruptedException {
        HgExe hg = new HgExe(findInstallation(getInstallation()), credentials, launcher, node, listener, build.getEnvironment(listener));
        EnvVars env = build.getEnvironment(listener);
        try {
        int pullExitCode;
        try {
            pullExitCode = pull(launcher, repository, listener, node, toRevision, credentials, env);
        } catch (IOException e) {
            if (causedByMissingHg(e)) {
                listener.error("Failed to pull because hg could not be found;" +
                        " check that you've properly configured your Mercurial installation");
            } else {
                e.printStackTrace(listener.error("Failed to pull"));
            }
            throw new AbortException("Failed to pull");
        }
        if (pullExitCode != 0) {
            listener.error("Failed to pull");
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
            CachedRepo cachedSource = cachedSource(node, env, launcher, listener, true, credentials);
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
        String branch = revisionType != RevisionType.BRANCH ? hg.branch(repository, null) : null;
        if (tip != null && rev != null) {
            build.addAction(new MercurialTagAction(tip, rev, getSubdir(env), branch));
        }
        } finally {
            hg.close();
        }
    }

    private String getRevToBuild(Run<?, ?> build, FilePath workspace, EnvVars env) {
        String revToBuild = getRevision(env);
        if (build instanceof MatrixRun) {
            MatrixRun matrixRun = (MatrixRun) build;
            MercurialTagAction parentRevision = null;

            final Jenkins jenkins = Jenkins.getInstance();
            if (jenkins != null && jenkins.getPlugin("multiple-scms") != null) {
                MultiSCMRevisionState parentRevisions = matrixRun.getParentBuild().getAction(MultiSCMRevisionState.class);
                if (parentRevisions != null) {
                    SCMRevisionState _parentRevisions = parentRevisions.get(this, workspace, (MatrixRun) build);
                    if (_parentRevisions instanceof MercurialTagAction) {
                        parentRevision = (MercurialTagAction)_parentRevisions;
                    } // otherwise fall-back to the default behavior
                } 
                
                if (parentRevisions == null) {
                    parentRevision = matrixRun.getParentBuild().getAction(MercurialTagAction.class);
                }
            } else {
                parentRevision = matrixRun.getParentBuild().getAction(MercurialTagAction.class);
            }

            if (parentRevision != null && parentRevision.getId() != null) {
                revToBuild = parentRevision.getId();
            }
        }
        return revToBuild;
    }

    /**
     * Start from scratch and clone the whole repository.
     */
    private void clone(Run<?, ?> build, Launcher launcher, FilePath repository, Node node, TaskListener listener, String toRevision, StandardUsernameCredentials credentials)
            throws InterruptedException, IOException {
        try {
            repository.deleteRecursive();
        } catch (IOException e) {
            e.printStackTrace(listener.error("Failed to clean the repository checkout"));
            throw new AbortException("Failed to clean the repository checkout");
        }

        EnvVars env = build.getEnvironment(listener);
        HgExe hg = new HgExe(findInstallation(getInstallation()), credentials, launcher, node, listener, env);
        try {

        ArgumentListBuilder args = hg.seed(true);
        CachedRepo cachedSource = cachedSource(node, env, launcher, listener, false, credentials);
        if (cachedSource != null) {
            if (cachedSource.isUseSharing()) {
                args.add("--config", "extensions.share=");
                args.add("share");
                args.add("--noupdate");
                args.add(cachedSource.getRepoLocation());
                if (new VersionNumber(hg.version()).compareTo(new VersionNumber("3.3")) >= 0) {
                    args.add("-B");
                }
            } else {
                args.add("clone");
                args.add("--noupdate");
                args.add(cachedSource.getRepoLocation());
            }
        } else {
            args.add("clone");
            if (revisionType == RevisionType.BRANCH || revisionType == RevisionType.CHANGESET) {
                args.add("--rev", toRevision);
            }
            args.add("--noupdate");
            args.add(getSource(env));
        }
        args.add(repository.getRemote());
        repository.mkdirs();
        int cloneExitCode;
        try {
            cloneExitCode = hg.launch(args).join();
        } catch (IOException e) {
            if (causedByMissingHg(e)) {
                listener.error("Failed to clone " + getSource(env) + " because hg could not be found;" +
                        " check that you've properly configured your Mercurial installation");
            } else {
                e.printStackTrace(listener.error(Messages.MercurialSCM_failed_to_clone(getSource(env))));
            }
            throw new AbortException(Messages.MercurialSCM_failed_to_clone(getSource(env)));
        }
        if(cloneExitCode!=0) {
            listener.error(Messages.MercurialSCM_failed_to_clone(getSource(env)));
            throw new AbortException(Messages.MercurialSCM_failed_to_clone(getSource(env)));
        }

        if (cachedSource != null && !cachedSource.isUseSharing()) {
            FilePath hgrc = repository.child(".hg/hgrc");
            if (hgrc.exists()) {
                String hgrcText = hgrc.readToString();
                if (!hgrcText.contains(cachedSource.getRepoLocation())) {
                    listener.error(".hg/hgrc did not contain " + cachedSource.getRepoLocation() + " as expected:\n" + hgrcText);
                    throw new AbortException(".hg/hgrc did not contain " + cachedSource.getRepoLocation() + " as expected:\n" + hgrcText);
                }
                hgrc.write(hgrcText.replace(cachedSource.getRepoLocation(), getSource(env)), null);
            }
            // Passing --rev disables hardlinks, so we need to recreate them:
            hg.run("--config", "extensions.relink=", "relink", cachedSource.getRepoLocation())
                    .pwd(repository).join(); // ignore failures
        }

        ArgumentListBuilder upArgs = hg.seed(true);
        upArgs.add("update");
        upArgs.add("--rev", toRevision);
        if (hg.launch(upArgs).pwd(repository).join() != 0) {
            throw new AbortException("Failed to update " + getSource(env) + " to rev " + toRevision);
        }

        String tip = hg.tip(repository, null);
        String rev = hg.tipNumber(repository, null);
        String branch = revisionType != RevisionType.BRANCH ? hg.branch(repository, null) : null;
        if (tip != null && rev != null) {
            build.addAction(new MercurialTagAction(tip, rev, getSubdir(env), branch));
        }
        } finally {
            hg.close();
        }
    }

    @Override
    public void buildEnvVars(AbstractBuild<?,?> build, Map<String, String> env) {
        buildEnvVarsFromActionable(build, env);
    }

    void buildEnvVarsFromActionable(Actionable build, Map<String, String> env) {
        MercurialTagAction a = findTag(build, new EnvVars(env));
        if (a != null) {
            env.put(ENV_MERCURIAL_REVISION, a.id);
            env.put(ENV_MERCURIAL_REVISION_SHORT, a.getShortId());
            env.put(ENV_MERCURIAL_REVISION_NUMBER, a.rev);
            if (revisionType != RevisionType.BRANCH)
                env.put(ENV_MERCURIAL_REVISION_BRANCH, a.getBranch());
            env.put(ENV_MERCURIAL_REPOSITORY_URL, this.getSource());
        }
    }

    private MercurialTagAction findTag(Actionable build, EnvVars env) {
        for (Action action : build.getActions()) {
            if (action instanceof MercurialTagAction) {
                MercurialTagAction tag = (MercurialTagAction) action;
                // JENKINS-12162: differentiate plugins in different subdirs
                String ourSubDir = getSubdir( env );
                String tagSubDir = tag.getSubdir( );
                if ((ourSubDir == null && tagSubDir == null) || (ourSubDir != null && ourSubDir.equals(tagSubDir))) {
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
        if ( build != null )
        {
            try {
                EnvVars env = build.getEnvironment(new LogTaskListener(LOGGER, Level.INFO));
                return workspace2Repo(workspace, env);
            } catch (IOException ex) {
                Logger.getLogger(MercurialSCM.class.getName()).log(Level.SEVERE, null, ex);
            } catch (InterruptedException ex) {
                Logger.getLogger(MercurialSCM.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
        
        EnvVars env = new EnvVars( );
        return workspace2Repo(workspace, env);
    }

    @Override
    public DescriptorImpl getDescriptor() {
        return (DescriptorImpl) super.getDescriptor();
    }

    public String getModules() {
        return modules;
    }

    @DataBoundSetter public final void setModules(String modules) {
        this.modules = Util.fixNull(modules);
        parseModules();
    }

    private boolean causedByMissingHg(IOException e) {
        String message = e.getMessage();
        return message != null && message.startsWith("Cannot run program") && message.endsWith("No such file or directory");
    }

    private @CheckForNull CachedRepo cachedSource(Node node, EnvVars env, Launcher launcher, TaskListener listener, boolean useTimeout, StandardUsernameCredentials credentials) 
            throws InterruptedException {
        MercurialInstallation inst = findInstallation(installation);
        if (inst == null || !inst.isUseCaches()) {
            return null;
        }
        try {
            FilePath cache = Cache.fromURL(getSource(env), credentials).repositoryCache(inst, node, launcher, listener, useTimeout);
            if (cache != null) {
                return new CachedRepo(cache.getRemote(), inst.isUseSharing());
            } else {
                listener.error("Failed to use repository cache for " + getSource(env));
                return null;
            }
        } catch (InterruptedException x) {
            throw x;
        } catch (Exception x) {
            x.printStackTrace(listener.error("Failed to use repository cache for " + getSource(env)));
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

    @CheckForNull
    private static Node workspaceToNode(FilePath workspace) { // TODO https://trello.com/c/doFFMdUm/46-filepath-getcomputer
        Jenkins j = Jenkins.getInstance();
        if (j != null && workspace.isRemote()) {
            for (Computer c : j.getComputers()) {
                if (c.getChannel() == workspace.getChannel()) {
                    Node n = c.getNode();
                    if (n != null) {
                        return n;
                    }
                }
            }
        }
        return j;
    }

    private static List<? extends StandardUsernameCredentials> availableCredentials(Job<?,?> owner, String source) {
        // TODO implement support for SSHUserPrivateKey
        return CredentialsProvider.lookupCredentials(StandardUsernameCredentials.class, owner, null, URIRequirementBuilder.fromUri(source).build());
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

        @Override public boolean isApplicable(Job project) {
            return true;
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

        public ListBoxModel doFillCredentialsIdItems(@AncestorInPath Job<?,?> owner, @QueryParameter String source) {
            if (owner == null || !owner.hasPermission(Item.EXTENDED_READ)) {
                return new ListBoxModel();
            }
            return new StandardUsernameListBoxModel()
                    .withEmptySelection()
                    .withAll(availableCredentials(owner, new EnvVars( ).expand( source )));
        }

    }

    private static final long serialVersionUID = 1L;

    private static final Logger LOGGER = Logger.getLogger(MercurialSCM.class.getName());
}
