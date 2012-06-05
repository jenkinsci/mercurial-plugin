package hudson.plugins.mercurial;

import hudson.FilePath;
import hudson.Launcher;
import hudson.Proc;
import hudson.model.AbstractBuild;
import hudson.model.Action;
import hudson.model.Cause.UserCause;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.ParametersAction;
import hudson.model.Result;
import hudson.model.StringParameterValue;
import hudson.scm.ChangeLogSet;
import hudson.scm.ChangeLogSet.Entry;
import hudson.scm.PollingResult;
import hudson.scm.SCM;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import org.jvnet.hudson.test.Bug;
import org.jvnet.hudson.test.FakeLauncher;
import org.jvnet.hudson.test.PretendSlave;

public class MercurialSCMTest extends MercurialTestCase {

    private File repo;

    protected @Override
    void setUp() throws Exception {
        super.setUp();
        repo = createTmpDir();
    }

    protected String hgInstallation() {
        return null;
    }

    @Bug(13329)
    public void testBasicOps() throws Exception {
        FreeStyleProject p = createFreeStyleProject();
        p.setScm(new MercurialSCM(hgInstallation(), repo.getPath(), null, null,
                null, null, false));

        hg(repo, "init");
        touchAndCommit(repo, "a");
        String log = buildAndCheck(p, "a");
        assertTrue(log, log.contains(" clone --"));
        touchAndCommit(repo, "b");
        log = buildAndCheck(p, "b");
        assertTrue(log, log.contains(" update --"));
        assertFalse(log, log.contains(" clone --"));
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
        p.setScm(new MercurialSCM(hgInstallation(), repo.getPath(), "b", null,
                null, null, false));
        buildAndCheck(p, "b-1");
        hg(repo, "update", "--clean", "default");
        touchAndCommit(repo, "default-2");
        // Changes in default should be ignored.
        assertFalse(pollSCMChanges(p).hasChanges());
        hg(repo, "update", "--clean", "b");
        touchAndCommit(repo, "b-2");
        // But changes in b should be pulled.
        assertTrue(pollSCMChanges(p).hasChanges());
        buildAndCheck(p, "b-2");
        // Switch to default branch with an existing workspace.
        p.setScm(new MercurialSCM(hgInstallation(), repo.getPath(), null, null,
                null, null, false));
        // Should now consider preexisting changesets in default to be poll
        // triggers.
        assertTrue(pollSCMChanges(p).hasChanges());
        // Should switch working copy to default branch.
        buildAndCheck(p, "default-2");
        touchAndCommit(repo, "b-3");
        // Changes in other branch should be ignored.
        assertFalse(pollSCMChanges(p).hasChanges());
    }

    @Bug(1099)
    public void testPollingLimitedToModules() throws Exception {
        PollingResult pr;
        FreeStyleProject p = createFreeStyleProject();
        p.setScm(new MercurialSCM(hgInstallation(), repo.getPath(), null,
                "dir1 dir2", null, null, false));
        hg(repo, "init");
        touchAndCommit(repo, "dir1/f");
        buildAndCheck(p, "dir1/f");
        touchAndCommit(repo, "dir2/f");
        pr = pollSCMChanges(p);
        assertEquals(PollingResult.Change.SIGNIFICANT, pr.change);
        buildAndCheck(p, "dir2/f");
        touchAndCommit(repo, "dir3/f");
        pr = pollSCMChanges(p);
        assertEquals(PollingResult.Change.INSIGNIFICANT, pr.change);
        // No support for partial checkouts yet, so workspace will contain
        // everything.
        buildAndCheck(p, "dir3/f");
        /* superseded by JENKINS-7594:
        // HUDSON-4972: do not pay attention to merges
        // (reproduce using the pathological scenario, since reproducing the
        // actual scenario
        // where merge gives meaningless file list is not so easy)
        hg(repo, "update", "0");
        touchAndCommit(repo, "dir4/f");
        hg(repo, "merge");
        new FilePath(repo).child("dir2/f").write("stuff", "UTF-8");
        hg(repo, "commit", "--message", "merged");
        pr = pollSCMChanges(p);
        assertEquals(PollingResult.Change.INSIGNIFICANT, pr.change);
        buildAndCheck(p, "dir4/f");
        */
    }

    @Bug(6337)
    public void testPollingLimitedToModules2() throws Exception {
        PollingResult pr;
        FreeStyleProject p = createFreeStyleProject();
        p.setScm(new MercurialSCM(hgInstallation(), repo.getPath(), null, "dir1",
                null, null, false));
        hg(repo, "init");
        touchAndCommit(repo, "starter");
        pollSCMChanges(p);
        buildAndCheck(p, "starter");
        touchAndCommit(repo, "dir2/f");
        pr = pollSCMChanges(p);
        assertEquals(PollingResult.Change.INSIGNIFICANT, pr.change);
        touchAndCommit(repo, "dir1/f");
        pr = pollSCMChanges(p);
        assertEquals(PollingResult.Change.SIGNIFICANT, pr.change);
        buildAndCheck(p, "dir1/f");
    }

    @Bug(12361)
    public void testPollingLimitedToModules3() throws Exception {
        PollingResult pr;
        FreeStyleProject p = createFreeStyleProject();
        p.setScm(new MercurialSCM(hgInstallation(), repo.getPath(), null, "dir1/f",
                null, null, false));
        hg(repo, "init");
        touchAndCommit(repo, "starter");
        pollSCMChanges(p);
        buildAndCheck(p, "starter");
        touchAndCommit(repo, "dir1/g");
        pr = pollSCMChanges(p);
        assertEquals(PollingResult.Change.INSIGNIFICANT, pr.change);
        touchAndCommit(repo, "dir1/f");
        pr = pollSCMChanges(p);
        assertEquals(PollingResult.Change.SIGNIFICANT, pr.change);
        buildAndCheck(p, "dir1/f");
    }

    @Bug(13174)
    public void testPollingIgnoresMetaFiles() throws Exception {
        FreeStyleProject p = createFreeStyleProject();
        p.setScm(new MercurialSCM(hgInstallation(), repo.getPath(), null, null, null, null, false));
        hg(repo, "init");
        touchAndCommit(repo, "f");
        buildAndCheck(p, "f");
        hg(repo, "tag", "mystuff");
        assertEquals(PollingResult.Change.INSIGNIFICANT, pollSCMChanges(p).change);
    }

    public void testParseStatus() throws Exception {
        assertEquals(new HashSet<String>(Arrays.asList("whatever", "added", "mo-re", "whatever-c", "initial", "more")), MercurialSCM.parseStatus(
                  "M whatever\n"
                + "A added\n"
                + "A mo-re\n"
                + "  more\n"
                + "A whatever-c\n"
                + "  whatever\n"
                + "R initial\n"
                + "R more\n"));
    }

    @Bug(7594)
    public void testPollingHonorsBranchMerges() throws Exception {
        FreeStyleProject p = createFreeStyleProject();
        p.setScm(new MercurialSCM(hgInstallation(), repo.getPath(), null, null, null, null, false));
        hg(repo, "init");
        touchAndCommit(repo, "starter");
        pollSCMChanges(p);
        buildAndCheck(p, "starter");
        hg(repo, "branch", "b");
        touchAndCommit(repo, "feature");
        hg(repo, "update", "default");
        hg(repo, "merge", "b");
        hg(repo, "commit", "--message", "merged");
        PollingResult pr = pollSCMChanges(p);
        assertEquals(PollingResult.Change.SIGNIFICANT, pr.change);
        buildAndCheck(p, "feature");
    }

    @Bug(7594)
    public void testPollingHonorsBranchMergesWithModules() throws Exception {
        FreeStyleProject p = createFreeStyleProject();
        p.setScm(new MercurialSCM(hgInstallation(), repo.getPath(), null, "mod1", null, null, false));
        hg(repo, "init");
        touchAndCommit(repo, "starter");
        pollSCMChanges(p);
        buildAndCheck(p, "starter");
        hg(repo, "branch", "mod1dev");
        touchAndCommit(repo, "mod1/feature");
        hg(repo, "update", "default");
        hg(repo, "merge", "mod1dev");
        hg(repo, "commit", "--message", "merged");
        PollingResult pr = pollSCMChanges(p);
        assertEquals(PollingResult.Change.SIGNIFICANT, pr.change);
        buildAndCheck(p, "mod1/feature");
        hg(repo, "branch", "mod2dev");
        touchAndCommit(repo, "mod2/feature");
        hg(repo, "update", "default");
        hg(repo, "merge", "mod2dev");
        hg(repo, "commit", "--message", "merged");
        pr = pollSCMChanges(p);
        assertEquals(PollingResult.Change.INSIGNIFICANT, pr.change);
    }

    @Bug(4702)
    public void testChangelogLimitedToModules() throws Exception {
        FreeStyleProject p = createFreeStyleProject();
        // Control case: no modules specified.
        p.setScm(new MercurialSCM(hgInstallation(), repo.getPath(), null, null,
                null, null, false));
        hg(repo, "init");
        touchAndCommit(repo, "dir1/f1");
        p.scheduleBuild2(0).get();
        touchAndCommit(repo, "dir2/f1");
        Iterator<? extends ChangeLogSet.Entry> it = p.scheduleBuild2(0).get()
                .getChangeSet().iterator();
        assertTrue(it.hasNext());
        ChangeLogSet.Entry entry = it.next();
        assertEquals(Collections.singleton("dir2/f1"), new HashSet<String>(
                entry.getAffectedPaths()));
        assertFalse(it.hasNext());
        p.setScm(new MercurialSCM(hgInstallation(), repo.getPath(), null,
                "dir1 extra", null, null, false));
        // dir2/f2 change should be ignored.
        touchAndCommit(repo, "dir1/f2");
        touchAndCommit(repo, "dir2/f2");
        it = p.scheduleBuild2(0).get().getChangeSet().iterator();
        assertTrue(it.hasNext());
        entry = it.next();
        assertEquals(Collections.singleton("dir1/f2"), new HashSet<String>(
                entry.getAffectedPaths()));
        assertFalse(it.hasNext());
        // First commit should match (because at least one file does) but not
        // second.
        touchAndCommit(repo, "dir2/f3", "dir1/f3");
        touchAndCommit(repo, "dir2/f4", "dir2/f5");
        it = p.scheduleBuild2(0).get().getChangeSet().iterator();
        assertTrue(it.hasNext());
        entry = it.next();
        assertEquals(new HashSet<String>(Arrays.asList("dir1/f3", "dir2/f3")),
                new HashSet<String>(entry.getAffectedPaths()));
        assertFalse(it.hasNext());
        // Any module in the list can trigger an inclusion.
        touchAndCommit(repo, "extra/f1");
        it = p.scheduleBuild2(0).get().getChangeSet().iterator();
        assertTrue(it.hasNext());
        entry = it.next();
        assertEquals(Collections.singleton("extra/f1"), new HashSet<String>(
                entry.getAffectedPaths()));
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
        p.setScm(new MercurialSCM(hgInstallation(), repo.getPath(), "${BRANCH}",
                null, null, null, false));
        // This is not how a real parameterized build runs, but using
        // ParametersDefinitionProperty just looks untestable:
        String log = buildAndCheck(p, "variant", new ParametersAction(
                new StringParameterValue("BRANCH", "b")));
        assertTrue(log, log.contains("--rev b"));
        assertFalse(log, log.contains("--rev ${BRANCH}"));
        touchAndCommit(repo, "further-variant");
        // the following assertion commented out as a part of the fix to
        // HUDSON-6126
        // assertTrue(pollSCMChanges(p));
        buildAndCheck(p, "further-variant", new ParametersAction(
                new StringParameterValue("BRANCH", "b")));
    }

    @Bug(6517)
    public void testFileListOmittedForMerges() throws Exception {
        FreeStyleProject p = createFreeStyleProject();
        p.setScm(new MercurialSCM(hgInstallation(), repo.getPath(), null, null,
                null, null, false));
        hg(repo, "init");
        touchAndCommit(repo, "f1");
        p.scheduleBuild2(0).get();
        hg(repo, "up", "null");
        touchAndCommit(repo, "f2");
        hg(repo, "merge");
        hg(repo, "commit", "--message", "merge");
        Iterator<? extends ChangeLogSet.Entry> it = p.scheduleBuild2(0).get()
                .getChangeSet().iterator();
        assertTrue(it.hasNext());
        ChangeLogSet.Entry entry = it.next();
        assertTrue(((MercurialChangeSet) entry).isMerge());
        assertEquals(Collections.emptySet(),
                new HashSet<String>(entry.getAffectedPaths()));
        assertTrue(it.hasNext());
        entry = it.next();
        assertFalse(((MercurialChangeSet) entry).isMerge());
        assertEquals(Collections.singleton("f2"),
                new HashSet<String>(entry.getAffectedPaths()));
        assertFalse(it.hasNext());
    }

    public void testChangesMergedToRenamedModulesTriggerBuild() throws Exception {
        hg(repo, "init");
        touchAndCommit(repo, "alltogether/some_interface", "alltogether/some_class");
        hg(repo, "branch", "stable");
        //create a change in a lower branch, which should trigger a build later on
        touchAndCommit(repo, "alltogether/some_class");


        hg(repo, "up", "default");
        hg(repo, "mv", "alltogether/some_interface", "api/some_interface");
        hg(repo, "mv", "alltogether/some_class", "impl/some_class");
        hg(repo, "commit", "--message", "reorganizing repository to properly split api and implementation");
        String reorganizationCommit = getLastChangesetId(repo);

        FreeStyleProject projectForImplModule = createFreeStyleProject();
        projectForImplModule.setScm(new MercurialSCM(hgInstallation(), repo.getPath(), null, "impl", null, null, false));
        projectForImplModule.scheduleBuild2(0).get();

        hg(repo, "merge", "stable");
        hg(repo, "commit", "--message", "merge changes from stable branch");
        String mergeCommit = getLastChangesetId(repo);

        assertPollingResult(PollingResult.Change.SIGNIFICANT, reorganizationCommit, mergeCommit, pollSCMChanges(projectForImplModule));
    }

    @Bug(3602)
    public void testSubdirectoryCheckout() throws Exception {
        FreeStyleProject p = createFreeStyleProject();
        p.setScm(new MercurialSCM(hgInstallation(), repo.getPath(), null, null,
                "repo", null, false));
        hg(repo, "init");
        touchAndCommit(repo, "f1");
        buildAndCheck(p, "repo/f1");
        touchAndCommit(repo, "f2");
        buildAndCheck(p, "repo/f2");
        touchAndCommit(repo, "f3");
        Iterator<? extends ChangeLogSet.Entry> it = p.scheduleBuild2(0).get()
                .getChangeSet().iterator();
        assertTrue(it.hasNext());
        ChangeLogSet.Entry entry = it.next();
        assertEquals(Collections.singleton("f3"),
                new HashSet<String>(entry.getAffectedPaths()));
        assertFalse(it.hasNext());
    }

    public void testClean() throws Exception {
        FreeStyleProject p = createFreeStyleProject();
        p.setScm(new MercurialSCM(hgInstallation(), repo.getPath(), null, null, null, null, true));
        hg(repo, "init");
        touchAndCommit(repo, "f1");
        buildAndCheck(p, "f1");
        FilePath ws = p.getLastBuild().getWorkspace();
        ws.child("junk1").write("junk", null);
        ws.child("junk2").write("junk", null);
        // from Hg 1.3 changelog: "update: don't unlink added files when -C/--clean is specified"
        hg(new File(ws.getRemote()), "add", "junk2");
        touchAndCommit(repo, "f2");
        buildAndCheck(p, "f2");
        Set<String> kids = new TreeSet<String>();
        for (FilePath kid : ws.list()) {
            kids.add(kid.getName());
        }
        assertEquals("[.hg, f1, f2]", kids.toString());
    }

    public void testMultipleProjectsForSingleSource() throws Exception {
        FreeStyleProject one = createFreeStyleProject();
        FreeStyleProject two = createFreeStyleProject();
        FreeStyleProject three = createFreeStyleProject();
        FreeStyleProject four = createFreeStyleProject();
        one.setScm(new MercurialSCM(hgInstallation(), repo.getPath(), null, null,
                null, null, false));
        two.setScm(new MercurialSCM(hgInstallation(), repo.getPath(), null, null,
                null, null, false));
        three.setScm(new MercurialSCM(hgInstallation(), repo.getPath(), "b",
                null, null, null, false));
        four.setScm(new MercurialSCM(hgInstallation(), repo.getPath(), "b", null,
                null, null, false));

        hg(repo, "init");
        touchAndCommit(repo, "f1");
        assertTrue(pollSCMChanges(one).hasChanges());
        buildAndCheck(one, "f1");
        assertTrue(pollSCMChanges(two).hasChanges());

        hg(repo, "branch", "b");
        touchAndCommit(repo, "b1");

        assertFalse(pollSCMChanges(one).hasChanges());

        buildAndCheck(three, "b1");
        buildAndCheck(four, "b1");

        touchAndCommit(repo, "b2");
        assertTrue(pollSCMChanges(three).hasChanges());
        buildAndCheck(three, "b2");
        assertTrue(pollSCMChanges(four).hasChanges());

        assertFalse(pollSCMChanges(one).hasChanges());
    }

    /**
     * Control case for {@link #testChangelogOnClone()}.
     */
    public void testChangelogOnUpdate() throws Exception {
        AbstractBuild<?, ?> b;
        FreeStyleProject p = createFreeStyleProject();
        p.setScm(new MercurialSCM(hgInstallation(), repo.getPath(), null, null,
                null, null, false));
        hg(repo, "init");
        touchAndCommit(repo, "dir1/f1");
        b = p.scheduleBuild2(0).get();
        assertTrue(b.getChangeSet().isEmptySet());
        touchAndCommit(repo, "dir2/f1");
        b = p.scheduleBuild2(0).get();
        assertChangeSetPaths(
                Collections.singletonList(Collections.singleton("dir2/f1")), b);
        touchAndCommit(repo, "dir3/f1");
        b = p.scheduleBuild2(0).get();
        assertChangeSetPaths(
                Collections.singletonList(Collections.singleton("dir3/f1")), b);
    }

    /**
     * The change log should be based on comparison with the previous build, not
     * depending on the state of the current local clone. If a workspace is
     * wiped out, or the build is run on a new slave, it should still result in
     * the same change log. This test verifies that, by comparing the "normal"
     * behavior with when the workspace is removed after every build.
     */
    @Bug(10255)
    public void testChangelogOnClone() throws Exception {
        AbstractBuild<?, ?> b;
        FreeStyleProject p = createFreeStyleProject();
        p.setScm(new MercurialSCM(hgInstallation(), repo.getPath(), null, null,
                null, null, false));
        hg(repo, "init");
        touchAndCommit(repo, "dir1/f1");
        b = p.scheduleBuild2(0).get();
        assertTrue(b.getChangeSet().isEmptySet());
        b.getWorkspace().deleteRecursive(); // Remove the workspace to force a
                                            // re-clone
        touchAndCommit(repo, "dir2/f1");
        b = p.scheduleBuild2(0).get();
        assertChangeSetPaths(
                Collections.singletonList(Collections.singleton("dir2/f1")), b);
        b.getWorkspace().deleteRecursive(); // Remove the workspace to force a
                                            // re-clone
        touchAndCommit(repo, "dir3/f1");
        b = p.scheduleBuild2(0).get();
        assertChangeSetPaths(
                Collections.singletonList(Collections.singleton("dir3/f1")), b);
    }

    /**
     * The change log should be based on comparison with the previous build, not
     * depending on the state of the current local clone. When there are
     * multiple nodes in use, it's possible that there will be a local clone
     * that doesn't contain the same changesets as the one that was used for the
     * previous build. Regardless, that shouldn't affect the change log. This
     * test verifies that by running 3 builds, each for one commit, but
     * alternating which node the build runs on.
     */
    @Bug(10255)
    public void testChangelogFromPreviousBuild() throws Exception {
        AbstractBuild<?, ?> b;
        FreeStyleProject p = createFreeStyleProject();
        PretendSlave s1 = createNoopPretendSlave();
        PretendSlave s2 = createNoopPretendSlave();
        p.setScm(new MercurialSCM(hgInstallation(), repo.getPath(), null, null,
                null, null, false));
        p.setAssignedNode(s1);
        hg(repo, "init");
        touchAndCommit(repo, "dir1/f1");
        b = p.scheduleBuild2(0).get();
        assertTrue(b.getChangeSet().isEmptySet());
        p.setAssignedNode(s2);
        touchAndCommit(repo, "dir2/f1");
        b = p.scheduleBuild2(0).get();
        // this isn't as notable, as it's also covered by testChangelogOnClone
        // assertChangeSetPaths(Collections.singletonList(Collections.singleton("dir2/f1")),
        // b);
        p.setAssignedNode(s1);
        touchAndCommit(repo, "dir3/f1");
        b = p.scheduleBuild2(0).get();
        assertChangeSetPaths(
                Collections.singletonList(Collections.singleton("dir3/f1")), b);
    }

    @Bug(12162)
    public void testChangelogInMultiSCM() throws Exception {
        FreeStyleProject p = createFreeStyleProject();
        hg(repo, "init");
        touchAndCommit(repo, "r1f1");
        File repo2 = createTmpDir();
        hg(repo2, "init");
        touchAndCommit(repo2, "r2f1");
        p.setScm(new MultiSCM(Arrays.<SCM>asList(
                new MercurialSCM(hgInstallation(), repo.getPath(), null, null, "r1", null, false),
                new MercurialSCM(hgInstallation(), repo2.getPath(), null, null, "r2", null, false))));
        FreeStyleBuild b = assertBuildStatusSuccess(p.scheduleBuild2(0, new UserCause()).get());
        touchAndCommit(repo, "r1f2");
        touchAndCommit(repo2, "r2f2");
        assertTrue(pollSCMChanges(p).hasChanges());
        b = assertBuildStatusSuccess(p.scheduleBuild2(0, new UserCause()).get());
        List<Set<String>> paths = new ArrayList<Set<String>>();
        // XXX "r1/r1f2" etc. would be preferable; probably requires determineChanges to prepend subdir?
        paths.add(Collections.singleton("r1f2"));
        paths.add(Collections.singleton("r2f2"));
        assertChangeSetPaths(paths, b);
    }

    public void testPolling() throws Exception {
        AbstractBuild<?, ?> b;
        PollingResult pr;
        FreeStyleProject p = createFreeStyleProject();
        p.setScm(new MercurialSCM(hgInstallation(), repo.getPath(), null, null,
                null, null, false));
        p.setAssignedLabel(null); // Allow roaming

        // No builds, no workspace, but an available remote repository
        hg(repo, "init");
        touchAndCommit(repo, "f1");
        String cs1 = getLastChangesetId(repo);
        pr = pollSCMChanges(p);
        assertPollingResult(PollingResult.Change.INCOMPARABLE, null, null, pr);

        // We have a workspace, and no new changes in remote repository
        b = p.scheduleBuild2(0).get();
        pr = pollSCMChanges(p);
        assertPollingResult(PollingResult.Change.NONE, cs1, cs1, pr);

        // We have a workspace, and new changes in the remote repository
        touchAndCommit(repo, "f2");
        String cs2 = getLastChangesetId(repo);
        pr = pollSCMChanges(p);
        assertPollingResult(PollingResult.Change.SIGNIFICANT, cs1, cs2, pr);

        // We lost the workspace
        b.getWorkspace().deleteRecursive();
        pr = pollSCMChanges(p);
        if (p.getScm().requiresWorkspaceForPolling()) {
            assertPollingResult(PollingResult.Change.INCOMPARABLE, null, null, pr);
        } else {
            assertPollingResult(PollingResult.Change.NONE, cs2, cs2, pr);
        }
        b = p.scheduleBuild2(0).get();

        // Multiple polls
        touchAndCommit(repo, "f3");
        touchAndCommit(repo, "f4");
        String cs4 = getLastChangesetId(repo);
        pr = pollSCMChanges(p);
        assertPollingResult(PollingResult.Change.SIGNIFICANT, cs2, cs4, pr);
        touchAndCommit(repo, "f5");
        String cs5 = getLastChangesetId(repo);
        pr = pollSCMChanges(p);
        assertPollingResult(PollingResult.Change.SIGNIFICANT, cs4, cs5, pr);
    }
    
    @Bug(11460)
    public void testTrailingUrlWhitespace() throws Exception {
        FreeStyleProject p = createFreeStyleProject();
        p.setScm(new MercurialSCM(hgInstallation(), repo.getPath() + " ", null,
                null, null, null, false));
        hg(repo, "init");
        touchAndCommit(repo, "dir1/f1");
        AbstractBuild<?, ?> b = p.scheduleBuild2(0).get();
        assertEquals(Result.SUCCESS, b.getResult());
    }
    
    @Bug(12829)
        public void testNonExistingBranchesDontGenerateMercurialTagActionsInTheBuild() throws Exception {
        AbstractBuild<?, ?> b;
        FreeStyleProject p = createFreeStyleProject();
        p.setScm(new MercurialSCM(hgInstallation(), repo.getPath(), "non-existing-branch", null,
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

    private PretendSlave createNoopPretendSlave() throws Exception {
        return createPretendSlave(new NoopFakeLauncher());
    }

    private void assertChangeSetPaths(List<? extends Set<String>> expectedChangeSetPaths,
            AbstractBuild<?, ?> build) throws IOException {
        ChangeLogSet<? extends Entry> actualChangeLogSet = build.getChangeSet();
        List<Set<String>> actualChangeSetPaths = new LinkedList<Set<String>>();
        for (Entry entry : actualChangeLogSet) {
            actualChangeSetPaths.add(new LinkedHashSet<String>(entry
                    .getAffectedPaths()));
        }
        assertEquals(build.getLog(99).toString(), expectedChangeSetPaths, actualChangeSetPaths);
    }

    private void assertPollingResult(PollingResult.Change expectedChangeDegree,
            String expectedBaselineId, String expectedRemoteId,
            PollingResult actualPollingResult) {
        assertNotNull(actualPollingResult);
        PollingResult.Change actualChangeDegree = actualPollingResult.change;
        assertEquals(expectedChangeDegree, actualChangeDegree);
        if (expectedBaselineId == null) {
            assertNull(actualPollingResult.baseline);
        } else {
            MercurialTagAction actualBaseline = (MercurialTagAction) actualPollingResult.baseline;
            assertEquals(expectedBaselineId, actualBaseline.id);
        }
        if (expectedRemoteId == null) {
            assertNull(actualPollingResult.remote);
        } else {
            MercurialTagAction actualRemote = (MercurialTagAction) actualPollingResult.remote;
            assertEquals(expectedRemoteId, actualRemote.id);
        }
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
