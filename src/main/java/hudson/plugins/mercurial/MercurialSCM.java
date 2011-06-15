package hudson.plugins.mercurial;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.SuppressWarnings;
import hudson.EnvVars;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.Util;
import hudson.FilePath.FileCallable;
import hudson.Launcher.ProcStarter;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.BuildListener;
import hudson.model.Computer;
import hudson.model.Descriptor;
import hudson.model.Node;
import hudson.model.TaskListener;
import hudson.plugins.mercurial.browser.HgBrowser;
import hudson.plugins.mercurial.browser.HgWeb;
import hudson.remoting.VirtualChannel;
import hudson.scm.ChangeLogParser;
import hudson.scm.PollingResult;
import hudson.scm.PollingResult.Change;
import hudson.scm.RepositoryBrowser;
import hudson.scm.RepositoryBrowsers;
import hudson.scm.SCM;
import hudson.scm.SCMDescriptor;
import hudson.scm.SCMRevisionState;
import hudson.util.ArgumentListBuilder;
import hudson.util.ForkOutputStream;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintStream;
import java.io.Serializable;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import static java.util.logging.Level.FINE;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.sf.json.JSONObject;

import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.framework.io.WriterOutputStream;

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
    private final boolean forest;

    private HgBrowser browser;

    @DataBoundConstructor
    public MercurialSCM(String installation, String source, String branch, String modules, String subdir, HgBrowser browser, boolean clean, boolean forest) {
        this.installation = installation;
        this.source = source;
        this.modules = Util.fixNull(modules);
        this.subdir = Util.fixEmptyAndTrim(subdir);
        this.clean = clean;
        this.forest = forest;
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

    /**
* True if we want consider repository a forest
*/
    public boolean isForest() {
        return forest;
    }

    private ArgumentListBuilder findHgExe(AbstractBuild<?,?> build, TaskListener listener, boolean allowDebug) throws IOException, InterruptedException {
        return findHgExe(build.getBuiltOn(), listener, allowDebug);
    }

    /**
* @param allowDebug
* If the caller intends to parse the stdout from Mercurial, pass in false to indicate
* that the optional --debug option shall never be activated.
*/
    ArgumentListBuilder findHgExe(Node node, TaskListener listener, boolean allowDebug) throws IOException, InterruptedException {
        for (MercurialInstallation inst : MercurialInstallation.allInstallations()) {
            if (inst.getName().equals(installation)) {
                // XXX what about forEnvironment?
                ArgumentListBuilder b = new ArgumentListBuilder(inst.executableWithSubstitution(
                        inst.forNode(node, listener).getHome()));
                if (forest) {
                    String downloadForest = inst.getDownloadForest();
                    if (downloadForest != null) {
                        // Uniquify path so if user chooses a different URL it will be downloaded again.
                        FilePath forestPy = node.getRootPath().child(String.format("forest-%08X.py", downloadForest.hashCode()));
                        if (!forestPy.exists()) {
                            listener.getLogger().println("Downloading: " + downloadForest);
                            InputStream is = new URL(downloadForest).openStream();
                            try {
                                forestPy.copyFrom(is);
                            } finally {
                                is.close();
                            }
                        }
                        b.add("--config", "extensions.forest=" + forestPy.getRemote());
                    }
                }
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
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            // Get the list of changed files.
            AbstractProject<?,?> _project = project; // javac considers project.getLastBuild() to be a Run
            Node node = _project.getLastBuiltOn(); // HUDSON-5984: ugly but matches what AbstractProject.poll uses
            ArgumentListBuilder cmd = findHgExe(node, listener, false);
            cmd.add(forest ? "fincoming" : "incoming", "--style", tmpFile.getRemote());
            cmd.add("--no-merges");
            cmd.add("--rev", getBranch());
            cmd.add("--newest-first");
            String cachedSource = cachedSource(node, launcher, listener, true);
            if (cachedSource != null) {
                cmd.add(cachedSource);
            }
            joinWithPossibleTimeout(
                    launch(launcher).cmds(cmd).stdout(new ForkOutputStream(baos, output)).pwd(workspace2Repo(workspace)),
                    true, listener);

            MercurialTagAction cur = parseIncomingOutput(baos, baseline, changedFileNames);
            return new PollingResult(baseline,cur,computeDegreeOfChanges(changedFileNames,output));
        } finally {
            tmpFile.delete();
        }
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

    private MercurialTagAction parseIncomingOutput(ByteArrayOutputStream output, MercurialTagAction baseline, Set<String> result) throws IOException {
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

                if (id.equals(baseline.id)) {
                    // Trim the baseline changeset and earlier.
                    // HUDSON-6337 uses --newest-first to try to order them;
                    // --prune would be better but incoming does not support it.
                    break;
                }
            }
        }

        if (headId==null) {
            return baseline; // no new revisions found
        }
        return new MercurialTagAction(headId);
    }

    @Override
    public boolean checkout(AbstractBuild<?,?> build, Launcher launcher, FilePath workspace, final BuildListener listener, File changelogFile)
            throws IOException, InterruptedException {
        boolean canUpdate = workspace2Repo(workspace).act(new FileCallable<Boolean>() {
            public Boolean invoke(File ws, VirtualChannel channel) throws IOException {
                if (!HgRc.getHgRcFile(ws).exists()) {
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
        });

        if (canUpdate) {
            return update(build, launcher, workspace2Repo(workspace), listener, changelogFile);
        } else {
            return clone(build, launcher, workspace2Repo(workspace), listener, changelogFile);
        }
    }

    /**
* Updates the current repository.
*/
    private boolean update(AbstractBuild<?,?> build, Launcher launcher, FilePath repository, BuildListener listener, File changelogFile)
            throws InterruptedException, IOException {

        EnvVars env = build.getEnvironment(listener);

        HgExe hg = new HgExe(this, launcher, build, listener, env);
        if(clean) {
            if (forest) {
                if(hg.run("fupdate").pwd(repository).join() != 0)
                {
                    listener.error("Failed to clobber local modifications");
                    return false;
                }
            }
            else
            {
                if (hg.run("update", "--clean", ".").pwd(repository).join() != 0) {
                    // Remove corrupted repository
                    try {
                        repository.deleteContents();
                    } catch (IOException e) {
                        e.printStackTrace(listener.error("Failed to clean the repository checkout"));
                        return false;
                    }

                    // Build clone command string
                    ArgumentListBuilder args = new ArgumentListBuilder();
                    args.add("clone");
                    args.add("--rev", getBranch(env));
                    String cachedSource = cachedSource(build.getBuiltOn(), launcher, listener, false);

                    if (cachedSource != null) {
                        args.add(cachedSource);
                    } else {
                        args.add(source);
                    }
                    args.add(repository.getRemote());

                    // Cloning...
                    try {
                        if(hg.run(args).join()!=0) {
                            listener.error("Failed to clone "+source);
                            return false;
                        }
                    } catch (IOException e) {
                        e.printStackTrace(listener.error("Failed to clone "+source));
                        return false;
                    }
                }
            }
            if (forest) {
                StringTokenizer trees = new StringTokenizer(hg.popen(repository, listener, false, new ArgumentListBuilder("ftrees", "--convert")));
                while (trees.hasMoreTokens()) {
                    String tree = trees.nextToken();
                    if (hg.cleanAll().pwd(tree.equals(".") ? repository : repository.child(tree)).join() != 0) {
                        listener.error("Failed to clean unversioned files in " + tree);
                        return false;
                    }
                }
            } else {
                if (hg.cleanAll().pwd(repository).join() != 0) {
                    listener.error("Failed to clean unversioned files");
                    return false;
                }
            }
        }
        FilePath hgBundle = new FilePath(repository, "hg.bundle");

        // delete the file prior to "hg incoming",
        // as one user reported that it causes a failure.
        // The error message was "abort: file 'hg.bundle' already exists"
        hgBundle.delete();

        // calc changelog and create bundle
        final FileOutputStream os = new FileOutputStream(changelogFile);
        int r;
        final String cachedSource;
        try {
            try {
                os.write("<changesets>\n".getBytes());
                ArgumentListBuilder args = findHgExe(build, listener, false);
                args.add(forest ? "fincoming" : "incoming", "--quiet");
                if (!forest) {
                    args.add("--bundle", "hg.bundle");
                }

                args.add("--template", MercurialChangeSet.CHANGELOG_TEMPLATE);

                args.add("--rev", getBranch(env));

                cachedSource = cachedSource(build.getBuiltOn(), launcher, listener, false);
                if (cachedSource != null) {
                    args.add(cachedSource);
                }

                ByteArrayOutputStream errorLog = new ByteArrayOutputStream();

                // mercurial produces text in the platform default encoding, so we need to
                // convert it back to UTF-8
                WriterOutputStream o = new WriterOutputStream(new OutputStreamWriter(os, "UTF-8"), Computer.currentComputer().getDefaultCharset());
                try {
                    r = launch(launcher).cmds(args).envs(env)
                            .stdout(new ForkOutputStream(o,errorLog)).pwd(repository).join();
                } finally {
                    o.flush(); // make sure to commit all output
                }
                if(r!=0 && r!=1) {// 0.9.4 returns 1 for no changes
                    Util.copyStream(new ByteArrayInputStream(errorLog.toByteArray()),listener.getLogger());
                    listener.error("Failed to determine incoming changes");
                    return false;
                }
            } catch (IOException e) {
                listener.error("Failed to pull");
                e.printStackTrace(listener.getLogger());
                return false;
            } finally {
                os.write("</changesets>".getBytes());
            }
        } finally {
            os.close();
        }

        // pull
        if (r == 0 && (hgBundle.exists() || forest)) {
            // if incoming didn't fetch anything, it will return 1. That was for 0.9.3.
            // in 0.9.4 apparently it returns 0.
            try {
                ProcStarter ps;
                if (forest) {
                    ps = hg.run("fpull", "--rev", getBranch(env));
                } else {
                    ps = hg.run("unbundle", "hg.bundle");
                }
                if(ps.pwd(repository).join()!=0) {
                    listener.error("Failed to pull");
                    return false;
                }
                if (cachedSource != null && build.getNumber() % 100 == 0) {
                    // Periodically recreate hardlinks to the cache to save disk space.
                    hg.run("--config", "extensions.relink=", "relink", cachedSource).pwd(repository).join(); // ignore failures
                }
                if(hg.run(forest ? "fupdate" : "update", "--clean", "--rev", getBranch(env)).pwd(repository).join()!=0) {
                    listener.error("Failed to update");
                    return false;
                }
            } catch (IOException e) {
                listener.error("Failed to pull");
                e.printStackTrace(listener.getLogger());
                return false;
            }
        }

        hgBundle.delete(); // do not leave it in workspace

        String tip = hg.tip(repository);
        if (tip != null) {
            build.addAction(new MercurialTagAction(tip));
        }

        return true;
    }

    /**
* Start from scratch and clone the whole repository.
*/
    private boolean clone(AbstractBuild<?,?> build, Launcher launcher, FilePath repository, BuildListener listener, File changelogFile)
            throws InterruptedException, IOException {
        try {
            repository.deleteRecursive();
        } catch (IOException e) {
            e.printStackTrace(listener.error("Failed to clean the repository checkout"));
            return false;
        }

        EnvVars env = build.getEnvironment(listener);
        HgExe hg = new HgExe(this,launcher,build.getBuiltOn(),listener,env);

        ArgumentListBuilder args = new ArgumentListBuilder();
        args.add(forest ? "fclone" : "clone");
        args.add("--rev", getBranch(env));
        String cachedSource = cachedSource(build.getBuiltOn(), launcher, listener, false);
        if (cachedSource != null) {
            args.add(cachedSource);
        } else {
            args.add(source);
        }
        args.add(repository.getRemote());
        try {
            if(hg.run(args).join()!=0) {
                listener.error("Failed to clone "+source);
                return false;
            }
        } catch (IOException e) {
            e.printStackTrace(listener.error("Failed to clone "+source));
            return false;
        }

        if (cachedSource != null) {
            FilePath hgrc = repository.child(".hg/hgrc");
            if (hgrc.exists()) {
                String hgrcText = hgrc.readToString();
                if (!hgrcText.contains(cachedSource)) {
                    listener.error(".hg/hgrc did not contain " + cachedSource + " as expected:\n" + hgrcText);
                    return false;
                }
                hgrc.write(hgrcText.replace(cachedSource, source), null);
            }
            // Passing --rev disables hardlinks, so we need to recreate them:
            hg.run("--config", "extensions.relink=", "relink", cachedSource)
                    .pwd(repository).join(); // ignore failures
        }

        String tip = hg.tip(repository);
        if (tip != null) {
            build.addAction(new MercurialTagAction(tip));
        }

        return createEmptyChangeLog(changelogFile, listener, "changelog");
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
    private @CheckForNull String cachedSource(Node node, Launcher launcher, TaskListener listener, boolean fromPolling) {
        if (!CACHE_LOCAL_REPOS && source.matches("(file:|[/\\\\]).+")) {
            return null;
        }
        if (forest) {
            // Caching forests not supported yet - too complicated.
            return null;
        }
        boolean useCaches = false;
        for (MercurialInstallation inst : MercurialInstallation.allInstallations()) {
            if (inst.getName().equals(installation)) {
                useCaches = inst.isUseCaches();
                break;
            }
        }
        if (!useCaches) {
            return null;
        }
        try {
            FilePath cache = Cache.fromURL(source).repositoryCache(this, node, launcher, listener, fromPolling);
            if (cache != null) {
                return cache.getRemote();
            } else {
                listener.error("Failed to use repository cache for " + source);
                return null;
            }
        } catch (Exception x) {
            x.printStackTrace(listener.error("Failed to use repository cache for " + source));
            return null;
        }
    }

    @Extension
    public static final class DescriptorImpl extends SCMDescriptor<MercurialSCM> {

        private String hgExe;

        public DescriptorImpl() {
            super(HgBrowser.class);
            load();
        }

        /**
* {@inheritDoc}
*
* Due to compatibility issues with older version we implement this ourselves instead of relying
* on the parent method. Kohsuke implemented a fix for this in the core (r21961), so we may drop
* this function after 1.325 is released.
*
* @todo: remove this function after 1.325 is released.
*
* @see <a href="https://hudson.dev.java.net/issues/show_bug.cgi?id=4514">#4514</a>
* @see <a href="http://fisheye4.atlassian.com/changelog/hudson/trunk/hudson?cs=21961">core fix</a>
*/
        @Override
        public List<Descriptor<RepositoryBrowser<?>>> getBrowserDescriptors() {
            return RepositoryBrowsers.filter(HgBrowser.class);
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