package hudson.plugins.mercurial;

import hudson.FilePath;
import hudson.Launcher;
import hudson.Proc;
import hudson.model.AbstractBuild;
import hudson.model.Action;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.ParametersAction;
import hudson.model.ParametersDefinitionProperty;
import hudson.model.Result;
import hudson.model.Slave;
import hudson.model.StringParameterDefinition;
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
import javax.annotation.CheckForNull;

import org.jenkinsci.plugins.multiplescms.MultiSCM;
import org.jenkinsci.test.acceptance.docker.DockerRule;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.jvnet.hudson.test.Bug;
import org.jvnet.hudson.test.FakeLauncher;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;

import static org.junit.Assert.*;
import org.junit.Assume;
import org.junit.AssumptionViolatedException;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.jvnet.hudson.test.BuildWatcher;

public abstract class SCMTestBase {

    /** {@link MercurialRule#hg(String...)} would skip these anyway, but it would take a lot longer. */
    @BeforeClass public static void requiresLocalHg() throws Exception {
        try {
            if (new ProcessBuilder("hg", "--version").start().waitFor() != 0) {
                throw new AssumptionViolatedException("hg --version signaled an error");
            }
        } catch (IOException ioe) {
            String message = ioe.getMessage();
            if (message.startsWith("Cannot run program \"hg\"") && message.endsWith("No such file or directory")) {
                throw new AssumptionViolatedException("hg is not available; please check that your PATH environment variable is properly configured");
            }
            Assume.assumeNoException(ioe); // failed to check availability of hg
        }
    }

    @Rule public JenkinsRule j = new JenkinsRule();
    @Rule public MercurialRule m = new MercurialRule(j);
    @Rule public TemporaryFolder tmp = new TemporaryFolder();
    @Rule public DockerRule<MercurialContainer> container = new DockerRule<MercurialContainer>(MercurialContainer.class);
    @ClassRule public static BuildWatcher buildWatcher = new BuildWatcher();
    private File repo;

    @Before public void setUp() throws Exception {
        repo = tmp.getRoot();
    }

    protected abstract @CheckForNull String hgInstallation() throws Exception;

    protected void assertClone(String log, boolean cloneExpected) {
        if (cloneExpected) {
            assertTrue(log, log.contains(" clone --"));
        } else {
            assertTrue(log, log.contains(" update --"));
            assertFalse(log, log.contains(" clone --"));
        }
    }

    // TODO migrate test cases to FunctionalTest, eventually delete this and subclasses

    @Bug(1099)
    @Test public void pollingLimitedToModules() throws Exception {
        PollingResult pr;
        FreeStyleProject p = j.createFreeStyleProject();
        p.setScm(new MercurialSCM(hgInstallation(), repo.getPath(), null,
                "dir1 dir2", null, null, false));
        m.hg(repo, "init");
        m.touchAndCommit(repo, "dir1/f");
        m.buildAndCheck(p, "dir1/f");
        m.touchAndCommit(repo, "dir2/f");
        pr = m.pollSCMChanges(p);
        assertEquals(PollingResult.Change.SIGNIFICANT, pr.change);
        m.buildAndCheck(p, "dir2/f");
        m.touchAndCommit(repo, "dir3/f");
        pr = m.pollSCMChanges(p);
        assertEquals(PollingResult.Change.INSIGNIFICANT, pr.change);
        // No support for partial checkouts yet, so workspace will contain
        // everything.
        m.buildAndCheck(p, "dir3/f");
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
    @Test public void pollingLimitedToModules2() throws Exception {
        PollingResult pr;
        FreeStyleProject p = j.createFreeStyleProject();
        p.setScm(new MercurialSCM(hgInstallation(), repo.getPath(), null, "dir1",
                null, null, false));
        m.hg(repo, "init");
        m.touchAndCommit(repo, "starter");
        m.pollSCMChanges(p);
        m.buildAndCheck(p, "starter");
        m.touchAndCommit(repo, "dir2/f");
        pr = m.pollSCMChanges(p);
        assertEquals(PollingResult.Change.INSIGNIFICANT, pr.change);
        m.touchAndCommit(repo, "dir1/f");
        pr = m.pollSCMChanges(p);
        assertEquals(PollingResult.Change.SIGNIFICANT, pr.change);
        m.buildAndCheck(p, "dir1/f");
    }

    @Bug(12361)
    @Test public void pollingLimitedToModules3() throws Exception {
        PollingResult pr;
        FreeStyleProject p = j.createFreeStyleProject();
        p.setScm(new MercurialSCM(hgInstallation(), repo.getPath(), null, "dir1/f",
                null, null, false));
        m.hg(repo, "init");
        m.touchAndCommit(repo, "starter");
        m.pollSCMChanges(p);
        m.buildAndCheck(p, "starter");
        m.touchAndCommit(repo, "dir1/g");
        pr = m.pollSCMChanges(p);
        assertEquals(PollingResult.Change.INSIGNIFICANT, pr.change);
        m.touchAndCommit(repo, "dir1/f");
        pr = m.pollSCMChanges(p);
        assertEquals(PollingResult.Change.SIGNIFICANT, pr.change);
        m.buildAndCheck(p, "dir1/f");
    }

    @Bug(13174)
    @Test public void pollingIgnoresMetaFiles() throws Exception {
        FreeStyleProject p = j.createFreeStyleProject();
        p.setScm(new MercurialSCM(hgInstallation(), repo.getPath(), null, null, null, null, false));
        m.hg(repo, "init");
        m.touchAndCommit(repo, "f");
        m.buildAndCheck(p, "f");
        m.hg(repo, "tag", "mystuff");
        assertEquals(PollingResult.Change.INSIGNIFICANT, m.pollSCMChanges(p).change);
    }

    @Bug(7594)
    @Test public void pollingHonorsBranchMerges() throws Exception {
        FreeStyleProject p = j.createFreeStyleProject();
        p.setScm(new MercurialSCM(hgInstallation(), repo.getPath(), null, null, null, null, false));
        m.hg(repo, "init");
        m.touchAndCommit(repo, "starter");
        m.pollSCMChanges(p);
        m.buildAndCheck(p, "starter");
        m.hg(repo, "branch", "b");
        m.touchAndCommit(repo, "feature");
        m.hg(repo, "update", "default");
        m.hg(repo, "merge", "b");
        m.hg(repo, "commit", "--message", "merged");
        PollingResult pr = m.pollSCMChanges(p);
        assertEquals(PollingResult.Change.SIGNIFICANT, pr.change);
        m.buildAndCheck(p, "feature");
    }

    @Bug(7594)
    @Test public void pollingHonorsBranchMergesWithModules() throws Exception {
        FreeStyleProject p = j.createFreeStyleProject();
        p.setScm(new MercurialSCM(hgInstallation(), repo.getPath(), null, "mod1", null, null, false));
        m.hg(repo, "init");
        m.touchAndCommit(repo, "starter");
        m.pollSCMChanges(p);
        m.buildAndCheck(p, "starter");
        m.hg(repo, "branch", "mod1dev");
        m.touchAndCommit(repo, "mod1/feature");
        m.hg(repo, "update", "default");
        m.hg(repo, "merge", "mod1dev");
        m.hg(repo, "commit", "--message", "merged");
        PollingResult pr = m.pollSCMChanges(p);
        assertEquals(PollingResult.Change.SIGNIFICANT, pr.change);
        m.buildAndCheck(p, "mod1/feature");
        m.hg(repo, "branch", "mod2dev");
        m.touchAndCommit(repo, "mod2/feature");
        m.hg(repo, "update", "default");
        m.hg(repo, "merge", "mod2dev");
        m.hg(repo, "commit", "--message", "merged");
        pr = m.pollSCMChanges(p);
        assertEquals(PollingResult.Change.INSIGNIFICANT, pr.change);
    }

    @Bug(4702)
    @Test public void changelogLimitedToModules() throws Exception {
        FreeStyleProject p = j.createFreeStyleProject();
        // Control case: no modules specified.
        p.setScm(new MercurialSCM(hgInstallation(), repo.getPath(), null, null,
                null, null, false));
        m.hg(repo, "init");
        m.touchAndCommit(repo, "dir1/f1");
        p.scheduleBuild2(0).get();
        m.touchAndCommit(repo, "dir2/f1");
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
        m.touchAndCommit(repo, "dir1/f2");
        m.touchAndCommit(repo, "dir2/f2");
        it = p.scheduleBuild2(0).get().getChangeSet().iterator();
        assertTrue(it.hasNext());
        entry = it.next();
        assertEquals(Collections.singleton("dir1/f2"), new HashSet<String>(
                entry.getAffectedPaths()));
        assertFalse(it.hasNext());
        // First commit should match (because at least one file does) but not
        // second.
        m.touchAndCommit(repo, "dir2/f3", "dir1/f3");
        m.touchAndCommit(repo, "dir2/f4", "dir2/f5");
        it = p.scheduleBuild2(0).get().getChangeSet().iterator();
        assertTrue(it.hasNext());
        entry = it.next();
        assertEquals(new HashSet<String>(Arrays.asList("dir1/f3", "dir2/f3")),
                new HashSet<String>(entry.getAffectedPaths()));
        assertFalse(it.hasNext());
        // Any module in the list can trigger an inclusion.
        m.touchAndCommit(repo, "extra/f1");
        it = p.scheduleBuild2(0).get().getChangeSet().iterator();
        assertTrue(it.hasNext());
        entry = it.next();
        assertEquals(Collections.singleton("extra/f1"), new HashSet<String>(
                entry.getAffectedPaths()));
        assertFalse(it.hasNext());
    }

    @Bug(4271)
    @Test public void parameterizedBuildsBranch() throws Exception {
        m.hg(repo, "init");
        m.touchAndCommit(repo, "trunk");
        m.hg(repo, "update", "null");
        m.hg(repo, "branch", "b");
        m.touchAndCommit(repo, "variant");
        FreeStyleProject p = j.createFreeStyleProject();
        p.setScm(new MercurialSCM(hgInstallation(), repo.getPath(), "${BRANCH}",
                null, null, null, false));
        // SECURITY-170 - have to use ParametersDefinitionProperty
        p.addProperty(new ParametersDefinitionProperty(new StringParameterDefinition("BRANCH", "b")));
        String log = m.buildAndCheck(p, "variant", new ParametersAction(
                new StringParameterValue("BRANCH", "b")));
        assertTrue(log, log.contains("--rev b"));
        assertFalse(log, log.contains("--rev ${BRANCH}"));
        m.touchAndCommit(repo, "further-variant");
        // the following assertion commented out as a part of the fix to
        // HUDSON-6126
        // assertTrue(pollSCMChanges(p));
        m.buildAndCheck(p, "further-variant", new ParametersAction(
                new StringParameterValue("BRANCH", "b")));
    }

    @Bug(9686)
    @Test public void pollingExpandsParameterDefaults() throws Exception {
        m.hg(repo, "init");
        m.touchAndCommit(repo, "trunk");
        m.hg(repo, "update", "null");
        m.hg(repo, "branch", "b");
        m.touchAndCommit(repo, "variant");
        FreeStyleProject p = j.createFreeStyleProject();
        p.addProperty(new ParametersDefinitionProperty(new StringParameterDefinition("branch", "default")));
        p.setScm(new MercurialSCM(hgInstallation(), repo.getPath(), "${branch}", null, null, null, false));
        String log = m.buildAndCheck(p, "trunk", new ParametersAction(new StringParameterValue("branch", "default")));
        assertTrue(log, log.contains("--rev default"));
        /* TODO cannot behave sensibly when workspace contains a branch build because the *current* trunk revision will be seen as new; would need to compare to all historical build records, or keep a separate workspace per branch:
        log = m.buildAndCheck(p, "variant", new ParametersAction(new StringParameterValue("branch", "b")));
        assertTrue(log, log.contains("--rev b"));
        */
        assertEquals(PollingResult.Change.NONE, m.pollSCMChanges(p).change);
        m.hg(repo, "update", "default");
        m.touchAndCommit(repo, "trunk2");
        assertEquals(PollingResult.Change.SIGNIFICANT, m.pollSCMChanges(p).change);
    }

    @Bug(6517)
    @Test public void fileListOmittedForMerges() throws Exception {
        FreeStyleProject p = j.createFreeStyleProject();
        p.setScm(new MercurialSCM(hgInstallation(), repo.getPath(), null, null,
                null, null, false));
        m.hg(repo, "init");
        m.touchAndCommit(repo, "f1");
        p.scheduleBuild2(0).get();
        m.hg(repo, "up", "null");
        m.touchAndCommit(repo, "f2");
        m.hg(repo, "merge");
        m.hg(repo, "commit", "--message", "merge");
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

    @Test public void changesMergedToRenamedModulesTriggerBuild() throws Exception {
        m.hg(repo, "init");
        m.touchAndCommit(repo, "alltogether/some_interface", "alltogether/some_class");
        m.hg(repo, "branch", "stable");
        //create a change in a lower branch, which should trigger a build later on
        m.touchAndCommit(repo, "alltogether/some_class");


        m.hg(repo, "up", "default");
        m.hg(repo, "mv", "alltogether/some_interface", "api/some_interface");
        m.hg(repo, "mv", "alltogether/some_class", "impl/some_class");
        m.hg(repo, "commit", "--message", "reorganizing repository to properly split api and implementation");
        String reorganizationCommit = m.getLastChangesetId(repo);

        FreeStyleProject projectForImplModule = j.createFreeStyleProject();
        projectForImplModule.setScm(new MercurialSCM(hgInstallation(), repo.getPath(), null, "impl", null, null, false));
        projectForImplModule.scheduleBuild2(0).get();

        m.hg(repo, "merge", "stable");
        m.hg(repo, "commit", "--message", "merge changes from stable branch");
        String mergeCommit = m.getLastChangesetId(repo);

        assertPollingResult(PollingResult.Change.SIGNIFICANT, reorganizationCommit, mergeCommit, m.pollSCMChanges(projectForImplModule));
    }

    @Bug(3602)
    @Test public void subdirectoryCheckout() throws Exception {
        FreeStyleProject p = j.createFreeStyleProject();
        p.setScm(new MercurialSCM(hgInstallation(), repo.getPath(), null, null,
                "repo", null, false));
        m.hg(repo, "init");
        m.touchAndCommit(repo, "f1");
        m.buildAndCheck(p, "repo/f1");
        m.touchAndCommit(repo, "f2");
        m.buildAndCheck(p, "repo/f2");
        m.touchAndCommit(repo, "f3");
        Iterator<? extends ChangeLogSet.Entry> it = p.scheduleBuild2(0).get()
                .getChangeSet().iterator();
        assertTrue(it.hasNext());
        ChangeLogSet.Entry entry = it.next();
        assertEquals(Collections.singleton("f3"),
                new HashSet<String>(entry.getAffectedPaths()));
        assertFalse(it.hasNext());
    }

    @Test public void clean() throws Exception {
        FreeStyleProject p = j.createFreeStyleProject();
        p.setScm(new MercurialSCM(hgInstallation(), repo.getPath(), null, null, null, null, true));
        m.hg(repo, "init");
        m.touchAndCommit(repo, "f1");
        m.buildAndCheck(p, "f1");
        FilePath ws = p.getLastBuild().getWorkspace();
        ws.child("junk1").write("junk", null);
        ws.child("junk2").write("junk", null);
        // from Hg 1.3 changelog: "update: don't unlink added files when -C/--clean is specified"
        m.hg(new File(ws.getRemote()), "add", "junk2");
        m.touchAndCommit(repo, "f2");
        m.buildAndCheck(p, "f2");
        Set<String> kids = new TreeSet<String>();
        for (FilePath kid : ws.list()) {
            kids.add(kid.getName());
        }
        assertEquals("[.hg, f1, f2]", kids.toString());
    }

    @Test public void multipleProjectsForSingleSource() throws Exception {
        FreeStyleProject one = j.createFreeStyleProject();
        FreeStyleProject two = j.createFreeStyleProject();
        FreeStyleProject three = j.createFreeStyleProject();
        FreeStyleProject four = j.createFreeStyleProject();
        one.setScm(new MercurialSCM(hgInstallation(), repo.getPath(), null, null,
                null, null, false));
        two.setScm(new MercurialSCM(hgInstallation(), repo.getPath(), null, null,
                null, null, false));
        three.setScm(new MercurialSCM(hgInstallation(), repo.getPath(), "b",
                null, null, null, false));
        four.setScm(new MercurialSCM(hgInstallation(), repo.getPath(), "b", null,
                null, null, false));

        m.hg(repo, "init");
        m.touchAndCommit(repo, "f1");
        assertTrue(m.pollSCMChanges(one).hasChanges());
        m.buildAndCheck(one, "f1");
        assertTrue(m.pollSCMChanges(two).hasChanges());

        m.hg(repo, "branch", "b");
        m.touchAndCommit(repo, "b1");

        assertFalse(m.pollSCMChanges(one).hasChanges());

        m.buildAndCheck(three, "b1");
        m.buildAndCheck(four, "b1");

        m.touchAndCommit(repo, "b2");
        assertTrue(m.pollSCMChanges(three).hasChanges());
        m.buildAndCheck(three, "b2");
        assertTrue(m.pollSCMChanges(four).hasChanges());

        assertFalse(m.pollSCMChanges(one).hasChanges());
    }

    /**
     * Control case for {@link #changelogOnClone()}.
     */
    @Test public void changelogOnUpdate() throws Exception {
        AbstractBuild<?, ?> b;
        FreeStyleProject p = j.createFreeStyleProject();
        p.setScm(new MercurialSCM(hgInstallation(), repo.getPath(), null, null,
                null, null, false));
        m.hg(repo, "init");
        m.touchAndCommit(repo, "dir1/f1");
        b = p.scheduleBuild2(0).get();
        assertTrue(b.getChangeSet().isEmptySet());
        m.touchAndCommit(repo, "dir2/f1");
        b = p.scheduleBuild2(0).get();
        assertChangeSetPaths(
                Collections.singletonList(Collections.singleton("dir2/f1")), b);
        m.touchAndCommit(repo, "dir3/f1");
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
    @Test public void changelogOnClone() throws Exception {
        AbstractBuild<?, ?> b;
        FreeStyleProject p = j.createFreeStyleProject();
        p.setScm(new MercurialSCM(hgInstallation(), repo.getPath(), null, null,
                null, null, false));
        m.hg(repo, "init");
        m.touchAndCommit(repo, "dir1/f1");
        b = p.scheduleBuild2(0).get();
        assertTrue(b.getChangeSet().isEmptySet());
        b.getWorkspace().deleteRecursive(); // Remove the workspace to force a
                                            // re-clone
        m.touchAndCommit(repo, "dir2/f1");
        b = p.scheduleBuild2(0).get();
        assertChangeSetPaths(
                Collections.singletonList(Collections.singleton("dir2/f1")), b);
        b.getWorkspace().deleteRecursive(); // Remove the workspace to force a
                                            // re-clone
        m.touchAndCommit(repo, "dir3/f1");
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
    @Test public void changelogFromPreviousBuild() throws Exception {
        AbstractBuild<?, ?> b;
        FreeStyleProject p = j.createFreeStyleProject();
        Slave s1 = j.createOnlineSlave();
        Slave s2 = j.createOnlineSlave();
        p.setScm(new MercurialSCM(hgInstallation(), repo.getPath(), null, null,
                null, null, false));
        p.setAssignedNode(s1);
        m.hg(repo, "init");
        m.touchAndCommit(repo, "dir1/f1");
        b = p.scheduleBuild2(0).get();
        assertTrue(b.getChangeSet().isEmptySet());
        p.setAssignedNode(s2);
        m.touchAndCommit(repo, "dir2/f1");
        b = p.scheduleBuild2(0).get();
        // this isn't as notable, as it's also covered by testChangelogOnClone
        // assertChangeSetPaths(Collections.singletonList(Collections.singleton("dir2/f1")),
        // b);
        p.setAssignedNode(s1);
        m.touchAndCommit(repo, "dir3/f1");
        b = p.scheduleBuild2(0).get();
        assertChangeSetPaths(
                Collections.singletonList(Collections.singleton("dir3/f1")), b);
    }

    @Bug(12162)
    @Test public void changelogInMultiSCM() throws Exception {
        FreeStyleProject p = j.createFreeStyleProject();
        m.hg(repo, "init");
        m.touchAndCommit(repo, "r1f1");
        File repo2 = tmp.newFolder();
        m.hg(repo2, "init");
        m.touchAndCommit(repo2, "r2f1");
        p.setScm(new MultiSCM(Arrays.<SCM>asList(
                new MercurialSCM(hgInstallation(), repo.getPath(), null, null, "r1", null, false),
                new MercurialSCM(hgInstallation(), repo2.getPath(), null, null, "r2", null, false))));
        FreeStyleBuild b = j.assertBuildStatusSuccess(p.scheduleBuild2(0).get());
        m.touchAndCommit(repo, "r1f2");
        m.touchAndCommit(repo2, "r2f2");
        assertTrue(m.pollSCMChanges(p).hasChanges());
        b = j.assertBuildStatusSuccess(p.scheduleBuild2(0).get());
        List<Set<String>> paths = new ArrayList<Set<String>>();
        // TODO "r1/r1f2" etc. would be preferable; probably requires determineChanges to prepend subdir?
        paths.add(Collections.singleton("r1f2"));
        paths.add(Collections.singleton("r2f2"));
        assertChangeSetPaths(paths, b);
    }

    @Test public void polling() throws Exception {
        AbstractBuild<?, ?> b;
        PollingResult pr;
        FreeStyleProject p = j.createFreeStyleProject();
        p.setScm(new MercurialSCM(hgInstallation(), repo.getPath(), null, null,
                null, null, false));
        p.setAssignedLabel(null); // Allow roaming

        // No builds, no workspace, but an available remote repository
        m.hg(repo, "init");
        m.touchAndCommit(repo, "f1");
        String cs1 = m.getLastChangesetId(repo);
        pr = m.pollSCMChanges(p);
        assertPollingResult(PollingResult.Change.INCOMPARABLE, null, null, pr);

        // We have a workspace, and no new changes in remote repository
        b = p.scheduleBuild2(0).get();
        pr = m.pollSCMChanges(p);
        assertPollingResult(PollingResult.Change.NONE, cs1, cs1, pr);

        // We have a workspace, and new changes in the remote repository
        m.touchAndCommit(repo, "f2");
        String cs2 = m.getLastChangesetId(repo);
        pr = m.pollSCMChanges(p);
        assertPollingResult(PollingResult.Change.SIGNIFICANT, cs1, cs2, pr);

        // We lost the workspace
        b.getWorkspace().deleteRecursive();
        pr = m.pollSCMChanges(p);
        if (p.getScm().requiresWorkspaceForPolling()) {
            assertPollingResult(PollingResult.Change.INCOMPARABLE, null, null, pr);
        } else {
            assertPollingResult(PollingResult.Change.NONE, cs2, cs2, pr);
        }
        b = p.scheduleBuild2(0).get();

        // Multiple polls
        m.touchAndCommit(repo, "f3");
        m.touchAndCommit(repo, "f4");
        String cs4 = m.getLastChangesetId(repo);
        pr = m.pollSCMChanges(p);
        assertPollingResult(PollingResult.Change.SIGNIFICANT, cs2, cs4, pr);
        m.touchAndCommit(repo, "f5");
        String cs5 = m.getLastChangesetId(repo);
        pr = m.pollSCMChanges(p);
        assertPollingResult(PollingResult.Change.SIGNIFICANT, cs4, cs5, pr);
    }
    
    @Bug(11460)
    @Test public void trailingUrlWhitespace() throws Exception {
        FreeStyleProject p = j.createFreeStyleProject();
        p.setScm(new MercurialSCM(hgInstallation(), repo.getPath() + " ", null,
                null, null, null, false));
        m.hg(repo, "init");
        m.touchAndCommit(repo, "dir1/f1");
        AbstractBuild<?, ?> b = p.scheduleBuild2(0).get();
        assertEquals(Result.SUCCESS, b.getResult());
    }
    
    @Bug(12829)
    @Test public void nonExistingBranchesDontGenerateMercurialTagActionsInTheBuild() throws Exception {
        AbstractBuild<?, ?> b;
        FreeStyleProject p = j.createFreeStyleProject();
        p.setScm(new MercurialSCM(hgInstallation(), repo.getPath(), "non-existing-branch", null,
                null, null, false));
        m.hg(repo, "init");
        m.touchAndCommit(repo, "dir1/f1");
        b = p.scheduleBuild2(0).get();
        for (Action action : b.getActions()) {
            if (action instanceof MercurialTagAction) {
                fail("There should not be any MercurialTagAction");
            }
        }
    }

    @Bug(5396)
    @Test public void tags() throws Exception {
        m.hg(repo, "init");
        m.touchAndCommit(repo, "f1");
        m.touchAndCommit(repo, "f2");
        m.hg(repo, "tag", "release");
        m.touchAndCommit(repo, "f3");
        FreeStyleProject p = j.createFreeStyleProject();
        p.setScm(new MercurialSCM(hgInstallation(), repo.getPath(), MercurialSCM.RevisionType.TAG, "release", null, null, null, false, null));
        FreeStyleBuild b = j.assertBuildStatusSuccess(p.scheduleBuild2(0));
        FilePath ws = b.getWorkspace();
        assertTrue(ws.child("f1").exists());
        assertTrue(ws.child("f2").exists());
        assertFalse(ws.child("f3").exists());
        m.hg(repo, "tag", "--force", "release");
        b = j.assertBuildStatusSuccess(p.scheduleBuild2(0));
        assertTrue(JenkinsRule.getLog(b), ws.child("f3").exists());
    }

    @Test public void revsets() throws Exception {
        m.hg(repo, "init");
        m.touchAndCommit(repo, "f1");
        m.touchAndCommit(repo, "f2");
        m.hg(repo, "log");
        FreeStyleProject p = j.createFreeStyleProject();
        p.setScm(new MercurialSCM(hgInstallation(), repo.getPath(), MercurialSCM.RevisionType.REVSET, "first(parents(tip))", null, null, null, false, null));
        FreeStyleBuild b = j.assertBuildStatusSuccess(p.scheduleBuild2(0));
        FilePath ws = b.getWorkspace();
        //We expect to be on the parent of f2: only f1 is present
        assertTrue(ws.child("f1").exists());
        assertFalse(ws.child("f2").exists());

        m.hg(repo, "update", "-r", "0");
        m.touchAndCommit(repo, "f3");
        m.hg(repo, "merge");
        m.hg(repo, "commit", "-m", "Merged some stuff");
        b = j.assertBuildStatusSuccess(p.scheduleBuild2(0));
        ws = b.getWorkspace();
        //We expect to be on the first parent of the merge: f3 is not present
        assertTrue(ws.child("f1").exists());
        assertTrue(ws.child("f2").exists());
        assertFalse(ws.child("f3").exists());

        m.touchAndCommit(repo, "f4");
        b = j.assertBuildStatusSuccess(p.scheduleBuild2(0));
        ws = b.getWorkspace();
        //We expect to be on the merge itself: f4 is not present
        assertTrue(ws.child("f1").exists());
        assertTrue(ws.child("f2").exists());
        assertTrue(ws.child("f3").exists());
        assertFalse(ws.child("f4").exists());
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

    @Bug(15806)
    @Test public void testPullReturnCode() throws Exception {
        File repoOnline = tmp.newFolder();
        File repoOffline = new File(repoOnline.getPath() + "-offline");

        m.hg(repoOnline, "init");
        m.touchAndCommit(repoOnline, "init");
        m.hg(repoOnline, "tag", "init");

        FreeStyleProject p = j.createFreeStyleProject();
        MercurialSCM a = new MercurialSCM(hgInstallation(), repoOnline.getPath(), null, null, null, null, false);
        p.setScm(a);

        // First build to get a local checkout (no update on this one)
        AbstractBuild<?, ?> b = p.scheduleBuild2(0).get();
        assertEquals(Result.SUCCESS, b.getResult());

        // Second build fails due to offline repo
        repoOnline.renameTo(repoOffline);
        b = p.scheduleBuild2(0).get();
        assertNotEquals(Result.SUCCESS, b.getResult());

        // Third build succeeds after repo returns
        repoOffline.renameTo(repoOnline);
        b = p.scheduleBuild2(0).get();
        assertEquals(Result.SUCCESS, b.getResult());
    }

    private void initRepoWithTag() throws Exception {
        m.hg(repo, "init");
        m.touchAndCommit(repo, "init");
        m.hg(repo, "branch", "stable");
        m.touchAndCommit(repo, "stable commit");
        m.hg(repo, "tag", "release");
        m.hg(repo, "update", "--clean", "default");
    }

    @Bug(10706)
    @Test public void testGetBranchFromTag() throws Exception {
        initRepoWithTag();
        FreeStyleProject p = j.createFreeStyleProject();
        p.setScm(new MercurialSCM(hgInstallation(), repo.getPath(), MercurialSCM.RevisionType.TAG, "release", null, null, null, false, null));

        FreeStyleBuild b = j.buildAndAssertSuccess(p);
        MercurialTagAction action = b.getAction(MercurialTagAction.class);
        assertNotNull(action);
        assertEquals("stable", action.getBranch());
        assertEquals("stable", b.getEnvironment().get("MERCURIAL_REVISION_BRANCH"));
    }

    @Bug(10706)
    @Test public void testGetNoBranchFromBranch() throws Exception {
        initRepoWithTag();
        FreeStyleProject p = j.createFreeStyleProject();
        p.setScm(new MercurialSCM(hgInstallation(), repo.getPath(), MercurialSCM.RevisionType.BRANCH, "default", null, null, null, false, null));

        FreeStyleBuild b = j.buildAndAssertSuccess(p);
        MercurialTagAction action = b.getAction(MercurialTagAction.class);
        assertNotNull(action);
        assertEquals(null, action.getBranch());
        assertEquals(null, b.getEnvironment().get("MERCURIAL_REVISION_BRANCH"));
    }

    @Issue("JENKINS-30295")
    @Test public void testChangeSetApiVersion1407Methods() throws Exception {
        FreeStyleProject p = j.createFreeStyleProject();
        p.setScm(new MercurialSCM(hgInstallation(), repo.getPath(), null, null, null, null, false));
        m.hg(repo, "init");
        m.touchAndCommit(repo, "f1");
        p.scheduleBuild2(0).get();

        m.touchAndCommit(repo, "f2");
        MercurialChangeSet changeSet = (MercurialChangeSet) p.scheduleBuild2(0).get().getChangeSet().iterator().next();

        String commitId = m.getLastChangesetId(repo);
        long timestampInSeconds = m.getLastChangesetUnixTimestamp(repo);

        assertEquals(commitId, changeSet.getCommitId());
        assertEquals(timestampInSeconds * 1000, changeSet.getTimestamp());

    }

    /* TODO the following will pass, but canUpdate is not going to work without further changes:
    public void testParameterizedBuildsSource() throws Exception {
        p = createFreeStyleProject();
        p.setScm(new MercurialSCM(hgInstallation, "${REPO}", null, null, null, false, false));
        buildAndCheck(p, "trunk", new ParametersAction(new StringParameterValue("REPO", repo.getPath())));
        String hgrc = p.getSomeWorkspace().child(".hg/hgrc").readToString();
        assertTrue(hgrc.contains(repo.getPath()));
    }
     */

    /* TODO not yet supported; not sure how to expand var in MercurialSCM.createChangeLogParser:
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
