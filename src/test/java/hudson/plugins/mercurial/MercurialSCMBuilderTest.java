package hudson.plugins.mercurial;

import java.io.IOException;
import java.util.Collections;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import hudson.FilePath;
import hudson.tools.ToolProperty;
import hudson.util.LogTaskListener;
import hudson.util.StreamTaskListener;
import jenkins.scm.api.SCMHead;
import jenkins.scm.api.SCMHeadObserver;
import jenkins.scm.api.SCMRevision;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.jvnet.hudson.test.JenkinsRule;

import static org.junit.Assert.assertEquals;

public class MercurialSCMBuilderTest {
    @Rule
    public JenkinsRule j = new JenkinsRule();
    @Rule
    public MercurialRule m = new MercurialRule(j);
    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    private static LogTaskListener listener;
    private static MercurialSCMSource mercurialSCMSource;

    @Before
    public void prepareEnvironment() throws Exception {
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

    @Test
    public void headNameEquals() throws IOException, InterruptedException {
        Map<SCMHead, SCMRevision> result = mercurialSCMSource.fetch(null, SCMHeadObserver.collect(), null, null).result();
        for (Map.Entry<SCMHead, SCMRevision> entry : result.entrySet()) {
            MercurialSCM mercurialSCM = new MercurialSCMBuilder(entry.getKey(), entry.getValue(), "", "")
                    .build();
            assertEquals(MercurialSCM.RevisionType.CHANGESET, mercurialSCM.getRevisionType());
            assertEquals(entry.getValue().getHead().getName(), mercurialSCM.getHeadName());
            assertEquals(entry.getKey().getName(), mercurialSCM.getHeadName());
        }
    }

    @Test
    public void headNameDefault() throws IOException, InterruptedException {
        SCMRevision revision = mercurialSCMSource.fetch("version-1.0", listener, null);
        MercurialSCM mercurialSCM = new MercurialSCMBuilder(new SCMHead("default"), revision, "", "")
                .build();
        assertEquals(MercurialSCM.RevisionType.CHANGESET, mercurialSCM.getRevisionType());
        assertEquals("default", mercurialSCM.getHeadName());
        assertEquals(revision.getHead().getName(), mercurialSCM.getHeadName());
    }

    @Test
    public void headNameNonDefault() throws IOException, InterruptedException {
        SCMRevision revision = mercurialSCMSource.fetch("version-1.1", listener, null);
        MercurialSCM mercurialSCM = new MercurialSCMBuilder(new SCMHead("my-branch"), revision, "", "")
                .build();
        assertEquals(MercurialSCM.RevisionType.CHANGESET, mercurialSCM.getRevisionType());
        assertEquals("my-branch", mercurialSCM.getHeadName());
        assertEquals(revision.getHead().getName(), mercurialSCM.getHeadName());
    }
}
