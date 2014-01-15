package hudson.plugins.mercurial;

import hudson.Extension;
import hudson.model.AbstractProject;
import hudson.model.Descriptor;
import hudson.model.Job;
import hudson.scm.SCM;
import hudson.views.ListViewColumn;

import org.kohsuke.stapler.DataBoundConstructor;

import com.google.common.base.Optional;

/**
 * Allows adding a column in the overview that displays the revision that is
 * checked out. For non-Mercurial projects the column value is simply left
 * empty.
 * 
 * @author andreas-schilling
 */
public class MercurialRevisionColumn extends ListViewColumn {

    @Extension
    public static final Descriptor<ListViewColumn> DESCRIPTOR = new DescriptorImpl();

    @DataBoundConstructor
    public MercurialRevisionColumn() {
	super();
    }

    /** {@inheritDoc} */
    @Override
    public Descriptor<ListViewColumn> getDescriptor() {
	return super.getDescriptor();
    }

    public String getMercurialRevision(final Job job) {
	final Optional<MercurialSCM> hgScm = tryGetMercurialSCM(job);

	return hgScm.isPresent() ? hgScm.get().getRevision() : "";
    }

    private Optional<MercurialSCM> tryGetMercurialSCM(final Job job) {
	if (!AbstractProject.class.isAssignableFrom(job.getClass())) {
	    return Optional.absent();
	}
	final SCM scm = ((AbstractProject) job).getScm();
	if (scm == null || !MercurialSCM.class.isAssignableFrom(scm.getClass())) {
	    return Optional.absent();
	}

	return Optional.of((MercurialSCM) scm);
    }

    private static class DescriptorImpl extends Descriptor<ListViewColumn> {

	@Override
	public String getDisplayName() {
	    return Messages.MercurialRevisionColumn_DisplayName();
	}

    }
}
