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
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.jenkinsci.plugins.workflow.multibranch.WorkflowMultiBranchProject;
import org.jenkinsci.test.acceptance.docker.DockerRule;
import org.junit.ClassRule;
import org.junit.Test;
import static org.junit.Assert.*;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.rules.TemporaryFolder;
import org.jvnet.hudson.test.BuildWatcher;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;

public class MercurialSCMSource2Test {

    @ClassRule public static BuildWatcher buildWatcher = new BuildWatcher();
    @Rule public JenkinsRule r = new JenkinsRule();
    @Rule public MercurialRule m = new MercurialRule(r);
    @Rule public DockerRule<MercurialContainer> containerRule = new DockerRule<MercurialContainer>(MercurialContainer.class);
    @Rule public TemporaryFolder tmp = new TemporaryFolder();

    @Ignore("TODO")
    @Issue("JENKINS-42278")
    @Test public void withCredentialsId() throws Exception {
        MercurialContainer container = containerRule.get();
        Slave slave = container.createSlave(r);
        m.withNode(slave);
        MercurialInstallation inst = container.createInstallation(r, MercurialContainer.Version.HG4, false, false, false, "", slave);
        assertNotNull(inst);
        m.withInstallation(inst);
        FilePath sampleRepo = slave.getRootPath().child("sampleRepo");
        sampleRepo.mkdirs();
        m.hg(sampleRepo, "init");
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
                new MercurialInstallation("default", "", "hg", false, true, null, false, "[ui]\nssh = ssh -o UserKnownHostsFile=" + tmp.newFile("known_hosts") + "\nremotecmd = " + remoteHgLoc, null));
        s.setTraits(Collections.singletonList(new MercurialInstallationSCMSourceTrait("default")));
        WorkflowMultiBranchProject mp = r.jenkins.createProject(WorkflowMultiBranchProject.class, "p");
        mp.getSourcesList().add(new BranchSource(s));
        WorkflowJob p = PipelineTest.scheduleAndFindBranchProject(mp, "default");
        assertEquals(1, mp.getItems().size());
        r.waitUntilNoActivity();
        WorkflowRun b = p.getLastBuild();
        assertNotNull(b);
        r.assertBuildStatusSuccess(b);
    }

}
