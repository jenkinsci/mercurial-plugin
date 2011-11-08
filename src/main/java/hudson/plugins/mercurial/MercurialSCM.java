package hudson.plugins.mercurial;

import static java.util.logging.Level.FINE;
import hudson.EnvVars;
import hudson.Extension;
import hudson.FilePath;
import hudson.FilePath.FileCallable;
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
import hudson.plugins.mercurial.build.BuildData;
import hudson.plugins.mercurial.build.BuildChooser;
import hudson.plugins.mercurial.build.BuildChooserDescriptor;
import hudson.plugins.mercurial.build.DefaultBuildChooser;
import hudson.remoting.VirtualChannel;
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
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
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

    private BuildChooser buildChooser;

    public MercurialSCM(String installation, String source, String branch, String modules, String subdir, HgBrowser browser, boolean clean) {
        this(installation, source, branch, modules, subdir, browser, clean, new DefaultBuildChooser());
    }


    @DataBoundConstructor
    public MercurialSCM(String installation, String source, String branch, String modules, String subdir, HgBrowser browser, boolean clean, BuildChooser buildChooser) {
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
        this.buildChooser = buildChooser;
        buildChooser.scm = this;
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
        if (buildChooser == null) {
            buildChooser = new DefaultBuildChooser();
        }
        buildChooser.scm = this;
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
        // MercurialTagAction is added to the build during checkout,
        // but accoring to HUDSON-7723 this MAY fail in some unknown circumstances
        HgExe hg = new HgExe(this, launcher, build, listener, build.getEnvironment(listener));
        String tip = hg.tip(workspace2Repo(build.getWorkspace()));
        return tip != null ? new MercurialTagAction(tip, "tip") : null;
    }

    private static final String FILES_STYLE = "changeset = 'id:{node}\\nfiles:{files}\\n'\n" + "file = '{file}:'";

    @Override
    protected PollingResult compareRemoteRevisionWith(AbstractProject<?, ?> project, Launcher launcher, FilePath workspace,
            TaskListener listener, SCMRevisionState _baseline) throws IOException, InterruptedException {

        final AbstractBuild lastBuild = project.getLastBuild();
        if (lastBuild == null) {
            // If we've never been built before, well, gotta build!
            return PollingResult.BUILD_NOW;
        }

        MercurialTagAction baseline = (MercurialTagAction)_baseline;
        PrintStream output = listener.getLogger();
        Node node = project.getLastBuiltOn(); // HUDSON-5984: ugly but matches what AbstractProject.poll uses

        FilePath repository = workspace2Repo(workspace);
        pull(launcher, repository, listener, output, node);

        String revisionToBuild = buildChooser.getRevisionToBuild(lastBuild, launcher, workspace, listener);

        return "tip".equals(revisionToBuild) ? PollingResult.NO_CHANGES : PollingResult.SIGNIFICANT;
    }

    private transient Pattern branchSpec;

    private boolean matches(String name) {
        if (branchSpec == null) {
            branchSpec = Pattern.compile("\\Q" + getBranch().replace("*", "\\E.*\\Q") + "\\E");
        }
        return branchSpec.matcher(name).matches();
    }

    /**
     * Compute the changes compared to existing state
     */
    public List<ChangeSet> changeSet(Launcher launcher, FilePath workspace, TaskListener listener, String branch,
                                     String baseline, PrintStream output, Node node, FilePath repository) throws IOException, InterruptedException {
        // Mercurial requires the style file to be in a file..
        FilePath tmpFile = workspace.createTextTempFile("tmp", "style", FILES_STYLE);
        try {
            ArgumentListBuilder logCmd = findHgExe(node, listener, false);
            logCmd.add("log", "--style", tmpFile.getRemote());
            if (branch != null) {
                logCmd.add("--branch", branch);
            }
            logCmd.add("--no-merges");

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ForkOutputStream fos = new ForkOutputStream(baos, output);

            logCmd.add("--prune", baseline);
            joinWithPossibleTimeout(
                    launch(launcher).cmds(logCmd).stdout(fos).pwd(repository),
                    true, listener);

            return parsePollingLogOutput(baos);
        } finally {
            tmpFile.delete();
        }
    }

    public Collection<MercurialTagAction> getActiveBranches(AbstractBuild<?, ?> build, Launcher launcher, TaskListener listener)
    throws IOException, InterruptedException {

        ArgumentListBuilder cmd = findHgExe(build, listener, false);
        cmd.add("branches", "--active");
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        joinWithPossibleTimeout(
                    launch(launcher).cmds(cmd)
                            .stdout(new ForkOutputStream(baos, listener.getLogger()))
                            .pwd(workspace2Repo(build.getWorkspace())),
                    true, listener);

        Collection<MercurialTagAction> branches = new ArrayList<MercurialTagAction>();
        BufferedReader in = new BufferedReader(new InputStreamReader(
                new ByteArrayInputStream(baos.toByteArray())));
        String line;
        while ((line = in.readLine()) != null) {
            String sha = line.substring(line.indexOf(':')+1);
            String name = line.substring(0, line.indexOf(" "));
            branches.add(new MercurialTagAction(sha, name));
        }
        return branches;
    }



    private void pull(Launcher launcher, FilePath repository, TaskListener listener, PrintStream output, Node node) throws IOException, InterruptedException {
        ArgumentListBuilder cmd = findHgExe(node, listener, false);
        cmd.add("pull");
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

    public Change computeDegreeOfChanges(ChangeSet changeSet, PrintStream output) {
        LOGGER.log(FINE, "Changed file names: {0}", changeSet.files);

        if (changeSet.files.isEmpty()) {
            return Change.NONE;
        }

        Set<String> depchanges = dependentChanges(changeSet.files);
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

    private List<ChangeSet> parsePollingLogOutput(ByteArrayOutputStream output) throws IOException {
        List<ChangeSet> incoming = new ArrayList<ChangeSet>();

        BufferedReader in = new BufferedReader(new InputStreamReader(
                new ByteArrayInputStream(output.toByteArray())));

        ChangeSet changeSet = null;
        String line;
        while ((line = in.readLine()) != null) {
            Matcher matcher = FILES_LINE.matcher(line);
            if (matcher.matches()) {
                for (String s : matcher.group(1).split(":")) {
                    if (s.length() > 0) {
                        changeSet.files.add(s);
                    }
                }
            }
            if (line.startsWith("id:")) {
                String id = line.substring(3);
                changeSet = new ChangeSet(id);
                incoming.add(changeSet);
            }
            if (line.startsWith("branch:")) {
                String branch = line.substring(7);
	            if (branch == null) {
                    branch = "default";
                }
                changeSet.branch = branch;
            }
        }

        return incoming;
    }

    public static class ChangeSet {
        String id;
        String branch;
        Set<String> files = new HashSet<String>();

        private ChangeSet(String id) {
            this.id = id;
        }
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
        boolean canReuseExistingWorkspace = repository.act(
                new CheckForReusableWorkspace(jobShouldUseSharing, listener));

        String branchToBuild;
        if (canReuseExistingWorkspace) {
            branchToBuild = update(build, launcher, repository, listener);
        } else {
            branchToBuild = clone(build, launcher, repository, listener);
        }

        if (branchToBuild == null) {
            return false;
        }

        try {
            determineChanges(build, launcher, listener, changelogFile, repository, branchToBuild);
        } catch (IOException e) {
            listener.error("Failed to capture change log");
            e.printStackTrace(listener.getLogger());
            return false;
        }

        EnvVars env = build.getEnvironment(listener);
        HgExe hg = new HgExe(this,launcher,build.getBuiltOn(),listener,env);
        String head = hg.head(repository, branchToBuild);
        if (head != null) {
            MercurialTagAction revision = new MercurialTagAction(head, branchToBuild);
            build.addAction(revision);
            BuildData buildData = BuildData.getBuildData(source, build);
            buildData.saveBuild(revision);
            build.addAction(buildData);
        }

        return true;
    }

    private void determineChanges(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener, File changelogFile,
                                  FilePath repository, String branch) throws IOException, InterruptedException {
        
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
                args.add("--rev", branch + ":0");
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
    private String update(AbstractBuild<?, ?> build, Launcher launcher, FilePath repository, BuildListener listener)
            throws InterruptedException, IOException {
        EnvVars env = build.getEnvironment(listener);

        HgExe hg = new HgExe(this, launcher, build, listener, env);
        try {
            pull(launcher, repository, listener, new PrintStream(new NullOutputStream()), Computer.currentComputer().getNode());
        } catch (IOException e) {
            listener.error("Failed to pull");
            e.printStackTrace(listener.getLogger());
            return null;
        }

        String branchToBuild = buildChooser.getRevisionToBuild(build, launcher, repository, listener);

        try {
            if(hg.run("update", "--clean", "--rev", branchToBuild).pwd(repository).join()!=0) {
                listener.error("Failed to update");
                return null;
            }
        } catch (IOException e) {
            listener.error("Failed to update");
            e.printStackTrace(listener.getLogger());
            return null;
        }
        
        if(clean) {
            if (hg.cleanAll().pwd(repository).join() != 0) {
                listener.error("Failed to clean unversioned files");
                return null;
            }
        }
        return branchToBuild;
    }

    /**
     * Start from scratch and clone the whole repository.
     */
    private String clone(AbstractBuild<?,?> build, Launcher launcher, FilePath repository, BuildListener listener)
            throws InterruptedException, IOException {
        try {
            repository.deleteRecursive();
        } catch (IOException e) {
            e.printStackTrace(listener.error("Failed to clean the repository checkout"));
            return null;
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
                args.add("--noupdate");
                args.add(cachedSource.getRepoLocation());
            }
        } else {
            args.add("clone");
            args.add("--noupdate");
            args.add(source);
        }
        args.add(repository.getRemote());
        try {
            if(hg.run(args).join()!=0) {
                listener.error("Failed to clone "+source);
                return null;
            }
        } catch (IOException e) {
            e.printStackTrace(listener.error("Failed to clone "+source));
            return null;
        }

        if (cachedSource != null && cachedSource.isUseCaches() && !cachedSource.isUseSharing()) {
            FilePath hgrc = repository.child(".hg/hgrc");
            if (hgrc.exists()) {
                String hgrcText = hgrc.readToString();
                if (!hgrcText.contains(cachedSource.getRepoLocation())) {
                    listener.error(".hg/hgrc did not contain " + cachedSource.getRepoLocation() + " as expected:\n" + hgrcText);
                    return null;
                }
                hgrc.write(hgrcText.replace(cachedSource.getRepoLocation(), source), null);
            }
            // Passing --rev disables hardlinks, so we need to recreate them:
            hg.run("--config", "extensions.relink=", "relink", cachedSource.getRepoLocation())
                    .pwd(repository).join(); // ignore failures
        }

        String branchToBuild = buildChooser.getRevisionToBuild(build, launcher, repository, listener);

        ArgumentListBuilder upArgs = new ArgumentListBuilder();
        upArgs.add("update");
        upArgs.add("--rev", branchToBuild);
        hg.run(upArgs).pwd(repository).join();

        return branchToBuild;
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

    private final class CheckForReusableWorkspace implements FileCallable<Boolean> {
        private final boolean jobShouldUseSharing;
        private final BuildListener listener;
        private static final long serialVersionUID = 1L;

        private CheckForReusableWorkspace(boolean jobShouldUseSharing,
                BuildListener listener) {
            this.jobShouldUseSharing = jobShouldUseSharing;
            this.listener = listener;
        }

        public Boolean invoke(File ws, VirtualChannel channel) throws IOException {
            if (!HgRc.getHgRcFile(ws).exists()) {
                return false;
            }

            boolean jobUsesSharing = HgRc.getShareFile(ws).exists();

            if (jobShouldUseSharing && !jobUsesSharing) {
                return false;
            }
            if (jobUsesSharing && !jobShouldUseSharing) {
                return false;
            }

            HgRc hgrc = new HgRc(ws);
            return canUpdate(hgrc);
        }

        private boolean canUpdate(HgRc ini) {
            String upstream = ini.getSection("paths").get("default");
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
