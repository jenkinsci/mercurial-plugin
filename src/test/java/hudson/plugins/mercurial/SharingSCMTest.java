package hudson.plugins.mercurial;

import hudson.model.AbstractBuild;
import hudson.model.Action;
import hudson.model.FreeStyleProject;
import hudson.model.Hudson;
import hudson.tools.ToolProperty;

import java.util.Collections;
import org.jvnet.hudson.test.Bug;

public class SharingSCMTest extends MercurialSCMTest {

    public static final String SHARING_INSTALLATION = "sharing";

    protected @Override
    void setUp() throws Exception {
        super.setUp();
        hgInstallation = SHARING_INSTALLATION;
        Hudson.getInstance()
                .getDescriptorByType(MercurialInstallation.DescriptorImpl.class)
                .setInstallations(
                        new MercurialInstallation(SHARING_INSTALLATION, "",
                                "hg", false, true, true, Collections
                                        .<ToolProperty<?>> emptyList()));
        MercurialSCM.CACHE_LOCAL_REPOS = true;
    }

    @Bug(12829)
    public void testNonExistingBranchesDontGenerateMercurialTagActionsInTheBuild() throws Exception {
        AbstractBuild<?, ?> b;
        FreeStyleProject p = createFreeStyleProject();
        p.setScm(new MercurialSCM(hgInstallation, repo.getPath(), "non-existing-branch", null,
                null, null, false));
        hg(repo, "init");
        touchAndCommit(repo, "dir1/f1");
        b = p.scheduleBuild2(0).get();
        for (Action action : b.getActions()) {
            if (action instanceof MercurialTagAction) {
                fail("There should not be any MercurialTagAction");
            }
        }
    }
}
