package hudson.plugins.mercurial;

import hudson.FilePath;
import hudson.Launcher;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.Hudson;
import hudson.model.TaskListener;
import hudson.util.StreamTaskListener;
import java.io.File;
import java.util.Arrays;
import org.jvnet.hudson.test.HudsonTestCase;

public abstract class MercurialTestCase extends HudsonTestCase {

    private TaskListener listener;
    private Launcher launcher;

    protected @Override void setUp() throws Exception {
        super.setUp();
        listener = new StreamTaskListener(System.out);
        launcher = Hudson.getInstance().createLauncher(listener);
    }

    protected void hg(File repo, String... args) throws Exception {
        assertEquals(0, launcher.launch().cmds(new File("hg"), args).pwd(repo).stdout(listener).join());
    }

    protected void touchAndCommit(File repo, String... names) throws Exception {
        for (String name : names) {
            FilePath toCreate = new FilePath(repo).child(name);
            toCreate.getParent().mkdirs();
            toCreate.touch(0);
            hg(repo, "add", name);
        }
        hg(repo, "commit", "--message", "added " + Arrays.toString(names));
    }

    protected void buildAndCheck(FreeStyleProject p, String name) throws Exception {
        FreeStyleBuild b = assertBuildStatusSuccess(p.scheduleBuild2(0).get());
//        for (String line : b.getLog(Integer.MAX_VALUE)) {
//            System.err.println(">> " + line);
//        }
        assertTrue(b.getWorkspace().child(name).exists());
        assertNotNull(b.getAction(MercurialTagAction.class));
    }

}
