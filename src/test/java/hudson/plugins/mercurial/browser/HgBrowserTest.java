package hudson.plugins.mercurial.browser;

import static org.junit.Assert.*;

import java.io.IOException;
import java.net.MalformedURLException;

import org.junit.Test;

public class HgBrowserTest {

    @Test(expected=UnsupportedOperationException.class)
    public final void testGetChangeSetLinkMercurialChangeSet() throws IOException {
        new MockBrowser("http://abc/").getChangeSetLink(null);
    }

    @Test(expected=UnsupportedOperationException.class)
    public final void getFileLink() throws IOException {
        new MockBrowser("http://abc/").getFileLink("");
    }

    @Test(expected=UnsupportedOperationException.class)
    public final void testGetDiffLink() throws IOException {
        new MockBrowser("http://abc/").getDiffLink("");
    }

    @Test
    public final void testGetUrl() throws MalformedURLException {
        assertEquals("http://abc/", new MockBrowser("http://abc").getUrl().toExternalForm());
    }

    @Test
    public final void testResolveObject() throws MalformedURLException {
        final Object browser = new MockBrowser("http://abc").readResolve();
        assertEquals(MockBrowser.class, browser.getClass());
    }

    public static final class MockBrowser extends HgBrowser {
        public MockBrowser(String url) throws MalformedURLException {
            super(url);
        }
    }

}
