package hudson.plugins.mercurial.build;

import hudson.DescriptorExtensionList;
import hudson.ExtensionPoint;
import hudson.model.Describable;
import hudson.model.Hudson;
import hudson.plugins.mercurial.MercurialSCM;

import java.io.IOException;
import java.io.Serializable;
import java.util.Collection;

/**
 * Extension point to identify the revision (commit ID) to be built
 *
 * @author <a href="mailto:nicolas.deloof@cloudbees.com">Nicolas De loof</a>
 */
public abstract class BuildChooser implements ExtensionPoint, Describable<BuildChooser>, Serializable {

    /**
     * Get a list of revisions (commit IDs or branch names) that are candidates to be built.
     * @param branchSpec the branch specification, may be a specific branch name or a pattern to monitor multiple branches
     * @return candidate revisions, may be an empty collection but not null
     */
    public abstract Collection<String> getCandidateRevisions(String branchSpec) throws IOException;

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

}
