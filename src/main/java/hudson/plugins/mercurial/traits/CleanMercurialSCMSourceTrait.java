package hudson.plugins.mercurial.traits;

import hudson.Extension;
import hudson.plugins.mercurial.MercurialSCM;
import hudson.plugins.mercurial.MercurialSCMBuilder;
import hudson.plugins.mercurial.MercurialSCMSource;
import hudson.scm.SCM;
import javax.annotation.Nonnull;
import jenkins.scm.api.trait.SCMBuilder;
import jenkins.scm.api.trait.SCMSourceTrait;
import jenkins.scm.api.trait.SCMSourceTraitDescriptor;
import org.kohsuke.stapler.DataBoundConstructor;

/**
 * A {@link SCMSourceTrait} for {@link MercurialSCMSource} that configures {@link MercurialSCM#setClean(boolean)}.
 *
 * @since 2.0
 */
public class CleanMercurialSCMSourceTrait extends SCMSourceTrait {
    /**
     * Constructor.
     */
    @DataBoundConstructor public CleanMercurialSCMSourceTrait() { }

    /**
     * {@inheritDoc}
     */
    @Override protected void decorateBuilder(SCMBuilder<?, ?> builder) {
        ((MercurialSCMBuilder<?>) builder).withClean(true);
    }

    @Extension public static class DescriptorImpl extends SCMSourceTraitDescriptor {
        @Override public @Nonnull String getDisplayName() {
            return "Clean Build";
        }

        /**
         * {@inheritDoc}
         */
        @Override public Class<? extends SCMBuilder> getBuilderClass() {
            return MercurialSCMBuilder.class;
        }

        /**
         * {@inheritDoc}
         */
        @Override public Class<? extends SCM> getScmClass() {
            return MercurialSCM.class;
        }
    }
}
