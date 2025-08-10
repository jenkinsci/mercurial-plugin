package hudson.plugins.mercurial;

import hudson.EnvVars;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.Action;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.Node;
import hudson.model.TaskListener;
import hudson.scm.PollingResult;
import hudson.triggers.SCMTrigger;
import hudson.util.ArgumentListBuilder;
import hudson.util.StreamTaskListener;
import hudson.util.VersionNumber;
import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.Set;
import java.util.TreeSet;
import static org.junit.Assert.*;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.junit.Assume;
import org.junit.AssumptionViolatedException;
import org.junit.rules.ExternalResource;
import org.jvnet.hudson.test.JenkinsRule;

public final class MercurialRule extends ExternalResource {

    private TaskListener listener;
    private boolean validated;

    private final JenkinsRule j;
    private Node node;
    private MercurialInstallation inst;

    public MercurialRule(JenkinsRule j) {
        this.j = j;
    }

    @Override protected void before() throws Exception {
        listener = new StreamTaskListener(System.out, Charset.defaultCharset());
    }

    public MercurialRule withNode(Node node) {
        this.node = node;
        return this;
    }

    public MercurialRule withInstallation(MercurialInstallation inst) {
        this.inst = inst;
        return this;
    }

    private Node node() {
        return node != null ? node : j.jenkins;
    }

    private Launcher launcher() {
        return node().createLauncher(listener);
    }

    private HgExe hgExe() throws Exception {
        return hgExe(new EnvVars());
    }

    private HgExe hgExe(EnvVars env) throws Exception {
        HgExe hg = new HgExe(inst, null, launcher(), node(), listener, env);
        if (!validated) {
            try {
                String version = hg.version();
                Assume.assumeNotNull(version);
                System.out.println("Mercurial version detected: " + version);
                Assume.assumeFalse(version + " is too old to even test", new VersionNumber(version).isOlderThan(new VersionNumber(MercurialContainer.Version.HG6.exactVersion)));
            } catch (IOException ioe) {
                String message = ioe.getMessage();
                if (message.startsWith("Cannot run program \"hg\"") && message.endsWith("No such file or directory")) {
                    throw new AssumptionViolatedException("hg is not available; please check that your PATH environment variable is properly configured");
                }
                Assume.assumeNoException(ioe); // failed to check availability of hg
            }
            validated = true;
        }
        return hg;
    }

    public void hg(String... args) throws Exception {
        HgExe hg = hgExe();
        assertEquals(0, hg.launch(nobody(hg.seed(false)).add(args)).join());
    }

    @Deprecated
    public void hg(File repo, String... args) throws Exception {
        hg(new FilePath(repo), args);
    }

    public void hg(FilePath repo, String... args) throws Exception {
        HgExe hg = hgExe();
        assertEquals(0, hg.launch(nobody(hg.seed(false)).add(args)).pwd(repo).join());
    }

    @Deprecated
    public void hg(File repo, EnvVars env, String... args) throws Exception {
        hg(new FilePath(repo), env, args);
    }

    public void hg(FilePath repo, EnvVars env, String... args) throws Exception {
        HgExe hg = hgExe(env);
        assertEquals(0, hg.launch(nobody(hg.seed(false)).add(args)).envs( env ).pwd(repo.child(env.expand(repo.getRemote()))).join());
    }

    private static ArgumentListBuilder nobody(ArgumentListBuilder args) {
        return args.add("--config").add("ui.username=nobody@nowhere.net");
    }

    @Deprecated
    public void touchAndCommit(File repo, String... names) throws Exception {
        touchAndCommit(new FilePath(repo), names);
    }

    public void touchAndCommit(FilePath repo, String... names) throws Exception {
        for (String name : names) {
            FilePath toTouch = repo.child(name);
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
            Set<String> children = new TreeSet<>();
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

    @Deprecated
    public String getLastChangesetId(File repo) throws Exception {
        return getLastChangesetId(new FilePath(repo));
    }

    public String getLastChangesetId(FilePath repo) throws Exception {
        return hgExe().popen(repo, listener, false, new ArgumentListBuilder("log", "-l1", "--template", "{node}"));
    }

    @Deprecated
    public long getLastChangesetUnixTimestamp(File repo) throws Exception {
        return getLastChangesetUnixTimestamp(new FilePath(repo));
    }

    public long getLastChangesetUnixTimestamp(FilePath repo) throws Exception {
        //hgdate returns the date as a pair of numbers: "1157407993 25200" (Unix timestamp, timezone offset).
        String date = hgExe().popen(repo, listener, false, new ArgumentListBuilder("log", "-l1", "--template", "{date|hgdate}"));
        return Long.valueOf(date.split(" ")[0]);
    }

    // Borrowed from AbstractSampleDVCSRepoRule:

    public void notifyCommit(FilePath repo) throws Exception {
        j.jenkins.getDescriptorByType(SCMTrigger.DescriptorImpl.class).synchronousPolling = true;
        System.out.println(j.createWebClient().goTo("mercurial/notifyCommit?url=" + repo.toURI().toString(), "text/plain").getWebResponse().getContentAsString());
        j.waitUntilNoActivity();
    }

    public void registerHook(FilePath repo) throws Exception {
        assert !repo.isRemote() : "TODO not currently supported for remote repositories since the callback URL would not be accessible from the Docker container unless we do some more exotic network configuration";
        FilePath hgDir = repo.child(".hg");
        FilePath enforcePython3 = hgDir.child("enforce-python3.py");
        enforcePython3.write(
                "import urllib.request, urllib.parse\n" +
                        "def precommit(**kwargs):\n" +
                        "    urllib.request.Request('http://nowhere.net/')\n", null);

        FilePath hook = hgDir.child("hook.py");

        String hook_text = IOUtils.toString(MercurialRule.class.getResourceAsStream("/hook.py"), "UTF-8");
        hook_text = StringUtils.replace(hook_text, "@JENKINS_URL@", j.getURL().toString());
        hook_text = StringUtils.replace(hook_text, "@REPO_URL@", repo.toURI().toString());
        hook.write(hook_text, null);

        hgDir.child("hgrc").write(
                "[hooks]\n" +
                        "precommit.enforce-python3 = python:" + enforcePython3.getRemote() + ":precommit\n" +
                        "commit.jenkins = python:" + hook.getRemote() + ":commit", null);
    }

}
