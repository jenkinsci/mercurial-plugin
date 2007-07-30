package hudson.plugins.mercurial;

import hudson.FilePath;
import hudson.Launcher;
import hudson.remoting.VirtualChannel;
import hudson.FilePath.FileCallable;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.BuildListener;
import hudson.model.TaskListener;
import hudson.scm.ChangeLogParser;
import hudson.scm.SCM;
import hudson.scm.SCMDescriptor;
import org.kohsuke.stapler.StaplerRequest;

import java.io.File;
import java.io.IOException;
import java.util.Map;

/**
 * Mercurial SCM.
 * 
 * @author Kohsuke Kawaguchi
 */
public class MercurialSCM extends SCM {
    @Override
    public boolean pollChanges(AbstractProject project, Launcher launcher, FilePath workspace, TaskListener listener) throws IOException, InterruptedException {
        // TODO
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean checkout(AbstractBuild build, Launcher launcher, FilePath workspace, BuildListener listener, File changelogFile) throws IOException, InterruptedException {
        workspace.act(new FileCallable<Object>() {
            public Object invoke(File ws, VirtualChannel channel) throws IOException {
                File hgrc = new File(ws, ".hg/hgrc");
            }
        })
        // TODO
        throw new UnsupportedOperationException();
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
}
