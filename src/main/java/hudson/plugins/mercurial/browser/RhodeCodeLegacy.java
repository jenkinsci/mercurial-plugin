package hudson.plugins.mercurial.browser;

import hudson.Extension;
import hudson.model.Descriptor;
import hudson.scm.RepositoryBrowser;

import java.net.MalformedURLException;
import java.net.URL;

import net.sf.json.JSONObject;

import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest;

/**
 * Mercurial web interface served using a <a
 * href="http://rhodecode.org/">RhodeCode</a> repository. There was a change to
 * the anchors in the changeset page between release 1.1.8 and 1.2.0, causing
 * the need for this class to provide compatibility with the older versions.
 */
public class RhodeCodeLegacy extends RhodeCode {

    @DataBoundConstructor
    public RhodeCodeLegacy(String url) throws MalformedURLException {
        super(url);
    }

    /**
     * {@inheritDoc}
     * 
     * Throws {@link IllegalStateException} when this method is called before at
     * least one call to {@literal getChangeSetLink(MercurialChangeSet)}.
     */
    @Override
    public URL getDiffLink(String path) throws MalformedURLException {
        checkCurrentIsNotNull();
        // http://demo.rhodecode.org/rhodecode/changeset/55a4cbcd464de2f3969ce680885b3193234a8854#CHANGE-rhodecode-templates-base-base.html
        // this actually points to the getChangeSetLink() page, and just adds an
        // anchor
        //
        // Rhodecode also has a specific page for a single diff, but that
        // requires parameters we don't have here.
        // http://demo.rhodecode.org/rhodecode/diff/rhodecode/templates/admin/users/user_edit_my_account.html?diff2=55a4cbcd464de2f3969ce680885b3193234a8854&diff1=9c0f5d5587895561148b46c68b989d19eeddb0ff&diff=diff
        return new URL(getUrl(), "changeset/" + current.getNode() + "#CHANGE-"
                + path.replace('/', '-'));
    }

    @Extension
    public static class DescriptorImpl extends Descriptor<RepositoryBrowser<?>> {
        public String getDisplayName() {
            return "rhodecode (pre-1.2.0)";
        }

        public @Override
        RhodeCodeLegacy newInstance(StaplerRequest req, JSONObject json)
                throws FormException {
            return req.bindParameters(RhodeCodeLegacy.class,
                    "rhodecode-legacy.");
        }
    }

    private static final long serialVersionUID = 1L;
}
