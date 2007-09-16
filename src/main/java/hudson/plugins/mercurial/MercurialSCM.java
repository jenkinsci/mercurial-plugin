package hudson.plugins.mercurial;

import hudson.*;
import hudson.FilePath.FileCallable;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.BuildListener;
import hudson.model.TaskListener;
import hudson.remoting.VirtualChannel;
import hudson.scm.ChangeLogParser;
import hudson.scm.SCM;
import hudson.scm.SCMDescriptor;
import hudson.util.ArgumentListBuilder;
import hudson.util.FormFieldValidator;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

import javax.servlet.ServletException;
import java.io.*;
import java.util.Map;

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

    @DataBoundConstructor
    public MercurialSCM(String source) {
        this.source = source;
    }

    /**
     * Gets the source repository path.
     * Either URL or local file path.
     */
    public String getSource() {
        return source;
    }

    @Override
    public boolean pollChanges(AbstractProject project, Launcher launcher, FilePath workspace, TaskListener listener) throws IOException, InterruptedException {
        String remoteTip = getTipRevision(launcher,workspace,listener);
        PrintStream output = listener.getLogger();

        if(launcher.launch(
            new String[]{getDescriptor().getHgExe(),"id","-r",remoteTip},
            EnvVars.masterEnvVars, output,workspace).join()==0) {

            output.println("No changes");
            return false;
        }

        output.println("Changes detected");
        return true;
    }

    /**
     * Determines the current tip revision id in the upstream and return it.
     */
    private String getTipRevision(Launcher launcher, FilePath workspace, TaskListener listener) throws IOException, InterruptedException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        if(launcher.launch(
            new String[]{getDescriptor().getHgExe(),"id","default"},
            EnvVars.masterEnvVars,baos,workspace).join()!=0) {
            // dump the output from hg to assist trouble-shooting.
            Util.copyStream(new ByteArrayInputStream(baos.toByteArray()),listener.getLogger());
            listener.error("Failed to check the tip revision");
            throw new AbortException();
        }

        // obtain the current changeset node number
        return new String(baos.toByteArray(), "ASCII").trim();
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
        // calc changelog and create bundle
        FileOutputStream os = new FileOutputStream(changelogFile);
        os.write("<changesets>\n".getBytes());
        int r;
        try {
            r = launcher.launch(
                new String[]{getDescriptor().getHgExe(),
                    "incoming","--quiet","--bundle","hg.bundle",
                    "--template", MercurialChangeSet.CHANGELOG_TEMPLATE},
                build.getEnvVars(), os, workspace).join();
            if(r!=0 && r!=1) {// 0.9.4 returns 1 for no changes
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
        if(r==0 && new FilePath(workspace,"hg.bundle").exists())
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
        args.add(getDescriptor().getHgExe(),"clone",source,workspace.getRemote());
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

    public static final class DescriptorImpl extends SCMDescriptor<MercurialSCM> {
        public static final DescriptorImpl DESCRIPTOR = new DescriptorImpl();

        private String hgExe;

        private  DescriptorImpl() {
            super(MercurialSCM.class, null);
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
        
        public SCM newInstance(StaplerRequest req) throws FormException {
            return req.bindParameters(MercurialSCM.class,"mercurial.");
        }

        public boolean configure(StaplerRequest req) throws FormException {
            hgExe = req.getParameter("mercurial.hgExe");
            save();
            return true;
        }

        public void doHgExeCheck(StaplerRequest req, StaplerResponse rsp) throws IOException, ServletException {
            new FormFieldValidator.Executable(req,rsp).process();
        }
    }

    private static final long serialVersionUID = 1L;
}
