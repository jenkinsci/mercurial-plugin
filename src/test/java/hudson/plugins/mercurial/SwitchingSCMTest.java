package hudson.plugins.mercurial;

import hudson.model.FreeStyleProject;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.jvnet.hudson.test.JenkinsRule;

import java.io.File;
import java.util.Collections;
import jenkins.model.Jenkins;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

@WithJenkins
class SwitchingSCMTest {

    private JenkinsRule j;
    private MercurialTestUtil m;

    @TempDir
    private File tmp;
    private File repo;

    private static final String CACHING_INSTALLATION = "caching";
    private static final String SHARING_INSTALLATION = "sharing";

    @BeforeEach
    void beforeEach(JenkinsRule rule) {
        j = rule;
        m = new MercurialTestUtil(j);

        repo = tmp;

        Jenkins.get()
                .getDescriptorByType(MercurialInstallation.DescriptorImpl.class)
                .setInstallations(
                        new MercurialInstallation(CACHING_INSTALLATION, "",
                                "hg", false, true, false, Collections
                                        .emptyList()));
        Jenkins.get()
                .getDescriptorByType(MercurialInstallation.DescriptorImpl.class)
                .setInstallations(
                        new MercurialInstallation(SHARING_INSTALLATION, "",
                                "hg", false, true, true, Collections
                                        .emptyList()));

    }

    @Test
    void switchingFromCachedToShared() throws Exception {
        FreeStyleProject p = j.createFreeStyleProject();
        p.setScm(new MercurialSCM(CACHING_INSTALLATION, repo.getPath(), null,
                null, null, null, false));

        m.hg(repo, "init");
        m.touchAndCommit(repo, "a");
        m.buildAndCheck(p, "a");
        assertFalse(p.getSomeWorkspace().child(".hg").child("sharedpath")
                .exists());

        p.setScm(new MercurialSCM(SHARING_INSTALLATION, repo.getPath(), null,
                null, null, null, false));

        m.touchAndCommit(repo, "b");
        m.buildAndCheck(p, "b");
        assertTrue(p.getSomeWorkspace().child(".hg").child("sharedpath")
                .exists());

    }

    @Test
    void testSwitchingFromSharedToCached() throws Exception {
        FreeStyleProject p = j.createFreeStyleProject();
        p.setScm(new MercurialSCM(SHARING_INSTALLATION, repo.getPath(), null,
                null, null, null, false));

        m.hg(repo, "init");
        m.touchAndCommit(repo, "a");
        m.buildAndCheck(p, "a");

        assertTrue(p.getSomeWorkspace().child(".hg").child("sharedpath")
                .exists());

        p.setScm(new MercurialSCM(CACHING_INSTALLATION, repo.getPath(), null,
                null, null, null, false));

        m.touchAndCommit(repo, "b");
        m.buildAndCheck(p, "b");
        assertFalse(p.getSomeWorkspace().child(".hg").child("sharedpath")
                .exists());

    }
}
