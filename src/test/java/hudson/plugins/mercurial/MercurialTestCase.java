package hudson.plugins.mercurial;

import hudson.FilePath;
import hudson.Launcher;
import hudson.model.Action;
import hudson.model.Cause.UserCause;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.Hudson;
import hudson.model.TaskListener;
import hudson.util.StreamTaskListener;
import java.io.File;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import org.jvnet.hudson.test.HudsonTestCase;

public abstract class MercurialTestCase extends HudsonTestCase {

    private TaskListener listener;
    private Launcher launcher;

    protected @Override void setUp() throws Exception {
        super.setUp();
        listener = new StreamTaskListener(System.out, Charset.defaultCharset());
        launcher = Hudson.getInstance().createLauncher(listener);
    }

    protected final void hg(File repo, String... args) throws Exception {
        List<String> cmds = new ArrayList<String>();
        cmds.add("hg");
        cmds.add("--config");
        cmds.add("ui.username=nobody@nowhere.net");
        cmds.addAll(Arrays.asList(args));
        assertEquals(0, MercurialSCM.launch(launcher).cmds(cmds).pwd(repo).stdout(listener).join());
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

    protected String buildAndCheck(FreeStyleProject p, String name, Action... actions) throws Exception {
        FreeStyleBuild b = assertBuildStatusSuccess(p.scheduleBuild2(0, new UserCause(), actions).get());
//        for (String line : b.getLog(Integer.MAX_VALUE)) {
//            System.err.println(">> " + line);
//        }
        if (!b.getWorkspace().child(name).exists()) {
            Set<String> children = new TreeSet<String>();
            for (FilePath child : b.getWorkspace().list()) {
                children.add(child.getName());
            }
            fail("Could not find " + name + " among " + children);
        }
        assertNotNull(b.getAction(MercurialTagAction.class));
        @SuppressWarnings("deprecation")
        String log = b.getLog();
        return log;
    }

    protected boolean pollSCMChanges(FreeStyleProject p) {
        return p.poll(new StreamTaskListener(System.out, Charset.defaultCharset())).hasChanges();
    }

}
