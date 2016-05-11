package hudson.plugins.mercurial;

import hudson.Launcher;
import hudson.Proc;
import hudson.matrix.*;
import hudson.model.*;
import hudson.scm.SCM;
import org.jvnet.hudson.test.FakeLauncher;
import org.jvnet.hudson.test.PretendSlave;
import org.jvnet.hudson.test.TestBuilder;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.jenkinsci.plugins.multiplescms.MultiSCM;
import static org.junit.Assert.*;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.jvnet.hudson.test.Bug;
import org.jvnet.hudson.test.JenkinsRule;

public class MatrixProjectTest {

    @Rule public JenkinsRule j = new JenkinsRule();
    @Rule public MercurialRule m = new MercurialRule(j);
    @Rule public TemporaryFolder tmp = new TemporaryFolder();
    @Rule public TemporaryFolder tmp2 = new TemporaryFolder();
    private File repo;
    private File repo2;
    private MatrixProject matrixProject;

    @Before public void setUp() throws Exception {
        repo = tmp.getRoot();

        createPretendSlave("slave_one");
        createPretendSlave("slave_two");
        matrixProject = j.createProject(MatrixProject.class, "matrix_test");
        matrixProject.setScm(new MercurialSCM(null, repo.getPath(), null, null, null, null, false));
        matrixProject.setAxes(new AxisList(new LabelAxis("label", Arrays.asList("slave_one", "slave_two"))));

        m.hg(repo, "init");
        m.touchAndCommit(repo, "a");
    }

    private void setUpMultiSCM() throws Exception {
        repo2 = tmp.newFolder();

        matrixProject.setScm(new MultiSCM(Arrays.<SCM>asList(
                new MercurialSCM(null, repo2.getPath(), null, null, "r2", null, false),
                new MercurialSCM(null, repo.getPath(), null, null, "r1", null, false))));

        m.hg(repo2, "init");
        m.touchAndCommit(repo2, "r2f1");
    }

    @Test public void allRunsBuildSameRevisionOnClone() throws Exception {
        assertAllMatrixRunsBuildSameMercurialRevision();
    }

    @Test public void allRunsBuildSameRevisionOnUpdate() throws Exception {
        //schedule an initial build, to test update behavior later in the test
        j.assertBuildStatusSuccess(matrixProject.scheduleBuild2(0));
        m.touchAndCommit(repo, "ab");

        assertAllMatrixRunsBuildSameMercurialRevision();
    }

    @Bug(18237)
    @Test public void multiSCMRunsBuildSameRevisionOnClone() throws Exception {
        setUpMultiSCM();
        assertAllMatrixRunsBuildSameMercurialRevision();
    }

    @Bug(18237)
    @Test public void multiSCMRunsBuildSameRevisionOnUpdate() throws Exception {
        setUpMultiSCM();

        //schedule an initial build, to test update behavior later in the test
        j.assertBuildStatusSuccess(matrixProject.scheduleBuild2(0));
        m.touchAndCommit(repo, "ab");

        assertAllMatrixRunsBuildSameMercurialRevision();
    }

    private void assertAllMatrixRunsBuildSameMercurialRevision() throws Exception {
        //set the second slave offline, to give us the opportunity to push changes to the original Mercurial repository
        //between the scheduling of the build and the actual run.
        Node slaveTwo = j.jenkins.getNode("slave_two");
        slaveTwo.toComputer().setTemporarilyOffline(true, null);

        final CountDownLatch firstBuild = new CountDownLatch(1);

        matrixProject.getBuildersList().add(new TestBuilder() {
            @Override
            public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) throws InterruptedException, IOException {
                firstBuild.countDown();
                return true;
            }
        });

        Future<MatrixBuild> matrixBuildFuture = matrixProject.scheduleBuild2(0);
        firstBuild.await(5, TimeUnit.SECONDS);

        //push an extra commit to the central repository
        m.touchAndCommit(repo, "b");

        //let the second slave start the build that was scheduled before this commit
        slaveTwo.toComputer().setTemporarilyOffline(false, null);

        MatrixBuild r = matrixBuildFuture.get();
        j.assertBuildStatus(Result.SUCCESS, r);
        List<MatrixRun> runs = r.getRuns();
        Set<String> builtIds = new HashSet<String>();
        for (MatrixRun run : runs) {
            MercurialTagAction builtRevision = run.getAction(MercurialTagAction.class);
            String buildId = builtRevision.getId();
            builtIds.add(buildId);
        }

        //check that all runs built the same mercurial revision
        assertEquals("All runs should build the same Mercurial revision, but they built " + builtIds.toString(),
                1, builtIds.size());
    }


    private PretendSlave createPretendSlave(String slaveName) throws Exception {
        PretendSlave slave = new PretendSlave(slaveName, tmp2.getRoot().getAbsolutePath(), "", j.createComputerLauncher(null), new NoopFakeLauncher());
        j.jenkins.addNode(slave);
        return slave;
    }

    private static class NoopFakeLauncher implements FakeLauncher {
        public Proc onLaunch(Launcher.ProcStarter p) throws IOException {
            return null;
        }
    }
}
