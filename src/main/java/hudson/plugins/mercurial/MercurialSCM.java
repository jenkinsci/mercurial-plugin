package hudson.plugins.mercurial;

import static java.util.logging.Level.FINE;
import hudson.AbortException;
import hudson.EnvVars;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.Launcher.ProcStarter;
import hudson.Util;
import hudson.model.BuildListener;
import hudson.model.TaskListener;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.Computer;
import hudson.model.Node;
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

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintStream;
import java.io.Serializable;
import java.net.MalformedURLException;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.sf.json.JSONObject;

import org.apache.commons.io.output.NullOutputStream;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.framework.io.WriterOutputStream;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.SuppressWarnings;

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

    @DataBoundConstructor
    public MercurialSCM(String installation, String source, String branch, String modules, String subdir, HgBrowser browser, boolean clean) {
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

    /**
     * In-repository branch to follow. Never null.
     */
    public String getBranch() {
        return branch == null ? "default" : branch;
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

    private ArgumentListBuilder findHgExe(AbstractBuild<?,?> build, TaskListener listener, boolean allowDebug) throws IOException, InterruptedException {
        return findHgExe(build.getBuiltOn(), listener, allowDebug);
    }

    /**
     * @param allowDebug
     *      If the caller intends to parse the stdout from Mercurial, pass in false to indicate
     *      that the optional --debug option shall never be activated.
     */
    ArgumentListBuilder findHgExe(Node node, TaskListener listener, boolean allowDebug) throws IOException, InterruptedException {
        for (MercurialInstallation inst : MercurialInstallation.allInstallations()) {
            if (inst.getName().equals(installation)) {
                // XXX what about forEnvironment?
                ArgumentListBuilder b = new ArgumentListBuilder(inst.executableWithSubstitution(
                        inst.forNode(node, listener).getHome()));
                if (allowDebug && inst.getDebug()) {
                    b.add("--debug");
                }
                return b;
            }
        }
        return new ArgumentListBuilder(getDescriptor().getHgExe());
    }

    static ProcStarter launch(Launcher launcher) {
        return launcher.launch().envs(Collections.singletonMap("HGPLAIN", "true"));
    }

    @Override
    public SCMRevisionState calcRevisionsFromBuild(AbstractBuild<?, ?> build, Launcher launcher, TaskListener listener)
            throws IOException, InterruptedException {
        // tag action is added during checkout, so this shouldn't be called, but just in case.
        HgExe hg = new HgExe(this, launcher, build, listener, build.getEnvironment(listener));
        String tip = hg.tip(workspace2Repo(build.getWorkspace()));
        return tip != null ? new MercurialTagAction(tip) : null;
    }

    private static final String FILES_STYLE = "changeset = 'id:{node}\\nfiles:{files}\\n'\n" + "file = '{file}:'";

    @Override
    protected PollingResult compareRemoteRevisionWith(AbstractProject<?, ?> project, Launcher launcher, FilePath workspace,
            TaskListener listener, SCMRevisionState _baseline) throws IOException, InterruptedException {
        MercurialTagAction baseline = (MercurialTagAction)_baseline;

        PrintStream output = listener.getLogger();

        // XXX do canUpdate check similar to in checkout, and possibly return INCOMPARABLE

        // Mercurial requires the style file to be in a file..
        Set<String> changedFileNames = new HashSet<String>();
        FilePath tmpFile = workspace.createTextTempFile("tmp", "style", FILES_STYLE);
        try {
            // Get the list of changed files.
            Node node = project.getLastBuiltOn(); // HUDSON-5984: ugly but matches what AbstractProject.poll uses

            FilePath repository = workspace2Repo(workspace);
            pull(launcher, repository, listener, output, node,getBranch());
            
            ArgumentListBuilder logCmd = findHgExe(node, listener, false);
            logCmd.add("log", "--style", tmpFile.getRemote());
            // Note: In order to support older Mercurial Versions including the
            // one used by Ubuntu 10.04 LTS, using the short option "-b"
            // instead of "--branch" which was renamed from "--only-branch",
            // see JENKINS-12048.
            logCmd.add("-b", getBranch());
            logCmd.add("--no-merges");

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ForkOutputStream fos = new ForkOutputStream(baos, output);

            logCmd.add("--prune", baseline.id);
            joinWithPossibleTimeout(
                    launch(launcher).cmds(logCmd).stdout(fos).pwd(repository),
                    true, listener);

            MercurialTagAction cur = parsePollingLogOutput(baos, baseline, changedFileNames);
            return new PollingResult(baseline,cur,computeDegreeOfChanges(changedFileNames,output));
        } catch(IOException e) {
            if (causedByMissingHg(e)) {
                listener.error("Failed to compare with remote repository because hg could not be found;" +
                        " check that you've properly configured your Mercurial installation");
                throw new AbortException("Failed to compare with remote repository");
            }
            IOException ex = new IOException("Failed to compare with remote repository");
            ex.initCause(e);
            throw ex;
        } finally {
            tmpFile.delete();
        }
    }

    private void pull(Launcher launcher, FilePath repository, TaskListener listener, PrintStream output, Node node, String branch) throws IOException, InterruptedException {
        ArgumentListBuilder cmd = findHgExe(node, listener, false);
        cmd.add("pull");
        cmd.add("--rev", branch);
        PossiblyCachedRepo cachedSource = cachedSource(node, launcher, listener, true);
        if (cachedSource != null) {
            cmd.add(cachedSource.getRepoLocation());
        }
        joinWithPossibleTimeout(
                launch(launcher).cmds(cmd).stdout(output).pwd(repository),
                true, listener);
    }

    static int joinWithPossibleTimeout(ProcStarter proc, boolean useTimeout, final TaskListener listener) throws IOException, InterruptedException {
        return useTimeout ? proc.start().joinWithTimeout(/* #4528: not in JDK 5: 1, TimeUnit.HOURS*/60 * 60, TimeUnit.SECONDS, listener) : proc.join();
    }

    private Change computeDegreeOfChanges(Set<String> changedFileNames, PrintStream output) {
        LOGGER.log(FINE, "Changed file names: {0}", changedFileNames);

        if (changedFileNames.isEmpty()) {
            return Change.NONE;
        }

        Set<String> depchanges = dependentChanges(changedFileNames);
        LOGGER.log(FINE, "Dependent changed file names: {0}", depchanges);

        if (depchanges.isEmpty()) {
            output.println("Non-dependent changes detected");
            return Change.INSIGNIFICANT;
        }

        output.println("Dependent changes detected");
        return Change.SIGNIFICANT;
    }

    /**
     * Filter out the given file name list by picking up changes that are in the modules we care about.
     */
    private Set<String> dependentChanges(Set<String> changedFileNames) {
        if (_modules == null) {
            // Old project created before this feature was added.
            return changedFileNames;
        }

        Set<String> affecting = new HashSet<String>();

        for (String changedFile : changedFileNames) {
            for (String dependency : _modules) {
                if (changedFile.startsWith(dependency)) {
                    affecting.add(changedFile);
                    break;
                }
            }
        }

        return affecting;
    }

    private static Pattern FILES_LINE = Pattern.compile("files:(.*)");

    private MercurialTagAction parsePollingLogOutput(ByteArrayOutputStream output, MercurialTagAction baseline,  Set<String> result) throws IOException {
        String headId = null; // the tip of the remote revision
        BufferedReader in = new BufferedReader(new InputStreamReader(
                new ByteArrayInputStream(output.toByteArray())));
        String line;
        while ((line = in.readLine()) != null) {
            Matcher matcher = FILES_LINE.matcher(line);
            if (matcher.matches()) {
                for (String s : matcher.group(1).split(":")) {
                    if (s.length() > 0) {
                        result.add(s);
                    }
                }
            }
            if (line.startsWith("id:")) {
                String id = line.substring(3);
                if (headId == null) {
                    headId = id;
                }
            }
        }

        if (headId==null) {
            return baseline; // no new revisions found
        }
        return new MercurialTagAction(headId);
    }

    public static MercurialInstallation findInstallation(String name) {
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

        if (canReuseExistingWorkspace) {
            update(build, launcher, repository, listener);
        } else {
            clone(build, launcher, repository, listener);
        }

        try {
            determineChanges(build, launcher, listener, changelogFile, repository);
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
        
        EnvVars env = build.getEnvironment(listener);
        HgExe hg = new HgExe(this,launcher,build,listener,env);
        String upstream = hg.config(repo, "paths.default");
        if (upstream == null) {
            return false;
        }
        if (upstream.equals(source)) {
            return true;
        }
        if ((upstream + '/').equals(source)) {
            return true;
        }
        if (upstream.equals(source + '/')) {
            return true;
        }
        if (source.startsWith("file:/") && new File(upstream).toURI().toString().equals(source)) {
            return true;
        }
        
        listener.error(
                "Workspace reports paths.default as " + upstream +
                "\nwhich looks different than " + source +
                "\nso falling back to fresh clone rather than incremental update");
        return false;
    }

    private void determineChanges(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener, File changelogFile, FilePath repository) throws IOException, InterruptedException {
        
        AbstractBuild<?, ?> previousBuild = build.getPreviousBuild();
        MercurialTagAction prevTag = previousBuild != null ? previousBuild.getAction(MercurialTagAction.class) : null;
        if (prevTag == null) {
            listener.getLogger().println("WARN: Revision data for previous build unavailable; unable to determine change log");
            createEmptyChangeLog(changelogFile, listener, "changelog");
            return;
        }
        EnvVars env = build.getEnvironment(listener);

        ArgumentListBuilder logCommand = findHgExe(build, listener, false).add("log", "--rev", prevTag.getId());
        int exitCode = launch(launcher).cmds(logCommand).envs(env).pwd(repository).join();
        if(exitCode != 0) {
            listener.error("Previous built revision " + prevTag.getId() + " is not know in this clone; unable to determine change log");
            createEmptyChangeLog(changelogFile, listener, "changelog");
            return;
        }
        
        // calc changelog
        final FileOutputStream os = new FileOutputStream(changelogFile);
        try {
            try {
                os.write("<changesets>\n".getBytes());
                ArgumentListBuilder args = findHgExe(build, listener, false);
                args.add("log");
                args.add("--template", MercurialChangeSet.CHANGELOG_TEMPLATE);
                args.add("--rev", getBranch(env) + ":0");
                args.add("--follow");
                args.add("--prune", prevTag.getId());

                ByteArrayOutputStream errorLog = new ByteArrayOutputStream();

                // mercurial produces text in the platform default encoding, so we need to
                // convert it back to UTF-8
                WriterOutputStream o = new WriterOutputStream(new OutputStreamWriter(os, "UTF-8"), Computer.currentComputer().getDefaultCharset());
                int r;
                try {
                    r = launch(launcher).cmds(args).envs(env)
                            .stdout(new ForkOutputStream(o,errorLog)).pwd(repository).join();
                } finally {
                    o.flush(); // make sure to commit all output
                }
                if(r!=0) {
                    Util.copyStream(new ByteArrayInputStream(errorLog.toByteArray()), listener.getLogger());
                    throw new IOException("Failure detected while running hg log to determine change log");
                }
            } finally {
                os.write("</changesets>".getBytes());
            }
        } finally {
            os.close();
        }
    }

    /*
     * Updates the current repository.
     */
    private void update(AbstractBuild<?, ?> build, Launcher launcher, FilePath repository, BuildListener listener)
            throws InterruptedException, IOException {
        EnvVars env = build.getEnvironment(listener);

        HgExe hg = new HgExe(this, launcher, build, listener, env);
        try {
            pull(launcher, repository, listener, new PrintStream(new NullOutputStream()), Computer.currentComputer().getNode(), getBranch(env));
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
            updateExitCode = hg.run("update", "--clean", "--rev", getBranch(env)).pwd(repository).join();
        } catch (IOException e) {
            listener.error("Failed to update");
            e.printStackTrace(listener.getLogger());
            throw new AbortException("Failed to update");
        }
        if (updateExitCode != 0) {
            listener.error("Failed to update");
            throw new AbortException("Failed to update");
        }
        
        if(clean) {
            if (hg.cleanAll().pwd(repository).join() != 0) {
                listener.error("Failed to clean unversioned files");
                throw new AbortException("Failed to clean unversioned files");
            }
        }

        String tip = hg.tip(repository);
        if (tip != null) {
            build.addAction(new MercurialTagAction(tip));
        }
    }

    /**
     * Start from scratch and clone the whole repository.
     */
    private void clone(AbstractBuild<?,?> build, Launcher launcher, FilePath repository, BuildListener listener)
            throws InterruptedException, IOException {
        try {
            repository.deleteRecursive();
        } catch (IOException e) {
            e.printStackTrace(listener.error("Failed to clean the repository checkout"));
            throw new AbortException("Failed to clean the repository checkout");
        }

        EnvVars env = build.getEnvironment(listener);
        HgExe hg = new HgExe(this,launcher,build.getBuiltOn(),listener,env);

        ArgumentListBuilder args = new ArgumentListBuilder();
        PossiblyCachedRepo cachedSource = cachedSource(build.getBuiltOn(), launcher, listener, false);
        if (cachedSource != null) {
            if (cachedSource.isUseSharing()) {
                args.add("--config", "extensions.share=");
                args.add("share");
                args.add("--noupdate");
                args.add(cachedSource.getRepoLocation());
            } else {
                args.add("clone");
                args.add("--rev", getBranch(env));
                args.add("--noupdate");
                args.add(cachedSource.getRepoLocation());
            }
        } else {
            args.add("clone");
            args.add("--rev", getBranch(env));
            args.add("--noupdate");
            args.add(source);
        }
        args.add(repository.getRemote());
        int cloneExitCode;
        try {
            cloneExitCode = hg.run(args).join();
        } catch (IOException e) {
            if (causedByMissingHg(e)) {
                listener.error("Failed to clone " + source + " because hg could not be found;" +
                        " check that you've properly configured your Mercurial installation");
            } else {
                e.printStackTrace(listener.error("Failed to clone "+source));
            }
            throw new AbortException("Failed to clone "+source);
        }
        if(cloneExitCode!=0) {
            listener.error("Failed to clone "+source);
            throw new AbortException("Failed to clone "+source);
        }

        if (cachedSource != null && cachedSource.isUseCaches() && !cachedSource.isUseSharing()) {
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

        ArgumentListBuilder upArgs = new ArgumentListBuilder();
        upArgs.add("update");
        upArgs.add("--rev", getBranch(env));
        hg.run(upArgs).pwd(repository).join();

        String tip = hg.tip(repository);
        if (tip != null) {
            build.addAction(new MercurialTagAction(tip));
        }
    }

    @Override
    public void buildEnvVars(AbstractBuild<?,?> build, Map<String, String> env) {
        MercurialTagAction a = build.getAction(MercurialTagAction.class);
        if (a != null) {
            env.put("MERCURIAL_REVISION", a.id);
        }
    }

    @Override
    public ChangeLogParser createChangeLogParser() {
        return new MercurialChangeLogParser(_modules);
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
        return message.startsWith("Cannot run program") && message.endsWith("No such file or directory");
    }

    static boolean CACHE_LOCAL_REPOS = false;
    private @CheckForNull PossiblyCachedRepo cachedSource(Node node, Launcher launcher, TaskListener listener, boolean fromPolling) {
        if (!CACHE_LOCAL_REPOS && source.matches("(file:|[/\\\\]).+")) {
            return null;
        }
        boolean useCaches = false;
        MercurialInstallation _installation = null;
        for (MercurialInstallation inst : MercurialInstallation.allInstallations()) {
            if (inst.getName().equals(installation)) {
                useCaches = inst.isUseCaches();
                _installation = inst;
                break;
            }
        }
        if (!useCaches) {
            return null;
        }
        try {
            FilePath cache = Cache.fromURL(source).repositoryCache(this, node, launcher, listener, fromPolling);
            if (cache != null) {
                return new PossiblyCachedRepo(cache.getRemote(), _installation.isUseCaches(), _installation.isUseSharing());
            } else {
                listener.error("Failed to use repository cache for " + source);
                return null;
            }
        } catch (Exception x) {
            x.printStackTrace(listener.error("Failed to use repository cache for " + source));
            return null;
        }
    }

    private static class PossiblyCachedRepo {
        private final String repoLocation;
        private final boolean useCaches;
        private final boolean useSharing;

        private PossiblyCachedRepo(String repoLocation, boolean useCaches, boolean useSharing) {
            this.repoLocation = repoLocation;
            this.useCaches = useCaches;
            this.useSharing = useSharing;
        }

        public String getRepoLocation() {
            return repoLocation;
        }

        public boolean isUseSharing() {
            return useSharing;
        }

        public boolean isUseCaches() {
            return useCaches;
        }
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

    }

    private static final long serialVersionUID = 1L;

    private static final Logger LOGGER = Logger.getLogger(MercurialSCM.class.getName());
}
