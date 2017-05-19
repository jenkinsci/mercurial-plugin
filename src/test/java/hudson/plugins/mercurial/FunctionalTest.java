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

import hudson.FilePath;
import hudson.model.FreeStyleProject;
import hudson.model.Slave;
import java.io.File;
import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.jenkinsci.test.acceptance.docker.DockerRule;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.jvnet.hudson.test.BuildWatcher;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;

/**
 * Runs various functional tests against different system configurations.
 */
@RunWith(Parameterized.class)
public class FunctionalTest {

    @Rule public JenkinsRule j = new JenkinsRule();
    @Rule public MercurialRule m = new MercurialRule(j);
    @Rule public TemporaryFolder tmp = new TemporaryFolder();
    @Rule public DockerRule<MercurialContainer> container = new DockerRule<MercurialContainer>(MercurialContainer.class);
    @ClassRule public static BuildWatcher buildWatcher = new BuildWatcher();

    /** Whether to run builds on a slave, or only on master. */
    @Parameterized.Parameter(0) public boolean useSlave;
    public interface MercurialInstallationFactory {
        @CheckForNull MercurialInstallation create(@Nonnull JenkinsRule j, @Nonnull DockerRule<MercurialContainer> container, @CheckForNull Slave slave, @Nullable MercurialContainer.Version version) throws Exception;
        @Override String toString();
    }
    /** How Mercurial is configured. */
    @Parameterized.Parameter(1) public MercurialInstallationFactory mercurialInstallationFactory;
    /** Version of Mercurial to run (if {@link #useSlave}). */
    @Parameterized.Parameter(2) public MercurialContainer.Version mercurialVersion;
    @Parameterized.Parameters(name="{index}: slave={0} {1} {2}") public static Object[][] data() {
        MercurialInstallationFactory defaultFactory = new MercurialInstallationFactory() {
            @Override public String toString() {return "default";}
            @Override public MercurialInstallation create(JenkinsRule j, DockerRule<MercurialContainer> container, Slave slave, MercurialContainer.Version version) throws Exception {
                if (slave != null) {
                    return container.get().createInstallation(j, version, false, false, false, "", slave);
                } else {
                    assert version == null;
                    return null;
                }
            }
        };
        MercurialInstallationFactory cachingFactory = new MercurialInstallationFactory() {
            @Override public String toString() {return "caching";}
            @Override public MercurialInstallation create(JenkinsRule j, DockerRule<MercurialContainer> container, Slave slave, MercurialContainer.Version version) throws Exception {
                if (slave != null) {
                    return container.get().createInstallation(j, version, false, true, false, "", slave);
                } else {
                    // TODO pull up common code here into superclass; or simply switch to @DataBoundSetter so we can create a stock installation (except in default / !useSlave) and then customize it
                    assert version == null;
                    MercurialInstallation inst = new MercurialInstallation("whatever", "", "hg", false, true, false, null);
                    j.jenkins.getDescriptorByType(MercurialInstallation.DescriptorImpl.class).setInstallations(inst); // TODO cf. MercurialContainer, caller should do it
                    return inst;
                }
            }
        };
        MercurialInstallationFactory sharingFactory = new MercurialInstallationFactory() {
            @Override public String toString() {return "sharing";}
            @Override public MercurialInstallation create(JenkinsRule j, DockerRule<MercurialContainer> container, Slave slave, MercurialContainer.Version version) throws Exception {
                if (slave != null) {
                    return container.get().createInstallation(j, version, false, true, true, "", slave);
                } else {
                    assert version == null;
                    MercurialInstallation inst = new MercurialInstallation("whatever", "", "hg", false, true, true, null);
                    j.jenkins.getDescriptorByType(MercurialInstallation.DescriptorImpl.class).setInstallations(inst);
                    return inst;
                }
            }
        };
        MercurialInstallationFactory debugFactory = new MercurialInstallationFactory() {
            @Override public String toString() {return "debug";}
            @Override public MercurialInstallation create(JenkinsRule j, DockerRule<MercurialContainer> container, Slave slave, MercurialContainer.Version version) throws Exception {
                if (slave != null) {
                    return container.get().createInstallation(j, version, true, false, false, "", slave);
                } else {
                    assert version == null;
                    MercurialInstallation inst = new MercurialInstallation("whatever", "", "hg", true, false, false, null);
                    j.jenkins.getDescriptorByType(MercurialInstallation.DescriptorImpl.class).setInstallations(inst);
                    return inst;
                }
            }
        };
        return new Object[][] {
            {false, defaultFactory, null},
            {true, defaultFactory, MercurialContainer.Version.HG1},
            {true, defaultFactory, MercurialContainer.Version.HG2},
            {true, defaultFactory, MercurialContainer.Version.HG3},
            {true, defaultFactory, MercurialContainer.Version.HG4},
            {false, cachingFactory, null},
            // Skip testing caching with older Hg versions since a locally installed version might be 3.x+,
            // in which case master sends a new version and we get abort: xfer.hg: unknown bundle version 20
            // cf. https://hglabhq.com/blog/2014/4/29/what-s-new-in-mercurial-3-0
            {true, cachingFactory, MercurialContainer.Version.HG3},
            {true, cachingFactory, MercurialContainer.Version.HG4},
            {false, sharingFactory, null},
            // Sharing implies caching, so same issue with bundle version.
            {true, sharingFactory, MercurialContainer.Version.HG3},
            {true, sharingFactory, MercurialContainer.Version.HG4},
            {false, debugFactory, null},
            // Do not waste time looking at old versions for this.
            {true, debugFactory, MercurialContainer.Version.HG4},
        };
    }

    private FilePath repo;
    private Slave slave;
    private MercurialInstallation inst;

    @Before public void setUp() throws Exception {
        slave = useSlave ? container.get().createSlave(j) : null;
        inst = mercurialInstallationFactory.create(j, container, slave, mercurialVersion);
        if (inst != null && inst.isUseCaches() || slave == null) {
            // Set up test repository on master, if we have hg installed locally.
            repo = new FilePath(tmp.getRoot());
        } else {
            // Set up test repository on agent.
            repo = slave.getRootPath().child("repo");
            repo.mkdirs();
            m.withNode(slave);
            m.withInstallation(inst);
        }
    }

    @Issue({"JENKINS-13329", "JENKINS-15829"})
    @Test public void basics() throws Exception {
        FreeStyleProject p = j.createFreeStyleProject();
        p.setScm(new MercurialSCM(inst != null ? inst.getName() : null, repo.getRemote(), null, null, null, null, false));
        if (slave != null) {
            p.setAssignedNode(slave);
        }
        m.hg(repo, "init");
        m.touchAndCommit(repo, "a");
        String log = m.buildAndCheck(p, "a");
        assertClone(log, true);
        m.touchAndCommit(repo, "b");
        log = m.buildAndCheck(p, "b");
        assertClone(log, false);
    }

    private void assertClone(String log, boolean cloneExpected) {
        if (cloneExpected) {
            assertTrue(log, log.contains(" clone --"));
        } else {
            assertTrue(log, log.contains(" update --"));
            assertFalse(log, log.contains(" clone --"));
        }
    }

    @Issue("JENKINS-4281")
    @Test public void branches() throws Exception {
        m.hg(repo, "init");
        m.touchAndCommit(repo, "init");
        m.hg(repo, "tag", "init");
        m.touchAndCommit(repo, "default-1");
        m.hg(repo, "update", "--clean", "init");
        m.hg(repo, "branch", "b");
        m.touchAndCommit(repo, "b-1");
        FreeStyleProject p = j.createFreeStyleProject();
        // Clone off b.
        p.setScm(new MercurialSCM(inst != null ? inst.getName() : null, repo.getRemote(), "b", null, null, null, false));
        m.buildAndCheck(p, "b-1");
        m.hg(repo, "update", "--clean", "default");
        m.touchAndCommit(repo, "default-2");
        // Changes in default should be ignored.
        assertFalse(m.pollSCMChanges(p).hasChanges());
        m.hg(repo, "update", "--clean", "b");
        m.touchAndCommit(repo, "b-2");
        // But changes in b should be pulled.
        assertTrue(m.pollSCMChanges(p).hasChanges());
        m.buildAndCheck(p, "b-2");
        // Switch to default branch with an existing workspace.
        p.setScm(new MercurialSCM(inst != null ? inst.getName() : null, repo.getRemote(), null, null, null, null, false));
        // Should now consider preexisting changesets in default to be poll
        // triggers.
        assertTrue(m.pollSCMChanges(p).hasChanges());
        // Should switch working copy to default branch.
        m.buildAndCheck(p, "default-2");
        m.touchAndCommit(repo, "b-3");
        // Changes in other branch should be ignored.
        assertFalse(m.pollSCMChanges(p).hasChanges());
    }

}
