package hudson.plugins.mercurial.browser;

import static org.junit.Assert.assertEquals;

import java.io.IOException;
import java.net.MalformedURLException;

import org.junit.Test;

public class RhodeCodeTest extends AbstractBrowserTestBase {

    private static final String REPO_URL = "https://secure.rhodecode.org/rhodecode";

    public RhodeCodeTest() throws MalformedURLException {
        super(new RhodeCode(REPO_URL));
    }

    @Test(expected = IllegalStateException.class)
    public void testGetFileLinkIllegalState() throws IOException {
        browser.getFileLink("src/main/java/hudson/plugins/mercurial/browser/HgBrowser.java");
    }

    @Test(expected = IllegalStateException.class)
    public void testGetDiffLinkIllegalState() throws IOException {
        browser.getDiffLink("src/main/java/hudson/plugins/mercurial/browser/HgBrowser.java");
    }

    @Test
    public void testGetFileLink() throws IOException, MalformedURLException {
        String expected = REPO_URL
                + "/files/6704efde87541766fadba17f66d04b926cd4d343/rhodecode/public/css/pygments.css";
        browser.getChangeSetLink(changeSet);
        assertEquals(expected,
                browser.getFileLink("rhodecode/public/css/pygments.css")
                        .toExternalForm());
    }

    @Test
    public void testGetDiffLink() throws IOException, MalformedURLException {
        String expected = REPO_URL
                + "/changeset/6704efde87541766fadba17f66d04b926cd4d343#Crhodecode-public-css-pygments.css";
        browser.getChangeSetLink(changeSet);
        assertEquals(expected,
                browser.getDiffLink("rhodecode/public/css/pygments.css")
                        .toExternalForm());
    }

    @Test
    public void testGetChangeSetLinkMercurialChangeSet() throws IOException {
        String expected = REPO_URL
                + "/changeset/6704efde87541766fadba17f66d04b926cd4d343";
        assertEquals(expected, browser.getChangeSetLink(changeSet)
                .toExternalForm());
    }
}
