package hudson.plugins.mercurial;

import org.htmlunit.Page;
import org.htmlunit.WebResponse;
import org.htmlunit.util.NameValuePair;
import hudson.FilePath;
import hudson.model.FreeStyleProject;
import hudson.model.Item;
import hudson.model.Slave;
import hudson.triggers.SCMTrigger;
import jenkins.model.Jenkins;
import org.hamcrest.Matcher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.MockAuthorizationStrategy;
import org.jvnet.hudson.test.SleepBuilder;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.collection.ArrayMatching.hasItemInArray;
import static org.hamcrest.collection.IsIterableContainingInAnyOrder.containsInAnyOrder;
import static org.hamcrest.core.IsNot.not;
import static org.hamcrest.core.StringContains.containsString;
import static org.hamcrest.core.StringEndsWith.endsWith;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@Testcontainers(disabledWithoutDocker = true)
@WithJenkins
class MercurialStatusPermissionTest {

    private JenkinsRule j;
    private MercurialTestUtil m;
    @Container
    private static final MercurialContainer container = new MercurialContainer();

    private final String[] names = new String[]{"one1", "two2", "three3"};
    private final String carls = names[1];
    private String source;

    @BeforeEach
    void beforeEach(JenkinsRule rule) throws Exception {
        j = rule;
        m = new MercurialTestUtil(j);

        m.hg("version"); // test environment needs to be able to run Mercurial
        Slave agent = container.createAgent(j);
        m.withNode(agent);
        MercurialInstallation inst = container.createInstallation(j, MercurialContainer.Version.HG6, false, false, false, "", agent);
        assertNotNull(inst);
        m.withInstallation(inst);
        FilePath sampleRepo = agent.getRootPath().child("sampleRepo");
        sampleRepo.mkdirs();
        m.hg(sampleRepo, "init");
        sampleRepo.child("a").write("a", "UTF-8");
        m.hg(sampleRepo, "commit", "--addremove", "--message=a-file");

        source = "ssh://test@" + container.getHost() + ":" + container.getMappedPort(22) + "/" + sampleRepo;

        FreeStyleProject p = j.createFreeStyleProject(names[0]);
        p.setScm(new MercurialSCM(
                inst.getName(),
                source,
                null, null, null, null, false));
        p.addTrigger(new SCMTrigger(""));
        p.getBuildersList().add(new SleepBuilder(1000));
        p = j.createFreeStyleProject(names[1]);
        FreeStyleProject carls = p;
        p.setScm(new MercurialSCM(
                inst.getName(),
                source,
                null, null, null, null, false));
        p.addTrigger(new SCMTrigger(""));
        p.getBuildersList().add(new SleepBuilder(1000));
        p = j.createFreeStyleProject(names[2]);
        p.setScm(new MercurialSCM(
                inst.getName(),
                source,
                null, null, null, null, false));
        p.addTrigger(new SCMTrigger(""));
        p.getBuildersList().add(new SleepBuilder(1000));

        j.jenkins.setSecurityRealm(j.createDummySecurityRealm());
        j.jenkins.setAuthorizationStrategy(new MockAuthorizationStrategy()
                .grant(Item.READ).everywhere().to("bob")
                .grant(Item.READ).onItems(carls).to("carl")
                .grant(Jenkins.ADMINISTER).everywhere().to("alice")
                .grant(Jenkins.READ).onRoot().toAuthenticated());
    }

    @Test
    void testTriggeredWithAnonymous() throws Exception {
        final Page page = j.createWebClient().goTo("mercurial/notifyCommit?url=" + source, "text/plain");
        final WebResponse response = page.getWebResponse();
        assertEquals(200, response.getStatusCode());
        final List<NameValuePair> headers = response.getResponseHeaders();
        final List<NameValuePair> triggered = headers.stream().filter(nvp -> nvp.getName().equals("Triggered")).toList();
        assertEquals(3, triggered.size(), "No headers!");
        List<String> headerValues = triggered.stream().map(NameValuePair::getValue).collect(Collectors.toList());
        final String content = response.getContentAsString();
        assertThat(content, containsString("Scheduled polling of a job"));
        assertThat(headerValues.toArray(new String[0]), hasItemInArray(containsString("Something")));

        List<Matcher<? super String>> testHeaders = new ArrayList<>();
        for (String name : names) {
            assertThat(content, not(containsString(name)));
            testHeaders.add(not(endsWith(name + "/")));
        }

        assertThat(headerValues, containsInAnyOrder(testHeaders));
    }

    @Test
    void testTriggeredWithAllReadable() throws Exception {
        final Page page = j.createWebClient().login("bob").goTo("mercurial/notifyCommit?url=" + source, "text/plain");
        final WebResponse response = page.getWebResponse();
        assertEquals(200, response.getStatusCode());
        final List<NameValuePair> headers = response.getResponseHeaders();
        final List<NameValuePair> triggered = headers.stream().filter(nvp -> nvp.getName().equals("Triggered")).toList();
        assertEquals(3, triggered.size(), "No headers!");
        List<String> headerValues = triggered.stream().map(NameValuePair::getValue).collect(Collectors.toList());
        final String content = response.getContentAsString();
        assertThat(content, containsString("Scheduled polling of"));
        assertThat(content, not(containsString("Scheduled polling of a job")));
        assertThat(headerValues.toArray(new String[0]), not(hasItemInArray(containsString("Something"))));

        List<Matcher<? super String>> testHeaders = new ArrayList<>();
        for (String name : names) {
            assertThat(content, containsString(name));
            testHeaders.add(endsWith(name + "/"));
        }

        assertThat(headerValues, containsInAnyOrder(testHeaders));
    }

    @Test
    void testTriggeredWithOneReadable() throws Exception {
        final Page page = j.createWebClient().login("carl").goTo("mercurial/notifyCommit?url=" + source, "text/plain");
        final WebResponse response = page.getWebResponse();
        assertEquals(200, response.getStatusCode());
        final List<NameValuePair> headers = response.getResponseHeaders();
        final List<NameValuePair> triggered = headers.stream().filter(nvp -> nvp.getName().equals("Triggered")).toList();
        assertEquals(3, triggered.size(), "No headers!");
        List<String> headerValues = triggered.stream().map(NameValuePair::getValue).collect(Collectors.toList());
        final String content = response.getContentAsString();
        assertThat(content, containsString("Scheduled polling of"));
        assertThat(content, containsString("Scheduled polling of a job"));
        assertThat(headerValues.toArray(new String[0]), hasItemInArray(containsString("Something")));

        List<Matcher<? super String>> testHeaders = new ArrayList<>();
        for (String name : names) {
            if (name.equals(carls)) {
                assertThat(content, containsString(name));
                testHeaders.add(endsWith(name + "/"));
            } else {
                assertThat(content, not(containsString(name)));
                testHeaders.add(not(endsWith(name + "/")));
            }
        }

        assertThat(headerValues, containsInAnyOrder(testHeaders));
    }
}
