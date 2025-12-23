package hudson.plugins.mercurial.browser;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;

import org.junit.jupiter.api.Test;

class RhodeCodeTest extends AbstractBrowserTestBase {

    private static final String REPO_URL = "https://secure.rhodecode.org/rhodecode";

    @Override
    protected HgBrowser getBrowser() throws Exception {
        return new RhodeCode(REPO_URL);
    }

    @Test
    void testGetFileLink() throws Exception {
        String expected = REPO_URL
                + "/files/6704efde87541766fadba17f66d04b926cd4d343/rhodecode/public/css/pygments.css";
        browser.getChangeSetLink(changeSet);
        assertEquals(expected,
                browser.getFileLink("rhodecode/public/css/pygments.css")
                        .toExternalForm());
    }

    @Test
    void testGetDiffLink() throws Exception {
        String expected = REPO_URL
                + "/changeset/6704efde87541766fadba17f66d04b926cd4d343#Crhodecode-public-css-pygments.css";
        browser.getChangeSetLink(changeSet);
        assertEquals(expected,
                browser.getDiffLink("rhodecode/public/css/pygments.css")
                        .toExternalForm());
    }

    @Test
    void testGetChangeSetLinkMercurialChangeSet() throws IOException {
        String expected = REPO_URL
                + "/changeset/6704efde87541766fadba17f66d04b926cd4d343";
        assertEquals(expected, browser.getChangeSetLink(changeSet)
                .toExternalForm());
    }
}
