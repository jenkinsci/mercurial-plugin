package hudson.plugins.mercurial.browser;

import org.junit.jupiter.api.Test;

class FishEyeTest extends AbstractBrowserTestBase {

    private static final String REPO_URL = "http://www.example.org/browse/hg/";

    @Override
    protected HgBrowser getBrowser() throws Exception {
        return new FishEye(REPO_URL);
    }

    @Test
    void testGetChangeSetLinkMercurialChangeSet() throws Exception {
        testGetChangeSetLinkMercurialChangeSet("http://www.example.org/changelog/hg?cs=6704efde87541766fadba17f66d04b926cd4d343");
    }

    @Test
    void testGetFileLink() throws Exception {
        testGetFileLink(REPO_URL + "src/main/java/hudson/plugins/mercurial/browser/HgBrowser.java#6704efde87541766fadba17f66d04b926cd4d343");
    }

    @Test
    void testGetDiffLink() throws Exception {
        testGetDiffLink(REPO_URL + "src/main/java/hudson/plugins/mercurial/browser/HgBrowser.java?r1=6704efde87541766fadba17f66d04b926cd4d343&r2=");
    }
}
