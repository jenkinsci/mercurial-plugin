package hudson.plugins.mercurial.browser;

import hudson.model.Descriptor;
import hudson.model.FreeStyleProject;
import hudson.plugins.mercurial.MercurialSCM;
import hudson.scm.RepositoryBrowser;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.List;
import org.jvnet.hudson.test.Bug;
import org.jvnet.hudson.test.HudsonTestCase;
import org.jvnet.hudson.test.recipes.LocalData;

public class HgBrowserSelectionTest extends HudsonTestCase {

    public HgBrowserSelectionTest(String n) {
        super(n);
    }

    /**
     * With an introduction of HgBrowser base class, a care has to be taken to load existing dataset.
     *
     * This test verifies that.
     */
    @LocalData
    public void testRepositoryBrowserCompatibility() throws Exception {
        FreeStyleProject p = (FreeStyleProject) hudson.getItem("foo");
        MercurialSCM ms = (MercurialSCM) p.getScm();
        assertTrue(ms.getBrowser() instanceof HgWeb);
        assertEqualBeans(new HgWeb("http://www.yahoo.com/"), ms.getBrowser(), "url");
    }

    @Bug(4510)
    @LocalData
    public void testPickingUpAlternativeBrowser() throws MalformedURLException, Exception {
        FreeStyleProject p = (FreeStyleProject) hudson.getItem("foo");
        MercurialSCM ms = (MercurialSCM) p.getScm();
        final HgBrowser browser = ms.getBrowser();
        assertEquals("wrong url", new URL("http://bitbucket.org/"), browser.getUrl());
        assertTrue("class:" + browser.getClass(), browser instanceof BitBucket);
        assertEqualBeans(new BitBucket("http://bitbucket.org/"), browser, "url");
    }

    @Bug(4514)
    @LocalData
    public void testBrowsersAvailableInDropDown() throws MalformedURLException, Exception {
        FreeStyleProject p = (FreeStyleProject) hudson.getItem("foo");
        MercurialSCM ms = (MercurialSCM) p.getScm();
        final HgBrowser browser = ms.getBrowser();
        assertEquals("wrong url", new URL("http://bitbucket.org/"), browser.getUrl());
        assertTrue("class:" + browser.getClass(), browser instanceof BitBucket);
        assertEqualBeans(new BitBucket("http://bitbucket.org/"), browser, "url");
        final List<Descriptor<RepositoryBrowser<?>>> browserDescriptors = ms.getDescriptor().getBrowserDescriptors();
        assertTrue("Could not find BitBucket in " + browserDescriptors, browserDescriptors.contains(browser.getDescriptor()));
    }

}
