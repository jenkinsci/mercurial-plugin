/*
 * The MIT License
 *
 * Copyright (c) 2012, Sebastian Sdorra
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package hudson.plugins.mercurial;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.stream.Collectors;

import hudson.FilePath;
import hudson.model.FreeStyleProject;
import hudson.model.Slave;
import hudson.triggers.SCMTrigger;
import org.apache.commons.lang.StringUtils;
import org.jenkinsci.test.acceptance.docker.DockerClassRule;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.SleepBuilder;

import static hudson.plugins.mercurial.MercurialStatus.MAX_REPORTED_PROJECTS;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.*;

import com.gargoylesoftware.htmlunit.Page;
import com.gargoylesoftware.htmlunit.WebResponse;
import com.gargoylesoftware.htmlunit.util.NameValuePair;

/**
 *
 * @author Sebastian Sdorra
 */
public class MercurialStatusTest {

    @Rule public JenkinsRule j = new JenkinsRule();
    @Rule public MercurialRule m = new MercurialRule(j);
    @ClassRule public static DockerClassRule<MercurialContainer> docker = new DockerClassRule<>(MercurialContainer.class);

    @Test public void testLooselyMatches() throws URISyntaxException {
      assertTrue( MercurialStatus.looselyMatches(new URI("ssh://somehost/"), "ssh://somehost"));
      assertTrue( MercurialStatus.looselyMatches(new URI("http://somehost"), "http://somehost/"));
      assertTrue( MercurialStatus.looselyMatches(new URI("http://somehost:80/"), "http://somehost/"));
      assertTrue( MercurialStatus.looselyMatches(new URI("http://somehost"), "http://somehost:80/"));
      assertTrue( MercurialStatus.looselyMatches(new URI("https://somehost:443"), "https://somehost/"));
      assertTrue( MercurialStatus.looselyMatches(new URI("https://somehost/"), "https://somehost:443/"));
      assertTrue( MercurialStatus.looselyMatches(new URI("https://somehost:443/"), "https://somehost:443/"));
      assertTrue( MercurialStatus.looselyMatches(new URI("http://somehost/jenkins"), "http://somehost/jenkins"));
      assertTrue( MercurialStatus.looselyMatches(new URI("http://somehost:80/jenkins"), "http://somehost:80/jenkins"));
      assertTrue( MercurialStatus.looselyMatches(new URI("https://somehost/jenkins"), "https://somehost/jenkins"));
      assertTrue( MercurialStatus.looselyMatches(new URI("https://somehost/jenkins?query=true"), "https://somehost/jenkins?query=true"));
      assertTrue( MercurialStatus.looselyMatches(new URI("https://somehost/jenkins/?query=some%20path"), "https://somehost/jenkins?query=some%20path"));
      assertTrue( MercurialStatus.looselyMatches(new URI("https://somehost/jenkins"), "https://user@somehost/jenkins"));
      assertTrue( MercurialStatus.looselyMatches(new URI("https://somehost/jenkins"), "https://user:password@somehost/jenkins"));
      assertTrue( MercurialStatus.looselyMatches(new URI("ssh://somehost/path"), "ssh://user:password@somehost:22/path"));

      assertFalse( MercurialStatus.looselyMatches(new URI("ssh://somehost/path"), "invalid/url") );
      assertFalse( MercurialStatus.looselyMatches(new URI("ssh://somehost/path"), "ssh://somehost/other/path") );
      assertFalse( MercurialStatus.looselyMatches(new URI("ssh://somehost/path"), "ssh://somehost/other/path") );
      assertFalse( MercurialStatus.looselyMatches(new URI("http://somehost/path"), "http://somehost/") );
      assertFalse( MercurialStatus.looselyMatches(new URI("http://somehost/path"), "http://somehost/path?query=test") );
      assertTrue( MercurialStatus.looselyMatches(new URI("/var/hg/stuff"), "/var/hg/stuff") );
      assertTrue( MercurialStatus.looselyMatches(new URI("file:///var/hg/stuff"), "/var/hg/stuff") );
      assertTrue( MercurialStatus.looselyMatches(new URI("file:/var/hg/stuff"), "/var/hg/stuff") );
      assertTrue( MercurialStatus.looselyMatches(new URI("/var/hg/stuff"), "file:/var/hg/stuff") );
      assertTrue( MercurialStatus.looselyMatches(new URI("/var/hg/stuff"), "file:///var/hg/stuff") );
      assertTrue( MercurialStatus.looselyMatches(new URI("file:///var/hg/stuff"), "file:///var/hg/stuff") );

      assertTrue( MercurialStatus.looselyMatches(new URI("http://somehost/"), "ssh://somehost/") );
      assertTrue( MercurialStatus.looselyMatches(new URI("https://somehost/"), "http://somehost/") );
      assertTrue( MercurialStatus.looselyMatches(new URI("ssh://somehost/"), "https://somehost/") );
      assertTrue( MercurialStatus.looselyMatches(new URI("http://somehost:80/"), "ssh://somehost:22/") );
      assertTrue( MercurialStatus.looselyMatches(new URI("https://somehost:443/"), "http://somehost:80/") );
      assertTrue( MercurialStatus.looselyMatches(new URI("ssh://somehost:22/"), "https://somehost:443/") );
      assertTrue( MercurialStatus.looselyMatches(new URI("http://somehost/path"), "ssh://somehost/path") );
      assertTrue( MercurialStatus.looselyMatches(new URI("https://somehost/path"), "http://somehost/path") );
      assertTrue( MercurialStatus.looselyMatches(new URI("ssh://somehost/path"), "https://somehost/path") );

      assertFalse( MercurialStatus.looselyMatches(new URI("http://scm.foocompany.com/hg/foocomponent/"), "${REPO_URL}") );
      assertFalse( MercurialStatus.looselyMatches(new URI("http://scm.foocompany.com/hg/foocomponent/"), "$REPO_URL") );
    }

    @Issue("JENKINS-12544")
    @Test public void testTriggeredHeadersAreLimited() throws Exception {
      m.hg("version"); // test environment needs to be able to run Mercurial
      MercurialContainer container = docker.create();
      Slave slave = container.createSlave(j);
      m.withNode(slave);
      MercurialInstallation inst = container.createInstallation(j, MercurialContainer.Version.HG5, false, false, false, "", slave);
      assertNotNull(inst);
      m.withInstallation(inst);
      FilePath sampleRepo = slave.getRootPath().child("sampleRepo");
      sampleRepo.mkdirs();
      m.hg(sampleRepo, "init");
      sampleRepo.child("a").write("a", "UTF-8");
      m.hg(sampleRepo, "commit", "--addremove", "--message=a-file");

      String source = "ssh://test@" + container.ipBound(22) + ":" + container.port(22) + "/" + sampleRepo;

      for (int i = 0; i < MAX_REPORTED_PROJECTS + 5; i++) {
        FreeStyleProject p = j.createFreeStyleProject("triggeredHeaderTest" + i);
        p.setScm(new MercurialSCM(
            inst.getName(),
            source,
            null, null, null, null, false));
        p.addTrigger(new SCMTrigger(""));
        p.getBuildersList().add(new SleepBuilder(1000));
      }

      final Page page = j.createWebClient().goTo("mercurial/notifyCommit?url=" + source, "text/plain");
      final WebResponse response = page.getWebResponse();
      assertEquals(200, response.getStatusCode());
      final List<NameValuePair> headers = response.getResponseHeaders();
      final List<NameValuePair> triggered = headers.stream().filter(nvp -> nvp.getName().equals("Triggered")).collect(Collectors.toList());
      assertEquals("No headers!", MAX_REPORTED_PROJECTS + 1, triggered.size());
      List<String> headerValues = triggered.stream().map(NameValuePair::getValue).collect(Collectors.toList());
      assertThat(headerValues.get(MAX_REPORTED_PROJECTS), is("<5 more>"));
      final String content = response.getContentAsString();
      assertThat(MAX_REPORTED_PROJECTS + 5, is(StringUtils.countMatches(content, "Scheduled polling of ")));
    }
}
