package hudson.plugins.mercurial;

import hudson.Launcher;
import hudson.Proc;
import hudson.matrix.*;
import hudson.model.*;
import org.junit.jupiter.api.io.TempDir;
import org.jvnet.hudson.test.FakeLauncher;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.PretendSlave;
import org.jvnet.hudson.test.TestBuilder;

import java.io.File;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

@WithJenkins
class MatrixProjectTest {

    private JenkinsRule j;
    private MercurialTestUtil m;
    @TempDir
    private File tmp;
    @TempDir
    private File tmp2;
    private File repo;
    private MatrixProject matrixProject;

    @BeforeEach
    void beforeEach(JenkinsRule rule) throws Exception {
        j = rule;
        m = new MercurialTestUtil(j);
        repo = tmp;

        createPretendAgent("agent_one");
        createPretendAgent("agent_two");
        matrixProject = j.createProject(MatrixProject.class, "matrix_test");
        matrixProject.setScm(new MercurialSCM(null, repo.getPath(), null, null, null, null, false));
        matrixProject.setAxes(new AxisList(new LabelAxis("label", Arrays.asList("agent_one", "agent_two"))));

        // TODO switch to MercurialContainer
        m.hg(repo, "init");
        m.touchAndCommit(repo, "a");
    }

    @Test
    void allRunsBuildSameRevisionOnClone() throws Exception {
        assertAllMatrixRunsBuildSameMercurialRevision();
    }

    @Test
    void allRunsBuildSameRevisionOnUpdate() throws Exception {
        //schedule an initial build, to test update behavior later in the test
        j.assertBuildStatusSuccess(matrixProject.scheduleBuild2(0));
        m.touchAndCommit(repo, "ab");

        assertAllMatrixRunsBuildSameMercurialRevision();
    }

    private void assertAllMatrixRunsBuildSameMercurialRevision() throws Exception {
        //set the second agent offline, to give us the opportunity to push changes to the original Mercurial repository
        //between the scheduling of the build and the actual run.
        Node agentTwo = j.jenkins.getNode("agent_two");
        agentTwo.toComputer().setTemporarilyOffline(true, null);

        final CountDownLatch firstBuild = new CountDownLatch(1);

        matrixProject.getBuildersList().add(new TestBuilder() {
            @Override
            public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) {
                firstBuild.countDown();
                return true;
            }
        });

        Future<MatrixBuild> matrixBuildFuture = matrixProject.scheduleBuild2(0);
        firstBuild.await(5, TimeUnit.SECONDS);

        //push an extra commit to the central repository
        m.touchAndCommit(repo, "b");

        //let the second agent start the build that was scheduled before this commit
        agentTwo.toComputer().setTemporarilyOffline(false, null);

        MatrixBuild r = matrixBuildFuture.get();
        j.assertBuildStatus(Result.SUCCESS, r);
        List<MatrixRun> runs = r.getRuns();
        Set<String> builtIds = new HashSet<>();
        for (MatrixRun run : runs) {
            MercurialTagAction builtRevision = run.getAction(MercurialTagAction.class);
            String buildId = builtRevision.getId();
            builtIds.add(buildId);
        }

        //check that all runs built the same mercurial revision
        assertEquals(1,
                builtIds.size(), "All runs should build the same Mercurial revision, but they built " + builtIds);
    }

    private PretendSlave createPretendAgent(String agentName) throws Exception {
        PretendSlave agent = new PretendSlave(agentName, tmp2.getAbsolutePath(), "", j.createComputerLauncher(null), new NoopFakeLauncher());
        j.jenkins.addNode(agent);
        return agent;
    }

    private static class NoopFakeLauncher implements FakeLauncher {
        public Proc onLaunch(Launcher.ProcStarter p) {
            return null;
        }
    }
}
