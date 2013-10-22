package hudson.plugins.mercurial.browser;

import hudson.model.Descriptor;
import hudson.model.FreeStyleProject;
import hudson.plugins.mercurial.MercurialSCM;
import hudson.scm.RepositoryBrowser;
import java.net.URL;
import java.util.List;
import static org.junit.Assert.*;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.Bug;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.recipes.LocalData;

public class HgBrowserSelectionTest {

    @Rule public JenkinsRule j = new JenkinsRule();

    /**
     * With an introduction of HgBrowser base class, a care has to be taken to load existing dataset.
     *
     * This test verifies that.
     */
    @LocalData
    @Test public void repositoryBrowserCompatibility() throws Exception {
        FreeStyleProject p = (FreeStyleProject) j.jenkins.getItem("foo");
        MercurialSCM ms = (MercurialSCM) p.getScm();
        assertTrue(ms.getBrowser() instanceof HgWeb);
        j.assertEqualBeans(new HgWeb("http://www.yahoo.com/"), ms.getBrowser(), "url");
    }

    @Bug(4510)
    @LocalData
    @Test public void pickingUpAlternativeBrowser() throws Exception {
        FreeStyleProject p = (FreeStyleProject) j.jenkins.getItem("foo");
        MercurialSCM ms = (MercurialSCM) p.getScm();
        final HgBrowser browser = ms.getBrowser();
        assertEquals("wrong url", new URL("http://bitbucket.org/"), browser.getUrl());
        assertTrue("class:" + browser.getClass(), browser instanceof BitBucket);
        j.assertEqualBeans(new BitBucket("http://bitbucket.org/"), browser, "url");
    }

    @Bug(4514)
    @LocalData
    @Test public void browsersAvailableInDropDown() throws Exception {
        FreeStyleProject p = (FreeStyleProject) j.jenkins.getItem("foo");
        MercurialSCM ms = (MercurialSCM) p.getScm();
        final HgBrowser browser = ms.getBrowser();
        assertEquals("wrong url", new URL("http://bitbucket.org/"), browser.getUrl());
        assertTrue("class:" + browser.getClass(), browser instanceof BitBucket);
        j.assertEqualBeans(new BitBucket("http://bitbucket.org/"), browser, "url");
        final List<Descriptor<RepositoryBrowser<?>>> browserDescriptors = ms.getDescriptor().getBrowserDescriptors();
        assertTrue("Could not find BitBucket in " + browserDescriptors, browserDescriptors.contains(browser.getDescriptor()));
    }

    @Bug(20186)
    @Test public void configureBrowser() throws Exception {
        FreeStyleProject p = j.createFreeStyleProject();
        p.setScm(new MercurialSCM(null, "https://host/repo", null, null, null, new HgWeb("https://host/repo"), false, null));
        j.configRoundtrip(p);
        assertEquals("https://host/repo/", ((MercurialSCM) p.getScm()).getBrowser().getUrl().toString());
    }

}
