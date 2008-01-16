package hudson.plugins.mercurial.browser;

import hudson.model.Descriptor;
import hudson.plugins.mercurial.MercurialChangeSet;
import hudson.scm.RepositoryBrowser;

import java.io.IOException;
import java.net.URL;
import java.net.MalformedURLException;

import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest;

/**
 * Mercurial web interface served using the standalone server
 * or hgweb CGI scripts.
 */
public class HgWeb extends RepositoryBrowser<MercurialChangeSet>{
	private final URL url;
	
	@DataBoundConstructor
	public HgWeb(String url) throws MalformedURLException {
        if(!url.endsWith("/"))
            url += '/';
        this.url = new URL(url);

        // TODO finish:
		//    add verification of url
	}
	
	public URL getUrl() {
		return url;
	}
	
	
	@Override
	public URL getChangeSetLink(MercurialChangeSet changeSet)
			throws IOException {
		// TODO: consider verifying the repository connection to tip at configuration time?
		return new URL(url, "rev/" + changeSet.getShortNode());
	}

	public Descriptor<RepositoryBrowser<?>> getDescriptor() {
		return DESCRIPTOR;
	}
	
    public static final Descriptor<RepositoryBrowser<?>> DESCRIPTOR = new Descriptor<RepositoryBrowser<?>>(HgWeb.class) {
        public String getDisplayName() {
            return "hgweb";
        }

        public HgWeb newInstance(StaplerRequest req) throws FormException {
            return req.bindParameters(HgWeb.class,"hgweb.");
        }
    };

    private static final long serialVersionUID = 1L;
}
