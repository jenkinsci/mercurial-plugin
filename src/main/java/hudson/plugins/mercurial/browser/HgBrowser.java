package hudson.plugins.mercurial.browser;

import hudson.plugins.mercurial.MercurialChangeSet;
import hudson.scm.RepositoryBrowser;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;

/**
 * Abstract parent class, as there is more than one browser.
 */
public abstract class HgBrowser extends RepositoryBrowser<MercurialChangeSet> {

    private final URL url;
    
    private static final long serialVersionUID = 1L;

    @Override
    public abstract URL getChangeSetLink(MercurialChangeSet changeset) throws IOException;

    public HgBrowser(String url) throws MalformedURLException {
        this.url = normalizeToEndWithSlash(new URL(url));
    }
    
    public URL getUrl() {
    	return url;
    }
}
