package hudson.plugins.mercurial;

import hudson.AbortException;
import hudson.tools.ToolProperty;
import hudson.util.LogTaskListener;
import hudson.util.StreamTaskListener;
import jenkins.scm.api.SCMRevision;
import org.junit.*;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;

import java.util.Collections;
import java.util.logging.Level;
import java.util.logging.Logger;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

@Issue("JENKINS-41657")
public class MercurialSCMSourceTest {

    @ClassRule public static JenkinsRule r = new JenkinsRule();
    @ClassRule public static MercurialSampleRepoRule sampleRepo = new MercurialSampleRepoRule();

    private static LogTaskListener listener;
    private static MercurialSCMSource mercurialSCMSource;

    @BeforeClass
    public static void prepareEnvironment() throws Exception {
        String instName = "caching";
        MercurialInstallation installation = new MercurialInstallation(instName, "", "hg", false, true, false, null,
                Collections.<ToolProperty<?>>emptyList());
        listener = new LogTaskListener(Logger.getLogger(MercurialSCMSourceTest.class.getName()), Level.INFO);
        r.jenkins.getDescriptorByType(MercurialInstallation.DescriptorImpl.class).setInstallations(installation);
        installation.forNode(r.jenkins, StreamTaskListener.fromStdout());
        sampleRepo.init();
        sampleRepo.write("file", "initial content");
        sampleRepo.hg("commit", "--addremove", "--message=initial");
        sampleRepo.hg("tag", "version-1.0");
        sampleRepo.hg("branch", "my-branch");
        sampleRepo.write("file2", "content in branch");
        sampleRepo.hg("commit", "--addremove", "--message=branch");
        sampleRepo.hg("tag", "version-1.1");


        installation.forNode(r.jenkins, StreamTaskListener.fromStdout());
        mercurialSCMSource = new MercurialSCMSource(null, instName, sampleRepo.fileUrl(), null, null, null, null, null, true);
    }

    @Test public void testRetrieveUnknownRevision() throws Exception {
        Assert.assertNull(mercurialSCMSource.retrieve("does_not_exist", listener));
    }

    @Test public void testRetrieveTag() throws Exception {
        SCMRevision revision = mercurialSCMSource.retrieve("version-1.1", listener);
        assertTrue(revision.toString().startsWith("my-branch:"));
        revision = mercurialSCMSource.retrieve("version-1.0", listener);
        assertTrue(revision.toString().startsWith("default:"));
    }

    @Test public void testRetrieveBranchTip() throws Exception {
        SCMRevision revision = mercurialSCMSource.retrieve("my-branch", listener);
        assertTrue(revision.toString().startsWith("my-branch:"));
    }

    @Test public void testRetrieveRepoTip() throws Exception {
        SCMRevision revision = mercurialSCMSource.retrieve("tip", listener);
        assertTrue(revision.toString().startsWith("my-branch:"));
    }

}
