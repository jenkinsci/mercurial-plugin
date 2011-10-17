package hudson.plugins.mercurial;

import hudson.model.FreeStyleProject;
import hudson.model.Hudson;
import hudson.tools.ToolProperty;

import java.io.File;
import java.util.Collections;

public class SwitchingSCMTest extends MercurialTestCase {

    private File repo;
    protected String cachingInstallation = "caching";
    protected String sharingInstallation = "sharing";

    protected @Override
    void setUp() throws Exception {
        super.setUp();
        repo = createTmpDir();

        Hudson.getInstance()
                .getDescriptorByType(MercurialInstallation.DescriptorImpl.class)
                .setInstallations(
                        new MercurialInstallation(cachingInstallation, "",
                                "hg", false, true, false, Collections
                                        .<ToolProperty<?>> emptyList()));
        Hudson.getInstance()
                .getDescriptorByType(MercurialInstallation.DescriptorImpl.class)
                .setInstallations(
                        new MercurialInstallation(sharingInstallation, "",
                                "hg", false, true, true, Collections
                                        .<ToolProperty<?>> emptyList()));

        MercurialSCM.CACHE_LOCAL_REPOS = true;
    }

    public void testSwitchingFromCachedToShared() throws Exception {
        FreeStyleProject p = createFreeStyleProject();
        p.setScm(new MercurialSCM(cachingInstallation, repo.getPath(), null,
                null, null, null, false));

        hg(repo, "init");
        touchAndCommit(repo, "a");
        buildAndCheck(p, "a");
        assertFalse(p.getSomeWorkspace().child(".hg").child("sharedpath")
                .exists());

        p.setScm(new MercurialSCM(sharingInstallation, repo.getPath(), null,
                null, null, null, false));

        touchAndCommit(repo, "b");
        buildAndCheck(p, "b");
        assertTrue(p.getSomeWorkspace().child(".hg").child("sharedpath")
                .exists());

    }

    public void testSwitchingFromSharedToCached() throws Exception {
        FreeStyleProject p = createFreeStyleProject();
        p.setScm(new MercurialSCM(sharingInstallation, repo.getPath(), null,
                null, null, null, false));

        hg(repo, "init");
        touchAndCommit(repo, "a");
        buildAndCheck(p, "a");

        assertTrue(p.getSomeWorkspace().child(".hg").child("sharedpath")
                .exists());

        p.setScm(new MercurialSCM(cachingInstallation, repo.getPath(), null,
                null, null, null, false));

        touchAndCommit(repo, "b");
        buildAndCheck(p, "b");
        assertFalse(p.getSomeWorkspace().child(".hg").child("sharedpath")
                .exists());

    }
}
