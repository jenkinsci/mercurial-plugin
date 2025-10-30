package hudson.plugins.mercurial;

import hudson.FilePath;
import hudson.util.LogTaskListener;
import hudson.util.StreamTaskListener;
import java.io.File;
import jenkins.scm.api.SCMRevision;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.logging.Level;
import java.util.logging.Logger;

@Issue("JENKINS-41657")
@WithJenkins
class MercurialSCMSourceTest {

    private static JenkinsRule j;
    private static MercurialTestUtil m;
    @TempDir
    private static File tmp;

    private static LogTaskListener listener;
    private static MercurialSCMSource mercurialSCMSource;

    @BeforeAll
    static void beforeAll(JenkinsRule rule) throws Exception {
        j = rule;
        m = new MercurialTestUtil(j);

        String instName = "caching";
        MercurialInstallation installation = new MercurialInstallation(instName, "", "hg", false, true, null, false, null,
                Collections.emptyList());
        listener = new LogTaskListener(Logger.getLogger(MercurialSCMSourceTest.class.getName()), Level.INFO);
        j.jenkins.getDescriptorByType(MercurialInstallation.DescriptorImpl.class).setInstallations(installation);
        FilePath repo = new FilePath(tmp);
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
        mercurialSCMSource = new MercurialSCMSource(null, instName, tmp.toURI().toURL().toString(), null, null, null, null, null, true);
    }

    @Test
    void testRetrieveUnknownRevision() throws Exception {
        assertNull(mercurialSCMSource.fetch("does_not_exist", listener, null));
    }

    @Test
    void testRetrieveTag() throws Exception {
        SCMRevision revision = mercurialSCMSource.fetch("version-1.1", listener, null);
        assertTrue(revision.toString().startsWith("my-branch:"));
        revision = mercurialSCMSource.fetch("version-1.0", listener, null);
        assertTrue(revision.toString().startsWith("default:"));
    }

    @Test
    void testRetrieveBranchTip() throws Exception {
        SCMRevision revision = mercurialSCMSource.fetch("my-branch", listener, null);
        assertTrue(revision.toString().startsWith("my-branch:"));
    }

    @Test
    void testRetrieveRepoTip() throws Exception {
        SCMRevision revision = mercurialSCMSource.fetch("tip", listener, null);
        assertTrue(revision.toString().startsWith("my-branch:"));
    }

}
