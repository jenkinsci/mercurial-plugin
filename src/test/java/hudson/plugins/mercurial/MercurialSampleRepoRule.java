/*
 * The MIT License
 *
 * Copyright 2015 CloudBees, Inc.
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

import java.io.File;
import jenkins.model.Jenkins;
import org.apache.commons.io.FileUtils;
import org.jenkinsci.plugins.workflow.steps.scm.AbstractSampleDVCSRepoRule;
import static org.jenkinsci.plugins.workflow.steps.scm.AbstractSampleRepoRule.run;
import org.jvnet.hudson.test.JenkinsRule;

public final class MercurialSampleRepoRule extends AbstractSampleDVCSRepoRule {

    public void hg(String... cmds) throws Exception {
        run("hg", cmds);
    }

    @Override public void init() throws Exception {
        run(true, tmp.getRoot(), "hg", "version");
        hg("init");
        write("file", "");
        hg("add", "file");
        hg("commit", "--message=init");
    }

    public void notifyCommit(JenkinsRule r) throws Exception {
        synchronousPolling(r);
        System.out.println(r.createWebClient().goTo("mercurial/notifyCommit?url=" + fileUrl(), "text/plain").getWebResponse().getContentAsString());
        r.waitUntilNoActivity();
    }

    public void registerHook(JenkinsRule r) throws Exception {
        File hgDir = new File(sampleRepo, ".hg");
        File hook = new File(hgDir, "hook.py");
        FileUtils.write(hook, "import urllib\n"
                + "import urllib2\n"
                + "\n"
                + "def commit(ui, repo, node, **kwargs):\n"
                + "    data = {\n"
                + "        'url': '" + fileUrl() + "',\n"
                + "        'branch': repo[node].branch(),\n"
                + "        'changesetId': node,\n"
                + "    }\n"
                + "    req = urllib2.Request('" + r.getURL() + "mercurial/notifyCommit')\n"
                + "    rsp = urllib2.urlopen(req, urllib.urlencode(data))\n"
                + "    ui.warn('Notify Commit hook response: %s\\n' % rsp.read())\n"
                + "    pass\n");
        FileUtils.write(new File(hgDir, "hgrc"), "[hooks]\n"
                + "commit.jenkins = python:"+hook.getAbsolutePath()+":commit");
    }

}
