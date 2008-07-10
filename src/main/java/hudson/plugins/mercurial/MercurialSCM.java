package hudson.plugins.mercurial;

import hudson.*;
import hudson.FilePath.FileCallable;
import hudson.model.*;
import hudson.plugins.mercurial.browser.HgWeb;
import hudson.remoting.VirtualChannel;
import hudson.scm.ChangeLogParser;
import hudson.scm.RepositoryBrowsers;
import hudson.scm.SCM;
import hudson.scm.SCMDescriptor;
import hudson.util.*;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;
import org.kohsuke.stapler.framework.io.WriterOutputStream;

import javax.servlet.ServletException;
import java.io.*;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.Arrays;
import java.util.logging.Logger;
import java.util.logging.Level;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

/**
 * Mercurial SCM.
 * 
 * @author Kohsuke Kawaguchi
 */
public class MercurialSCM extends SCM implements Serializable {
    /**
     * Source repository URL from which we pull.
     */
    private final String source;
    
    /**
     * Prefixes of files within the repository which we're dependent on.
     * Storing as member variable so as to only parse the dependencies string once.
     */
    private transient final Set<String> _modules = new HashSet<String>();
    // Same thing, but not parsed for jelly.
    private final String modules;
    
    /**
     * In-repository branch to follow. Null indicates "default". 
     */
    private final String branch;

    private HgWeb browser;

    @DataBoundConstructor
    public MercurialSCM(String source, String branch, String modules, HgWeb browser) {
        this.source = source;
        this.modules = modules;

        // split by commas and whitespace, except "\ "
        String[] r = modules.split("(?<!\\\\)[ \\r\\n,]+");
        for (int i = 0; i < r.length; i++) {
            // now replace "\ " to " ".
            r[i] = r[i].replaceAll("\\\\ ", " ");

            // Strip leading slashes
            while (r[i].startsWith("/"))
                r[i] = r[i].substring(1);

            // Use unix file path separators
            r[i] = r[i].replace('\\', '/');
        }

        this._modules.addAll(Arrays.asList(r));

        // normalization
        branch = Util.fixEmpty(branch);
        if(branch!=null && branch.equals("default"))
            branch = null;
        this.branch = branch;

        this.browser = browser;
    }

    /**
     * Gets the source repository path.
     * Either URL or local file path.
     */
    public String getSource() {
        return source;
    }

    /**
     * In-repository branch to follow. Null indicates "default".
     */
    public String getBranch() {
        return branch;
    }

    @Override
    public HgWeb getBrowser() {
        return browser;
    }

    private static final String FILES_STYLE = "changeset = 'files:{files}\\n'\n" + "file = '{file}:'";

    @Override
    public boolean pollChanges(AbstractProject project, Launcher launcher, FilePath workspace, TaskListener listener) throws IOException, InterruptedException {
        PrintStream output = listener.getLogger();

        // Mercurial requires the style file to be in a file..
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        File tmpFile = File.createTempFile("tmp", "style");
        TextFile tmpTxt = new TextFile(tmpFile);
        tmpTxt.write(FILES_STYLE);

        // Get the list of changed files.
        launcher.launch(
                new String[]{getDescriptor().getHgExe(), "incoming", "--style", tmpFile.getCanonicalPath()},
                EnvVars.masterEnvVars, new ForkOutputStream(baos, output), workspace).join();


        Set<String> changedFileNames = new HashSet<String>();
        parseIncomingOutput(baos, changedFileNames);

        tmpFile.delete();

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
    public boolean checkout(AbstractBuild build, Launcher launcher, FilePath workspace, BuildListener listener, File changelogFile) throws IOException, InterruptedException {
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
                return source.startsWith("file:/") && new File(upstream).toURI().toString().equals(source);
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
            args.add(getDescriptor().getHgExe(),"incoming","--quiet","--bundle","hg.bundle");
            String template = MercurialChangeSet.CHANGELOG_TEMPLATE_09x;
            try {
                String v = getDescriptor().findHgVersion();
                try {
                    if (v != null && new VersionNumber(v).compareTo(new VersionNumber("1.0"))>=0) {
                        template = MercurialChangeSet.CHANGELOG_TEMPLATE_10x;
                    }
                } catch (IllegalArgumentException e) {
                    LOGGER.log(Level.INFO,"Failed to parse Mercurial version number: "+v,e);
                }
            } catch (IOException x) {
                // don't know, never mind
            } catch (InterruptedException x) {
                // ditto
            }
            args.add("--template", template);

            boolean hg10 = false;
            try {
                String v = getDescriptor().findHgVersion();
                if (v != null && new VersionNumber(v).compareTo(new VersionNumber("1.0"))>=0) {
                    hg10 = true;
                }
            } catch (IOException x) {
                // don't know, never mind
            } catch (InterruptedException x) {
                // ditto
            }
            if (!hg10) {
                // Pre-1.0 Hg fails to honor {file_adds} and {file_dels} without --debug.
                args.add("--debug");
            }

            if(branch!=null)    args.add("-r",branch);

            ByteArrayOutputStream errorLog = new ByteArrayOutputStream();

            // mercurial produces text in the platform default encoding, so we need to
            // convert it back to UTF-8
            WriterOutputStream o = new WriterOutputStream(new OutputStreamWriter(os, "UTF-8"));
            try {
                r = launcher.launch(args.toCommandArray(),build.getEnvVars(), new ForkOutputStream(o,errorLog), workspace).join();
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
                if(launcher.launch(
                    new String[]{getDescriptor().getHgExe(),"pull","-u","hg.bundle"},
                    build.getEnvVars(),listener.getLogger(),workspace).join()!=0) {
                    listener.error("Failed to pull");
                    return false;
                }
            } catch (IOException e) {
                listener.error("Failed to pull");
                return false;
            }

        hgBundle.delete(); // do not leave it in workspace

        return true;
    }


    /**
     * Start from scratch and clone the whole repository.
     */
    private boolean clone(AbstractBuild<?,?> build, Launcher launcher, FilePath workspace, BuildListener listener, File changelogFile) throws InterruptedException {
        try {
            workspace.deleteRecursive();
        } catch (IOException e) {
            e.printStackTrace(listener.error("Failed to clean the workspace"));
            return false;
        }

        ArgumentListBuilder args = new ArgumentListBuilder();
        args.add(getDescriptor().getHgExe(),"clone");
        if(branch!=null)    args.add("-r",branch);
        args.add(source,workspace.getRemote());
        try {
            if(launcher.launch(args.toCommandArray(),build.getEnvVars(),listener.getLogger(),null).join()!=0) {
                listener.error("Failed to clone "+source);
                return false;
            }
        } catch (IOException e) {
            e.printStackTrace(listener.error("Failed to clone "+source));
            return false;
        }

        return createEmptyChangeLog(changelogFile, listener, "changelog");
    }

    @Override
    public void buildEnvVars(AbstractBuild build, Map<String, String> env) {
    }

    @Override
    public ChangeLogParser createChangeLogParser() {
        return new MercurialChangeLogParser();
    }

    @Override
    public DescriptorImpl getDescriptor() {
        return DescriptorImpl.DESCRIPTOR;
    }

    public String getModules() {
        return modules;
    }

    public static final class DescriptorImpl extends SCMDescriptor<MercurialSCM> {
        public static final DescriptorImpl DESCRIPTOR = new DescriptorImpl();

        private String hgExe;
        private transient String version;

        private  DescriptorImpl() {
            super(MercurialSCM.class, HgWeb.class);
            load();
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
        public SCM newInstance(StaplerRequest req) throws FormException {
            //return req.bindParameters(MercurialSCM.class,"mercurial.");
            return new MercurialSCM(
                    req.getParameter("mercurial.source"),
                    req.getParameter("mercurial.branch"),
                    req.getParameter("mercurial.modules"),
                    RepositoryBrowsers.createInstance(HgWeb.class, req, "mercurial.browser"));
        }

        @Override
        public boolean configure(StaplerRequest req) throws FormException {
            hgExe = req.getParameter("mercurial.hgExe");
            version = null;
            save();
            return true;
        }

        public void doHgExeCheck(StaplerRequest req, StaplerResponse rsp) throws IOException, ServletException {
            new FormFieldValidator.Executable(req,rsp) {
                @Override
                protected void checkExecutable(File exe) throws IOException, ServletException {
                    try {
                        String v = findHgVersion();
                        if(v != null) {
                            try {
                                if(new VersionNumber(v).compareTo(V0_9_4)>=0) {
                                    ok(); // right version
                                } else {
                                    error("This hg is ver."+v+" but we need 0.9.4+");
                                }
                            } catch (IllegalArgumentException e) {
                                warning("Hudson can't tell if this hg is 0.9.4 or later (detected version is %s)",v);
                            }
                            return;
                        }
                        v = findHgVersion(UUID_VERSION_STRING);
                        if(v!=null) {
                            warning("Hudson can't tell if this hg is 0.9.4 or later (detected version is %s)",v);
                            return;
                        }
                    } catch (IOException e) {
                        // failed
                    } catch (InterruptedException e) {
                        // failed
                    }
                    error("Unable to check hg version");
                }
            }.process();
        }

        private String findHgVersion() throws IOException, InterruptedException {
            return findHgVersion(VERSION_STRING);
        }

        private String findHgVersion(Pattern p) throws IOException, InterruptedException {
            if (version != null) {
                return version;
            }
            ByteBuffer baos = new ByteBuffer();
            Proc proc = Hudson.getInstance().createLauncher(TaskListener.NULL).launch(
                    new String[] {getHgExe(), "version"}, new String[0], baos, null);
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

        /**
         * UUID version string.
         * This appears to be used for snapshot builds. See issue #1683
         */
        private static final Pattern UUID_VERSION_STRING = Pattern.compile("\\(version ([0-9a-f]+)");

        private static final VersionNumber V0_9_4 = new VersionNumber("0.9.4");
    }


    private static final long serialVersionUID = 1L;

    private static final Logger LOGGER = Logger.getLogger(MercurialSCM.class.getName());
}
