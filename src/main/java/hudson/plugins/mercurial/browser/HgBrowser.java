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

    transient MercurialChangeSet current;
    
    private final URL url;
    
    private static final long serialVersionUID = 1L;

    
    /**
     * {@inheritDoc}
     */
    @Override
    public URL getChangeSetLink(MercurialChangeSet changeset) throws IOException {
        throw new UnsupportedOperationException("Method is not implemented for HgBrowser");
    }
    
    /**
     * Returns a link to a specific revision of a file.
     * 
     * @param path to a file.
     * @return URL pointing to a specific revision of the file.
     * 
     * @throws MalformedURLException
     */
    public URL getFileLink(String path) throws MalformedURLException {
        throw new UnsupportedOperationException("Method is not implemented for HgBrowser");
    }
    
    /**
     * Returns a link to a diff for a file.
     * 
     * @param path to a file.
     * @return URL pointing to a specific revision of the file.
     * 
     * @throws MalformedURLException
     */
    public URL getDiffLink(String path) throws MalformedURLException {
        throw new UnsupportedOperationException("Method is not implemented for HgBrowser");
    }
    
    /**
     * Throws an {@link IllegalStateException} if current is null. This is used in subclasses.
     * 
     * @throws IllegalStateException if current is null. 
     */
    void checkCurrentIsNotNull() {
        if (current == null) {
            throw new IllegalStateException("current changeset must not be null, did you forget to call 'getChangeSetLink'?");
        }
    }
    
    HgBrowser(String url) throws MalformedURLException {
        this.url = normalizeToEndWithSlash(new URL(url));
    }
    
    public URL getUrl() {
    	return url;
    }

    // compatibility with earlier plugins
    public Object readResolve() {        
        if (!this.getClass().equals(HgBrowser.class)) {
            return this;
        }
        // make sure to return the default HgWeb only if we no class is given in config.
        try {
            return new HgWeb(url.toExternalForm());
        } catch (MalformedURLException e) {
            throw new RuntimeException(e);
        }
    }
}
