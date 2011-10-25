package hudson.plugins.mercurial.build;

import hudson.DescriptorExtensionList;
import hudson.ExtensionPoint;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.BuildListener;
import hudson.model.Describable;
import hudson.model.Hudson;
import hudson.plugins.mercurial.MercurialSCM;

import java.io.IOException;
import java.io.Serializable;
import java.util.Collection;
import java.util.logging.Logger;

/**
 * Extension point to identify the revision (commit ID) to be built
 *
 * @author <a href="mailto:nicolas.deloof@cloudbees.com">Nicolas De loof</a>
 */
public abstract class BuildChooser implements ExtensionPoint, Describable<BuildChooser>, Serializable {

    /**
     * Get a the revision (commit IDs or branch names) that will be built.
     * @param branchSpec the branch specification, may be a specific branch name or a pattern to monitor multiple branches
     */
    public abstract String getRevisionToBuild(AbstractBuild<?, ?> build, Launcher launcher, FilePath repository, BuildListener listener) throws IOException, InterruptedException;

    /**
     * Refers back to the {@link MercurialSCM} that owns this build chooser.
     */
    public transient MercurialSCM scm;

    public BuildChooserDescriptor getDescriptor() {
        return (BuildChooserDescriptor)Hudson.getInstance().getDescriptorOrDie(getClass());
    }

    public static DescriptorExtensionList<BuildChooser,BuildChooserDescriptor> all() {
        return Hudson.getInstance().<BuildChooser,BuildChooserDescriptor>getDescriptorList(BuildChooser.class);
    }

    private static final long serialVersionUID = 1L;

    protected static final Logger LOGGER = Logger.getLogger(BuildChooser.class.getName());

}
