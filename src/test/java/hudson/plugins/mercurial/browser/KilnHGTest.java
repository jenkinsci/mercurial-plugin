/*
 * KilnHGTest.java 25.03.2010
 */
package hudson.plugins.mercurial.browser;

import java.io.IOException;
import java.net.MalformedURLException;

import org.junit.Test;

public class KilnHGTest extends AbstractBrowserTestBase {

    private static final String REPO_URL = "https://example.kilnhg.com/Repo/Repositories/Group/hg-repo";

    public KilnHGTest() throws MalformedURLException {
        super(new KilnHG(REPO_URL));        
    }

    @Test
    public void testGetChangeSetLinkMercurialChangeSet() throws IOException {
        testGetChangeSetLinkMercurialChangeSet(REPO_URL+ "/History/6704efde8754");
    }
    
    @Test
    public void testGetFileLink() throws IOException {
        testGetFileLink(REPO_URL + "/File/src/main/java/hudson/plugins/mercurial/browser/HgBrowser.java?rev=6704efde8754");
    }

    @Test
    public void testGetDiffLink() throws IOException {        
        testGetDiffLink(REPO_URL+ "/History/6704efde8754");
    }
}
