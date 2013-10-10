package hudson.plugins.mercurial;

import hudson.EnvVars;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.Action;
import hudson.model.FreeStyleBuild;
import hudson.model.TaskListener;
import hudson.model.FreeStyleProject;
import hudson.scm.PollingResult;
import hudson.util.ArgumentListBuilder;
import hudson.util.StreamTaskListener;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.Assert.*;
import org.junit.Assume;
import org.junit.internal.AssumptionViolatedException;
import org.junit.rules.ExternalResource;

import org.jvnet.hudson.test.JenkinsRule;

public final class MercurialRule extends ExternalResource {

    private TaskListener listener;

    private final JenkinsRule j;

    public MercurialRule(JenkinsRule j) {
        this.j = j;
    }

    @Override protected void before() throws Exception {
        listener = new StreamTaskListener(System.out, Charset.defaultCharset());
        try {
            if (new ProcessBuilder("hg", "--version").start().waitFor() != 0) {
                throw new AssumptionViolatedException("hg --version signaled an error");
            }
        } catch(IOException ioe) {
            String message = ioe.getMessage();
            if(message.startsWith("Cannot run program \"hg\"") && message.endsWith("No such file or directory")) {
                throw new AssumptionViolatedException("hg is not available; please check that your PATH environment variable is properly configured");
            }
            Assume.assumeNoException(ioe); // failed to check availability of hg
        }
    }

    private Launcher launcher() {
        return j.jenkins.createLauncher(listener);
    }

    private HgExe hgExe() throws Exception {
        return new HgExe(null, null, launcher(), j.jenkins, listener, new EnvVars());
    }

    public void hg(String... args) throws Exception {
        HgExe hg = hgExe();
        assertEquals(0, hg.launch(nobody(hg.seed(false)).add(args)).join());
    }

    public void hg(File repo, String... args) throws Exception {
        HgExe hg = hgExe();
        assertEquals(0, hg.launch(nobody(hg.seed(false)).add(args)).pwd(repo).join());
    }

    private static ArgumentListBuilder nobody(ArgumentListBuilder args) {
        return args.add("--config").add("ui.username=nobody@nowhere.net");
    }

    public void touchAndCommit(File repo, String... names) throws Exception {
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

    public String buildAndCheck(FreeStyleProject p, String name,
            Action... actions) throws Exception {
        FreeStyleBuild b = j.assertBuildStatusSuccess(p.scheduleBuild2(0, null, actions).get());
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

    public PollingResult pollSCMChanges(FreeStyleProject p) {
        return p.poll(new StreamTaskListener(System.out, Charset
                .defaultCharset()));
    }

    public String getLastChangesetId(File repo) throws Exception {
        return hgExe().popen(new FilePath(repo), listener, false, new ArgumentListBuilder("log", "-l1", "--template", "{node}"));
    }

}
