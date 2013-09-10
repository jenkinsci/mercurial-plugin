package hudson.plugins.mercurial;

import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.common.StandardUsernameListBoxModel;
import com.cloudbees.plugins.credentials.common.StandardUsernamePasswordCredentials;
import com.cloudbees.plugins.credentials.domains.URIRequirementBuilder;
import hudson.Extension;
import hudson.model.TaskListener;
import hudson.scm.SCM;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import java.io.IOException;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import jenkins.scm.api.SCMHead;
import jenkins.scm.api.SCMHeadObserver;
import jenkins.scm.api.SCMRevision;
import jenkins.scm.api.SCMSource;
import jenkins.scm.api.SCMSourceDescriptor;
import jenkins.scm.api.SCMSourceOwner;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

public final class MercurialSCMFactory extends SCMSource {
    
    private final String source;
    private final String credentialsId;
    private final String branchPattern;

    @DataBoundConstructor
    public MercurialSCMFactory(String id, String source, String credentialsId, String branchPattern) {
        super(id);
        this.source = source;
        this.credentialsId = credentialsId;
        this.branchPattern = branchPattern;
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

    @Override public <O extends SCMHeadObserver> O fetch(O observer, TaskListener listener) throws IOException, InterruptedException {
        // TODO
        return observer;
    }

    // TODO possibly override other fetch variants as well

    @Override public SCM build(SCMHead head, SCMRevision revision) {
        return null; // TODO
    }

    @Extension public static final class DescriptorImpl extends SCMSourceDescriptor {

        @Override public String getDisplayName() {
            return "Mercurial";
        }

        public ListBoxModel doFillCredentialsIdItems(@AncestorInPath SCMSourceOwner owner, @QueryParameter String source) {
            return new StandardUsernameListBoxModel()
                    .withEmptySelection()
                    .withAll(CredentialsProvider.lookupCredentials(StandardUsernamePasswordCredentials.class, owner, null, URIRequirementBuilder.fromUri(source).build()));
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

}
