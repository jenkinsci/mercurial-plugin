package hudson.plugins.mercurial;

import hudson.FilePath;
import hudson.Launcher;
import hudson.model.Action;
import hudson.model.FreeStyleBuild;
import hudson.model.TaskListener;
import hudson.model.Cause.UserCause;
import hudson.model.FreeStyleProject;
import hudson.model.Hudson;
import hudson.scm.PollingResult;
import hudson.util.StreamTaskListener;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
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

    protected @Override
    void setUp() throws Exception {
        super.setUp();
        listener = new StreamTaskListener(System.out, Charset.defaultCharset());
        launcher = Hudson.getInstance().createLauncher(listener);
        try {
            hg("--version");
        } catch(IOException ioe) {
            String message = ioe.getMessage();
            if(message.startsWith("Cannot run program \"hg\"") && message.endsWith("No such file or directory")) {
                fail("hg is not available; please check that your PATH environment variable is properly configured");
            }
            throw new Exception("Failed to check availability of hg", ioe);
        }
    }

    protected final void hg(String... args) throws Exception {
        List<String> cmds = assembleHgCommand(args);
        assertEquals(0, MercurialSCM.launch(launcher).cmds(cmds).stdout(listener).join());
    }

    protected final void hg(File repo, String... args) throws Exception {
        List<String> cmds = assembleHgCommand(args);
        assertEquals(0, MercurialSCM.launch(launcher).cmds(cmds).pwd(repo).stdout(listener).join());
    }

    private List<String> assembleHgCommand(String[] args) {
        List<String> cmds = new ArrayList<String>();
        cmds.add("hg");
        cmds.add("--config");
        cmds.add("ui.username=nobody@nowhere.net");
        cmds.addAll(Arrays.asList(args));
        return cmds;
    }

    protected void touchAndCommit(File repo, String... names) throws Exception {
        for (String name : names) {
            FilePath toTouch = new FilePath(repo).child(name);
            if (!toTouch.exists()) {
                toTouch.getParent().mkdirs();
                toTouch.touch(0);
                hg(repo, "add", name);
            } else {
                toTouch.write(toTouch.readToString() + "extra line\n", "UTF-8");
            }
        }
        hg(repo, "commit", "--message", "added " + Arrays.toString(names));
    }

    protected String buildAndCheck(FreeStyleProject p, String name,
            Action... actions) throws Exception {
        FreeStyleBuild b = assertBuildStatusSuccess(p.scheduleBuild2(0,
                new UserCause(), actions).get());
        // for (String line : b.getLog(Integer.MAX_VALUE)) {
        // System.err.println(">> " + line);
        // }
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

    protected PollingResult pollSCMChanges(FreeStyleProject p) {
        return p.poll(new StreamTaskListener(System.out, Charset
                .defaultCharset()));
    }

    protected String getLastChangesetId(File repo) throws Exception {
        List<String> cmds = new ArrayList<String>();
        cmds.add("hg");
        cmds.add("log");
        cmds.add("-l1");
        cmds.add("--template");
        cmds.add("{node}");
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        TaskListener nodeListener = new StreamTaskListener(baos);
        assertEquals(0, MercurialSCM.launch(launcher).cmds(cmds).pwd(repo)
                .stdout(nodeListener).stderr(baos).join());
        return baos.toString();
    }

}
