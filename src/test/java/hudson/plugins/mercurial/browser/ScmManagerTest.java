package hudson.plugins.mercurial.browser;

import org.junit.Test;

import java.io.IOException;
import java.net.MalformedURLException;

public class ScmManagerTest extends AbstractBrowserTestBase {

  private static final String REPO_URL = "https://scm.hitchhiker.com/repo/spaceships/hog";

  public ScmManagerTest() throws MalformedURLException {
    super(new ScmManager(REPO_URL));
  }

  @Test
  public void testGetChangeSetLinkMercurialChangeSet() throws IOException {
    testGetChangeSetLinkMercurialChangeSet(REPO_URL+ "/code/changeset/6704efde87541766fadba17f66d04b926cd4d343");
  }

  @Test
  public void testGetFileLink() throws IOException {
    testGetFileLink(REPO_URL + "/code/sources/6704efde87541766fadba17f66d04b926cd4d343/src/main/java/hudson/plugins/mercurial/browser/HgBrowser.java");
  }

  @Test
  public void testGetDiffLink() throws IOException {
    testGetDiffLink(REPO_URL + "/code/changeset/6704efde87541766fadba17f66d04b926cd4d343/#diff-src/main/java/hudson/plugins/mercurial/browser/HgBrowser.java");
  }
}
