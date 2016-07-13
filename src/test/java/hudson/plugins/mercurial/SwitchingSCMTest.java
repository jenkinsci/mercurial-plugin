package hudson.plugins.mercurial;

import hudson.model.FreeStyleProject;
import hudson.tools.ToolProperty;

import static org.junit.Assert.*;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.jvnet.hudson.test.JenkinsRule;

import java.io.File;
import java.util.Collections;
import jenkins.model.Jenkins;

public class SwitchingSCMTest {

    @Rule public JenkinsRule j = new JenkinsRule();
    @Rule public MercurialRule m = new MercurialRule(j);
    @Rule public TemporaryFolder tmp = new TemporaryFolder();
    private File repo;
    protected String cachingInstallation = "caching";
    protected String sharingInstallation = "sharing";

    @Before public void setUp() throws Exception {
        repo = tmp.getRoot();

        Jenkins.getInstance()
                .getDescriptorByType(MercurialInstallation.DescriptorImpl.class)
                .setInstallations(
                        new MercurialInstallation(cachingInstallation, "",
                                "hg", false, true, false, Collections
                                        .<ToolProperty<?>> emptyList()));
        Jenkins.getInstance()
                .getDescriptorByType(MercurialInstallation.DescriptorImpl.class)
                .setInstallations(
                        new MercurialInstallation(sharingInstallation, "",
                                "hg", false, true, true, Collections
                                        .<ToolProperty<?>> emptyList()));

    }

    @Test public void switchingFromCachedToShared() throws Exception {
        FreeStyleProject p = j.createFreeStyleProject();
        p.setScm(new MercurialSCM(cachingInstallation, repo.getPath(), null,
                null, null, null, false));

        m.hg(repo, "init");
        m.touchAndCommit(repo, "a");
        m.buildAndCheck(p, "a");
        assertFalse(p.getSomeWorkspace().child(".hg").child("sharedpath")
                .exists());

        p.setScm(new MercurialSCM(sharingInstallation, repo.getPath(), null,
                null, null, null, false));

        m.touchAndCommit(repo, "b");
        m.buildAndCheck(p, "b");
        assertTrue(p.getSomeWorkspace().child(".hg").child("sharedpath")
                .exists());

    }

    public void testSwitchingFromSharedToCached() throws Exception {
        FreeStyleProject p = j.createFreeStyleProject();
        p.setScm(new MercurialSCM(sharingInstallation, repo.getPath(), null,
                null, null, null, false));

        m.hg(repo, "init");
        m.touchAndCommit(repo, "a");
        m.buildAndCheck(p, "a");

        assertTrue(p.getSomeWorkspace().child(".hg").child("sharedpath")
                .exists());

        p.setScm(new MercurialSCM(cachingInstallation, repo.getPath(), null,
                null, null, null, false));

        m.touchAndCommit(repo, "b");
        m.buildAndCheck(p, "b");
        assertFalse(p.getSomeWorkspace().child(".hg").child("sharedpath")
                .exists());

    }
}
