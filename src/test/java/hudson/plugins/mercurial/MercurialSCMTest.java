package hudson.plugins.mercurial;

import hudson.FilePath;
import hudson.model.FreeStyleProject;
import hudson.model.ParametersAction;
import hudson.model.StringParameterValue;
import hudson.scm.ChangeLogSet;
import java.io.File;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import org.jvnet.hudson.test.Bug;

public class MercurialSCMTest extends MercurialTestCase {

    private File repo;
    protected String hgInstallation = null; // see DebugFlagTest
    protected @Override void setUp() throws Exception {
        super.setUp();
        repo = createTmpDir();
    }

    public void testBasicOps() throws Exception {
        FreeStyleProject p = createFreeStyleProject();
        p.setScm(new MercurialSCM(hgInstallation, repo.getPath(), null, null, null, false, false));

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
        p.setScm(new MercurialSCM(hgInstallation, repo.getPath(), "b", null, null, false, false));
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
        p.setScm(new MercurialSCM(hgInstallation, repo.getPath(), null, null, null, false, false));
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
        p.setScm(new MercurialSCM(hgInstallation, repo.getPath(), null, "dir1 dir2", null, false, false));
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

    @Bug(4702)
    public void testChangelogLimitedToModules() throws Exception {
        FreeStyleProject p = createFreeStyleProject();
        // Control case: no modules specified.
        p.setScm(new MercurialSCM(hgInstallation, repo.getPath(), null, null, null, false, false));
        hg(repo, "init");
        touchAndCommit(repo, "dir1/f1");
        p.scheduleBuild2(0).get();
        touchAndCommit(repo, "dir2/f1");
        Iterator<? extends ChangeLogSet.Entry> it = p.scheduleBuild2(0).get().getChangeSet().iterator();
        assertTrue(it.hasNext());
        ChangeLogSet.Entry entry = it.next();
        assertEquals(Collections.singleton("dir2/f1"), new HashSet<String>(entry.getAffectedPaths()));
        assertFalse(it.hasNext());
        p.setScm(new MercurialSCM(hgInstallation, repo.getPath(), null, "dir1 extra", null, false, false));
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
        p.setScm(new MercurialSCM(hgInstallation, repo.getPath(), "${BRANCH}", null, null, false, false));
        // This is not how a real parameterized build runs, but using ParametersDefinitionProperty just looks untestable:
        String log = buildAndCheck(p, "variant", new ParametersAction(new StringParameterValue("BRANCH", "b")));
        assertTrue(log, log.contains("--rev b"));
        assertFalse(log, log.contains("--rev ${BRANCH}"));
        touchAndCommit(repo, "further-variant");
        assertTrue(pollSCMChanges(p));
        buildAndCheck(p, "further-variant", new ParametersAction(new StringParameterValue("BRANCH", "b")));
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
