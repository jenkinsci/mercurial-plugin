/*
 * The MIT License
 *
 * Copyright 2014 Jesse Glick.
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
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package hudson.plugins.mercurial;

import com.cloudbees.plugins.credentials.Credentials;
import com.cloudbees.plugins.credentials.CredentialsNameProvider;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.CredentialsScope;
import com.cloudbees.plugins.credentials.domains.Domain;
import com.cloudbees.plugins.credentials.impl.UsernamePasswordCredentialsImpl;
import hudson.EnvVars;
import hudson.XmlFile;
import hudson.model.FreeStyleProject;
import hudson.model.Item;
import hudson.model.Job;
import hudson.model.User;
import hudson.security.ACL;
import hudson.security.AuthorizationMatrixProperty;
import hudson.security.ProjectMatrixAuthorizationStrategy;
import hudson.util.ListBoxModel;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import jenkins.model.Jenkins;
import org.junit.Test;
import static org.junit.Assert.*;
import org.junit.Rule;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.MockAuthorizationStrategy;

public class ConfigurationTest {

    @Rule public JenkinsRule r = new JenkinsRule();

    @Test public void configRoundTrip() throws Exception {
        FreeStyleProject p = r.createFreeStyleProject();
        MercurialSCM scm = new MercurialSCM("http://repo/");
        assertEquals("default", scm.getRevision());
        assertEquals(MercurialSCM.RevisionType.BRANCH, scm.getRevisionType());
        assertFalse(scm.isClean());
        assertNull(scm.getCredentialsId());
        // Etc., the defaults
        scm.setClean(true);
        scm.setRevisionType(MercurialSCM.RevisionType.TAG);
        scm.setRevision("LATEST");
        UsernamePasswordCredentialsImpl c = new UsernamePasswordCredentialsImpl(CredentialsScope.GLOBAL, null, "test", "bob", "s3cr3t");
        CredentialsProvider.lookupStores(r.jenkins).iterator().next().addCredentials(Domain.global(), c);
        scm.setCredentialsId(c.getId());
        scm.setModules("src");
        scm.setSubdir("checkout");
        r.jenkins.getDescriptorByType(MercurialInstallation.DescriptorImpl.class).setInstallations(new MercurialInstallation[] {new MercurialInstallation("caching", null, "hg", false, true, false, null, null)});
        scm.setInstallation("caching");
        p.setScm(scm);
        XmlFile xml = p.getConfigFile();
        System.out.println(xml.asString());
        r.configRoundtrip(p);
        System.out.println(xml.asString());
        scm = (MercurialSCM) p.getScm();
        assertEquals("http://repo/", scm.getSource());
        assertTrue(scm.isClean());
        assertEquals(MercurialSCM.RevisionType.TAG, scm.getRevisionType());
        assertEquals("LATEST", scm.getRevision());
        assertEquals(c.getId(), scm.getCredentialsId());
        assertEquals("s3cr3t", ((UsernamePasswordCredentialsImpl) scm.getCredentials(p, new EnvVars())).getPassword().getPlainText());
        assertEquals("src", scm.getModules());
        assertEquals("checkout", scm.getSubdir());
        assertEquals("caching", scm.getInstallation());
        // Did not explicitly set this one:
        assertFalse(scm.isDisableChangeLog());
    }

    @Test public void doFillCredentialsIdItemsWithoutJobWhenAdmin() throws Exception {
        r.jenkins.setSecurityRealm(r.createDummySecurityRealm());
        ProjectMatrixAuthorizationStrategy as = new ProjectMatrixAuthorizationStrategy();
        as.add(Jenkins.ADMINISTER, "alice");
        r.jenkins.setAuthorizationStrategy(as);
        final UsernamePasswordCredentialsImpl c = new UsernamePasswordCredentialsImpl(CredentialsScope.GLOBAL, null, "test", "bob", "s3cr3t");
        CredentialsProvider.lookupStores(r.jenkins).iterator().next().addCredentials(Domain.global(), c);
        ACL.impersonate(User.get("alice").impersonate(), new Runnable() {
            @Override public void run() {
                ListBoxModel options = r.jenkins.getDescriptorByType(MercurialSCM.DescriptorImpl.class).doFillCredentialsIdItems(null, "http://nowhere.net/");
                assertEquals(CredentialsNameProvider.name(c), options.get(1).name);
            }
        });
    }

    @Issue("SECURITY-158")
    @Test public void doFillCredentialsIdItems() throws Exception {
        r.jenkins.setSecurityRealm(r.createDummySecurityRealm());
        ProjectMatrixAuthorizationStrategy as = new ProjectMatrixAuthorizationStrategy();
        as.add(Jenkins.READ, "alice");
        as.add(Jenkins.READ, "bob");
        r.jenkins.setAuthorizationStrategy(as);
        FreeStyleProject p1 = r.createFreeStyleProject("p1");
        FreeStyleProject p2 = r.createFreeStyleProject("p2");
        p2.addProperty(new AuthorizationMatrixProperty(Collections.singletonMap(Item.CONFIGURE, Collections.singleton("bob"))));
        UsernamePasswordCredentialsImpl c = new UsernamePasswordCredentialsImpl(CredentialsScope.GLOBAL, null, "test", "bob", "s3cr3t");
        CredentialsProvider.lookupStores(r.jenkins).iterator().next().addCredentials(Domain.global(), c);
        assertCredentials("alice", null);
        assertCredentials("alice", p1);
        assertCredentials("alice", p2);
        assertCredentials("bob", null);
        assertCredentials("bob", p1);
        assertCredentials("bob", p2, c);
    }
    private void assertCredentials(String user, final Job<?,?> owner, Credentials... expected) {
        final List<String> expectedNames = new ArrayList<String>();
        for (Credentials c : expected) {
            expectedNames.add(CredentialsNameProvider.name(c));
        }
        ACL.impersonate(User.get(user).impersonate(), new Runnable() {
            @Override public void run() {
                List<String> actualNames = new ArrayList<String>();
                for (ListBoxModel.Option o : r.jenkins.getDescriptorByType(MercurialSCM.DescriptorImpl.class).doFillCredentialsIdItems(owner, "http://nowhere.net/")) {
                    if (o.value.isEmpty()) {
                        continue; // AbstractIdCredentialsListBoxModel.EmptySelection
                    }
                    actualNames.add(o.name);
                }
                assertEquals(expectedNames, actualNames);
            }
        });
    }

}
