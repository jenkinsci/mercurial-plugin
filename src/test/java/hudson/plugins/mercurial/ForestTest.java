package hudson.plugins.mercurial;

import hudson.FilePath;
import hudson.model.FreeStyleProject;
import hudson.model.Hudson;
import hudson.tools.ToolProperty;
import java.io.File;
import java.util.Collections;

public class ForestTest extends MercurialTestCase {

    private File toprepo, subrepo;
    protected @Override void setUp() throws Exception {
        super.setUp();
        String downloadForest = ForestTest.class.getResource("forest.py").toString(); // copied from 9e722e8d001d
        Hudson.getInstance().getDescriptorByType(MercurialInstallation.DescriptorImpl.class).setInstallations(
                new MercurialInstallation("forested", "", "hg", downloadForest, false, false, Collections.<ToolProperty<?>>emptyList()));
        toprepo = createTmpDir();
        hg(toprepo, "init");
        subrepo = new File(toprepo, "sub");
        subrepo.mkdir();
        hg(subrepo, "init");
    }

    public void testCloneAndClean() throws Exception {
        touchAndCommit(toprepo, "a");
        touchAndCommit(subrepo, "b");
        FreeStyleProject p = createFreeStyleProject();
        p.setScm(new MercurialSCM("forested" ,toprepo.getPath(), null, null, null, true, true));
        buildAndCheck(p, "sub/b");
        FilePath ws = p.getSomeWorkspace();
        ws.child("junk").touch(0);
        ws.child("sub/trash").touch(0);
        buildAndCheck(p, "a");
        assertFalse(ws.child("junk").exists());
        assertFalse(ws.child("sub/trash").exists());
        touchAndCommit(subrepo, "more");
        buildAndCheck(p, "sub/more");
    }

}
