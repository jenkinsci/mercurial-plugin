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

import com.cloudbees.jenkins.plugins.sshcredentials.impl.BasicSSHUserPrivateKey;
import com.cloudbees.plugins.credentials.CredentialsScope;
import com.cloudbees.plugins.credentials.impl.UsernamePasswordCredentialsImpl;
import hudson.EnvVars;
import hudson.Launcher;
import hudson.model.TaskListener;
import hudson.tools.ToolProperty;
import hudson.util.ArgumentListBuilder;
import hudson.util.StreamTaskListener;
import java.io.File;
import java.nio.charset.Charset;
import java.util.Collections;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.commons.io.FileUtils;

import static org.junit.Assert.assertEquals;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.Bug;
import org.jvnet.hudson.test.JenkinsRule;


public class HgExeFunctionalTest {

    @Rule
    public JenkinsRule j = new JenkinsRule();

    private static final String INSTALLATION = "test";
    private MercurialInstallation mercurialInstallation;
    private TaskListener listener;
    private Launcher launcher;
    private EnvVars vars;

    @Before
    public void setUp() throws Exception {
        this.mercurialInstallation = new MercurialInstallation(
                INSTALLATION, "",
                "hg", false, true, false, Collections
                .<ToolProperty<?>> emptyList());
        this.listener = new StreamTaskListener(System.out, Charset.defaultCharset());
        this.launcher = j.jenkins.createLauncher(listener);
        this.vars = new EnvVars();
    }

    @Test
    public void credentialsUsernamePasswordTest() throws Exception {
        UsernamePasswordCredentialsImpl credentials = new UsernamePasswordCredentialsImpl(
                CredentialsScope.GLOBAL, "", "", "testuser", "testpassword");

        HgExe hgexe = new HgExe(
                this.mercurialInstallation, credentials,
                this.launcher, j.jenkins,
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
        hgexe.close();
    }

    @Test public void credentialsSSHKeyTest() throws Exception {
        BasicSSHUserPrivateKey.PrivateKeySource source = new BasicSSHUserPrivateKey.DirectEntryPrivateKeySource(
                "test key");
        BasicSSHUserPrivateKey credentials = new BasicSSHUserPrivateKey(
                CredentialsScope.GLOBAL, "", "testuser", source, null, null);

        HgExe hgexe = new HgExe(
                this.mercurialInstallation, credentials,
                this.launcher, j.jenkins,
                this.listener, this.vars);
        ArgumentListBuilder b = hgexe.seed(false);
        Matcher matcher = Pattern.compile("ssh\\s-i\\s(.+)\\s-l\\stestuser").matcher(b.toCommandArray()[2]);
        matcher.find();
        String fileName = matcher.group(1);
        assertEquals("test key", FileUtils.readFileToString(new File(fileName)));
        assertEquals(new ArgumentListBuilder(
                "hg", "--config", "******").toString(),
                b.toString());
        hgexe.close();
    }

    @Bug(5723)
    @Test public void customConfiguration() throws Exception {
        HgExe hgexe = new HgExe(new MercurialInstallation(INSTALLATION, "", "hg", false, false, false, "[defaults]\nclone = --uncompressed\n", null), null, this.launcher, j.jenkins, this.listener, this.vars);
        ArgumentListBuilder b = hgexe.seed(false).add("clone", "http://some.thing/");
        assertEquals("hg --config defaults.clone=--uncompressed clone http://some.thing/", b.toString());
    }

}
