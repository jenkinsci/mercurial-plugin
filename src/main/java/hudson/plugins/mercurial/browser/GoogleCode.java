package hudson.plugins.mercurial.browser;

import hudson.Extension;
import hudson.model.Descriptor;
import hudson.plugins.mercurial.MercurialChangeSet;
import hudson.scm.RepositoryBrowser;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLEncoder;

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
	
	/**
	 * {@inheritDoc}
	 */
	@Override
	public URL getChangeSetLink(MercurialChangeSet changeSet)
			throws IOException {
	    current = changeSet;
	    //E.g.: http://code.google.com/p/jmemcache-daemon/source/detail?r=eb1b7d8338ccaf6d54420bc98f52d00563d3cb40
		return new URL(getUrl(), "detail?r=" + changeSet.getNode());
	}

	/**
	 * {@inheritDoc}
	 * 
     * Throws {@link IllegalStateException} when this method is called before at least one call 
     * to {@literal getChangeSetLink(MercurialChangeSet)}.
	 */
	@Override
	public URL getFileLink(String path) throws MalformedURLException {
        checkCurrentIsNotNull();
	    //E.g.: http://code.google.com/p/jmemcache-daemon/source/browse/test/src/test/java/com/thimbleware/jmemcached/test/AvailablePortFinder.java?r=2634a09900cb4dbc1dea714ac0a5db6ddf882321
	    return new URL(getUrl(), "browse/" + path + "?spec=svn"+ current.getNode() + "&r=" + current.getNode());
	}
	
    /**
     * {@inheritDoc}
     * 
     * Throws {@link IllegalStateException} when this method is called before at least one call 
     * to {@literal getChangeSetLink(MercurialChangeSet)}.
     */
	@Override
	public URL getDiffLink(String path) throws MalformedURLException {
	    checkCurrentIsNotNull();
	    //E.g: http://code.google.com/p/jmemcache-daemon/source/diff?spec=svn8365b0a208d3d5f07a014d05b878ed8c88e72ddf&old=eb1b7d8338ccaf6d54420bc98f52d00563d3cb40&r=8365b0a208d3d5f07a014d05b878ed8c88e72ddf&format=unidiff&path=%2Fcore%2Fsrc%2Fmain%2Fjava%2Fcom%2Fthimbleware%2Fjmemcached%2FCache.java	    
	    try {
	        // We don't specify the old revision, but google seems to clever enough to take the predecessor as default.
            return new URL(getUrl(), "diff?spec=svn"+ current.getNode() + "&r=" + current.getNode() + "&format=unidiff&path=%2F" + URLEncoder.encode(path, "UTF-8"));
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException("JDK broken?",  e);
        }
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
