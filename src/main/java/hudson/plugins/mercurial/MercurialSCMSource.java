package hudson.plugins.mercurial;

import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.common.StandardUsernameCredentials;
import com.cloudbees.plugins.credentials.common.StandardUsernameListBoxModel;
import com.cloudbees.plugins.credentials.common.StandardUsernamePasswordCredentials;
import com.cloudbees.plugins.credentials.domains.URIRequirementBuilder;
import hudson.EnvVars;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.Node;
import hudson.model.TaskListener;
import hudson.scm.SCM;
import hudson.util.ArgumentListBuilder;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import java.io.IOException;
import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import javax.annotation.CheckForNull;
import jenkins.model.Jenkins;
import jenkins.scm.api.SCMHead;
import jenkins.scm.api.SCMHeadObserver;
import jenkins.scm.api.SCMRevision;
import jenkins.scm.api.SCMSource;
import jenkins.scm.api.SCMSourceDescriptor;
import jenkins.scm.api.SCMSourceOwner;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

public final class MercurialSCMSource extends SCMSource {

    private final String installation;
    private final String source;
    private final String credentialsId;
    private final String branchPattern;

    @DataBoundConstructor
    public MercurialSCMSource(String id, String installation, String source, String credentialsId, String branchPattern) {
        super(id);
        this.installation = installation;
        this.source = source;
        this.credentialsId = credentialsId;
        this.branchPattern = branchPattern;
    }
    
    public String getInstallation() {
        return installation;
    }

    public String getSource() {
        return source;
    }

    public String getCredentialsId() {
        return credentialsId;
    }

    public String getBranchPattern() {
        return branchPattern;
    }

    @Override
    protected void retrieve(SCMHeadObserver observer, TaskListener listener) throws IOException, InterruptedException {
        MercurialInstallation inst = MercurialSCM.findInstallation(installation);
        if (inst == null) {
            listener.error("No configured Mercurial installation");
            return;
        }
        if (!inst.isUseCaches()) {
            listener.error("Mercurial installation " + installation + " does not support caches");
            return;
        }
        Node node = Jenkins.getInstance();
        Launcher launcher = node.createLauncher(listener);
        FilePath cache = Cache.fromURL(source, getCredentials()).repositoryCache(inst, node, launcher, listener, true);
        if (cache == null) {
            listener.error("Could not use caches, not fetching branch heads");
            return;
        }
        String heads = new HgExe(inst, launcher, node, listener, new EnvVars()).popen(cache, listener, true, new ArgumentListBuilder("heads", "--template", "{node} {branch}\\n"));
        for (String line : heads.split("\r?\n")) {
            String[] nodeBranch = line.split(" ", 2);
            SCMHead branch = new SCMHead(nodeBranch[1]);
            observer.observe(branch, new MercurialRevision(branch, nodeBranch[0]));
        }
    }

    @Override public SCM build(SCMHead head, SCMRevision revision) {
        String rev = revision == null ? head.getName() : ((MercurialRevision) revision).hash;
        return new MercurialSCM(installation, source, rev, null, null, null, true, credentialsId);
    }

    // TODO call getOwner().onSCMSourceUpdated(this) in response to MercurialStatus.handleNotifyCommit

    private @CheckForNull StandardUsernameCredentials getCredentials() {
        if (credentialsId != null) {
            for (StandardUsernameCredentials c : availableCredentials(getOwner(), source)) {
                if (c.getId().equals(credentialsId)) {
                    return c;
                }
            }
        }
        return null;
    }

    private static List<StandardUsernamePasswordCredentials> availableCredentials(@CheckForNull SCMSourceOwner owner, @CheckForNull String source) {
        return CredentialsProvider.lookupCredentials(StandardUsernamePasswordCredentials.class, owner, null, URIRequirementBuilder.fromUri(source).build());
    }

    @Extension public static final class DescriptorImpl extends SCMSourceDescriptor {

        @Override public String getDisplayName() {
            return "Mercurial";
        }

        public ListBoxModel doFillCredentialsIdItems(@AncestorInPath SCMSourceOwner owner, @QueryParameter String source) {
            return new StandardUsernameListBoxModel()
                    .withEmptySelection()
                    .withAll(availableCredentials(owner, source));
        }

        public FormValidation doCheckBranchPattern(@QueryParameter String value) {
            try {
                Pattern.compile(value);
                return FormValidation.ok();
            } catch (PatternSyntaxException x) {
                return FormValidation.error(x.getDescription());
            }
        }

    }

    private static final class MercurialRevision extends SCMRevision {
        private final String hash;
        MercurialRevision(SCMHead branch, String hash) {
            super(branch);
            this.hash = hash;
        }
        @Override public boolean equals(Object obj) {
            return obj instanceof MercurialRevision && ((MercurialRevision) obj).hash.equals(hash);
        }
        @Override public int hashCode() {
            return hash.hashCode();
        }
        @Override public String toString() {
            return getHead().getName() + ":" + hash;
        }
    }

}
