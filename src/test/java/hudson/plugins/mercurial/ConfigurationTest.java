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
import hudson.model.AbstractProject;
import hudson.model.FreeStyleProject;
import hudson.model.Item;
import hudson.model.User;
import hudson.security.ACL;
import hudson.security.AuthorizationMatrixProperty;
import hudson.security.ProjectMatrixAuthorizationStrategy;
import hudson.util.ListBoxModel;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import jenkins.model.Jenkins;
import org.acegisecurity.context.SecurityContext;
import org.acegisecurity.context.SecurityContextHolder;
import org.junit.Test;
import static org.junit.Assert.*;
import org.junit.Rule;
import org.jvnet.hudson.test.JenkinsRule;

public class ConfigurationTest {

    @Rule public JenkinsRule r = new JenkinsRule();

    // SECURITY-158
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
    private void assertCredentials(String user, final AbstractProject<?,?> owner, Credentials... expected) {
        final List<String> expectedNames = new ArrayList<String>();
        for (Credentials c : expected) {
            expectedNames.add(CredentialsNameProvider.name(c));
        }
        SecurityContext orig = ACL.impersonate(User.get(user).impersonate());
        try {
            List<String> actualNames = new ArrayList<String>();
            for (ListBoxModel.Option o : r.jenkins.getDescriptorByType(MercurialSCM.DescriptorImpl.class).doFillCredentialsIdItems(owner, "http://nowhere.net/")) {
                if (o.value.isEmpty()) {
                    continue; // AbstractIdCredentialsListBoxModel.EmptySelection
                }
                actualNames.add(o.name);
            }
            assertEquals(expectedNames, actualNames);
        } finally {
            SecurityContextHolder.setContext(orig);
        }
    }

}
