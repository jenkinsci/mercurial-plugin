package hudson.plugins.mercurial.browser;

import static org.junit.Assert.assertEquals;

import java.io.IOException;
import java.net.MalformedURLException;

import org.junit.Test;

public class GoogleCodeTest extends AbstractBrowserTestBase {

    private static final String REPO_URL = "http://code.google.com/p/PROJECTNAME/source";
    
    public GoogleCodeTest() throws MalformedURLException {
        super(new GoogleCode(REPO_URL));
    }

    @Test
    public void testGetChangeSetLinkMercurialChangeSet() throws IOException {
        testGetChangeSetLinkMercurialChangeSet(REPO_URL+"/detail?r=6704efde87541766fadba17f66d04b926cd4d343");
    }

    @Test
    public void testGetFileLink() throws IOException {
        testGetFileLink(REPO_URL + "/browse/src/main/java/hudson/plugins/mercurial/browser/HgBrowser.java?spec=svn6704efde87541766fadba17f66d04b926cd4d343&r=6704efde87541766fadba17f66d04b926cd4d343");
    }

    @Test
    public void testGetDiffLink() throws IOException {        
        testGetDiffLink(REPO_URL + "/diff?spec=svn6704efde87541766fadba17f66d04b926cd4d343&r=6704efde87541766fadba17f66d04b926cd4d343&format=unidiff&path=%2Fsrc%2Fmain%2Fjava%2Fhudson%2Fplugins%2Fmercurial%2Fbrowser%2FHgBrowser.java");
    }

    @Test
    public void testGoogleCode() throws MalformedURLException {
        assertEquals(REPO_URL +"/", browser.getUrl().toExternalForm());
    }

    @Test(expected=MalformedURLException.class)
    public void testGoogleCodeMustEndWithSource() throws MalformedURLException {
        new GoogleCode("http://code.google.com/p/PROJECTNAME");        
    }
}
