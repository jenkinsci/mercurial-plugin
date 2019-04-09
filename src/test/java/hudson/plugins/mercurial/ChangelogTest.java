/*
 * The MIT License
 *
 * Copyright 2019 CloudBees, Inc.
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
import hudson.model.FreeStyleProject;
import hudson.model.Slave;
import org.jenkinsci.test.acceptance.docker.DockerClassRule;
import static org.junit.Assert.*;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.jvnet.hudson.test.BuildWatcher;
import org.jvnet.hudson.test.For;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;

/**
 * Unlike {@link FunctionalTest} we only care here about the Mercurial version.
 */
@For({MercurialChangeSet.class, MercurialChangeLogParser.class})
@RunWith(Parameterized.class)
public class ChangelogTest {

    @ClassRule public static final JenkinsRule j = new JenkinsRule();
    @ClassRule public static final MercurialRule m = new MercurialRule(j);
    @ClassRule public static final DockerClassRule<MercurialContainer> docker = new DockerClassRule<>(MercurialContainer.class);
    @ClassRule public static final BuildWatcher buildWatcher = new BuildWatcher();

    @Parameterized.Parameter(0) public MercurialContainer.Version mercurialVersion;

    @Parameterized.Parameters  public static Object[] data() {
        return MercurialContainer.Version.values();
    }

    private FilePath repo;
    private Slave slave;
    private MercurialInstallation inst;

    @Before public void setUp() throws Exception {
        MercurialContainer container = docker.create();
        slave = container.createSlave(j);
        inst = container.createInstallation(j, mercurialVersion, false, false, false, "", slave);
        repo = slave.getRootPath().child("repo");
        repo.mkdirs();
        m.withNode(slave);
        m.withInstallation(inst);
    }

    @Issue("JENKINS-55319")
    @Test public void spacesInPaths() throws Exception {
        FreeStyleProject p = j.createFreeStyleProject();
        MercurialSCM scm = new MercurialSCM(repo.getRemote());
        scm.setInstallation(inst.getName());
        p.setScm(scm);
        p.setAssignedNode(slave);
        m.hg(repo, "init");
        m.touchAndCommit(repo, "one", "two");
        assertChangelog("", p);
        m.touchAndCommit(repo, "two", "three");
        assertChangelog("added=[three] deleted=[] modified=[two] ", p);
        m.hg(repo, "rm", "one", "three");
        m.hg(repo, "commit", "--message", "removed");
        assertChangelog("added=[] deleted=[one, three] modified=[] ", p);
    }

    private static void assertChangelog(String summary, FreeStyleProject p) throws Exception {
        assertEquals(summary, MercurialChangeLogParserTest.summary((MercurialChangeSetList) p.scheduleBuild2(0).get().getChangeSet()));
    }

}
