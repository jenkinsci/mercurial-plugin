package hudson.plugins.mercurial;

import hudson.FilePath;
import hudson.Launcher;
import hudson.Proc;
import hudson.model.AbstractBuild;
import hudson.model.FreeStyleProject;
import hudson.model.ParametersAction;
import hudson.model.StringParameterValue;
import hudson.scm.ChangeLogSet;
import java.io.File;
import java.io.IOException;
import java.util.*;

import hudson.scm.ChangeLogSet.Entry;
import org.jvnet.hudson.test.Bug;
import org.jvnet.hudson.test.FakeLauncher;
import org.jvnet.hudson.test.PretendSlave;

public class MercurialSCMTest extends MercurialTestCase {

    private File repo;
    protected String hgInstallation = null; // see DebugFlagTest
    protected @Override void setUp() throws Exception {
        super.setUp();
        repo = createTmpDir();
    }

    public void testBasicOps() throws Exception {
        FreeStyleProject p = createFreeStyleProject();
        p.setScm(new MercurialSCM(hgInstallation, repo.getPath(), null, null, null, null, false, false));

        hg(repo, "init");
        touchAndCommit(repo, "a");
        buildAndCheck(p,"a");   // this tests the clone op
        touchAndCommit(repo, "b");
        buildAndCheck(p,"b");   // this tests the update op
    }

    @Bug(4281)
    public void testBranches() throws Exception {
        hg(repo, "init");
        touchAndCommit(repo, "init");
        hg(repo, "tag", "init");
        touchAndCommit(repo, "default-1");
        hg(repo, "update", "--clean", "init");
        hg(repo, "branch", "b");
        touchAndCommit(repo, "b-1");
        FreeStyleProject p = createFreeStyleProject();
        // Clone off b.
        p.setScm(new MercurialSCM(hgInstallation, repo.getPath(), "b", null, null, null, false, false));
        buildAndCheck(p, "b-1");
        hg(repo, "update", "--clean", "default");
        touchAndCommit(repo, "default-2");
        // Changes in default should be ignored.
        assertFalse(pollSCMChanges(p));
        hg(repo, "update", "--clean", "b");
        touchAndCommit(repo, "b-2");
        // But changes in b should be pulled.
        assertTrue(pollSCMChanges(p));
        buildAndCheck(p, "b-2");
        // Switch to default branch with an existing workspace.
        p.setScm(new MercurialSCM(hgInstallation, repo.getPath(), null, null, null, null, false, false));
        // Should now consider preexisting changesets in default to be poll triggers.
        assertTrue(pollSCMChanges(p));
        // Should switch working copy to default branch.
        buildAndCheck(p, "default-2");
        touchAndCommit(repo, "b-3");
        // Changes in other branch should be ignored.
        assertFalse(pollSCMChanges(p));
    }

    @Bug(1099)
    public void testPollingLimitedToModules() throws Exception {
        FreeStyleProject p = createFreeStyleProject();
        p.setScm(new MercurialSCM(hgInstallation, repo.getPath(), null, "dir1 dir2", null, null, false, false));
        hg(repo, "init");
        touchAndCommit(repo, "dir1/f");
        buildAndCheck(p, "dir1/f");
        touchAndCommit(repo, "dir2/f");
        assertTrue(pollSCMChanges(p));
        buildAndCheck(p, "dir2/f");
        touchAndCommit(repo, "dir3/f");
        assertFalse(pollSCMChanges(p));
        // No support for partial checkouts yet, so workspace will contain everything.
        buildAndCheck(p, "dir3/f");
        // HUDSON-4972: do not pay attention to merges
        // (reproduce using the pathological scenario, since reproducing the actual scenario
        // where merge gives meaningless file list is not so easy)
        hg(repo, "update", "0");
        touchAndCommit(repo, "dir4/f");
        hg(repo, "merge");
        new FilePath(repo).child("dir2/f").write("stuff", "UTF-8");
        hg(repo, "commit", "--message", "merged");
        assertFalse(pollSCMChanges(p));
        buildAndCheck(p, "dir4/f");
    }

    @Bug(6337)
    public void testPollingLimitedToModules2() throws Exception {
        FreeStyleProject p = createFreeStyleProject();
        p.setScm(new MercurialSCM(hgInstallation, repo.getPath(), null, "dir1", null, null, false, false));
        hg(repo, "init");
        touchAndCommit(repo, "starter");
        pollSCMChanges(p);
        buildAndCheck(p, "starter");
        touchAndCommit(repo, "dir2/f");
        assertFalse(pollSCMChanges(p));
        touchAndCommit(repo, "dir1/f");
        assertTrue(pollSCMChanges(p));
        buildAndCheck(p, "dir1/f");
    }

    @Bug(4702)
    public void testChangelogLimitedToModules() throws Exception {
        FreeStyleProject p = createFreeStyleProject();
        // Control case: no modules specified.
        p.setScm(new MercurialSCM(hgInstallation, repo.getPath(), null, null, null, null, false, false));
        hg(repo, "init");
        touchAndCommit(repo, "dir1/f1");
        p.scheduleBuild2(0).get();
        touchAndCommit(repo, "dir2/f1");
        Iterator<? extends ChangeLogSet.Entry> it = p.scheduleBuild2(0).get().getChangeSet().iterator();
        assertTrue(it.hasNext());
        ChangeLogSet.Entry entry = it.next();
        assertEquals(Collections.singleton("dir2/f1"), new HashSet<String>(entry.getAffectedPaths()));
        assertFalse(it.hasNext());
        p.setScm(new MercurialSCM(hgInstallation, repo.getPath(), null, "dir1 extra", null, null, false, false));
        // dir2/f2 change should be ignored.
        touchAndCommit(repo, "dir1/f2");
        touchAndCommit(repo, "dir2/f2");
        it = p.scheduleBuild2(0).get().getChangeSet().iterator();
        assertTrue(it.hasNext());
        entry = it.next();
        assertEquals(Collections.singleton("dir1/f2"), new HashSet<String>(entry.getAffectedPaths()));
        assertFalse(it.hasNext());
        // First commit should match (because at least one file does) but not second.
        touchAndCommit(repo, "dir2/f3", "dir1/f3");
        touchAndCommit(repo, "dir2/f4", "dir2/f5");
        it = p.scheduleBuild2(0).get().getChangeSet().iterator();
        assertTrue(it.hasNext());
        entry = it.next();
        assertEquals(new HashSet<String>(Arrays.asList("dir1/f3", "dir2/f3")), new HashSet<String>(entry.getAffectedPaths()));
        assertFalse(it.hasNext());
        // Any module in the list can trigger an inclusion.
        touchAndCommit(repo, "extra/f1");
        it = p.scheduleBuild2(0).get().getChangeSet().iterator();
        assertTrue(it.hasNext());
        entry = it.next();
        assertEquals(Collections.singleton("extra/f1"), new HashSet<String>(entry.getAffectedPaths()));
        assertFalse(it.hasNext());
    }

    @Bug(4271)
    public void testParameterizedBuildsBranch() throws Exception {
        hg(repo, "init");
        touchAndCommit(repo, "trunk");
        hg(repo, "update", "null");
        hg(repo, "branch", "b");
        touchAndCommit(repo, "variant");
        FreeStyleProject p = createFreeStyleProject();
        p.setScm(new MercurialSCM(hgInstallation, repo.getPath(), "${BRANCH}", null, null, null, false, false));
        // This is not how a real parameterized build runs, but using ParametersDefinitionProperty just looks untestable:
        String log = buildAndCheck(p, "variant", new ParametersAction(new StringParameterValue("BRANCH", "b")));
        assertTrue(log, log.contains("--rev b"));
        assertFalse(log, log.contains("--rev ${BRANCH}"));
        touchAndCommit(repo, "further-variant");
        // the following assertion commented out as a part of the fix to HUDSON-6126
        // assertTrue(pollSCMChanges(p));
        buildAndCheck(p, "further-variant", new ParametersAction(new StringParameterValue("BRANCH", "b")));
    }

    @Bug(6517)
    public void testFileListOmittedForMerges() throws Exception {
        FreeStyleProject p = createFreeStyleProject();
        p.setScm(new MercurialSCM(hgInstallation, repo.getPath(), null, null, null, null, false, false));
        hg(repo, "init");
        touchAndCommit(repo, "f1");
        p.scheduleBuild2(0).get();
        hg(repo, "up", "null");
        touchAndCommit(repo, "f2");
        hg(repo, "merge");
        hg(repo, "commit", "--message", "merge");
        Iterator<? extends ChangeLogSet.Entry> it = p.scheduleBuild2(0).get().getChangeSet().iterator();
        assertTrue(it.hasNext());
        ChangeLogSet.Entry entry = it.next();
        assertTrue(((MercurialChangeSet) entry).isMerge());
        assertEquals(Collections.emptySet(), new HashSet<String>(entry.getAffectedPaths()));
        assertTrue(it.hasNext());
        entry = it.next();
        assertFalse(((MercurialChangeSet) entry).isMerge());
        assertEquals(Collections.singleton("f2"), new HashSet<String>(entry.getAffectedPaths()));
        assertFalse(it.hasNext());
    }

    @Bug(3602)
    public void testSubdirectoryCheckout() throws Exception {
        FreeStyleProject p = createFreeStyleProject();
        p.setScm(new MercurialSCM(hgInstallation, repo.getPath(), null, null, "repo", null, false, false));
        hg(repo, "init");
        touchAndCommit(repo, "f1");
        buildAndCheck(p, "repo/f1");
        touchAndCommit(repo, "f2");
        buildAndCheck(p, "repo/f2");
        touchAndCommit(repo, "f3");
        Iterator<? extends ChangeLogSet.Entry> it = p.scheduleBuild2(0).get().getChangeSet().iterator();
        assertTrue(it.hasNext());
        ChangeLogSet.Entry entry = it.next();
        assertEquals(Collections.singleton("f3"), new HashSet<String>(entry.getAffectedPaths()));
        assertFalse(it.hasNext());
    }

    public void testMultipleProjectsForSingleSource() throws Exception {
        FreeStyleProject one = createFreeStyleProject();
        FreeStyleProject two = createFreeStyleProject();
        FreeStyleProject three = createFreeStyleProject();
        FreeStyleProject four = createFreeStyleProject();
        one.  setScm(new MercurialSCM(hgInstallation, repo.getPath(), null, null, null, null, false, false));
        two.  setScm(new MercurialSCM(hgInstallation, repo.getPath(), null, null, null, null, false, false));
        three.setScm(new MercurialSCM(hgInstallation, repo.getPath(), "b", null, null, null, false, false));
        four. setScm(new MercurialSCM(hgInstallation, repo.getPath(), "b", null, null, null, false, false));

        hg(repo, "init");
        touchAndCommit(repo, "f1");
        assertTrue(pollSCMChanges(one));
        buildAndCheck(one, "f1");
        assertTrue(pollSCMChanges(two));

        hg(repo, "branch", "b");
        touchAndCommit(repo, "b1");

        assertFalse(pollSCMChanges(one));

        buildAndCheck(three, "b1");
        buildAndCheck(four, "b1");

        touchAndCommit(repo, "b2");
        assertTrue(pollSCMChanges(three));
        buildAndCheck(three, "b2");
        assertTrue(pollSCMChanges(four));
        
        assertFalse(pollSCMChanges(one));
    }

    /**
     * Control case for {@link #testChangelogOnClone()}.
     */
    public void testChangelogOnUpdate() throws Exception {
        AbstractBuild<?, ?> b;
        FreeStyleProject p = createFreeStyleProject();
        p.setScm(new MercurialSCM(hgInstallation, repo.getPath(), null, null, null, null, false, false));
        hg(repo, "init");
        touchAndCommit(repo, "dir1/f1");
        b = p.scheduleBuild2(0).get();
        assertTrue(b.getChangeSet().isEmptySet());
        touchAndCommit(repo, "dir2/f1");
        b = p.scheduleBuild2(0).get();
        assertChangeSetPaths(Collections.singletonList(Collections.singleton("dir2/f1")), b);
        touchAndCommit(repo, "dir3/f1");
        b = p.scheduleBuild2(0).get();
        assertChangeSetPaths(Collections.singletonList(Collections.singleton("dir3/f1")), b);
    }

    /**
     * The change log should be based on comparison with the previous build,
     * not depending on the state of the current local clone.  If a workspace
     * is wiped out, or the build is run on a new slave, it should still result
     * in the same change log.  This test verifies that, by comparing the
     * "normal" behavior with when the workspace is removed after every build.
     */
    @Bug(10255)
    public void testChangelogOnClone() throws Exception {
        AbstractBuild<?, ?> b;
        FreeStyleProject p = createFreeStyleProject();
        p.setScm(new MercurialSCM(hgInstallation, repo.getPath(), null, null, null, null, false, false));
        hg(repo, "init");
        touchAndCommit(repo, "dir1/f1");
        b = p.scheduleBuild2(0).get();
        assertTrue(b.getChangeSet().isEmptySet());
        b.getWorkspace().deleteRecursive(); // Remove the workspace to force a re-clone
        touchAndCommit(repo, "dir2/f1");
        b = p.scheduleBuild2(0).get();
        assertChangeSetPaths(Collections.singletonList(Collections.singleton("dir2/f1")), b);
        b.getWorkspace().deleteRecursive(); // Remove the workspace to force a re-clone
        touchAndCommit(repo, "dir3/f1");
        b = p.scheduleBuild2(0).get();
        assertChangeSetPaths(Collections.singletonList(Collections.singleton("dir3/f1")), b);
    }

    /**
     * The change log should be based on comparison with the previous build,
     * not depending on the state of the current local clone.  When there are
     * multiple nodes in use, it's possible that there will be a local clone
     * that doesn't contain the same changesets as the one that was used for
     * the previous build.  Regardless, that shouldn't affect the change log.
     * This test verifies that by running 3 builds, each for one commit, but
     * alternating which node the build runs on.
     */
    @Bug(10255)
    public void testChangelogFromPreviousBuild() throws Exception {
        AbstractBuild<?, ?> b;
        FreeStyleProject p = createFreeStyleProject();
        PretendSlave s1 = createNoopPretendSlave();
        PretendSlave s2 = createNoopPretendSlave();
        p.setScm(new MercurialSCM(hgInstallation, repo.getPath(), null, null, null, null, false, false));
        p.setAssignedNode(s1);
        hg(repo, "init");
        touchAndCommit(repo, "dir1/f1");
        b = p.scheduleBuild2(0).get();
        assertTrue(b.getChangeSet().isEmptySet());
        p.setAssignedNode(s2);
        touchAndCommit(repo, "dir2/f1");
        b = p.scheduleBuild2(0).get();
        // this isn't as notable, as it's also covered by testChangelogOnClone
        // assertChangeSetPaths(Collections.singletonList(Collections.singleton("dir2/f1")), b);
        p.setAssignedNode(s1);
        touchAndCommit(repo, "dir3/f1");
        b = p.scheduleBuild2(0).get();
        assertChangeSetPaths(Collections.singletonList(Collections.singleton("dir3/f1")), b);
    }

    private PretendSlave createNoopPretendSlave() throws Exception {
        return createPretendSlave(new NoopFakeLauncher());
    }

    private void assertChangeSetPaths(List<Set<String>> expectedChangeSetPaths, AbstractBuild<?, ?> build) {
        ChangeLogSet<? extends Entry> actualChangeLogSet = build.getChangeSet();
        List<Set<String>> actualChangeSetPaths = new LinkedList<Set<String>>();
        for (Entry entry : actualChangeLogSet) {
            actualChangeSetPaths.add(new LinkedHashSet<String>(entry.getAffectedPaths()));
        }
        assertEquals(expectedChangeSetPaths, actualChangeSetPaths);
    }

    private static final class NoopFakeLauncher implements FakeLauncher {
        public Proc onLaunch(Launcher.ProcStarter p) throws IOException {
            return null;
        }
    }

    /* XXX the following will pass, but canUpdate is not going to work without further changes:
    public void testParameterizedBuildsSource() throws Exception {
        p = createFreeStyleProject();
        p.setScm(new MercurialSCM(hgInstallation, "${REPO}", null, null, null, false, false));
        buildAndCheck(p, "trunk", new ParametersAction(new StringParameterValue("REPO", repo.getPath())));
        String hgrc = p.getSomeWorkspace().child(".hg/hgrc").readToString();
        assertTrue(hgrc.contains(repo.getPath()));
    }
     */

    /* XXX not yet supported; not sure how to expand var in MercurialSCM.createChangeLogParser:
    public void testParameterizedBuildsModules() throws Exception {
        hg(repo, "init");
        touchAndCommit(repo, "trunk", "dir1/f", "dir2/f");
        FreeStyleProject p = createFreeStyleProject();
        p.setScm(new MercurialSCM(hgInstallation, repo.getPath(), null, "${MODULES}", null, false, false));
        buildAndCheck(p, "dir1/f", new ParametersAction(new StringParameterValue("MODULES", "dir2")));
        hg(repo, "update", "default");
        touchAndCommit(repo, "dir1/g");
        assertFalse(pollSCMChanges(p));
        touchAndCommit(repo, "dir2/g");
        assertTrue(pollSCMChanges(p));
    }
     */

}
