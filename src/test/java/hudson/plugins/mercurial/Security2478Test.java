package hudson.plugins.mercurial;

import hudson.FilePath;
import hudson.model.Result;
import hudson.slaves.DumbSlave;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.rules.TestRule;
import org.jvnet.hudson.test.FlagRule;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;

import java.io.File;
import java.util.Collections;

import static org.junit.Assert.assertFalse;

public class Security2478Test {

    private static final String INSTALLATION = "mercurial";

    @Rule
    public JenkinsRule rule = new JenkinsRule();
    @Rule
    public MercurialRule m = new MercurialRule(rule);

    @Rule
    public TestRule notAllowNonRemoteCheckout = new FlagRule<>(() -> MercurialSCM.ALLOW_LOCAL_CHECKOUT, x -> MercurialSCM.ALLOW_LOCAL_CHECKOUT = x, false);

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();
    private File repo;

    @Before
    public void setUp() throws Exception {
        repo = tmp.getRoot();
        rule.jenkins
                .getDescriptorByType(MercurialInstallation.DescriptorImpl.class)
                .setInstallations(new MercurialInstallation(INSTALLATION, "", "hg",
                        false, true, new File(tmp.newFolder(),"custom-dir").getAbsolutePath(), false, "",
                        Collections.emptyList()));

    }

    @Issue("SECURITY-2478")
    @Test
    public void checkoutShouldAbortWhenSourceIsNonRemoteAndBuildOnController() throws Exception {
        assertFalse("Non Remote checkout should be disallowed", MercurialSCM.ALLOW_LOCAL_CHECKOUT);
        WorkflowJob p = rule.jenkins.createProject(WorkflowJob.class, "pipeline");
        FilePath sourcePath = rule.jenkins.getRootPath().createTempDir("t", "");
        String script = "node {\n" +
                "checkout([$class: 'MercurialSCM', credentialsId: '', installation: 'mercurial', source: '" + sourcePath + "'])\n" +
                "}";
        p.setDefinition(new CpsFlowDefinition(script, true));
        WorkflowRun run = rule.assertBuildStatus(Result.FAILURE, p.scheduleBuild2(0));
        rule.assertLogContains("Checkout of Mercurial source '" + sourcePath + "' aborted because it references a local directory, which may be insecure. You can allow local checkouts anyway by setting the system property '" + MercurialSCM.ALLOW_LOCAL_CHECKOUT_PROPERTY + "' to true.", run);
    }

    @Issue("SECURITY-2478")
    @Test
    public void checkoutOnAgentShouldNotAbortWhenSourceIsNonRemoteAndBuildOnAgent() throws Exception {
        assertFalse("Non Remote checkout should be disallowed", MercurialSCM.ALLOW_LOCAL_CHECKOUT);
        DumbSlave agent = rule.createOnlineSlave();
        FilePath workspace = agent.getRootPath().child("testws");
        workspace.mkdirs();
        m.hg(workspace, "init");
        m.touchAndCommit(workspace, "a");
        WorkflowJob p = rule.jenkins.createProject(WorkflowJob.class, "pipeline");
        String script = "node('slave0') {\n" +
                "checkout([$class: 'MercurialSCM', credentialsId: '', installation: 'mercurial', source: '" + workspace + "'])\n" +
                "}";
        p.setDefinition(new CpsFlowDefinition(script, true));
        rule.assertBuildStatus(Result.SUCCESS, p.scheduleBuild2(0));
    }

    @Issue("SECURITY-2478")
    @Test
    public void checkoutShouldNotAbortWhenSourceIsAlias() throws Exception {
        assertFalse("Non Remote checkout should be disallowed", MercurialSCM.ALLOW_LOCAL_CHECKOUT);

        WorkflowJob p = rule.jenkins.createProject(WorkflowJob.class, "pipeline");
        String aliasName = "alias1";
        // configure mercurial installation with an alias in a path
        rule.jenkins.getDescriptorByType(MercurialInstallation.DescriptorImpl.class).setInstallations(new MercurialInstallation("mercurial", "", "hg", false, false,"", false, "[paths]\n" + aliasName + " = https://www.mercurial-scm.org/repo/hello", null));
        String script = "node {\n" +
                "checkout([$class: 'MercurialSCM', credentialsId: '', installation: 'mercurial', source: '" + aliasName + "'])\n" +
                "}";
        p.setDefinition(new CpsFlowDefinition(script, true));
        m.hg(new FilePath(repo), "init");
        m.touchAndCommit(new FilePath(repo), "a");
        WorkflowRun run = rule.assertBuildStatus(Result.SUCCESS, p.scheduleBuild2(0));
        rule.assertLogNotContains("Checkout of Mercurial source '" + aliasName + "' aborted because it references a local directory, which may be insecure. You can allow local checkouts anyway by setting the system property '" + MercurialSCM.ALLOW_LOCAL_CHECKOUT_PROPERTY + "' to true.", run);

    }

    @Issue("SECURITY-2478")
    @Test
    public void checkoutShouldNotAbortWhenSourceIsAliasPointingToLocalPath() throws Exception {
        assertFalse("Non Remote checkout should be disallowed", MercurialSCM.ALLOW_LOCAL_CHECKOUT);

        WorkflowJob p = rule.jenkins.createProject(WorkflowJob.class, "pipeline");
        String aliasName = "alias1";
        // configure mercurial installation with an alias in a path
        rule.jenkins.getDescriptorByType(MercurialInstallation.DescriptorImpl.class).setInstallations(new MercurialInstallation("mercurial", "", "hg", false, false,"", false, "[paths]\n" + aliasName + " = " + repo.getPath(), null));
        String script = "node {\n" +
                "checkout([$class: 'MercurialSCM', credentialsId: '', installation: 'mercurial', source: 'alias1'])\n" +
                "}";
        p.setDefinition(new CpsFlowDefinition(script, true));
        m.hg(new FilePath(repo), "init");
        m.touchAndCommit(new FilePath(repo), "a");
        rule.assertBuildStatus(Result.SUCCESS, p.scheduleBuild2(0));
    }
}
