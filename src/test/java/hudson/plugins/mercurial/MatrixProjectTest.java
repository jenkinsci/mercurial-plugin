package hudson.plugins.mercurial;

import hudson.Launcher;
import hudson.Proc;
import hudson.matrix.*;
import hudson.model.*;
import org.jvnet.hudson.test.FakeLauncher;
import org.jvnet.hudson.test.PretendSlave;
import org.jvnet.hudson.test.TestBuilder;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;

public class MatrixProjectTest extends MercurialTestCase {
    private File repo;
    private MatrixProject matrixProject;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        repo = createTmpDir();

        createPretendSlave("slave_one");
        createPretendSlave("slave_two");

        matrixProject = createMatrixProject("matrix_test");
        matrixProject.setScm(new MercurialSCM(null, repo.getPath(), null, null, null, null, false));
        matrixProject.setAxes(new AxisList(new LabelAxis("label", Arrays.asList("slave_one", "slave_two"))));

        hg(repo, "init");
        touchAndCommit(repo, "a");
    }

    public void testAllRunsBuildSameRevisionOnClone() throws Exception {
        assertAllMatrixRunsBuildSameMercurialRevision();
    }

    public void testAllRunsBuildSameRevisionOnUpdate() throws Exception {
        //schedule an initial build, to test update behavior later in the test
        assertBuildStatusSuccess(matrixProject.scheduleBuild2(0, new Cause.UserCause(), new Action[0]));
        touchAndCommit(repo, "ab");

        assertAllMatrixRunsBuildSameMercurialRevision();
    }

    private void assertAllMatrixRunsBuildSameMercurialRevision() throws Exception {
        //set the second slave offline, to give us the opportunity to push changes to the original Mercurial repository
        //between the scheduling of the build and the actual run.
        Node slaveTwo = hudson.getNode("slave_two");
        slaveTwo.toComputer().setTemporarilyOffline(true, null);

        final CountDownLatch firstBuild = new CountDownLatch(1);

        matrixProject.getBuildersList().add(new TestBuilder() {
            @Override
            public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) throws InterruptedException, IOException {
                firstBuild.countDown();
                return true;
            }
        });

        Future<MatrixBuild> matrixBuildFuture = matrixProject.scheduleBuild2(0, new Cause.UserCause(), new Action[]{});
        firstBuild.await();

        //push an extra commit to the central repository
        touchAndCommit(repo, "b");

        //let the second slave start the build that was scheduled before this commit
        slaveTwo.toComputer().setTemporarilyOffline(false, null);

        MatrixBuild r = matrixBuildFuture.get();
        this.assertBuildStatus(Result.SUCCESS, r);
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
        PretendSlave slave = new PretendSlave(slaveName, this.createTmpDir().getPath(), "", createComputerLauncher(null), new NoopFakeLauncher());
        hudson.addNode(slave);
        return slave;
    }

    private static class NoopFakeLauncher implements FakeLauncher {
        public Proc onLaunch(Launcher.ProcStarter p) throws IOException {
            return null;
        }
    }
}
