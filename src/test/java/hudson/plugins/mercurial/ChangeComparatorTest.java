package hudson.plugins.mercurial;

import static org.junit.Assert.*;
import org.jvnet.hudson.test.TestExtension;

import hudson.FilePath;
import hudson.Launcher;
import hudson.model.AbstractProject;
import hudson.model.Computer;
import hudson.model.FreeStyleProject;
import hudson.model.Hudson;
import hudson.model.Node;
import hudson.model.TaskListener;
import hudson.scm.PollingResult;
import hudson.scm.PollingResult.Change;
import hudson.util.StreamTaskListener;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.lang.reflect.Method;
import java.nio.charset.Charset;

import org.junit.Test;

public class ChangeComparatorTest extends SCMTestBase {

	@Override
	protected String hgInstallation() {
		return null;
	}
	
	@TestExtension("testTriggersNewBuild")
	static public class DummyComparator extends ChangeComparator {
		public Change compare(MercurialSCM scm, Launcher launcher, TaskListener listener, MercurialTagAction baseline, PrintStream output, Node node, FilePath repository, AbstractProject<?,?> project)  
				throws IOException, InterruptedException {
			return Change.SIGNIFICANT;
		}
	}
	
	@Test public void testTriggersNewBuild() throws Exception {
		TaskListener listener = new StreamTaskListener(System.out, Charset.defaultCharset());
		Launcher launcher = Hudson.getInstance().createLauncher(listener);
		
		Method compareMethod = MercurialSCM.class.getDeclaredMethod("compare", Launcher.class , TaskListener.class , MercurialTagAction.class , PrintStream.class , Node.class , FilePath.class, AbstractProject.class);
		compareMethod.setAccessible(true);
		FreeStyleProject project = j.createFreeStyleProject();
		
		MercurialSCM scm = new MercurialSCM(hgInstallation(), tmp.getRoot().getPath(), null, null,null, null, false);
		project.setScm(scm);
		File repo = tmp.getRoot();
		
		m.hg(repo, "init");
		
		//PollingResult pr = m.pollSCMChanges(project);
		//Change.INSIGNIFICANT!?
		
		PollingResult pr = (PollingResult) compareMethod.invoke(
				scm, 
				launcher, 
				listener, 
				new MercurialTagAction("tip","",null), 
				listener.getLogger(), 
				Computer.currentComputer().getNode(), 
				new FilePath(tmp.getRoot()), 
				project);
		assertEquals(PollingResult.Change.SIGNIFICANT, pr.change);
		
	}	

}
