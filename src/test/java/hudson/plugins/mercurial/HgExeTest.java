/*
 * The MIT License
 *
 * Copyright 2012 Jesse Glick.
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

import static org.junit.Assert.*;

import org.apache.commons.io.FileUtils;
import org.junit.*;

import com.cloudbees.jenkins.plugins.sshcredentials.impl.BasicSSHUserPrivateKey;
import com.cloudbees.plugins.credentials.CredentialsScope;
import com.cloudbees.plugins.credentials.common.StandardUsernamePasswordCredentials;
import com.cloudbees.plugins.credentials.impl.BaseStandardCredentials;
import com.cloudbees.plugins.credentials.impl.UsernamePasswordCredentialsImpl;
import hudson.EnvVars;
import hudson.Launcher;
import hudson.model.Computer;
import hudson.model.FreeStyleProject;
import hudson.model.Node;
import hudson.model.TaskListener;
import hudson.tools.ToolProperty;
import hudson.util.ArgumentListBuilder;
import hudson.util.Secret;
import hudson.util.StreamTaskListener;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.jvnet.hudson.test.JenkinsRule;
import sun.security.util.Password;

import java.io.File;
import java.io.FileReader;
import java.lang.reflect.ParameterizedType;
import java.nio.charset.Charset;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


public class HgExeTest {

    @Rule public JenkinsRule j = new JenkinsRule();

    private static final String INSTALLATION = "test";
    private MercurialInstallation mercurialInstallation;
    private TaskListener listener;
    private Node node;
    private Launcher launcher;
    private EnvVars vars;

    @Before
    public void setUp() throws Exception {
        this.mercurialInstallation = new MercurialInstallation(
                INSTALLATION, "",
                "hg", false, true, false, Collections
                .<ToolProperty<?>> emptyList());
        this.listener = new StreamTaskListener(System.out, Charset.defaultCharset());
        this.node = Computer.currentComputer().getNode();
        this.launcher = j.jenkins.createLauncher(listener);
        this.vars = new EnvVars();
    }

    @Test public void pathEquals() {
        assertTrue(HgExe.pathEquals("http://nowhere.net/hg/", "http://nowhere.net/hg/"));
        assertTrue(HgExe.pathEquals("http://nowhere.net/hg", "http://nowhere.net/hg/"));
        assertTrue(HgExe.pathEquals("http://nowhere.net/hg/", "http://nowhere.net/hg"));
        assertTrue(HgExe.pathEquals("http://nowhere.net/hg", "http://nowhere.net/hg"));
        assertFalse(HgExe.pathEquals("https://nowhere.net/hg/", "http://nowhere.net/hg/"));
        if (  org.apache.commons.lang.SystemUtils.IS_OS_UNIX ) {
            assertTrue(HgExe.pathEquals("file:/var/hg/stuff", "/var/hg/stuff"));
            assertTrue(HgExe.pathEquals("file:///var/hg/stuff", "/var/hg/stuff"));
            assertFalse(HgExe.pathEquals("file:/var/hg/stuff", "/var/hg/other"));
            assertTrue(HgExe.pathEquals("/var/hg/stuff", "file:/var/hg/stuff"));
            assertTrue(HgExe.pathEquals("/var/hg/stuff", "file:///var/hg/stuff"));
            assertFalse(HgExe.pathEquals("/var/hg/other", "file:/var/hg/stuff"));
        }
    }

    @Test public void credentialsUsernamePasswordTest() throws Exception {
        MockUsernamePasswordCredentials credentials = new MockUsernamePasswordCredentials(
                CredentialsScope.GLOBAL, "", "testuser");

        HgExe hgexe = new HgExe(
                this.mercurialInstallation, credentials,
                this.launcher, this.node,
                this.listener, this.vars);
        ArgumentListBuilder b = hgexe.seed(false);
        assertEquals(new ArgumentListBuilder(
                "hg", "--config", "auth.jenkins.prefix=*", "--config", "auth.jenkins.username=testuser",
                "--config", "auth.jenkins.password=testpassword", "--config", "auth.jenkins.schemes=http https")
                .toList(), b.toList());
        assertEquals(new ArgumentListBuilder(
                "hg", "--config", "auth.jenkins.prefix=*", "--config", "******",
                "--config", "******", "--config", "auth.jenkins.schemes=http https").toString(),
                b.toString());
    }

    @Test public void credentialsSSHKeyTest() throws Exception {
        BasicSSHUserPrivateKey.PrivateKeySource source = new BasicSSHUserPrivateKey.DirectEntryPrivateKeySource(
                "test key");
        BasicSSHUserPrivateKey credentials = new BasicSSHUserPrivateKey(
                CredentialsScope.GLOBAL, "", "testuser", source, null, null);

        HgExe hgexe = new HgExe(
                this.mercurialInstallation, credentials,
                this.launcher, this.node,
                this.listener, this.vars);
        ArgumentListBuilder b = hgexe.seed(false);
        Matcher matcher = Pattern.compile("ssh\\s-i\\s(.+)\\s-l\\stestuser").matcher(b.toCommandArray()[2]);
        matcher.find();
        String fileName = matcher.group(1);
        assertEquals("test key", FileUtils.readFileToString(new File(fileName)));
        assertEquals(new ArgumentListBuilder(
                "hg", "--config", "******").toString(),
                b.toString());
    }

    private static class MockUsernamePasswordCredentials extends BaseStandardCredentials
            implements StandardUsernamePasswordCredentials {
        private final String username;
        MockUsernamePasswordCredentials(CredentialsScope scope, String id, String username) {
            super(scope, id, "");
            this.username = username;
        }
        @Override public String getUsername() {
            return username;
        }
        @Override public Secret getPassword() {
            return Secret.fromString("testpassword");
        }
    }
}
