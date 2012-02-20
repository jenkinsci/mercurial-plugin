package hudson.plugins.mercurial;

import hudson.FilePath;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.BuildListener;
import hudson.model.TaskListener;
import hudson.scm.ChangeLogParser;
import hudson.scm.PollingResult;
import hudson.scm.SCM;
import hudson.scm.SCMRevisionState;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.commons.io.FileUtils;

/**
 * Simplified adaptation of code from org.jenkinsci.plugins.multiplescms, for
 * testing purposes.
 */
class MultiSCM extends SCM {

    private final List<SCM> scms;

    MultiSCM(List<SCM> scms) {
        this.scms = scms;
    }

    private static class MultiSCMRevisionState extends SCMRevisionState {
        final Map<String,SCMRevisionState> revisionStates = new HashMap<String,SCMRevisionState>();
    }

    private static String keyFor(SCM scm, FilePath ws, AbstractBuild<?,?> build) { // JENKINS-12298
        StringBuilder b = new StringBuilder(scm.getType());
        for (FilePath root : scm.getModuleRoots(ws, build)) {
            b.append(root.getRemote().substring(ws.getRemote().length()));
        }
        return b.toString();
    }
    
    @Override public SCMRevisionState calcRevisionsFromBuild(AbstractBuild<?,?> build, Launcher launcher, TaskListener listener) throws IOException, InterruptedException {
        MultiSCMRevisionState revisionStates = new MultiSCMRevisionState();
        for (SCM scm : scms) {
            SCMRevisionState scmState = scm.calcRevisionsFromBuild(build, launcher, listener);
            revisionStates.revisionStates.put(keyFor(scm, build.getWorkspace(), build), scmState);
        }
        return revisionStates;
    }

    @Override protected PollingResult compareRemoteRevisionWith(AbstractProject<?,?> project, Launcher launcher, FilePath workspace, TaskListener listener, SCMRevisionState baseline) throws IOException, InterruptedException {
        MultiSCMRevisionState baselineStates = baseline instanceof MultiSCMRevisionState ? (MultiSCMRevisionState) baseline : null;
        MultiSCMRevisionState currentStates = new MultiSCMRevisionState();
        PollingResult.Change overallChange = PollingResult.Change.NONE;
        for (SCM scm : scms) {
            String key = keyFor(scm, workspace, null);
            SCMRevisionState scmBaseline = baselineStates != null ? baselineStates.revisionStates.get(key) : null;
            PollingResult scmResult = scm.poll(project, launcher, workspace, listener, scmBaseline != null ? scmBaseline : SCMRevisionState.NONE);
            currentStates.revisionStates.put(key, scmResult.remote);
            if (scmResult.change.compareTo(overallChange) > 0) {
                overallChange = scmResult.change;
            }
        }
        return new PollingResult(baselineStates, currentStates, overallChange);
    }

    @Override public boolean checkout(AbstractBuild<?, ?> build, Launcher launcher, FilePath workspace, BuildListener listener, File changelogFile) throws IOException, InterruptedException {
        build.addAction(new MultiSCMRevisionState());
        FileOutputStream logStream = new FileOutputStream(changelogFile);
        OutputStreamWriter logWriter = new OutputStreamWriter(logStream);
        logWriter.write(String.format("<%s>\n", MultiSCMChangeLogParser.ROOT_XML_TAG));
        boolean checkoutOK = true;
        for (SCM scm : scms) {
            String changeLogPath = changelogFile.getPath() + ".temp";
            File subChangeLog = new File(changeLogPath);
            checkoutOK = scm.checkout(build, launcher, workspace, listener, subChangeLog) && checkoutOK;
            String subLogText = FileUtils.readFileToString(subChangeLog);
            logWriter.write(String.format("<%s scm=\"%s\">\n<![CDATA[%s]]>\n</%s>\n", MultiSCMChangeLogParser.SUB_LOG_TAG, scm.getClass().getName(), subLogText, MultiSCMChangeLogParser.SUB_LOG_TAG));
            subChangeLog.delete();
        }
        logWriter.write(String.format("</%s>\n", MultiSCMChangeLogParser.ROOT_XML_TAG));
        logWriter.close();
        return checkoutOK;
    }

    @Override public ChangeLogParser createChangeLogParser() {
        return new MultiSCMChangeLogParser(scms);
    }
}
