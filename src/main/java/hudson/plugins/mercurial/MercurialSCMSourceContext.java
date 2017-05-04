package hudson.plugins.mercurial;

import com.cloudbees.plugins.credentials.common.IdCredentials;
import hudson.model.TaskListener;
import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import jenkins.scm.api.SCMHeadObserver;
import jenkins.scm.api.SCMSource;
import jenkins.scm.api.SCMSourceCriteria;
import jenkins.scm.api.trait.SCMSourceContext;

public class MercurialSCMSourceContext<C extends MercurialSCMSourceContext<C>> extends SCMSourceContext<C, MercurialSCMSourceRequest> {
    /**
     * The {@link IdCredentials#getId()} of the credentials to use or {@code null} to use none / the installation defaults.
     */
    private String credentialsId;
    /**
     * The {@link MercurialInstallation#getName()}.
     */
    private String installation;

    public MercurialSCMSourceContext(@CheckForNull SCMSourceCriteria criteria,
                                     @Nonnull SCMHeadObserver observer) {
        super(criteria, observer);
    }

    public final String credentialsId() {
        return credentialsId;
    }

    public final String installation() {
        return installation;
    }

    @SuppressWarnings("unchecked") public @Nonnull C withCredentialsId(String credentialsId) {
        this.credentialsId = credentialsId;
        return (C) this;
    }

    @SuppressWarnings("unchecked") public @Nonnull C withInstallation(String installation) {
        this.installation = installation;
        return (C) this;
    }

    @Override public @Nonnull MercurialSCMSourceRequest newRequest(@Nonnull SCMSource scmSource,
                                                                   @CheckForNull TaskListener taskListener) {
        return new MercurialSCMSourceRequest((MercurialSCMSource) scmSource, this, taskListener);
    }
}
