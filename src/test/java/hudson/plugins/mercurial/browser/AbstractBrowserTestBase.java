package hudson.plugins.mercurial.browser;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import hudson.plugins.mercurial.MercurialChangeSet;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

abstract class AbstractBrowserTestBase {

    protected HgBrowser browser;
    protected MercurialChangeSet changeSet;

    @BeforeEach
    void beforeEach() throws Exception {
        browser = getBrowser();
        changeSet = new MercurialChangeSet();
        changeSet.setNode("6704efde87541766fadba17f66d04b926cd4d343");
    }

    protected abstract HgBrowser getBrowser() throws Exception;

    @Test
    void testGetFileLinkIllegalState() {
        assertThrows(IllegalStateException.class, () ->
            browser.getFileLink("src/main/java/hudson/plugins/mercurial/browser/HgBrowser.java"));
    }

    @Test
    void testGetDiffLinkIllegalState() {
        assertThrows(IllegalStateException.class, () ->
            browser.getDiffLink("src/main/java/hudson/plugins/mercurial/browser/HgBrowser.java"));
    }

    protected void testGetFileLink(final String expected) throws Exception {
        browser.getChangeSetLink(changeSet);
        assertEquals(expected, browser.getFileLink("src/main/java/hudson/plugins/mercurial/browser/HgBrowser.java").toExternalForm());
    }

    protected void testGetDiffLink(final String expected) throws Exception {
        browser.getChangeSetLink(changeSet);
        assertEquals(expected, browser.getDiffLink("src/main/java/hudson/plugins/mercurial/browser/HgBrowser.java").toExternalForm());
    }

    protected void testGetChangeSetLinkMercurialChangeSet(final String expected) throws Exception {
        assertEquals(expected, browser.getChangeSetLink(changeSet).toExternalForm());
    }

}
