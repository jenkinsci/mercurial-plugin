package hudson.plugins.mercurial.browser;

import static org.junit.Assert.*;

import java.io.IOException;
import java.net.MalformedURLException;

import org.junit.Test;

public class HgBrowserTest {

    @Test(expected=UnsupportedOperationException.class)
    public final void testGetChangeSetLinkMercurialChangeSet() throws MalformedURLException, IOException {
        new HgBrowser("http://abc/").getChangeSetLink(null);
    }

    @Test(expected=UnsupportedOperationException.class)
    public final void getFileLink() throws IOException {
        new HgBrowser("http://abc/").getFileLink("");
    }

    @Test(expected=UnsupportedOperationException.class)
    public final void testGetDiffLink() throws IOException {
        new HgBrowser("http://abc/").getDiffLink("");
    }

    @Test
    public final void testGetUrl() throws MalformedURLException {
        assertEquals("http://abc/", new HgBrowser("http://abc").getUrl().toExternalForm());
    }

    @Test
    public final void testResolveObject() throws MalformedURLException {
        final Object browser = new HgBrowser("http://abc").readResolve();
        assertEquals(HgWeb.class, browser.getClass());
    }

}
