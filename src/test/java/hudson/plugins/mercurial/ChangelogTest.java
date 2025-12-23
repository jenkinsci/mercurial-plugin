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

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.Parameter;
import org.junit.jupiter.params.ParameterizedClass;
import org.junit.jupiter.params.provider.EnumSource;
import org.jvnet.hudson.test.For;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.BuildWatcherExtension;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Unlike {@link FunctionalTest} we only care here about the Mercurial version.
 */
@Testcontainers(disabledWithoutDocker = true)
@For({MercurialChangeSet.class, MercurialChangeLogParser.class})
@ParameterizedClass
@EnumSource(MercurialContainer.Version.class)
@WithJenkins
class ChangelogTest {

    private JenkinsRule j;
    private MercurialTestUtil m;
    @Container
    private static final MercurialContainer container = new MercurialContainer();
    @SuppressWarnings("unused")
    @RegisterExtension
    private static final BuildWatcherExtension BUILD_WATCHER = new BuildWatcherExtension();

    @Parameter(0)
    private MercurialContainer.Version mercurialVersion;

    private FilePath repo;
    private Slave agent;
    private MercurialInstallation inst;

    @BeforeEach
    void beforeEach(JenkinsRule rule) throws Exception {
        j = rule;
        m = new MercurialTestUtil(j);

        agent = container.createAgent(j);
        inst = container.createInstallation(j, mercurialVersion, false, false, false, "", agent);
        repo = agent.getRootPath().child("repo");
        repo.mkdirs();
        m.withNode(agent);
        m.withInstallation(inst);
    }

    @Issue("JENKINS-55319")
    @Test
    void spacesInPaths() throws Exception {
        FreeStyleProject p = j.createFreeStyleProject();
        MercurialSCM scm = new MercurialSCM(repo.getRemote());
        scm.setInstallation(inst.getName());
        p.setScm(scm);
        p.setAssignedNode(agent);
        m.hg(repo, "init");
        m.touchAndCommit(repo, "one", "two");
        assertChangelog("", p);
        m.touchAndCommit(repo, "two", "three");
        assertChangelog("added=[three] deleted=[] modified=[two] ", p);
        m.hg(repo, "rm", "one", "three");
        m.hg(repo, "commit", "--message", "removed");
        assertChangelog("added=[] deleted=[one, three] modified=[] ", p);
        m.touchAndCommit(repo, "some -> thing");
        assertChangelog("added=[some -> thing] deleted=[] modified=[] ", p);
        m.touchAndCommit(repo, "some -> thing", "two");
        assertChangelog("added=[] deleted=[] modified=[some -> thing, two] ", p);
        m.hg(repo, "rm", "some -> thing");
        m.hg(repo, "commit", "--message", "removed");
        assertChangelog("added=[] deleted=[some -> thing] modified=[] ", p);
    }

    private static void assertChangelog(String summary, FreeStyleProject p) throws Exception {
        assertEquals(summary, MercurialChangeLogParserTest.summary((MercurialChangeSetList) p.scheduleBuild2(0).get().getChangeSet()));
    }

}
