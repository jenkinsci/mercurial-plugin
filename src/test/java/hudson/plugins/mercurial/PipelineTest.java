package hudson.plugins.mercurial;

/*
 * The MIT License
 *
 * Copyright 2016 CloudBees, Inc.
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

import com.cloudbees.hudson.plugins.folder.computed.FolderComputation;
import hudson.EnvVars;
import hudson.FilePath;
import hudson.console.AnnotatedLargeText;
import hudson.model.Slave;
import hudson.scm.ChangeLogSet;
import hudson.scm.SCM;
import hudson.triggers.SCMTrigger;
import hudson.util.LogTaskListener;
import hudson.util.StreamTaskListener;
import hudson.util.VersionNumber;
import java.io.File;
import java.util.Iterator;
import java.util.List;
import java.util.TreeSet;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import edu.umd.cs.findbugs.annotations.NonNull;
import jenkins.branch.BranchSource;
import jenkins.scm.api.SCMEvents;
import jenkins.util.VirtualFile;
import org.apache.commons.io.FileUtils;

import org.jenkinsci.plugins.scriptsecurity.scripts.ScriptApproval;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.jenkinsci.plugins.workflow.multibranch.WorkflowMultiBranchProject;
import org.jenkinsci.plugins.workflow.test.steps.SemaphoreStep;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.BuildWatcherExtension;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;
import org.opentest4j.TestAbortedException;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers(disabledWithoutDocker = true)
@WithJenkins
class PipelineTest {

    @SuppressWarnings("unused")
    @RegisterExtension
    private static final BuildWatcherExtension BUILD_WATCHER = new BuildWatcherExtension();
    private JenkinsRule r;
    private MercurialTestUtil m;
    @Container
    private static final MercurialContainer container = new MercurialContainer();
    @TempDir
    private File tmp;

    @BeforeEach
    void beforeEach(JenkinsRule rule) {
        r = rule;
        m = new MercurialTestUtil(r);
    }

    @Test
    void multipleSCMs() throws Exception {
        Slave agent = container.createAgent(r);
        m.withNode(agent);
        MercurialInstallation inst = container.createInstallation(r, MercurialContainer.Version.HG6, false, false, false, "", agent);
        assertNotNull(inst);
        m.withInstallation(inst);
        FilePath sampleRepo = agent.getRootPath().child("sampleRepo");
        sampleRepo.mkdirs();
        m.hg(sampleRepo, "init");
        m.touchAndCommit(sampleRepo, "file");
        FilePath otherRepo = agent.getRootPath().child("otherRepo");
        otherRepo.mkdirs();
        m.hg(otherRepo, "init");
        m.touchAndCommit(otherRepo, "otherfile");
        WorkflowJob p = r.jenkins.createProject(WorkflowJob.class, "demo");
        p.addTrigger(new SCMTrigger(""));
        p.setQuietPeriod(3); // so it only does one build
        p.setDefinition(new CpsFlowDefinition(
            "node('" + agent.getNodeName() + "') {\n" +
            "    dir('main') {\n" +
            "        checkout([$class: 'MercurialSCM', source: $/" + sampleRepo.toURI() + "/$, installation: '" + inst.getName() + "'])\n" +
            "    }\n" +
            "    dir('other') {\n" +
            "        checkout([$class: 'MercurialSCM', source: $/" + otherRepo.toURI() + "/$, installation: '" + inst.getName() + "', clean: true])\n" +
            "        if (fileExists('unversioned')) {\n" +
            "            error 'unversioned did exist'\n" +
            "        } else {\n" +
            "            echo 'unversioned did not exist'\n" +
            "        }\n" +
            "        writeFile text: '', file: 'unversioned'\n" +
            "    }\n" +
            "    archive '**'\n" +
            "}"));
        WorkflowRun b = r.assertBuildStatusSuccess(p.scheduleBuild2(0));
        VirtualFile artifacts = b.getArtifactManager().root();
        assertTrue(artifacts.child("main/file").isFile());
        assertTrue(artifacts.child("other/otherfile").isFile());
        r.assertLogContains("unversioned did not exist", b);
        m.touchAndCommit(sampleRepo, "file2");
        m.touchAndCommit(otherRepo, "otherfile2");
        m.notifyCommit(sampleRepo);
        m.notifyCommit(otherRepo);
        FileUtils.copyFile(p.getSCMTrigger().getLogFile(), System.out);
        b = r.assertBuildStatusSuccess(p.getLastBuild());
        assertEquals(2, b.number);
        artifacts = b.getArtifactManager().root();
        assertTrue(artifacts.child("main/file2").isFile());
        assertTrue(artifacts.child("other/otherfile2").isFile());
        r.assertLogContains("unversioned did not exist", b);
        Iterator<? extends SCM> scms = p.getSCMs().iterator();
        assertTrue(scms.hasNext());
        assertEquals(sampleRepo.toURI().toString(), ((MercurialSCM) scms.next()).getSource());
        assertTrue(scms.hasNext());
        assertEquals(otherRepo.toURI().toString(), ((MercurialSCM) scms.next()).getSource());
        assertFalse(scms.hasNext());
        List<ChangeLogSet<? extends ChangeLogSet.Entry>> changeSets = b.getChangeSets();
        assertEquals(2, changeSets.size());
        ChangeLogSet<? extends ChangeLogSet.Entry> changeSet = changeSets.get(0);
        assertEquals(b, changeSet.getRun());
        assertEquals("hg", changeSet.getKind());
        Iterator<? extends ChangeLogSet.Entry> iterator = changeSet.iterator();
        assertTrue(iterator.hasNext());
        ChangeLogSet.Entry entry = iterator.next();
        assertEquals("[file2]", entry.getAffectedPaths().toString());
        assertFalse(iterator.hasNext());
        changeSet = changeSets.get(1);
        iterator = changeSet.iterator();
        assertTrue(iterator.hasNext());
        entry = iterator.next();
        assertEquals("[otherfile2]", entry.getAffectedPaths().toString());
        assertFalse(iterator.hasNext());
    }

    @Issue("JENKINS-42278")
    @Test
    void exactRevisionMercurial() throws Exception {
        // TODO mostly pointless to use MercurialContainer here since multibranch requires a caching installation and thus for hg to be installed on controller
        FilePath sampleRepo = new FilePath(tmp);
        m.hg(sampleRepo, "init");
        ScriptApproval sa = ScriptApproval.get();
        sa.approveSignature("staticField hudson.model.Items XSTREAM2");
        sa.approveSignature("method com.thoughtworks.xstream.XStream toXML java.lang.Object");
        sampleRepo.child("Jenkinsfile").write("echo hudson.model.Items.XSTREAM2.toXML(scm); semaphore 'wait'; node {checkout scm; echo readFile('file')}", null);
        sampleRepo.child("file").write("initial content", null);
        m.hg(sampleRepo, "commit", "--addremove", "--message=flow");
        m.hg(sampleRepo, "update", "null");
        m.hg(sampleRepo, "branch", "docs");
        sampleRepo.child("README").write("Just docs here!", null);
        m.hg(sampleRepo, "commit", "--message=unrelated branch, not buildable");
        m.hg(sampleRepo, "update", "default");
        WorkflowMultiBranchProject mp = r.jenkins.createProject(WorkflowMultiBranchProject.class, "p");
        String instName = "caching";
        r.jenkins.getDescriptorByType(MercurialInstallation.DescriptorImpl.class).setInstallations(
                new MercurialInstallation(instName, "", "hg", false, true, false, null, null));
        mp.getSourcesList().add(new BranchSource(new MercurialSCMSource(null, instName, sampleRepo.toURI().toString(), null, null, null, null, null, true)));
        WorkflowJob p = scheduleAndFindBranchProject(mp, "default");
        assertEquals(1, mp.getItems().size());
        SemaphoreStep.waitForStart("wait/1", null);
        WorkflowRun b1 = p.getLastBuild();
        assertNotNull(b1);
        assertEquals(1, b1.getNumber());
        sampleRepo.child("Jenkinsfile").write("node {checkout scm; echo readFile('file').toUpperCase()}", null);
        sampleRepo.child("file").write("subsequent content", null);
        m.hg(sampleRepo, "commit", "--message=tweaked");
        SemaphoreStep.success("wait/1", null);
        m.notifyCommit(sampleRepo);
        showIndexing(mp);
        WorkflowRun b2 = p.getLastBuild();
        assertEquals(2, b2.getNumber());
        r.assertLogContains("initial content", r.assertBuildStatusSuccess(b1));
        r.assertLogContains("SUBSEQUENT CONTENT", r.assertBuildStatusSuccess(b2));
        List<ChangeLogSet<? extends ChangeLogSet.Entry>> changeSets = b2.getChangeSets();
        /* TODO JENKINS-29326 analogue, as per SubversionSCM:
        assertEquals(1, changeSets.size());
        */
        ChangeLogSet<? extends ChangeLogSet.Entry> changeSet = changeSets.get(0);
        assertEquals(b2, changeSet.getRun());
        assertEquals("hg", changeSet.getKind());
        Iterator<? extends ChangeLogSet.Entry> iterator = changeSet.iterator();
        assertTrue(iterator.hasNext());
        ChangeLogSet.Entry entry = iterator.next();
        assertEquals("tweaked", entry.getMsg());
        assertEquals("[Jenkinsfile, file]", new TreeSet<>(entry.getAffectedPaths()).toString());
        assertFalse(iterator.hasNext());
    }

    @Test
    void modernHook() throws Exception {
        // Cannot use MercurialContainer here because of registerHook limitation, q.v.
        String instName = "caching";
        MercurialInstallation installation = new MercurialInstallation(instName, "", "hg", false, true, false, null, null);
        LogTaskListener listener = new LogTaskListener(Logger.getLogger(getClass().getName()), Level.INFO);
        final HgExe hg = new HgExe(installation, null, r.jenkins.createLauncher(
                listener), r.jenkins, listener, new EnvVars());
        String version;
        try {
            version = hg.version();
        } catch (Exception x) {
            throw new TestAbortedException("cannot run hg version; perhaps not installed locally", x);
        }
        // I could not find the exact version when the new hooks were added, but not found on any 2.x
        // and found in all the 3.x versions I could get my hands on
        assumeTrue(new VersionNumber(version).isNewerThan(new VersionNumber("3.0")),
                "Need mercurial 3.0ish to have in-process hooks, have " + version);

        FilePath sampleRepo = new FilePath(tmp);
        m.hg(sampleRepo, "init");
        sampleRepo.child("Jenkinsfile").write("node {checkout scm; echo readFile('file')}", null);
        sampleRepo.child("file").write("initial content", null);
        m.hg(sampleRepo, "commit", "--addremove", "--message=flow");
        WorkflowMultiBranchProject mp = r.jenkins.createProject(WorkflowMultiBranchProject.class, "p");
        r.jenkins.getDescriptorByType(MercurialInstallation.DescriptorImpl.class).setInstallations(installation);
        installation.forNode(r.jenkins, StreamTaskListener.fromStdout());
        mp.getSourcesList().add(new BranchSource(new MercurialSCMSource(null, instName, sampleRepo.toURI().toString(), null, null, null, null, null, true)));
        WorkflowJob p = scheduleAndFindBranchProject(mp, "default");
        r.waitUntilNoActivity();
        WorkflowRun b1 = p.getLastBuild();
        assertNotNull(b1);
        assertEquals(1, b1.getNumber());

        // capture the watermark before the event triggered
        long watermark = SCMEvents.getWatermark();

        m.registerHook(sampleRepo);
        sampleRepo.child("Jenkinsfile").write("node {checkout scm; echo readFile('file').toUpperCase()}", null);
        sampleRepo.child("file").write("subsequent content", null);
        try {
            m.hg(sampleRepo, "commit", "--message=tweaked");
        } catch (AssertionError x) {
            throw new TestAbortedException("probably using Python 2", x);
        }

        // wait for the event to have completed processing
        SCMEvents.awaitAll(watermark, 5, TimeUnit.SECONDS);

        // ensure the queue has picked up the job being scheduled as we are at full tilt
        r.jenkins.getQueue().maintain();

        // now show the events log
        showEvents(mp);

        // and wait for the build to finish
        r.waitUntilNoActivity();
        WorkflowRun b2 = p.getLastBuild();
        assertEquals(2, b2.getNumber());
        r.assertLogContains("initial content", r.assertBuildStatusSuccess(b1));
        r.assertLogContains("SUBSEQUENT CONTENT", r.assertBuildStatusSuccess(b2));
        List<ChangeLogSet<? extends ChangeLogSet.Entry>> changeSets = b2.getChangeSets();
        /* TODO JENKINS-29326 analogue, as per SubversionSCM:
        assertEquals(1, changeSets.size());
        */
        ChangeLogSet<? extends ChangeLogSet.Entry> changeSet = changeSets.get(0);
        assertEquals(b2, changeSet.getRun());
        assertEquals("hg", changeSet.getKind());
        Iterator<? extends ChangeLogSet.Entry> iterator = changeSet.iterator();
        assertTrue(iterator.hasNext());
        ChangeLogSet.Entry entry = iterator.next();
        assertEquals("tweaked", entry.getMsg());
        assertEquals("[Jenkinsfile, file]", new TreeSet<>(entry.getAffectedPaths()).toString());
        assertFalse(iterator.hasNext());
    }

    // Copied from WorkflowMultiBranchProjectTest; do not want to depend on that due to its dependency on git:
    public static @NonNull WorkflowJob scheduleAndFindBranchProject(@NonNull WorkflowMultiBranchProject mp, @NonNull String name) throws Exception {
        mp.scheduleBuild2(0).getFuture().get();
        return findBranchProject(mp, name);
    }

    public static @NonNull WorkflowJob findBranchProject(@NonNull WorkflowMultiBranchProject mp, @NonNull String name) throws Exception {
        WorkflowJob p = mp.getItem(name);
        showIndexing(mp);
        if (p == null) {
            fail(name + " project not found");
        }
        return p;
    }

    static void showIndexing(@NonNull WorkflowMultiBranchProject mp) throws Exception {
        FolderComputation<?> indexing = mp.getIndexing();
        System.out.println("---%<--- " + indexing.getUrl());
        indexing.writeWholeLogTo(System.out);
        System.out.println("---%<--- ");
    }

    static void showEvents(@NonNull WorkflowMultiBranchProject mp) throws Exception {
        AnnotatedLargeText<FolderComputation<WorkflowJob>> events = mp.getComputation().getEventsText();
        System.out.println("---%<--- " + mp.getComputation().getUrl() + " EVENTS");
        events.writeLogTo(0, System.out);
        System.out.println("---%<--- ");
    }

}
