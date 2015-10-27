package hudson.plugins.mercurial;

import static org.junit.Assert.*;
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
import jenkins.model.Jenkins;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.Charset;
import org.junit.Rule;

import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.jvnet.hudson.test.JenkinsRule;

public class ChangeComparatorTest {

    @Rule public JenkinsRule j = new JenkinsRule();
    @Rule public MercurialRule m = new MercurialRule(j);
    @Rule public TemporaryFolder tmp = new TemporaryFolder();

    @TestExtension("triggersNewBuild")
	static public class DummyComparator extends ChangeComparator {
		public Change compare(MercurialSCM scm, Launcher launcher, TaskListener listener, MercurialTagAction baseline, PrintStream output, Node node, FilePath repository, AbstractProject<?,?> project)  
				throws IOException, InterruptedException {
			return Change.SIGNIFICANT;
		}
	}
	
    @Test public void triggersNewBuild() throws Exception {
		TaskListener listener = new StreamTaskListener(System.out, Charset.defaultCharset());
        Launcher launcher = j.jenkins.createLauncher(listener);
		
		FreeStyleProject project = j.createFreeStyleProject();
		
        MercurialSCM scm = new MercurialSCM(null, tmp.getRoot().getPath(), null, null, null, null, false, null);
		project.setScm(scm);
		File repo = tmp.getRoot();
		
		m.hg(repo, "init");
        m.touchAndCommit(repo, "x");
		
        PollingResult pr = scm.compare(
				launcher, 
				listener, 
				new MercurialTagAction("tip","",null,null),
				listener.getLogger(), 
				j.jenkins, 
				new FilePath(tmp.getRoot()), 
				project);
		assertEquals(PollingResult.Change.SIGNIFICANT, pr.change);
		
	}	

}
