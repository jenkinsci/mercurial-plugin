package hudson.plugins.mercurial.browser;

import java.io.IOException;
import java.net.MalformedURLException;

import org.junit.Test;

public class RhodeCodeLegacyTest extends AbstractBrowserTestBase {

    private static final String REPO_URL = "http://www.example.org/hg/repos";

    public RhodeCodeLegacyTest() throws MalformedURLException {
        super(new RhodeCodeLegacy(REPO_URL));
    }

    @Test
    public void testGetChangeSetLinkMercurialChangeSet() throws IOException {
        testGetChangeSetLinkMercurialChangeSet(REPO_URL
                + "/changeset/6704efde87541766fadba17f66d04b926cd4d343");
    }

    @Test
    public void testGetFileLink() throws IOException {
        testGetFileLink(REPO_URL
                + "/files/6704efde87541766fadba17f66d04b926cd4d343/src/main/java/hudson/plugins/mercurial/browser/HgBrowser.java");
    }

    @Test
    public void testGetDiffLink() throws IOException {
        testGetDiffLink(REPO_URL
                + "/changeset/6704efde87541766fadba17f66d04b926cd4d343#CHANGE-src-main-java-hudson-plugins-mercurial-browser-HgBrowser.java");
    }
}
