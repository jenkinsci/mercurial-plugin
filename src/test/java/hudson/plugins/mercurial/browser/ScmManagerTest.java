package hudson.plugins.mercurial.browser;

import org.junit.jupiter.api.Test;

class ScmManagerTest extends AbstractBrowserTestBase {

    private static final String REPO_URL = "https://scm.hitchhiker.com/repo/spaceships/hog";

    @Override
    protected HgBrowser getBrowser() throws Exception {
        return new ScmManager(REPO_URL);
    }

    @Test
    void testGetChangeSetLinkMercurialChangeSet() throws Exception {
        testGetChangeSetLinkMercurialChangeSet(REPO_URL + "/code/changeset/6704efde87541766fadba17f66d04b926cd4d343");
    }

    @Test
    void testGetFileLink() throws Exception {
        testGetFileLink(REPO_URL + "/code/sources/6704efde87541766fadba17f66d04b926cd4d343/src/main/java/hudson/plugins/mercurial/browser/HgBrowser.java");
    }

    @Test
    void testGetDiffLink() throws Exception {
        testGetDiffLink(REPO_URL + "/code/changeset/6704efde87541766fadba17f66d04b926cd4d343/#diff-src/main/java/hudson/plugins/mercurial/browser/HgBrowser.java");
    }
}
