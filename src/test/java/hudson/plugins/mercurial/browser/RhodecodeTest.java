/*
 * RhodecodeTest.java 17.09.2011
 */
package hudson.plugins.mercurial.browser;
import static org.junit.Assert.assertEquals;
import hudson.plugins.mercurial.MercurialChangeSet;

import java.io.IOException;
import java.net.MalformedURLException;

import org.junit.Test;

public class RhodeCodeTest {

    private static final String REPO_URL = "https://secure.rhodecode.org/rhodecode";
    protected final HgBrowser browser;
    protected final MercurialChangeSet changeSet;

    public RhodeCodeTest() throws MalformedURLException {
        this.browser = new RhodeCode(REPO_URL);        
        changeSet = new MercurialChangeSet();
        changeSet.setNode("6f1439efaed6");
    }

    @Test(expected = IllegalStateException.class)
    public void testGetFileLinkIllegalState() throws IOException {
        browser.getFileLink("src/main/java/hudson/plugins/mercurial/browser/HgBrowser.java");
    }

    @Test(expected = IllegalStateException.class)
    public void testGetDiffLinkIllegalState() throws IOException {        
        browser.getDiffLink("src/main/java/hudson/plugins/mercurial/browser/HgBrowser.java");
    }

    /**
     * @param expected
     * @throws IOException
     * @throws MalformedURLException
     */
    @Test
    public void testGetFileLink() throws IOException, MalformedURLException {
        String expected = REPO_URL + "/files/6f1439efaed6/rhodecode/public/css/pygments.css";
        browser.getChangeSetLink(changeSet);
        assertEquals(expected, browser.getFileLink("rhodecode/public/css/pygments.css").toExternalForm());
    }

    /**
     * @param expected
     * @throws IOException
     * @throws MalformedURLException
     */
    @Test
    public void testGetDiffLink() throws IOException, MalformedURLException {
        String expected = REPO_URL + "/changeset/6f1439efaed6#Crhodecode-public-css-pygments.css";
        browser.getChangeSetLink(changeSet);
        assertEquals(expected, browser.getDiffLink("rhodecode/public/css/pygments.css").toExternalForm());
    }

    /**
     * @param expected
     * @throws IOException
     */
    @Test
    public void testGetChangeSetLinkMercurialChangeSet() throws IOException {
        String expected = REPO_URL + "/changeset/6f1439efaed6";
        assertEquals(expected, browser.getChangeSetLink(changeSet).toExternalForm());
    }
}

