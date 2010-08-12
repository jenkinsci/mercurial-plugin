package hudson.plugins.mercurial.browser;

import hudson.Extension;
import hudson.model.Descriptor;
import hudson.plugins.mercurial.MercurialChangeSet;
import hudson.scm.RepositoryBrowser;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;

import net.sf.json.JSONObject;

import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest;

/**
 * Mercurial web interface served using Fisheye.
 */
public class Fisheye extends HgBrowser {	    
    
	@DataBoundConstructor
	public Fisheye(String url) throws MalformedURLException {
	    super(url);
	}
	
	/**
	 * {@inheritDoc}
	 */
	@Override
	public URL getChangeSetLink(MercurialChangeSet changeSet)
			throws IOException {
	    current = changeSet;

	    // replace /browse/ with /changelog/ so that the full changeset changelog will be displayed 
	    String url = getUrl().toExternalForm();
	    url = url.replaceAll("/browse/", "/changelog/");
	    if (url.endsWith("/"))
	    {
	        url = url.substring(0, url.length() - 1);
	    }
	    // http://deadlock.netbeans.org/fisheye/browse/netbeans?cs=0a43e7e89449d1ca8a9da37e1cc644a620d48e71
		return new URL(url + "?cs=" + changeSet.getNode());
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
        // http://deadlock.netbeans.org/fisheye/browse/netbeans/samplefile.txt#0a43e7e89449d1ca8a9da37e1cc644a620d48e71
        return new URL(getUrl(), path + "#" + current.getNode());
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
            
        // http://deadlock.netbeans.org/fisheye/browse/netbeans/samplefile.txt?r1=0a43e7e89449d1ca8a9da37e1cc644a620d48e71&r2=
        return new URL(getUrl(), path + "?r1=" + current.getNode() + "&r2=");
    }
	
    @Extension
    public static class DescriptorImpl extends Descriptor<RepositoryBrowser<?>> {
        public String getDisplayName() {
            return "fisheye";
        }

        public @Override Fisheye newInstance(StaplerRequest req, JSONObject json) throws FormException {
            return req.bindParameters(Fisheye.class,"fisheye.");
        }
    }

    private static final long serialVersionUID = 1L;
}
