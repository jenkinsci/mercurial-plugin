package hudson.plugins.mercurial;

import hudson.FilePath;
import hudson.Launcher;
import hudson.plugins.mercurial.browser.HgWeb;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.Hudson;
import hudson.model.TaskListener;
import hudson.util.StreamTaskListener;
import org.jvnet.hudson.test.HudsonTestCase;
import org.jvnet.hudson.test.recipes.LocalData;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.ExecutionException;

/**
 * @author Kohsuke Kawaguchi
 */
public class MercurialSCMTest extends HudsonTestCase {
    TaskListener listener = new StreamTaskListener(System.out);
    Launcher launcher;
    private File repo;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        launcher = Hudson.getInstance().createLauncher(listener);
        repo = createTmpDir();
    }

    public void testBasicOps() throws Exception {
        FreeStyleProject p = createFreeStyleProject();
        p.setScm(new MercurialSCM(null,repo.getPath(),null,null,null,false));

        hg("hg","init");
        touchAndCommit("a");
        buildAndCheck(p,"a");   // this tests the clone op
        touchAndCommit("b");
        buildAndCheck(p,"b");   // this tests the update op
    }

    private void touchAndCommit(String name) throws Exception {
        new FilePath(repo).child(name).touch(0);
        hg("hg","add",name);
        hg("hg","commit","-m","added "+name);
    }

    private void buildAndCheck(FreeStyleProject p, String name) throws InterruptedException, ExecutionException, IOException {
        FreeStyleBuild b = p.scheduleBuild2(0).get();
        assertTrue(p.getWorkspace().child(name).exists());
        assertNotNull(b.getAction(MercurialTagAction.class));
    }

    private void hg(String... args) throws Exception {
        assertEquals(0,launcher.launch().cmds(args).pwd(repo).stdout(listener).join());
    }

    /**
     * With an introduction of HgBrowser base class, a care has to be taken to load existing dataset.
     *
     * This test verifies that. 
     */
    @LocalData
    public void testRepositoryBrowserCompatibility() throws Exception {
        FreeStyleProject p = (FreeStyleProject)hudson.getItem("foo");
        MercurialSCM ms = (MercurialSCM)p.getScm();
        assertTrue(ms.getBrowser() instanceof HgWeb);
        assertEqualBeans(new HgWeb("http://www.yahoo.com/"),ms.getBrowser(),"url");
    }
}
