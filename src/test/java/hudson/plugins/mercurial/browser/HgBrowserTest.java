package hudson.plugins.mercurial.browser;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.net.MalformedURLException;
import org.junit.jupiter.api.Test;

class HgBrowserTest {

    @Test
    void testGetChangeSetLinkMercurialChangeSet() {
        assertThrows(UnsupportedOperationException.class, () ->
            new MockBrowser("http://abc/").getChangeSetLink(null));
    }

    @Test
    void getFileLink() {
        assertThrows(UnsupportedOperationException.class, () ->
            new MockBrowser("http://abc/").getFileLink(""));
    }

    @Test
    void testGetDiffLink() {
        assertThrows(UnsupportedOperationException.class, () ->
            new MockBrowser("http://abc/").getDiffLink(""));
    }

    @Test
    void testGetUrl() throws Exception {
        assertEquals("http://abc/", new MockBrowser("http://abc").getUrl().toExternalForm());
    }

    @Test
    void testResolveObject() throws Exception {
        final Object browser = new MockBrowser("http://abc").readResolve();
        assertEquals(MockBrowser.class, browser.getClass());
    }

    public static final class MockBrowser extends HgBrowser {
        public MockBrowser(String url) throws MalformedURLException {
            super(url);
        }
    }

}
