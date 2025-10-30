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

import hudson.FilePath;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import java.io.File;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertFalse;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

@WithJenkins
class CustomConfigTest {

    private JenkinsRule r;
    private MercurialTestUtil m;

    @TempDir
    private File tmp;

    @BeforeEach
    void beforeEach(JenkinsRule rule) {
        r = rule;
        m = new MercurialTestUtil(r);
    }

    @Issue("JENKINS-5723")
    @Test
    void customConfiguration() throws Exception {
        File repo = tmp;
        // TODO switch to MercurialContainer
        m.hg(repo, "init");
        m.touchAndCommit(repo, "f");
        r.jenkins.getDescriptorByType(MercurialInstallation.DescriptorImpl.class).setInstallations(new MercurialInstallation("test", "", "hg", false, false, false, "[format]\nusestore = false", null));
        FreeStyleProject p = r.createFreeStyleProject();
        MercurialSCM scm = new MercurialSCM(repo.getPath());
        scm.setInstallation("test");
        scm.setRevisionType(MercurialSCM.RevisionType.BRANCH);
        scm.setDisableChangeLog(false);
        p.setScm(scm);
        FreeStyleBuild b = r.assertBuildStatusSuccess(p.scheduleBuild2(0));
        FilePath ws = b.getWorkspace();
        assertNotNull(ws);
        String requires = ws.child(".hg/requires").readToString();
        assertFalse(requires.contains("store"), requires);
    }

}
