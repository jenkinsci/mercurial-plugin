package hudson.plugins.mercurial.browser;

import org.junit.jupiter.api.Test;

class HgWebTest extends AbstractBrowserTestBase {
     
    private static final String REPO_URL = "http://hg.friedenhagen.net/index.cgi/mercurial-hudson";

    @Override
    protected HgBrowser getBrowser() throws Exception {
        return new HgWeb(REPO_URL);
    }

    @Test
    void testGetChangeSetLinkMercurialChangeSet() throws Exception {
        testGetChangeSetLinkMercurialChangeSet(REPO_URL + "/rev/6704efde8754");
    }

    @Test
    void testGetFileLink() throws Exception {
        testGetFileLink(REPO_URL + "/file/6704efde8754/src/main/java/hudson/plugins/mercurial/browser/HgBrowser.java");
    }

    @Test
    void testGetDiffLink() throws Exception {
        testGetDiffLink(REPO_URL + "/diff/6704efde8754/src/main/java/hudson/plugins/mercurial/browser/HgBrowser.java");
    }

}
