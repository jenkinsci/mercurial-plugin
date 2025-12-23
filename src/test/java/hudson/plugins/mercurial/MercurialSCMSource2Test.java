/*
 * The MIT License
 *
 * Copyright 2017 CloudBees, Inc.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package hudson.plugins.mercurial;

import com.cloudbees.jenkins.plugins.sshcredentials.impl.BasicSSHUserPrivateKey;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.CredentialsScope;
import com.cloudbees.plugins.credentials.domains.Domain;
import hudson.FilePath;
import hudson.model.Slave;
import hudson.plugins.mercurial.traits.MercurialInstallationSCMSourceTrait;
import hudson.util.StreamTaskListener;
import java.io.File;
import java.io.IOException;
import java.util.Collections;
import jenkins.branch.BranchSource;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.jenkinsci.plugins.workflow.multibranch.WorkflowMultiBranchProject;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.BuildWatcherExtension;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers(disabledWithoutDocker = true)
@WithJenkins
class MercurialSCMSource2Test {

    // TODO -i option for authentication does not work with a space in $JENKINS_HOME (or agent root)
    private static String noSpaceInTmpDirs;

    @SuppressWarnings("unused")
    @RegisterExtension
    private static final BuildWatcherExtension BUILD_WATCHER = new BuildWatcherExtension();
    private JenkinsRule r;
    private MercurialTestUtil m;
    @Container
    private static final MercurialContainer container = new MercurialContainer();
    @TempDir
    private File tmp;

    @BeforeAll
    static void beforeAll() {
        noSpaceInTmpDirs = System.setProperty("jenkins.test.noSpaceInTmpDirs", "true");
    }

    @BeforeEach
    void beforeEach(JenkinsRule rule) {
        r = rule;
        m = new MercurialTestUtil(r);
    }

    @AfterAll
    static void afterAll() {
        if (noSpaceInTmpDirs != null) {
            System.setProperty("jenkins.test.noSpaceInTmpDirs", noSpaceInTmpDirs);
        } else {
            System.clearProperty("jenkins.test.noSpaceInTmpDirs");
        }
    }

    @Issue({"JENKINS-42278", "JENKINS-46851", "JENKINS-48867"})
    @Test
    void withCredentialsId() throws Exception {
        m.hg("version"); // test environment needs to be able to run Mercurial
        Slave agent = container.createAgent(r);
        m.withNode(agent);
        MercurialInstallation inst = container.createInstallation(r, MercurialContainer.Version.HG6, false, false, false, "", agent);
        assertNotNull(inst);
        m.withInstallation(inst);
        FilePath sampleRepo = agent.getRootPath().child("sampleRepo");
        sampleRepo.mkdirs();
        m.hg(sampleRepo, "init");
        // Tricky because the SSH URL will not work on that agent; it is actually only valid on the controller.
        // So we need to check out on the controller, which is where branch indexing happens as well.
        sampleRepo.child("Jenkinsfile").write("node('master') {checkout scm}", null);
        m.hg(sampleRepo, "commit", "--addremove", "--message=flow");
        MercurialSCMSource s = new MercurialSCMSource("ssh://test@" + container.getHost() + ":" + container.getMappedPort(22) + "/" + sampleRepo);
        CredentialsProvider.lookupStores(r.jenkins).iterator().next().addCredentials(Domain.global(),
            new BasicSSHUserPrivateKey(CredentialsScope.GLOBAL, "creds", "test", new BasicSSHUserPrivateKey.FileOnMasterPrivateKeySource(container.getPrivateKey().getAbsolutePath()), null, null));
        s.setCredentialsId("creds");
        String toolHome = inst.forNode(agent, StreamTaskListener.fromStdout()).getHome();
        assertNotNull(toolHome);
        String remoteHgLoc = inst.executableWithSubstitution(toolHome);
        r.jenkins.getDescriptorByType(MercurialInstallation.DescriptorImpl.class).setInstallations(
                new MercurialInstallation("default", "", "hg", false, true, null, false,
                    "[ui]\nssh = ssh -o UserKnownHostsFile=" + newFile(tmp, "known_hosts") + " -o StrictHostKeyChecking=no\n" +
                    "remotecmd = " + remoteHgLoc, null));
        s.setTraits(Collections.singletonList(new MercurialInstallationSCMSourceTrait("default")));
        WorkflowMultiBranchProject mp = r.jenkins.createProject(WorkflowMultiBranchProject.class, "p");
        mp.getSourcesList().add(new BranchSource(s));
        WorkflowJob p = PipelineTest.scheduleAndFindBranchProject(mp, "default");
        assertEquals(1, mp.getItems().size());
        r.waitUntilNoActivity();
        WorkflowRun b = p.getLastBuild();
        assertNotNull(b);
        r.assertBuildStatusSuccess(b);
        // JENKINS-46851: commit lookup for libraries
        assertNotNull(s.fetch("default", StreamTaskListener.fromStderr(), p));
        // JENKINS-48867: if indexing fails, should not lose existing branches
        sampleRepo.deleteRecursive();
        assertEquals(p, PipelineTest.scheduleAndFindBranchProject(mp, "default"));
    }

    private static File newFile(File parent, String child) throws IOException {
        File result = new File(parent, child);
        result.createNewFile();
        return result;
    }

}
