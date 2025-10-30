package hudson.plugins.mercurial.browser;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.net.MalformedURLException;
import org.junit.jupiter.api.Test;

class GoogleCodeTest extends AbstractBrowserTestBase {

    private static final String REPO_URL = "http://code.google.com/p/PROJECTNAME/source";

    @Override
    protected HgBrowser getBrowser() throws Exception {
        return new GoogleCode(REPO_URL);
    }

    @Test
    void testGetChangeSetLinkMercurialChangeSet() throws Exception {
        testGetChangeSetLinkMercurialChangeSet(REPO_URL+"/detail?r=6704efde87541766fadba17f66d04b926cd4d343");
    }

    @Test
    void testGetFileLink() throws Exception {
        testGetFileLink(REPO_URL + "/browse/src/main/java/hudson/plugins/mercurial/browser/HgBrowser.java?spec=svn6704efde87541766fadba17f66d04b926cd4d343&r=6704efde87541766fadba17f66d04b926cd4d343");
    }

    @Test
    void testGetDiffLink() throws Exception {
        testGetDiffLink(REPO_URL + "/diff?spec=svn6704efde87541766fadba17f66d04b926cd4d343&r=6704efde87541766fadba17f66d04b926cd4d343&format=unidiff&path=%2Fsrc%2Fmain%2Fjava%2Fhudson%2Fplugins%2Fmercurial%2Fbrowser%2FHgBrowser.java");
    }

    @Test
    void testGoogleCode() {
        assertEquals(REPO_URL +"/", browser.getUrl().toExternalForm());
    }

    @Test
    void testGoogleCodeMustEndWithSource() {
        assertThrows(MalformedURLException.class, () -> new GoogleCode("http://code.google.com/p/PROJECTNAME"));
    }
}
