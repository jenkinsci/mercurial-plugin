package hudson.plugins.mercurial.browser;

import hudson.model.Descriptor;
import hudson.model.FreeStyleProject;
import hudson.plugins.mercurial.MercurialSCM;
import hudson.scm.RepositoryBrowser;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;
import org.jvnet.hudson.test.recipes.LocalData;

@WithJenkins
class HgBrowserSelectionTest {

    private JenkinsRule j;

    @BeforeEach
    void beforeEach(JenkinsRule rule) {
        j = rule;
    }

    /**
     * With an introduction of HgBrowser base class, a care has to be taken to load existing dataset.
     *
     * This test verifies that.
     */
    @LocalData
    @Test
    void repositoryBrowserCompatibility() throws Exception {
        FreeStyleProject p = (FreeStyleProject) j.jenkins.getItem("foo");
        MercurialSCM ms = (MercurialSCM) p.getScm();
        RepositoryBrowser<?> browser = ms.getEffectiveBrowser();
        assertInstanceOf(HgWeb.class, browser, String.valueOf(browser));
        j.assertEqualBeans(new HgWeb("http://www.yahoo.com/"), browser, "url");
    }

    @Issue("JENKINS-4510")
    @LocalData
    @Test
    void pickingUpAlternativeBrowser() throws Exception {
        FreeStyleProject p = (FreeStyleProject) j.jenkins.getItem("foo");
        MercurialSCM ms = (MercurialSCM) p.getScm();
        final HgBrowser browser = ms.getBrowser();
        assertEquals("http://code.google.com/p/xxx/source/", browser.getUrl().toString(), "wrong url");
        assertInstanceOf(GoogleCode.class, browser, "class:" + browser.getClass());
        j.assertEqualBeans(new GoogleCode("http://code.google.com/p/xxx/source/"), browser, "url");
    }

    @Issue("JENKINS-4514")
    @LocalData
    @Test
    void browsersAvailableInDropDown() throws Exception {
        FreeStyleProject p = (FreeStyleProject) j.jenkins.getItem("foo");
        MercurialSCM ms = (MercurialSCM) p.getScm();
        final HgBrowser browser = ms.getBrowser();
        assertEquals("http://code.google.com/p/xxx/source/", browser.getUrl().toString(), "wrong url");
        assertInstanceOf(GoogleCode.class, browser, "class:" + browser.getClass());
        j.assertEqualBeans(new GoogleCode("http://code.google.com/p/xxx/source/"), browser, "url");
        final List<Descriptor<RepositoryBrowser<?>>> browserDescriptors = ms.getDescriptor().getBrowserDescriptors();
        assertTrue(browserDescriptors.contains(browser.getDescriptor()), "Could not find GoogleCode in " + browserDescriptors);
    }

    @Issue("JENKINS-20186")
    @Test
    void configureBrowser() throws Exception {
        FreeStyleProject p = j.createFreeStyleProject();
        p.setScm(new MercurialSCM(null, "https://host/repo", null, null, null, new HgWeb("https://host/repo"), false, null));
        j.configRoundtrip(p);
        assertEquals("https://host/repo/", ((MercurialSCM) p.getScm()).getBrowser().getUrl().toString());
    }

}
