package hudson.plugins.mercurial.browser;

import org.junit.jupiter.api.Test;

class RhodeCodeLegacyTest extends AbstractBrowserTestBase {

    private static final String REPO_URL = "http://www.example.org/hg/repos";

    @Override
    protected HgBrowser getBrowser() throws Exception {
        return new RhodeCodeLegacy(REPO_URL);
    }

    @Test
    void testGetChangeSetLinkMercurialChangeSet() throws Exception {
        testGetChangeSetLinkMercurialChangeSet(REPO_URL
                + "/changeset/6704efde87541766fadba17f66d04b926cd4d343");
    }

    @Test
    void testGetFileLink() throws Exception {
        testGetFileLink(REPO_URL
                + "/files/6704efde87541766fadba17f66d04b926cd4d343/src/main/java/hudson/plugins/mercurial/browser/HgBrowser.java");
    }

    @Test
    void testGetDiffLink() throws Exception {
        testGetDiffLink(REPO_URL
                + "/changeset/6704efde87541766fadba17f66d04b926cd4d343#CHANGE-src-main-java-hudson-plugins-mercurial-browser-HgBrowser.java");
    }
}
