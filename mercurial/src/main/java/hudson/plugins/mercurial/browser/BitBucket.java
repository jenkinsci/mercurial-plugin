package hudson.plugins.mercurial.browser;

import hudson.Extension;
import hudson.model.Descriptor;
import hudson.plugins.mercurial.MercurialChangeSet;
import hudson.scm.RepositoryBrowser;

import java.io.IOException;
import java.net.URL;
import java.net.MalformedURLException;

import net.sf.json.JSONObject;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest;

/**
 * Mercurial web interface served using a <a href="http://bitbucket.org/">BitBucket</a> repository.
 */
public class BitBucket extends HgBrowser {
	
	@DataBoundConstructor
	public BitBucket(String url) throws MalformedURLException {
	    super(url);
	}
	
	@Override
	public URL getChangeSetLink(MercurialChangeSet changeSet)
			throws IOException {
		return new URL(getUrl(), "changeset/" + changeSet.getShortNode() + "/");
	}

    @Extension
    public static class DescriptorImpl extends Descriptor<RepositoryBrowser<?>> {
        public String getDisplayName() {
            return "bitbucket";
        }

        public @Override BitBucket newInstance(StaplerRequest req, JSONObject json) throws FormException {
            return req.bindParameters(BitBucket.class,"bitbucket.");
        }
    }

    private static final long serialVersionUID = 1L;
}
