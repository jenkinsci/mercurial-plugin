package hudson.plugins.mercurial;

import hudson.model.AbstractBuild;
import hudson.model.FreeStyleProject;
import hudson.tools.ToolProperty;

import java.io.File;
import java.util.Collections;
import static org.junit.Assert.*;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import org.jvnet.hudson.test.JenkinsRule;

public class DisableChangeLogTest {
    @Rule public JenkinsRule j = new JenkinsRule();
    @Rule public MercurialRule m = new MercurialRule(j);
    @Rule public TemporaryFolder tmp = new TemporaryFolder();
    private File repo;

    private static final String DISABLE_CHANGELOG_INSTALLATION = "changelog";

    @Before public void setUp() throws Exception {
        repo = tmp.getRoot();
        // TODO switch to MercurialContainer
        j.jenkins
                .getDescriptorByType(MercurialInstallation.DescriptorImpl.class)
                .setInstallations(
                        new MercurialInstallation(DISABLE_CHANGELOG_INSTALLATION, "", "hg",
                                true, false, false, Collections
                                        .<ToolProperty<?>> emptyList()));
    }

    protected String hgInstallation() {
        return DISABLE_CHANGELOG_INSTALLATION;
    }

    @Test public void changelogIsDisabled() throws Exception {
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
