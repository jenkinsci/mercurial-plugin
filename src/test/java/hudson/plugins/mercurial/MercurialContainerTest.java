/*
 * The MIT License
 *
 * Copyright 2017 CloudBees, Inc.
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

import hudson.EnvVars;
import hudson.model.Slave;
import hudson.model.TaskListener;
import hudson.util.ArgumentListBuilder;
import hudson.util.StreamTaskListener;
import static org.hamcrest.Matchers.*;
import org.jenkinsci.test.acceptance.docker.DockerRule;
import org.junit.Test;
import static org.junit.Assert.*;
import org.junit.Rule;
import org.jvnet.hudson.test.JenkinsRule;

public class MercurialContainerTest {

    @Rule public JenkinsRule r = new JenkinsRule();
    @Rule public DockerRule<MercurialContainer> container = new DockerRule<MercurialContainer>(MercurialContainer.class);

    @Test public void smokes() throws Exception {
        Slave slave = container.get().createSlave(r);
        TaskListener listener = StreamTaskListener.fromStdout();
        for (MercurialContainer.Version v : MercurialContainer.Version.values()) {
            HgExe hgExe = new HgExe(container.get().createInstallation(r, v, false, false, false, "", slave), null, slave.createLauncher(listener), slave, listener, new EnvVars());
            assertThat(hgExe.popen(slave.getRootPath(), listener, true, new ArgumentListBuilder("version")), containsString("Mercurial Distributed SCM (version " + v.exactVersion + ")"));
        }
    }

}
