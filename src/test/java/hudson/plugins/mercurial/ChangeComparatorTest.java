package hudson.plugins.mercurial;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.BeforeEach;
import org.jvnet.hudson.test.TestExtension;

import hudson.FilePath;
import hudson.Launcher;
import hudson.model.AbstractProject;
import hudson.model.FreeStyleProject;
import hudson.model.Node;
import hudson.model.TaskListener;
import hudson.scm.PollingResult;
import hudson.scm.PollingResult.Change;
import hudson.util.StreamTaskListener;

import java.io.File;
import java.io.PrintStream;
import java.nio.charset.Charset;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

@WithJenkins
class ChangeComparatorTest {

    private JenkinsRule j;
    private MercurialTestUtil m;

    @TempDir
    private File tmp;

    @BeforeEach
    void beforeEach(JenkinsRule rule) {
        j = rule;
        m = new MercurialTestUtil(j);
    }

    @Test
    void triggersNewBuild() throws Exception {
		TaskListener listener = new StreamTaskListener(System.out, Charset.defaultCharset());
        Launcher launcher = j.jenkins.createLauncher(listener);
		
		FreeStyleProject project = j.createFreeStyleProject();
		
        // TODO switch to MercurialContainer
        MercurialSCM scm = new MercurialSCM(null, tmp.getPath(), null, null, null, null, false, null);
		project.setScm(scm);
		File repo = tmp;
		
		m.hg(repo, "init");
        m.touchAndCommit(repo, "x");
		
        PollingResult pr = scm.compare(
				launcher, 
				listener, 
				new MercurialTagAction("tip","",null,null),
				listener.getLogger(), 
				j.jenkins, 
				new FilePath(tmp), 
				project);
		assertEquals(PollingResult.Change.SIGNIFICANT, pr.change);
		
	}

    @TestExtension("triggersNewBuild")
    public static class DummyComparator extends ChangeComparator {
        public Change compare(MercurialSCM scm, Launcher launcher, TaskListener listener, MercurialTagAction baseline, PrintStream output, Node node, FilePath repository, AbstractProject<?,?> project) {
            return Change.SIGNIFICANT;
        }
    }
}
