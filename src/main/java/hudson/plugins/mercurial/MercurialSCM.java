package hudson.plugins.mercurial;

import edu.umd.cs.findbugs.annotations.SuppressWarnings;
import hudson.AbortException;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.Proc;
import hudson.Util;
import hudson.FilePath.FileCallable;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.BuildListener;
import hudson.model.Computer;
import hudson.model.Descriptor;
import hudson.model.Hudson;
import hudson.model.TaskListener;
import hudson.plugins.mercurial.browser.HgBrowser;
import hudson.plugins.mercurial.browser.HgWeb;
import hudson.remoting.VirtualChannel;
import hudson.scm.ChangeLogParser;
import hudson.scm.RepositoryBrowser;
import hudson.scm.RepositoryBrowsers;
import hudson.scm.SCM;
import hudson.scm.SCMDescriptor;
import hudson.util.ArgumentListBuilder;
import hudson.util.ForkOutputStream;
import hudson.util.VersionNumber;

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
import java.util.HashSet;
import java.util.Locale;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.sf.json.JSONObject;

import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.framework.io.ByteBuffer;
import org.kohsuke.stapler.framework.io.WriterOutputStream;

/**
 * Mercurial SCM.
 * 
 * @author Kohsuke Kawaguchi
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
    @SuppressWarnings("SE_TRANSIENT_FIELD_NOT_RESTORED")
    private transient final Set<String> _modules;
    // Same thing, but not parsed for jelly.
    private final String modules;
    
    /**
     * In-repository branch to follow. Null indicates "default". 
     */
    private final String branch;

    private final boolean clean;

    private HgBrowser browser;

    @DataBoundConstructor
    public MercurialSCM(String installation, String source, String branch, String modules, HgBrowser browser, boolean clean) {
        this.installation = installation;
        this.source = source;
        this.modules = Util.fixNull(modules);
        this.clean = clean;

        if (this.modules.trim().length() > 0) {
            _modules = new HashSet<String>();
            // split by commas and whitespace, except "\ "
            for (String r : this.modules.split("(?<!\\\\)[ \\r\\n,]+")) {
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

        // normalization
        branch = Util.fixEmpty(branch);
        if(branch!=null && branch.equals("default"))
            branch = null;
        this.branch = branch;

        this.browser = browser;
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
     * True if we want clean check out each time. This means deleting everything in the workspace
     * (except <tt>.hg</tt>)
     */
    public boolean isClean() {
        return clean;
    }

    private String findHgExe(TaskListener listener) throws IOException, InterruptedException {
        for (MercurialInstallation inst : MercurialInstallation.allInstallations()) {
            if (inst.getName().equals(installation)) {
                // XXX what about forEnvironment?
                return inst.forNode(Computer.currentComputer().getNode(), listener).getHome() + "/bin/hg";
            }
        }
        return getDescriptor().getHgExe();
    }

    private static final String FILES_STYLE = "changeset = 'files:{files}\\n'\n" + "file = '{file}:'";

    @Override
    public boolean pollChanges(AbstractProject project, Launcher launcher, FilePath workspace, TaskListener listener) throws IOException, InterruptedException {
        PrintStream output = listener.getLogger();

        // Mercurial requires the style file to be in a file..
        Set<String> changedFileNames = new HashSet<String>();
        FilePath tmpFile = workspace.createTextTempFile("tmp", "style", FILES_STYLE);
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            // Get the list of changed files.
            ArgumentListBuilder cmd = new ArgumentListBuilder();
            cmd.add(findHgExe(listener), "incoming", "--style" , tmpFile.getRemote());
            cmd.add("-r", getBranch());
            joinWithTimeout(
                    launcher.launch().cmds(cmd).stdout(new ForkOutputStream(baos, output)).pwd(workspace).start(),
                    /* #4528: not in JDK 5: 1, TimeUnit.HOURS*/60 * 60, TimeUnit.SECONDS, listener);
            parseIncomingOutput(baos, changedFileNames);
        } finally {
            tmpFile.delete();
        }

        if (changedFileNames.isEmpty()) {
            output.println("No changes");
            return false;
        }

        if (dependentChanges(changedFileNames).isEmpty()) {
            output.println("Non-dependent changes detected");
            return false;
        }

        output.println("Dependent changes detected");
        return true;
    }

    // XXX maybe useful enough to make a convenience method on Proc?
    private static final ExecutorService executor = Executors.newCachedThreadPool();
    private int joinWithTimeout(final Proc proc, final long timeout, final TimeUnit unit,
            final TaskListener listener) throws IOException, InterruptedException {
        final CountDownLatch latch = new CountDownLatch(1);
        try {
            executor.submit(new Runnable() {
                public void run() {
                    try {
                        if (!latch.await(timeout, unit)) {
                            listener.error("Timeout after " + timeout + " " +
                                    unit.toString().toLowerCase(Locale.ENGLISH));
                            proc.kill();
                        }
                    } catch (Exception x) {
                        listener.error(x.toString());
                    }
                }
            });
            return proc.join();
        } finally {
            latch.countDown();
        }
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

    private void parseIncomingOutput(ByteArrayOutputStream output, Set<String> result) throws IOException {
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
        }
    }

    @Override
    public boolean checkout(AbstractBuild build, Launcher launcher, FilePath workspace, final BuildListener listener, File changelogFile) throws IOException, InterruptedException {
        boolean canUpdate = workspace.act(new FileCallable<Boolean>() {
            public Boolean invoke(File ws, VirtualChannel channel) throws IOException {
                if(!HgRc.getHgRcFile(ws).exists())
                    return false;
                HgRc hgrc = new HgRc(ws);
                return canUpdate(hgrc);
            }

            private boolean canUpdate(HgRc ini) {
                String upstream = ini.getSection("paths").get("default");
                if(upstream==null)  return false;

                if(upstream.equals(source)) return true;
                if((upstream+'/').equals(source))   return true;
                if (source.startsWith("file:/") && new File(upstream).toURI().toString().equals(source)) return true;
                listener.error(
                        "Workspace reports paths.default as " + upstream +
                        "\nwhich looks different than " + source +
                        "\nso falling back to fresh clone rather than incremental update");
                return false;
            }
        });

        if(canUpdate)
            return update(build,launcher,workspace,listener,changelogFile);
        else
            return clone(build,launcher,workspace,listener,changelogFile);
    }

    /**
     * Updates the current workspace.
     */
    private boolean update(AbstractBuild<?,?> build, Launcher launcher, FilePath workspace, BuildListener listener, File changelogFile) throws InterruptedException, IOException {
        if(clean) {
            if (launcher.launch().cmds(findHgExe(listener), "update", "-C", ".")
                .envs(build.getEnvironment(listener)).stdout(listener)
                .pwd(workspace).join() != 0) {
                listener.error("Failed to clobber local modifications");
                return false;
            }
            if (launcher.launch().cmds(findHgExe(listener), "--config", "extensions.purge=", "clean", "--all")
                    .envs(build.getEnvironment(listener)).stdout(listener).pwd(workspace).join() != 0) {
                listener.error("Failed to clean unversioned files");
                return false;
            }
        }
        FilePath hgBundle = new FilePath(workspace, "hg.bundle");

        // delete the file prior to "hg incoming",
        // as one user reported that it causes a failure.
        // The error message was "abort: file 'hg.bundle' already exists"
        hgBundle.delete();

        // calc changelog and create bundle
        FileOutputStream os = new FileOutputStream(changelogFile);
        os.write("<changesets>\n".getBytes());
        int r;
        try {
            ArgumentListBuilder args = new ArgumentListBuilder();
            args.add(findHgExe(listener),"incoming","--quiet","--bundle","hg.bundle");

            String template;

            if(isHg10orLater()) {
                template = MercurialChangeSet.CHANGELOG_TEMPLATE_10x;
            } else {
                template = MercurialChangeSet.CHANGELOG_TEMPLATE_09x;
                // Pre-1.0 Hg fails to honor {file_adds} and {file_dels} without --debug.
                args.add("--debug");
            }

            args.add("--template", template);

            args.add("-r", getBranch());

            ByteArrayOutputStream errorLog = new ByteArrayOutputStream();

            // mercurial produces text in the platform default encoding, so we need to
            // convert it back to UTF-8
            WriterOutputStream o = new WriterOutputStream(new OutputStreamWriter(os, "UTF-8"));
            try {
                r = launcher.launch().cmds(args).envs(build.getEnvironment(listener))
                        .stdout(new ForkOutputStream(o,errorLog)).pwd(workspace).join();
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
            os.close();
        }

        // pull
        if(r==0 && hgBundle.exists())
            // if incoming didn't fetch anything, it will return 1. That was for 0.9.3.
            // in 0.9.4 apparently it returns 0.
            try {
                if(launcher.launch()
                    .cmds(findHgExe(listener),"pull","hg.bundle")
                    .envs(build.getEnvironment(listener)).stdout(listener).pwd(workspace).join()!=0) {
                    listener.error("Failed to pull");
                    return false;
                }
                if(launcher.launch()
                    .cmds(findHgExe(listener),"up","-C", "-r", getBranch())
                    .envs(build.getEnvironment(listener)).stdout(listener).pwd(workspace).join()!=0) {
                    listener.error("Failed to update");
                    return false;
                }
            } catch (IOException e) {
                listener.error("Failed to pull");
                e.printStackTrace(listener.getLogger());
                return false;
            }

        hgBundle.delete(); // do not leave it in workspace

        addTagActionToBuild(build, launcher, workspace, listener);

        return true;
    }

    private void addTagActionToBuild(AbstractBuild<?, ?> build, Launcher launcher, FilePath workspace, BuildListener listener) throws IOException, InterruptedException {
        ByteArrayOutputStream rev = new ByteArrayOutputStream();
        if (launcher.launch().cmds(findHgExe(listener), "log", "-r", ".", "--template", "{node}")
                .pwd(workspace).stdout(rev).join()!=0) {
            listener.error("Failed to id");
            listener.getLogger().write(rev.toByteArray());
            throw new AbortException();
        } else {
            String id = rev.toString();
            if(!REVISIONID_PATTERN.matcher(id).matches()) {
                listener.error("Expected to get an id but got "+id+" instead.");
                throw new AbortException();
            }
            build.addAction(new MercurialTagAction(id));
        }
    }

    /**
     * Returns true if we think our Mercurial is 1.0 or newer.
     */
    @SuppressWarnings("DLS_DEAD_LOCAL_STORE")
    private boolean isHg10orLater() {
        boolean hg10 = false;
        try {
            String v = getDescriptor().findHgVersion();
            try {
                if (v != null && new VersionNumber(v).compareTo(new VersionNumber("1.0"))>=0) {
                    hg10 = true;
                }
            } catch (IllegalArgumentException e) {
                LOGGER.log(Level.INFO,"Failed to parse Mercurial version number: "+v,e);
            }
        } catch (IOException x) {
            // don't know, never mind
        } catch (InterruptedException x) {
            // ditto
        }
        return hg10;
    }


    /**
     * Start from scratch and clone the whole repository.
     */
    private boolean clone(AbstractBuild<?,?> build, Launcher launcher, FilePath workspace, BuildListener listener, File changelogFile) throws InterruptedException, IOException {
        try {
            workspace.deleteRecursive();
        } catch (IOException e) {
            e.printStackTrace(listener.error("Failed to clean the workspace"));
            return false;
        }

        ArgumentListBuilder args = new ArgumentListBuilder();
        args.add(findHgExe(listener),"clone");
        args.add("-r", getBranch());
        args.add(source,workspace.getRemote());
        try {
            if(launcher.launch().cmds(args).envs(build.getEnvironment(listener)).stdout(listener).join()!=0) {
                listener.error("Failed to clone "+source);
                return false;
            }
        } catch (IOException e) {
            e.printStackTrace(listener.error("Failed to clone "+source));
            return false;
        }

        addTagActionToBuild(build, launcher, workspace, listener);

        return createEmptyChangeLog(changelogFile, listener, "changelog");
    }

    @Override
    public void buildEnvVars(AbstractBuild build, Map<String, String> env) {
        MercurialTagAction a = build.getAction(MercurialTagAction.class);
        if (a!=null)
            env.put("MERCURIAL_REVISION",a.id);
    }

    @Override
    public ChangeLogParser createChangeLogParser() {
        return new MercurialChangeLogParser();
    }

    @Override
    public DescriptorImpl getDescriptor() {
        return (DescriptorImpl) super.getDescriptor();
    }

    public String getModules() {
        return modules;
    }

    @Extension
    public static final class DescriptorImpl extends SCMDescriptor<MercurialSCM> {

        private String hgExe;
        private transient String version;

        public DescriptorImpl() {
            super(HgBrowser.class);
            load();
        }
        
        /**
         * {@inheritDoc}
         * 
         * Due to compatibility issues with older version we implement this ourselves instead of relying
         * on the parent method. Koshuke implemented a fix for this in the core (r21961), so we may drop
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
            if(hgExe==null) return "hg";
            return hgExe;
        }

        @Override
        public SCM newInstance(StaplerRequest req, JSONObject formData) throws FormException {
            return super.newInstance(req, formData);
        }

        @Override
        public boolean configure(StaplerRequest req, JSONObject json) throws FormException {
            hgExe = req.getParameter("mercurial.hgExe");
            version = null;
            save();
            return true;
        }

        /* XXX restore insofar as that is possible:
        public FormValidation doHgExeCheck(StaplerRequest req, StaplerResponse rsp) throws IOException, ServletException {
            return FormValidation.validateExecutable(req.getParameter("value"), new FormValidation.FileValidator() {
                public FormValidation validate(File f) {
                    try {
                        String v = findHgVersion();
                        if(v != null) {
                            try {
                                if(new VersionNumber(v).compareTo(V0_9_4)>=0) {
                                    return FormValidation.ok(); // right version
                                } else {
                                    return FormValidation.error("This hg is ver."+v+" but we need 0.9.4+");
                                }
                            } catch (IllegalArgumentException e) {
                                return FormValidation.warning("Hudson can't tell if this hg is 0.9.4 or later (detected version is %s)",v);
                            }
                        }
                        v = findHgVersion(UUID_VERSION_STRING);
                        if(v!=null) {
                            return FormValidation.warning("Hudson can't tell if this hg is 0.9.4 or later (detected version is %s)",v);
                        }
                    } catch (IOException e) {
                        // failed
                    } catch (InterruptedException e) {
                        // failed
                    }
                    return FormValidation.error("Unable to check hg version");
                }
            });
        }
        */

        private String findHgVersion() throws IOException, InterruptedException {
            return findHgVersion(VERSION_STRING);
        }

        private String findHgVersion(Pattern p) throws IOException, InterruptedException {
            if (version != null) {
                return version;
            }
            ByteBuffer baos = new ByteBuffer();
            Proc proc = Hudson.getInstance().createLauncher(TaskListener.NULL).launch()
                    .cmds(getHgExe(), "version").stdout(baos).start();
            proc.join();
            Matcher m = p.matcher(baos.toString());
            if (m.find()) {
                version = m.group(1);
                return version;
            } else {
                return null;
            }
        }

        /**
         * Pattern matcher for the version number.
         */
        private static final Pattern VERSION_STRING = Pattern.compile("\\(version ([0-9.]+)");
    }


    private static final long serialVersionUID = 1L;

    private static final Logger LOGGER = Logger.getLogger(MercurialSCM.class.getName());

    /**
     * Pattern that matches revision ID.
     */
    private static final Pattern REVISIONID_PATTERN = Pattern.compile("[0-9a-f]{40}");

}
