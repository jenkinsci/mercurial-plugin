package hudson.plugins.mercurial;

import hudson.Extension;
import hudson.model.Item;
import hudson.scm.SCM;
import hudson.views.ListViewColumn;
import hudson.views.ListViewColumnDescriptor;
import jenkins.triggers.SCMTriggerItem;
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
        SCMTriggerItem s = SCMTriggerItem.SCMTriggerItems.asSCMTriggerItem(item);
        if (s == null) {
            return "";
        }
        String revision = null;
        for (SCM scm : s.getSCMs()) {
            if (!(scm instanceof MercurialSCM)) {
                return "";
            }
            String _revision = ((MercurialSCM) scm).getRevision();
            if (revision != null && !revision.equals(_revision)) {
                return "";
            }
            revision = _revision;
        }
        return revision != null ? revision : "";
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
