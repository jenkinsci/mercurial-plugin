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
 * Mercurial web interface served using a <a href="http://code.google.com/">Google code</a> repository.
 */
public class GoogleCode extends HgBrowser {
	
	@DataBoundConstructor
	public GoogleCode(String url) throws MalformedURLException {
	    super(url);
	    if (!this.getUrl().toExternalForm().endsWith("/source/")) {
	        throw new MalformedURLException("GoogleCode browser URL (currently: " + url + ") must end with '/source/'");
	    }
	}
	
	@Override
	public URL getChangeSetLink(MercurialChangeSet changeSet)
			throws IOException {
	    //E.g.: http://code.google.com/p/jmemcache-daemon/source/detail?r=eb1b7d8338ccaf6d54420bc98f52d00563d3cb40
		return new URL(getUrl(), "detail?r=" + changeSet.getNode());
	}

    @Extension
    public static class DescriptorImpl extends Descriptor<RepositoryBrowser<?>> {
        public String getDisplayName() {
            return "googlecode";
        }

        public @Override GoogleCode newInstance(StaplerRequest req, JSONObject json) throws FormException {
            return req.bindParameters(GoogleCode.class,"googlecode.");
        }
    }

    private static final long serialVersionUID = 1L;
}
