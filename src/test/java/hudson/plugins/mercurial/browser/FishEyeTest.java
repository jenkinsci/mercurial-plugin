package hudson.plugins.mercurial.browser;

import java.io.IOException;
import java.net.MalformedURLException;

import org.junit.Test;

public class FishEyeTest extends AbstractBrowserTestBase {

    private static final String REPO_URL = "http://www.example.org/browse/hg/";

    public FishEyeTest() throws MalformedURLException {
        super(new FishEye(REPO_URL));        
    }

    @Test
    public void testGetChangeSetLinkMercurialChangeSet() throws IOException {
        testGetChangeSetLinkMercurialChangeSet("http://www.example.org/changelog/hg?cs=6704efde87541766fadba17f66d04b926cd4d343");
    }
    
    @Test
    public void testGetFileLink() throws IOException {
        testGetFileLink(REPO_URL + "src/main/java/hudson/plugins/mercurial/browser/HgBrowser.java#6704efde87541766fadba17f66d04b926cd4d343");
    }

    @Test
    public void testGetDiffLink() throws IOException {        
        testGetDiffLink(REPO_URL + "src/main/java/hudson/plugins/mercurial/browser/HgBrowser.java?r1=6704efde87541766fadba17f66d04b926cd4d343&r2=");
    }
}
