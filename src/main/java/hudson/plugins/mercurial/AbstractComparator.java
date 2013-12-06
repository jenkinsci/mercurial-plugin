package hudson.plugins.mercurial;

import java.io.IOException;
import java.io.PrintStream;

import hudson.ExtensionList;
import hudson.ExtensionPoint;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.TaskListener;
import hudson.model.AbstractProject;
import hudson.model.Hudson;
import hudson.model.Node;
import hudson.scm.PollingResult.Change;

public abstract class AbstractComparator implements ExtensionPoint {
	
	public static ExtensionList<AbstractComparator> all() {
        return Hudson.getInstance().getExtensionList(AbstractComparator.class);
    }

	public Change compare(MercurialSCM scm, Launcher launcher,
			TaskListener listener, MercurialTagAction baseline,
			PrintStream output, Node node, FilePath repository,
			AbstractProject<?, ?> project) throws IOException,
			InterruptedException {
		output.println("This will get you nowhere");
		return null;
	}
	
}
