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

    public abstract void select(ArgumentListBuilder cmd);

    public abstract void select(ArgumentListBuilder cmd, EnvVars env);

    public abstract boolean monitored(String branch);

    public abstract String getDefaultRevisionToBuild(EnvVars env);
}
