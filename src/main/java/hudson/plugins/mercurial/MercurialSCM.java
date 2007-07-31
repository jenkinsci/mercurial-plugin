package hudson.plugins.mercurial;

import hudson.AbortException;
import hudson.FilePath;
import hudson.FilePath.FileCallable;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.BuildListener;
import hudson.model.TaskListener;
import hudson.remoting.VirtualChannel;
import hudson.scm.ChangeLogParser;
import hudson.scm.SCM;
import hudson.scm.SCMDescriptor;
import hudson.util.ArgumentListBuilder;
import org.ini4j.Ini;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Map;

/**
 * Mercurial SCM.
 * 
 * @author Kohsuke Kawaguchi
 */
public class MercurialSCM extends SCM {
    /**
     * Source repository URL from which we pull.
     */
    private final String source;

    @DataBoundConstructor
    public MercurialSCM(String source) {
        this.source = source;
    }

    @Override
    public boolean pollChanges(AbstractProject project, Launcher launcher, FilePath workspace, TaskListener listener) throws IOException, InterruptedException {
        // TODO
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean checkout(AbstractBuild build, Launcher launcher, FilePath workspace, BuildListener listener, File changelogFile) throws IOException, InterruptedException {
        boolean canUpdate = workspace.act(new FileCallable<Boolean>() {
            public Boolean invoke(File ws, VirtualChannel channel) throws IOException {
                File hgrc = new File(ws, ".hg/hgrc");
                Ini ini = loadIni(hgrc);
                return canUpdate(ini);
            }

            /**
             * Loads the .ini file.
             */
            private Ini loadIni(File hgrc) throws IOException {
                FileInputStream in = new FileInputStream(hgrc);
                try {
                    return new Ini(in);
                } finally {
                    in.close();
                }
            }

            private boolean canUpdate(Ini ini) {
                Ini.Section section = ini.get("paths");
                if(section==null)   return false;
                String upstream = section.get("default");
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
        String oldRev=getTipRevision(launcher, build, workspace, listener);

        // pull
        try {
            if(launcher.launch("hg pull -u",build.getEnvVars(),listener.getLogger(),workspace).join()!=0) {
                listener.error("Failed to pull");
                return false;
            }
        } catch (IOException e) {
            listener.error("Failed to pull");
            return false;
        }

        String newRev=getTipRevision(launcher, build, workspace, listener);

        // calc changeset
        FileOutputStream os = new FileOutputStream(changelogFile);
        try {
            if(launcher.launch(new String[]{"hg","log","-r",oldRev+":"+newRev,"--template",CHANGELOG_TEMPLATE},
                build.getEnvVars(),os,workspace).join()!=0) {
                listener.error("Failed to calc changelog");
                return false;
            }
        } catch(IOException e) {
            listener.error("Failed to calc changelog");
            return false;
        }

        return true;
    }

    /**
     * Determines the current tip revision id and reutnr it.
     */
    private String getTipRevision(Launcher launcher, AbstractBuild<?,?> build, FilePath workspace, BuildListener listener) throws IOException, InterruptedException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        if(launcher.launch("hg tip --template {rev}",build.getEnvVars(),baos,workspace).join()!=0) {
            listener.error("Failed to check the tip revision");
            throw new AbortException();
        }

        // obtain the current changeset node number
        return new String(baos.toByteArray(), "ASCII");
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
        args.add("hg","clone",source,workspace.getRemote());
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
        // TODO
        throw new UnsupportedOperationException();
    }

    @Override
    public DescriptorImpl getDescriptor() {
        return DescriptorImpl.DESCRIPTOR;
    }

    public static final class DescriptorImpl extends SCMDescriptor<MercurialSCM> {
        public static final DescriptorImpl DESCRIPTOR = new DescriptorImpl();
        private  DescriptorImpl() {
            super(MercurialSCM.class, null);
        }

        public String getDisplayName() {
            return "Mercurial";
        }

        public SCM newInstance(StaplerRequest req) throws FormException {
            return req.bindParameters(MercurialSCM.class,"mercurial.");
        }
    }

    private static final String CHANGELOG_TEMPLATE = "<changeset node='{node}' author='{author}' rev='{rev}' date='{date}'><log>{desc|escape}</log><added>{files_added}</added><deleted>{file_dels}</deleted><files>{files}</files}</changeset>\\n";
}
