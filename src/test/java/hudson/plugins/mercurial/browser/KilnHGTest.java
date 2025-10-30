/*
 * KilnHGTest.java 25.03.2010
 */
package hudson.plugins.mercurial.browser;

import org.junit.jupiter.api.Test;

class KilnHGTest extends AbstractBrowserTestBase {

    private static final String REPO_URL = "https://example.kilnhg.com/Repo/Repositories/Group/hg-repo";

    @Override
    protected HgBrowser getBrowser() throws Exception {
        return new KilnHG(REPO_URL);
    }

    @Test
    void testGetChangeSetLinkMercurialChangeSet() throws Exception {
        testGetChangeSetLinkMercurialChangeSet(REPO_URL+ "/History/6704efde8754");
    }

    @Test
    void testGetFileLink() throws Exception {
        testGetFileLink(REPO_URL + "/File/src/main/java/hudson/plugins/mercurial/browser/HgBrowser.java?rev=6704efde8754");
    }

    @Test
    void testGetDiffLink() throws Exception {
        testGetDiffLink(REPO_URL+ "/History/6704efde8754");
    }
}
