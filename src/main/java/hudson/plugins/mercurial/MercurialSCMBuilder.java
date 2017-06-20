package hudson.plugins.mercurial;

import com.cloudbees.plugins.credentials.common.IdCredentials;
import hudson.plugins.mercurial.browser.HgBrowser;
import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import jenkins.scm.api.SCMHead;
import jenkins.scm.api.SCMRevision;
import jenkins.scm.api.trait.SCMBuilder;

public class MercurialSCMBuilder<B extends MercurialSCMBuilder<B>> extends SCMBuilder<B, MercurialSCM> {
    /**
     * The browser to use or {@code null} to use the "auto" browser.
     */
    private @CheckForNull HgBrowser browser;
    /**
     * {@code true} to clean local modifications and untracked files.
     */
    private boolean clean;
    /**
     * The {@link IdCredentials#getId()} of the credentials to use or {@code null} to use none / the installation
     * defaults.
     */
    private @CheckForNull String credentialsId;
    /**
     * The {@link MercurialInstallation#getName()}.
     */
    private @CheckForNull String installation;
    /**
     * The repository to track. This can be URL or a local file path.
     */
    private @Nonnull String source;

    public MercurialSCMBuilder(@Nonnull SCMHead head, @CheckForNull SCMRevision revision, @Nonnull String source,
                               @CheckForNull String credentialsId) {
        super(MercurialSCM.class, head, revision);
        this.source = source;
        this.credentialsId = credentialsId;
    }

    public final HgBrowser browser() {
        return browser;
    }

    public final boolean clean() {
        return clean;
    }

    public final String credentialsId() {
        return credentialsId;
    }

    public final String installation() {
        return installation;
    }

    public final String source() {
        return source;
    }

    @SuppressWarnings("unchecked") public @Nonnull B withBrowser(HgBrowser browser) {
        this.browser = browser;
        return (B) this;
    }

    @SuppressWarnings("unchecked") public @Nonnull B withClean(boolean clean) {
        this.clean = clean;
        return (B) this;
    }

    @SuppressWarnings("unchecked") public @Nonnull B withCredentialsId(String credentialsId) {
        this.credentialsId = credentialsId;
        return (B) this;
    }

    @SuppressWarnings("unchecked") public @Nonnull B withInstallation(String installation) {
        this.installation = installation;
        return (B) this;
    }

    @SuppressWarnings("unchecked")
    public @Nonnull
    B withSource(String source) {
        this.source = source;
        return (B) this;
    }

    @Override public @Nonnull MercurialSCM build() {
        SCMRevision revision = revision();
        MercurialSCM result = new MercurialSCM(source());
        if (revision instanceof MercurialSCMSource.MercurialRevision) {
            result.setRevisionType(MercurialSCM.RevisionType.CHANGESET);
            result.setRevision(((MercurialSCMSource.MercurialRevision) revision).getHash());
        } else {
            result.setRevisionType(MercurialSCM.RevisionType.BRANCH);
            result.setRevision(head().getName());
        }
        result.setBrowser(browser());
        result.setClean(clean());
        result.setCredentialsId(credentialsId());
        result.setInstallation(installation());
        result.setDisableChangeLog(false);
        return result;
    }
}
