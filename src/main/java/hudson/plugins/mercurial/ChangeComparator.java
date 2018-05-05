package hudson.plugins.mercurial;

import java.io.IOException;
import java.io.PrintStream;

import hudson.ExtensionList;
import hudson.ExtensionPoint;
import hudson.FilePath;
import hudson.Launcher;
import hudson.Util;
import hudson.model.TaskListener;
import hudson.model.AbstractProject;
import hudson.model.Job;
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
        return ExtensionList.lookup(ChangeComparator.class);
    }

	/**
	 * Override to customize the compare functionality.
	 * @return either a class of change, or null if the standard comparison is wanted
	 */
	public PollingResult.Change compare(MercurialSCM scm, Launcher launcher, TaskListener listener, MercurialTagAction baseline, PrintStream output, Node node, FilePath repository, Job<?, ?> project) throws IOException, InterruptedException {
        if (Util.isOverridden(ChangeComparator.class, getClass(), "compare", MercurialSCM.class, Launcher.class, TaskListener.class, MercurialTagAction.class, PrintStream.class, Node.class, FilePath.class, AbstractProject.class) && project instanceof AbstractProject) {
            return compare(scm, launcher, listener, baseline, output, node, repository, (AbstractProject) project);
        } else {
            throw new AbstractMethodError("you must override the new overload of compare");
        }
    }

    @Deprecated
	public PollingResult.Change compare(MercurialSCM scm, Launcher launcher, TaskListener listener, MercurialTagAction baseline, PrintStream output, Node node, FilePath repository, AbstractProject<?, ?> project) throws IOException, InterruptedException {
        return compare(scm, launcher, listener, baseline, output, node, repository, (Job) project);
    }
	
}
