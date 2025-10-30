package hudson.plugins.mercurial;

import hudson.FilePath;
import hudson.model.Result;
import hudson.slaves.DumbSlave;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

import java.io.File;

import static org.junit.jupiter.api.Assertions.assertFalse;
import java.io.IOException;
import java.util.Collections;

@WithJenkins
class Security2478Test {

    private static final String INSTALLATION = "mercurial";

    private JenkinsRule rule;
    private MercurialTestUtil m ;

    private boolean notAllowNonRemoteCheckout;

    @TempDir
    private File tmp;
    private File repo;

    @BeforeEach
    void beforeEach(JenkinsRule rule) throws Exception {
        this.rule = rule;
        m = new MercurialTestUtil(this.rule);

        notAllowNonRemoteCheckout = MercurialSCM.ALLOW_LOCAL_CHECKOUT;
        MercurialSCM.ALLOW_LOCAL_CHECKOUT = false;

        repo = tmp;
        rule.jenkins
                .getDescriptorByType(MercurialInstallation.DescriptorImpl.class)
                .setInstallations(new MercurialInstallation(INSTALLATION, "", "hg",
                        false, true, new File(newFolder(tmp, "junit"),"custom-dir").getAbsolutePath(), false, "",
                        Collections.emptyList()));
    }

    @AfterEach
    void afterEach() {
        MercurialSCM.ALLOW_LOCAL_CHECKOUT = notAllowNonRemoteCheckout;
    }

    @Issue("SECURITY-2478")
    @Test
    void checkoutShouldAbortWhenSourceIsNonRemoteAndBuildOnController() throws Exception {
        assertFalse(MercurialSCM.ALLOW_LOCAL_CHECKOUT, "Non Remote checkout should be disallowed");
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
    void checkoutOnAgentShouldNotAbortWhenSourceIsNonRemoteAndBuildOnAgent() throws Exception {
        assertFalse(MercurialSCM.ALLOW_LOCAL_CHECKOUT, "Non Remote checkout should be disallowed");
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
    void checkoutShouldNotAbortWhenSourceIsAlias() throws Exception {
        assertFalse(MercurialSCM.ALLOW_LOCAL_CHECKOUT, "Non Remote checkout should be disallowed");

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
    void checkoutShouldNotAbortWhenSourceIsAliasPointingToLocalPath() throws Exception {
        assertFalse(MercurialSCM.ALLOW_LOCAL_CHECKOUT, "Non Remote checkout should be disallowed");

        WorkflowJob p = rule.jenkins.createProject(WorkflowJob.class, "pipeline");
        String aliasName = "alias1";
        // configure mercurial installation with an alias in a path
        rule.jenkins.getDescriptorByType(MercurialInstallation.DescriptorImpl.class).setInstallations(new MercurialInstallation("mercurial", "", "hg", false, false,"", false, "[paths]\n" + aliasName + " = " + repo.getPath(), null));
        String script = """
                node {
                checkout([$class: 'MercurialSCM', credentialsId: '', installation: 'mercurial', source: 'alias1'])
                }""";
        p.setDefinition(new CpsFlowDefinition(script, true));
        m.hg(new FilePath(repo), "init");
        m.touchAndCommit(new FilePath(repo), "a");
        rule.assertBuildStatus(Result.SUCCESS, p.scheduleBuild2(0));
    }

    private static File newFolder(File root, String... subDirs) throws IOException {
        String subFolder = String.join("/", subDirs);
        File result = new File(root, subFolder);
        if (!result.mkdirs()) {
            throw new IOException("Couldn't create folders " + root);
        }
        return result;
    }
}
