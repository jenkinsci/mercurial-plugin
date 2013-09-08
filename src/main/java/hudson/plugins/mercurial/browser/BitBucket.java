package hudson.plugins.mercurial.browser;

import hudson.Extension;
import hudson.plugins.mercurial.MercurialChangeSet;
import hudson.util.FormValidation;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;

import net.sf.json.JSONObject;

import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;

/**
 * Mercurial web interface served using a <a href="http://bitbucket.org/">BitBucket</a> repository.
 */
public class BitBucket extends HgBrowser {
    
    @DataBoundConstructor
    public BitBucket(String url) throws MalformedURLException {
        super(url);
    }
    
    /**
     * {@inheritDoc}
     */
    @Override
    public URL getChangeSetLink(MercurialChangeSet changeSet)
            throws IOException {
        current = changeSet;
        return new URL(getUrl(), "changeset/" + changeSet.getShortNode() + "/");
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
        // http://bitbucket.org/mfriedenhagen/hudson-mercurial/src/d736d15e5389/src/main/java/hudson/plugins/mercurial/browser/HgBrowser.java        
        return new URL(getUrl(), "src/" + current.getShortNode() + "/" + path);
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
        // http://bitbucket.org/mfriedenhagen/hudson-mercurial/changeset/d736d15e5389/#chg-src/main/java/hudson/plugins/mercurial/browser/HgBrowser.java
        return new URL(getUrl(), "changeset/" + current.getShortNode() + "/#chg-" + path);
    }
    
    @Extension
    public static class DescriptorImpl extends HgBrowserDescriptor {
        public String getDisplayName() {
            return "bitbucket";
        }

        public @Override BitBucket newInstance(StaplerRequest req, JSONObject json) throws FormException {
            return req.bindParameters(BitBucket.class,"bitbucket.");
        }

        @Override public FormValidation doCheckUrl(@QueryParameter String url) {
            return _doCheckUrl(url);
        }

        @Override protected FormValidation check(URL url) {
            if (url.toString().matches("https?://bitbucket[.]org/[^/]+/[^/]+/")) {
                return FormValidation.ok();
            } else {
                return FormValidation.warning("Possibly incorrect root URL; expected http://bitbucket.org/USERNAME/REPOS/");
            }
        }
    }

    private static final long serialVersionUID = 1L;
}
