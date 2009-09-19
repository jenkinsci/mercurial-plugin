/*
 * BitBucketTest.java 07.09.2009
 */
package hudson.plugins.mercurial.browser;

import java.io.IOException;
import java.net.MalformedURLException;

import org.junit.Test;

public class BitBucketTest extends AbstractBrowserTestBase {

    private static final String REPO_URL = "http://www.example.org/hg/repos";

    public BitBucketTest() throws MalformedURLException {
        super(new BitBucket(REPO_URL));        
    }

    @Test
    public void testGetChangeSetLinkMercurialChangeSet() throws IOException {
        testGetChangeSetLinkMercurialChangeSet(REPO_URL+ "/changeset/6704efde8754/");
    }
    
    @Test
    public void testGetFileLink() throws IOException {
        testGetFileLink(REPO_URL + "/src/6704efde8754/src/main/java/hudson/plugins/mercurial/browser/HgBrowser.java");
    }

    @Test
    public void testGetDiffLink() throws IOException {        
        testGetDiffLink(REPO_URL + "/changeset/6704efde8754/#chg-src/main/java/hudson/plugins/mercurial/browser/HgBrowser.java");
    }
}
