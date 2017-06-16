package hudson.plugins.mercurial;

import hudson.FilePath;
import hudson.tools.ToolProperty;
import hudson.util.LogTaskListener;
import hudson.util.StreamTaskListener;
import jenkins.scm.api.SCMRevision;
import org.junit.*;
import org.junit.rules.TemporaryFolder;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;

import java.util.Collections;
import java.util.logging.Level;
import java.util.logging.Logger;

import static org.junit.Assert.assertTrue;

@Issue("JENKINS-41657")
public class MercurialSCMSourceTest {

    @ClassRule public static JenkinsRule j = new JenkinsRule();
    @ClassRule public static MercurialRule m = new MercurialRule(j);
    @ClassRule public static TemporaryFolder tmp = new TemporaryFolder();

    private static LogTaskListener listener;
    private static MercurialSCMSource mercurialSCMSource;

    @BeforeClass
    public static void prepareEnvironment() throws Exception {
        String instName = "caching";
        MercurialInstallation installation = new MercurialInstallation(instName, "", "hg", false, true, null, false, null,
                Collections.<ToolProperty<?>>emptyList());
        listener = new LogTaskListener(Logger.getLogger(MercurialSCMSourceTest.class.getName()), Level.INFO);
        j.jenkins.getDescriptorByType(MercurialInstallation.DescriptorImpl.class).setInstallations(installation);
        FilePath repo = new FilePath(tmp.getRoot());
        installation.forNode(j.jenkins, StreamTaskListener.fromStdout());
        m.hg(repo, "init");
        repo.child("file").write("initial content", "UTF-8");
        m.hg(repo, "commit", "--addremove", "--message=initial");
        m.hg(repo, "tag", "version-1.0");
        m.hg(repo, "branch", "my-branch");
        repo.child("file2").write("content in branch", "UTF-8");
        m.hg(repo, "commit", "--addremove", "--message=branch");
        m.hg(repo, "tag", "version-1.1");


        installation.forNode(j.jenkins, StreamTaskListener.fromStdout());
        mercurialSCMSource = new MercurialSCMSource(null, instName, tmp.getRoot().toURI().toURL().toString(), null, null, null, null, null, true);
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
