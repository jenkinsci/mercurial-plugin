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
import org.jenkinsci.test.acceptance.docker.DockerClassRule;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.MockAuthorizationStrategy;
import org.jvnet.hudson.test.SleepBuilder;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.beans.HasPropertyWithValue.hasProperty;
import static org.hamcrest.collection.ArrayMatching.hasItemInArray;
import static org.hamcrest.collection.IsIterableContainingInAnyOrder.containsInAnyOrder;
import static org.hamcrest.core.IsNot.not;
import static org.hamcrest.core.StringContains.containsString;
import static org.hamcrest.core.StringEndsWith.endsWith;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class MercurialStatusPermissionTest {

    @Rule public JenkinsRule j = new JenkinsRule();
    @Rule public MercurialRule m = new MercurialRule(j);
    @ClassRule
    public static DockerClassRule<MercurialContainer> docker = new DockerClassRule<>(MercurialContainer.class);

    private final String[] names = new String[]{"one1", "two2", "three3"};
    private final String carls = names[1];
    private String source;

    @Before
    public void setup() throws Exception {
        m.hg("version"); // test environment needs to be able to run Mercurial
        MercurialContainer container = docker.create();
        Slave slave = container.createSlave(j);
        m.withNode(slave);
        MercurialInstallation inst = container.createInstallation(j, MercurialContainer.Version.HG6, false, false, false, "", slave);
        assertNotNull(inst);
        m.withInstallation(inst);
        FilePath sampleRepo = slave.getRootPath().child("sampleRepo");
        sampleRepo.mkdirs();
        m.hg(sampleRepo, "init");
        sampleRepo.child("a").write("a", "UTF-8");
        m.hg(sampleRepo, "commit", "--addremove", "--message=a-file");

        source = "ssh://test@" + container.ipBound(22) + ":" + container.port(22) + "/" + sampleRepo;

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
    public void testTriggeredWithAnonymous() throws Exception {
        final Page page = j.createWebClient().goTo("mercurial/notifyCommit?url=" + source, "text/plain");
        final WebResponse response = page.getWebResponse();
        assertEquals(200, response.getStatusCode());
        final List<NameValuePair> headers = response.getResponseHeaders();
        final List<NameValuePair> triggered = headers.stream().filter(nvp -> nvp.getName().equals("Triggered")).collect(Collectors.toList());
        assertEquals("No headers!", 3, triggered.size());
        List<String> headerValues = triggered.stream().map(NameValuePair::getValue).collect(Collectors.toList());
        final String content = response.getContentAsString();
        assertThat(content, containsString("Scheduled polling of a job"));
        assertThat(headerValues.toArray(new String[headerValues.size()]), hasItemInArray(containsString("Something")));

        List<Matcher<? super String>> testHeaders = new ArrayList<>();
        for (String name : names) {
            assertThat(content, not(containsString(name)));
            testHeaders.add(not(endsWith(name + "/")));
        }

        assertThat(headerValues, containsInAnyOrder(testHeaders));
    }

    @Test
    public void testTriggeredWithAllReadable() throws Exception {
        final Page page = j.createWebClient().login("bob").goTo("mercurial/notifyCommit?url=" + source, "text/plain");
        final WebResponse response = page.getWebResponse();
        assertEquals(200, response.getStatusCode());
        final List<NameValuePair> headers = response.getResponseHeaders();
        final List<NameValuePair> triggered = headers.stream().filter(nvp -> nvp.getName().equals("Triggered")).collect(Collectors.toList());
        assertEquals("No headers!", 3, triggered.size());
        List<String> headerValues = triggered.stream().map(NameValuePair::getValue).collect(Collectors.toList());
        final String content = response.getContentAsString();
        assertThat(content, containsString("Scheduled polling of"));
        assertThat(content, not(containsString("Scheduled polling of a job")));
        assertThat(headerValues.toArray(new String[headerValues.size()]), not(hasItemInArray(containsString("Something"))));

        List<Matcher<? super String>> testHeaders = new ArrayList<>();
        for (String name : names) {
            assertThat(content, containsString(name));
            testHeaders.add(endsWith(name + "/"));
        }

        assertThat(headerValues, containsInAnyOrder(testHeaders));
    }

    @Test
    public void testTriggeredWithOneReadable() throws Exception {
        final Page page = j.createWebClient().login("carl").goTo("mercurial/notifyCommit?url=" + source, "text/plain");
        final WebResponse response = page.getWebResponse();
        assertEquals(200, response.getStatusCode());
        final List<NameValuePair> headers = response.getResponseHeaders();
        final List<NameValuePair> triggered = headers.stream().filter(nvp -> nvp.getName().equals("Triggered")).collect(Collectors.toList());
        assertEquals("No headers!", 3, triggered.size());
        List<String> headerValues = triggered.stream().map(NameValuePair::getValue).collect(Collectors.toList());
        final String content = response.getContentAsString();
        assertThat(content, containsString("Scheduled polling of"));
        assertThat(content, containsString("Scheduled polling of a job"));
        assertThat(headerValues.toArray(new String[headerValues.size()]), hasItemInArray(containsString("Something")));

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
