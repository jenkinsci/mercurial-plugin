package hudson.plugins.mercurial;

import hudson.model.AbstractBuild;
import hudson.model.FreeStyleProject;

import java.io.File;
import java.util.Collections;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

@WithJenkins
class DisableChangeLogTest {

    private JenkinsRule j;
    private MercurialTestUtil m;

    @TempDir
    private File tmp;
    private File repo;

    private static final String DISABLE_CHANGELOG_INSTALLATION = "changelog";

    @BeforeEach
    void beforeEach(JenkinsRule rule) {
        j = rule;
        m = new MercurialTestUtil(j);

        repo = tmp;
        // TODO switch to MercurialContainer
        j.jenkins
                .getDescriptorByType(MercurialInstallation.DescriptorImpl.class)
                .setInstallations(
                        new MercurialInstallation(DISABLE_CHANGELOG_INSTALLATION, "", "hg",
                                true, false, false, Collections
                                        .emptyList()));
    }

    protected String hgInstallation() {
        return DISABLE_CHANGELOG_INSTALLATION;
    }

    @Test
    void changelogIsDisabled() throws Exception {
        AbstractBuild<?, ?> b;
        FreeStyleProject p = j.createFreeStyleProject();
        p.setScm(new MercurialSCM(hgInstallation(), repo.getPath(),
                MercurialSCM.RevisionType.BRANCH, null, null,
                null, null, false, null, true));
        m.hg(repo, "init");
        m.touchAndCommit(repo, "dir1/f1");
        b = p.scheduleBuild2(0).get();
        assertTrue(b.getChangeSet().isEmptySet());
        m.touchAndCommit(repo, "dir2/f1");
        b = p.scheduleBuild2(0).get();
        assertTrue(b.getChangeSet().isEmptySet());
    }
}
