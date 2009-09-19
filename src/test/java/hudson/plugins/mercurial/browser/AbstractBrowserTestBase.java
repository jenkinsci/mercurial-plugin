package hudson.plugins.mercurial.browser;

import static org.junit.Assert.assertEquals;
import hudson.plugins.mercurial.MercurialChangeSet;

import java.io.IOException;
import java.net.MalformedURLException;

import org.junit.Test;

public abstract class AbstractBrowserTestBase {

    protected final HgBrowser browser;
    protected final MercurialChangeSet changeSet;

    
    @SuppressWarnings("deprecation")
    public AbstractBrowserTestBase(HgBrowser browser) {
        this.browser = browser;
        changeSet = new MercurialChangeSet();
        changeSet.setNode("6704efde87541766fadba17f66d04b926cd4d343");

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
    protected void testGetFileLink(final String expected) throws IOException, MalformedURLException {
        browser.getChangeSetLink(changeSet);
        assertEquals(expected, browser.getFileLink("src/main/java/hudson/plugins/mercurial/browser/HgBrowser.java").toExternalForm());
    }

    /**
     * @param expected
     * @throws IOException
     * @throws MalformedURLException
     */
    protected void testGetDiffLink(final String expected) throws IOException, MalformedURLException {
        browser.getChangeSetLink(changeSet);
        assertEquals(expected, browser.getDiffLink("src/main/java/hudson/plugins/mercurial/browser/HgBrowser.java").toExternalForm());
    }

    /**
     * @param expected
     * @throws IOException
     */
    protected void testGetChangeSetLinkMercurialChangeSet(final String expected) throws IOException {
        assertEquals(expected, browser.getChangeSetLink(changeSet).toExternalForm());
    }

}
