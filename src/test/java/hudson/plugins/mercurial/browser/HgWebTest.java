package hudson.plugins.mercurial.browser;

import java.io.IOException;
import java.net.MalformedURLException;

import org.junit.Test;

public class HgWebTest extends AbstractBrowserTestBase {
     
    private static final String REPO_URL = "http://hg.friedenhagen.net/index.cgi/mercurial-hudson";

    public HgWebTest() throws MalformedURLException {
        super(new HgWeb(REPO_URL));
    }
    
    @Test
    public void testGetChangeSetLinkMercurialChangeSet() throws IOException {
        testGetChangeSetLinkMercurialChangeSet(REPO_URL + "/rev/6704efde8754");
    }

    @Test
    public void testGetFileLink() throws IOException {
        testGetFileLink(REPO_URL + "/file/6704efde8754/src/main/java/hudson/plugins/mercurial/browser/HgBrowser.java");
    }

    @Test
    public void testGetDiffLink() throws IOException {        
        testGetDiffLink(REPO_URL + "/diff/6704efde8754/src/main/java/hudson/plugins/mercurial/browser/HgBrowser.java");
    }

}
