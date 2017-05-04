package hudson.plugins.mercurial;

import hudson.model.TaskListener;
import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import jenkins.scm.api.trait.SCMSourceRequest;

public class MercurialSCMSourceRequest extends SCMSourceRequest {
    private final String credentialsId;
    private final String installation;
    private final String source;
    protected MercurialSCMSourceRequest(@Nonnull MercurialSCMSource source,
                                        @Nonnull MercurialSCMSourceContext<?> context,
                                        @CheckForNull TaskListener listener) {
        super(source, context, listener);
        this.credentialsId = context.credentialsId();
        this.installation = context.installation();
        this.source = source.getSource();
    }

    public String installation() {
        return this.installation;
    }

    public String credentialsId() {
        return this.credentialsId;
    }

    public String source() {
        return this.source;
    }
}
