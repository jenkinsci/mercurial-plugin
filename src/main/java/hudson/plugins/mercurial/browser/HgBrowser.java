package hudson.plugins.mercurial.browser;

import hudson.plugins.mercurial.MercurialChangeSet;
import hudson.scm.RepositoryBrowser;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;

/**
 * Parent class, as there is more than one browser.
 * {@link HgBrowser#readResolve()} will return the old default {@link HgWeb}.
 * Direct calls on this class will always throw {@link UnsupportedOperationException}s.
 */
public class HgBrowser extends RepositoryBrowser<MercurialChangeSet> {

    private URL url;

    private static final long serialVersionUID = 1L;

    /**
     * {@inheritDoc}
     */
    @Override
    public URL getChangeSetLink(MercurialChangeSet changeset) throws IOException {
        return doReadResolve().getChangeSetLink(changeset);
    }

    /*protected*/public HgBrowser(String url) throws MalformedURLException {
        this.url = normalizeToEndWithSlash(new URL(url));
    }

    public URL getUrl() {
    	return url;
    }

    // compatibility with earlier plugins
    public Object readResolve() {
        return doReadResolve();
    }
    private HgWeb doReadResolve() {
        try {
            return new HgWeb(url.toExternalForm());
        } catch (MalformedURLException e) {
            throw new RuntimeException(e);
        }
    }
}
