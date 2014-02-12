package hudson.plugins.mercurial;

import hudson.Extension;
import hudson.model.AbstractProject;
import hudson.model.Item;
import hudson.scm.SCM;
import hudson.views.ListViewColumn;
import hudson.views.ListViewColumnDescriptor;
import org.kohsuke.stapler.DataBoundConstructor;

/**
 * Allows adding a column in the overview that displays the revision that is
 * checked out. For non-Mercurial projects the column value is simply left
 * empty.
 * 
 * @author andreas-schilling
 */
public class MercurialRevisionColumn extends ListViewColumn {

    @DataBoundConstructor
    public MercurialRevisionColumn() {
    }

    public String getMercurialRevision(final Item item) {
	if (!AbstractProject.class.isAssignableFrom(item.getClass())) {
	    return "";
	}
	final SCM scm = ((AbstractProject) item).getScm();
	if (scm == null || !MercurialSCM.class.isAssignableFrom(scm.getClass())) {
	    return "";
	}

	return ((MercurialSCM) scm).getRevision();
    }

    @Extension public static class DescriptorImpl extends ListViewColumnDescriptor {

	@Override
	public String getDisplayName() {
	    return Messages.MercurialRevisionColumn_DisplayName();
	}

        @Override public boolean shownByDefault() {
            return false;
        }

    }
}
