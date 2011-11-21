package hudson.plugins.mercurial.build;

import hudson.DescriptorExtensionList;
import hudson.EnvVars;
import hudson.ExtensionPoint;
import hudson.model.Describable;
import hudson.model.Hudson;
import hudson.plugins.mercurial.MercurialTagAction;
import hudson.util.ArgumentListBuilder;

import java.io.Serializable;

/**
 * Plugable strategy to select the Mercurial branches to be monitored by the Job. This component is responsible for
 * contributing the hg command line and filter incoming branches, so that only a configurable subset of incoming
 * changesets are considered by the Job an can trigger a new Run.
 *
 * @author <a href="mailto:nicolas.deloof@cloudbees.com">Nicolas De loof</a>
 */
public abstract class BranchSelector implements ExtensionPoint, Describable<BranchSelector>, Serializable {

    public BranchSelectorDescriptor getDescriptor() {
        return (BranchSelectorDescriptor) Hudson.getInstance().getDescriptorOrDie(getClass());
    }

    public static DescriptorExtensionList<BranchSelector,BranchSelectorDescriptor> all() {
        return Hudson.getInstance().<BranchSelector,BranchSelectorDescriptor>getDescriptorList(BranchSelector.class);
    }

    private static final long serialVersionUID = 1L;

    /**
     * Add branch selection arguments to the hg command line. This method is used when no Environment is set,
     * typically on SCM polling
     */
    public abstract void select(ArgumentListBuilder cmd);

    /**
     * Add branch selection arguments to the hg command line. This method is used during Build, passing the environment
     * to adapt the command
     */
    public abstract void select(ArgumentListBuilder cmd, EnvVars env);

    /**
     * @return <code>true</code> if the specified branch matches the selection strategy
     */
    public abstract boolean monitored(String branch);

    /**
     * @return the default revision to build when no candidate can be extracted from incoming changesets
     */
    public abstract String getDefaultRevisionToBuild(EnvVars env);
}
