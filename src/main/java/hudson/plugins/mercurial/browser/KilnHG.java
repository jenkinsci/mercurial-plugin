package hudson.plugins.mercurial.browser;

import hudson.Extension;
import hudson.plugins.mercurial.MercurialChangeSet;
import hudson.util.FormValidation;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;


import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

/**
 * Mercurial web interface served using a <a href="http://kiln.org/">Kiln</a> repository.
 */
public class KilnHG extends HgBrowser {

    @DataBoundConstructor
    public KilnHG(String url) throws MalformedURLException {
        super(url);
    }
    
    /**
     * {@inheritDoc}
     */
    @Override
    public URL getChangeSetLink(MercurialChangeSet changeSet)
            throws IOException {
        current = changeSet;
        // http://kilnhg.com/Repo/Repositories/Group/RepoName/History/12345
        return new URL(getUrl(), "History/" + changeSet.getShortNode());
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
        // http://kilnhg.com/Repo/Repositories/Group/RepoName/File/filename?rev=12345
        return new URL(getUrl(), "File/" + path + "?rev=" + current.getShortNode());
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
        
        // http://kilnhg.com/Repo/Repositories/Group/RepoName/History/12345
        return new URL(getUrl(), "History/" + current.getShortNode());
    }

    @Extension
    public static class DescriptorImpl extends HgBrowserDescriptor {
        public String getDisplayName() {
            return "kilnhg";
        }

        @Override public FormValidation doCheckUrl(@QueryParameter String url) {
            return _doCheckUrl(url);
        }

    }

    private static final long serialVersionUID = 1L;

}
