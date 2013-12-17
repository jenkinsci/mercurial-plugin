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
import hudson.scm.PollingResult;

/**
 * An extension point that allows plugins to override the built in compare 
 * functionality when deciding whether to trigger a build or not.
 * @author Ronni Elken Lindsgaard
 * @since 1.50
 */
public abstract class ChangeComparator implements ExtensionPoint {
	
	public static ExtensionList<ChangeComparator> all() {
        return Hudson.getInstance().getExtensionList(ChangeComparator.class);
    }

	/**
	 * Override to customize the compare functionality.
	 * @return either a class of change, or null if the standard comparison is wanted
	 */
	abstract public PollingResult.Change compare(MercurialSCM scm, Launcher launcher,
			TaskListener listener, MercurialTagAction baseline,
			PrintStream output, Node node, FilePath repository,
			AbstractProject<?, ?> project) 
					throws IOException, InterruptedException;
	
}
