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
import hudson.console.AnnotatedLargeText;
import hudson.scm.ChangeLogSet;
import hudson.scm.SCM;
import hudson.tools.ToolProperty;
import hudson.triggers.SCMTrigger;
import hudson.util.LogTaskListener;
import hudson.util.StreamTaskListener;
import hudson.util.VersionNumber;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.TreeSet;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.annotation.Nonnull;
import jenkins.branch.BranchSource;
import jenkins.scm.api.SCMEvent;
import jenkins.scm.api.SCMEvents;
import jenkins.util.VirtualFile;
import org.apache.commons.io.FileUtils;
import org.jenkinsci.plugins.scriptsecurity.scripts.ScriptApproval;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.jenkinsci.plugins.workflow.multibranch.WorkflowMultiBranchProject;
import org.jenkinsci.plugins.workflow.test.steps.SemaphoreStep;
import org.junit.Test;

import static org.hamcrest.Matchers.is;
import static org.junit.Assert.*;
import static org.junit.Assume.assumeThat;

import org.junit.ClassRule;
import org.junit.Rule;
import org.jvnet.hudson.test.BuildWatcher;
import org.jvnet.hudson.test.JenkinsRule;

public class PipelineTest {

    @ClassRule public static BuildWatcher buildWatcher = new BuildWatcher();
    @Rule public JenkinsRule r = new JenkinsRule();
    @Rule public MercurialSampleRepoRule sampleRepo = new MercurialSampleRepoRule();
    @Rule public MercurialSampleRepoRule otherRepo = new MercurialSampleRepoRule();

    @Test public void multipleSCMs() throws Exception {
        sampleRepo.init();
        otherRepo.hg("init");
        otherRepo.write("otherfile", "");
        otherRepo.hg("add", "otherfile");
        otherRepo.hg("commit", "--message=init");
        WorkflowJob p = r.jenkins.createProject(WorkflowJob.class, "demo");
        p.addTrigger(new SCMTrigger(""));
        p.setQuietPeriod(3); // so it only does one build
        p.setDefinition(new CpsFlowDefinition(
            "node {\n" +
            "    ws {\n" +
            "        dir('main') {\n" +
            "            checkout([$class: 'MercurialSCM', source: $/" + sampleRepo.fileUrl() + "/$])\n" +
            "        }\n" +
            "        dir('other') {\n" +
            "            checkout([$class: 'MercurialSCM', source: $/" + otherRepo.fileUrl() + "/$, clean: true])\n" +
            "            try {\n" + // TODO or use fileExists
            "                readFile 'unversioned'\n" +
            "                error 'unversioned did exist'\n" +
            "            } catch (FileNotFoundException x) {\n" +
            "                echo 'unversioned did not exist'\n" +
            "            }\n" +
            "            writeFile text: '', file: 'unversioned'\n" +
            "        }\n" +
            "        archive '**'\n" +
            "    }\n" +
            "}"));
        WorkflowRun b = r.assertBuildStatusSuccess(p.scheduleBuild2(0));
        VirtualFile artifacts = b.getArtifactManager().root();
        assertTrue(artifacts.child("main/file").isFile());
        assertTrue(artifacts.child("other/otherfile").isFile());
        r.assertLogContains("unversioned did not exist", b);
        sampleRepo.write("file2", "");
        sampleRepo.hg("add", "file2");
        sampleRepo.hg("commit", "--message=file2");
        otherRepo.write("otherfile2", "");
        otherRepo.hg("add", "otherfile2");
        otherRepo.hg("commit", "--message=otherfile2");
        sampleRepo.notifyCommit(r);
        otherRepo.notifyCommit(r);
        FileUtils.copyFile(p.getSCMTrigger().getLogFile(), System.out);
        b = r.assertBuildStatusSuccess(p.getLastBuild());
        assertEquals(2, b.number);
        artifacts = b.getArtifactManager().root();
        assertTrue(artifacts.child("main/file2").isFile());
        assertTrue(artifacts.child("other/otherfile2").isFile());
        r.assertLogContains("unversioned did not exist", b);
        Iterator<? extends SCM> scms = p.getSCMs().iterator();
        assertTrue(scms.hasNext());
        assertEquals(sampleRepo.fileUrl(), ((MercurialSCM) scms.next()).getSource());
        assertTrue(scms.hasNext());
        assertEquals(otherRepo.fileUrl(), ((MercurialSCM) scms.next()).getSource());
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

    @Test public void exactRevisionMercurial() throws Exception {
        sampleRepo.init();
        ScriptApproval sa = ScriptApproval.get();
        sa.approveSignature("staticField hudson.model.Items XSTREAM2");
        sa.approveSignature("method com.thoughtworks.xstream.XStream toXML java.lang.Object");
        sampleRepo.write("Jenkinsfile", "echo hudson.model.Items.XSTREAM2.toXML(scm); semaphore 'wait'; node {checkout scm; echo readFile('file')}");
        sampleRepo.write("file", "initial content");
        sampleRepo.hg("commit", "--addremove", "--message=flow");
        WorkflowMultiBranchProject mp = r.jenkins.createProject(WorkflowMultiBranchProject.class, "p");
        String instName = "caching";
        r.jenkins.getDescriptorByType(MercurialInstallation.DescriptorImpl.class).setInstallations(
                new MercurialInstallation(instName, "", "hg", false, true, false, null, Collections.<ToolProperty<?>> emptyList()));
        mp.getSourcesList().add(new BranchSource(new MercurialSCMSource(null, instName, sampleRepo.fileUrl(), null, null, null, null, false, null, true)));
        WorkflowJob p = scheduleAndFindBranchProject(mp, "default");
        SemaphoreStep.waitForStart("wait/1", null);
        WorkflowRun b1 = p.getLastBuild();
        assertNotNull(b1);
        assertEquals(1, b1.getNumber());
        sampleRepo.write("Jenkinsfile", "node {checkout scm; echo readFile('file').toUpperCase()}");
        sampleRepo.write("file", "subsequent content");
        sampleRepo.hg("commit", "--message=tweaked");
        SemaphoreStep.success("wait/1", null);
        sampleRepo.notifyCommit(r);
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

    @Test public void modernHook() throws Exception {
        String instName = "caching";
        MercurialInstallation installation = new MercurialInstallation(instName, "", "hg", false, true, false, null,
                Collections.<ToolProperty<?>>emptyList());
        LogTaskListener listener = new LogTaskListener(Logger.getLogger(getClass().getName()), Level.INFO);
        final HgExe hg = new HgExe(installation, null, r.jenkins.createLauncher(
                listener), r.jenkins, listener, new EnvVars());
        String version = hg.version();
        // I could not find the exact version when the new hooks were added, but not found on any 2.x
        // and found in all the 3.x versions I could get my hands on
        assumeThat("Need mercurial 3.0ish to have in-process hooks, have " + version,
                new VersionNumber(version).isNewerThan(new VersionNumber("3.0")),is(true));

        sampleRepo.init();
        sampleRepo.write("Jenkinsfile", "node {checkout scm; echo readFile('file')}");
        sampleRepo.write("file", "initial content");
        sampleRepo.hg("commit", "--addremove", "--message=flow");
        WorkflowMultiBranchProject mp = r.jenkins.createProject(WorkflowMultiBranchProject.class, "p");
        r.jenkins.getDescriptorByType(MercurialInstallation.DescriptorImpl.class).setInstallations(installation);
        installation.forNode(r.jenkins, StreamTaskListener.fromStdout());
        mp.getSourcesList().add(new BranchSource(new MercurialSCMSource(null, instName, sampleRepo.fileUrl(), null, null, null, null, false, null, true)));
        WorkflowJob p = scheduleAndFindBranchProject(mp, "default");
        r.waitUntilNoActivity();
        WorkflowRun b1 = p.getLastBuild();
        assertNotNull(b1);
        assertEquals(1, b1.getNumber());

        // capture the watermark before the event triggered
        long watermark = SCMEvents.getWatermark();

        sampleRepo.registerHook(r);
        sampleRepo.write("Jenkinsfile", "node {checkout scm; echo readFile('file').toUpperCase()}");
        sampleRepo.write("file", "subsequent content");
        sampleRepo.hg("commit", "--message=tweaked");

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
    public static @Nonnull WorkflowJob scheduleAndFindBranchProject(@Nonnull WorkflowMultiBranchProject mp, @Nonnull String name) throws Exception {
        mp.scheduleBuild2(0).getFuture().get();
        return findBranchProject(mp, name);
    }

    public static @Nonnull WorkflowJob findBranchProject(@Nonnull WorkflowMultiBranchProject mp, @Nonnull String name) throws Exception {
        WorkflowJob p = mp.getItem(name);
        showIndexing(mp);
        if (p == null) {
            fail(name + " project not found");
        }
        return p;
    }

    static void showIndexing(@Nonnull WorkflowMultiBranchProject mp) throws Exception {
        FolderComputation<?> indexing = mp.getIndexing();
        System.out.println("---%<--- " + indexing.getUrl());
        indexing.writeWholeLogTo(System.out);
        System.out.println("---%<--- ");
    }

    static void showEvents(@Nonnull WorkflowMultiBranchProject mp) throws Exception {
        AnnotatedLargeText<FolderComputation<WorkflowJob>> events = mp.getComputation().getEventsText();
        System.out.println("---%<--- " + mp.getComputation().getUrl() + " EVENTS");
        events.writeLogTo(0, System.out);
        System.out.println("---%<--- ");
    }

}
