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
import java.util.Collections;
import jenkins.branch.BranchSource;
import jenkins.scm.api.trait.SCMSourceTrait;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.jenkinsci.plugins.workflow.multibranch.WorkflowMultiBranchProject;
import org.jenkinsci.test.acceptance.docker.DockerClassRule;
import org.junit.ClassRule;
import org.junit.Test;
import static org.junit.Assert.*;
import org.junit.Rule;
import org.junit.rules.TemporaryFolder;
import org.junit.rules.TestRule;
import org.jvnet.hudson.test.BuildWatcher;
import org.jvnet.hudson.test.FlagRule;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;

public class MercurialSCMSource2Test {

    // TODO -i option for authentication does not work with a space in $JENKINS_HOME (or agent root)
    @ClassRule public static TestRule noSpaceInTmpDirs = FlagRule.systemProperty("jenkins.test.noSpaceInTmpDirs", "true");

    @ClassRule public static BuildWatcher buildWatcher = new BuildWatcher();
    @Rule public JenkinsRule r = new JenkinsRule();
    @Rule public MercurialRule m = new MercurialRule(r);
    @ClassRule public static DockerClassRule<MercurialContainer> docker = new DockerClassRule<>(MercurialContainer.class);
    @Rule public TemporaryFolder tmp = new TemporaryFolder();

    @Issue({"JENKINS-42278", "JENKINS-48867"})
    @Test public void withCredentialsId() throws Exception {
        m.hg("version"); // test environment needs to be able to run Mercurial
        MercurialContainer container = docker.create();
        Slave slave = container.createSlave(r);
        m.withNode(slave);
        MercurialInstallation inst = container.createInstallation(r, MercurialContainer.Version.HG4, false, false, false, "", slave);
        assertNotNull(inst);
        m.withInstallation(inst);
        FilePath sampleRepo = slave.getRootPath().child("sampleRepo");
        sampleRepo.mkdirs();
        m.hg(sampleRepo, "init");
        // Tricky because the SSH URL will not work on that agent; it is actually only valid on the master.
        // So we need to check out on the master, which is where branch indexing happens as well.
        sampleRepo.child("Jenkinsfile").write("node('master') {checkout scm}", null);
        m.hg(sampleRepo, "commit", "--addremove", "--message=flow");
        MercurialSCMSource s = new MercurialSCMSource("ssh://test@" + container.ipBound(22) + ":" + container.port(22) + "/" + sampleRepo);
        CredentialsProvider.lookupStores(r.jenkins).iterator().next().addCredentials(Domain.global(),
            new BasicSSHUserPrivateKey(CredentialsScope.GLOBAL, "creds", "test", new BasicSSHUserPrivateKey.FileOnMasterPrivateKeySource(container.getPrivateKey().getAbsolutePath()), null, null));
        s.setCredentialsId("creds");
        String toolHome = inst.forNode(slave, StreamTaskListener.fromStdout()).getHome();
        assertNotNull(toolHome);
        String remoteHgLoc = inst.executableWithSubstitution(toolHome);
        r.jenkins.getDescriptorByType(MercurialInstallation.DescriptorImpl.class).setInstallations(
                new MercurialInstallation("default", "", "hg", false, true, null, false,
                    "[ui]\nssh = ssh -o UserKnownHostsFile=" + tmp.newFile("known_hosts") + " -o StrictHostKeyChecking=no\n" +
                    "remotecmd = " + remoteHgLoc, null));
        s.setTraits(Collections.<SCMSourceTrait>singletonList(new MercurialInstallationSCMSourceTrait("default")));
        WorkflowMultiBranchProject mp = r.jenkins.createProject(WorkflowMultiBranchProject.class, "p");
        mp.getSourcesList().add(new BranchSource(s));
        WorkflowJob p = PipelineTest.scheduleAndFindBranchProject(mp, "default");
        assertEquals(1, mp.getItems().size());
        r.waitUntilNoActivity();
        WorkflowRun b = p.getLastBuild();
        assertNotNull(b);
        r.assertBuildStatusSuccess(b);
        // JENKINS-48867: if indexing fails, should not lose existing branches
        sampleRepo.deleteRecursive();
        assertEquals(p, PipelineTest.scheduleAndFindBranchProject(mp, "default"));
    }

}
