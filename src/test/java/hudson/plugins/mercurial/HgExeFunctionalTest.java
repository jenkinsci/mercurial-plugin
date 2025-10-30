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
import hudson.util.ArgumentListBuilder;
import hudson.util.StreamTaskListener;
import java.io.File;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.commons.io.FileUtils;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

import static org.junit.jupiter.api.Assertions.assertEquals;


@WithJenkins
class HgExeFunctionalTest {

    private JenkinsRule j;

    private static final String INSTALLATION = "test";
    private MercurialInstallation mercurialInstallation;
    private TaskListener listener;
    private Launcher launcher;
    private EnvVars vars;

    @BeforeEach
    void beforeEach(JenkinsRule rule) {
        j = rule;
        this.mercurialInstallation = new MercurialInstallation(
                INSTALLATION, "",
                "hg", false, true, false, Collections
                .emptyList());
        this.listener = new StreamTaskListener(System.out, Charset.defaultCharset());
        this.launcher = j.jenkins.createLauncher(listener);
        this.vars = new EnvVars();
    }

    @Test
    void credentialsUsernamePasswordTest() throws Exception {
        UsernamePasswordCredentialsImpl credentials = new UsernamePasswordCredentialsImpl(
                CredentialsScope.GLOBAL, "", "", "testuser", "testpassword");

        try (HgExe hgexe = new HgExe(mercurialInstallation, credentials, launcher, j.jenkins, listener, vars)) {
            ArgumentListBuilder b = hgexe.seed(false);
            assertEquals(new ArgumentListBuilder(
                    "hg", "--config", "auth.jenkins.prefix=*", "--config", "auth.jenkins.username=testuser",
                    "--config", "auth.jenkins.password=testpassword", "--config", "auth.jenkins.schemes=http https").toList(),
                    b.toList());
            assertEquals(new ArgumentListBuilder(
                "hg", "--config", "auth.jenkins.prefix=*", "--config", "******",
                        "--config", "******", "--config", "auth.jenkins.schemes=http https").toString(),
                    b.toString());
        }
    }

    @Test
    void credentialsSSHKeyTest() throws Exception {
        BasicSSHUserPrivateKey.PrivateKeySource source = new BasicSSHUserPrivateKey.DirectEntryPrivateKeySource(
                "test key\n");
        BasicSSHUserPrivateKey credentials = new BasicSSHUserPrivateKey(
                CredentialsScope.GLOBAL, "", "testuser", source, null, null);

        try (HgExe hgexe = new HgExe(mercurialInstallation, credentials, launcher, j.jenkins, listener, vars)) {
            ArgumentListBuilder b = hgexe.seed(false);
            Matcher matcher = Pattern.compile("ssh\\s-i\\s(.+)\\s-l\\stestuser").matcher(b.toCommandArray()[2]);
            matcher.find();
            String fileName = matcher.group(1);
            assertEquals("test key\n", FileUtils.readFileToString(new File(fileName), StandardCharsets.UTF_8));
            assertEquals(new ArgumentListBuilder("hg", "--config", "******").toString(), b.toString());
        }
    }

    @Issue("JENKINS-5723")
    @Test
    void customConfiguration() throws Exception {
        MercurialInstallation customConfiguration = new MercurialInstallation(INSTALLATION, "", "hg", false, false, false, "[defaults]\nclone = --uncompressed\n", null);
        try (HgExe hgexe = new HgExe(customConfiguration, null, launcher, j.jenkins, listener, vars)) {
            ArgumentListBuilder b = hgexe.seed(false).add("clone", "http://some.thing/");
            assertEquals("hg --config defaults.clone=--uncompressed clone http://some.thing/", b.toString());
        }
    }
}
