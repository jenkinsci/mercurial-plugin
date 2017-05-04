package hudson.plugins.mercurial.traits;

import hudson.Extension;
import hudson.plugins.mercurial.MercurialSCMBuilder;
import hudson.scm.SCM;
import javax.annotation.Nonnull;
import jenkins.scm.api.trait.SCMBuilder;
import jenkins.scm.api.trait.SCMSourceTrait;
import jenkins.scm.api.trait.SCMSourceTraitDescriptor;
import org.kohsuke.stapler.DataBoundConstructor;

public class CleanMercurialSCMSourceTrait extends SCMSourceTrait {
    @DataBoundConstructor public CleanMercurialSCMSourceTrait() { }

    @Override protected <B extends SCMBuilder<B, S>, S extends SCM> void decorateBuilder(B builder) {
        ((MercurialSCMBuilder<?>) builder).withClean(true);
    }

    @Extension public static class DescriptorImpl extends SCMSourceTraitDescriptor {
        @Override public @Nonnull String getDisplayName() {
            return "Clean Build";
        }

        @Override public boolean isApplicableToBuilder(@Nonnull Class<? extends SCMBuilder> builderClass) {
            return MercurialSCMBuilder.class.isAssignableFrom(builderClass)
                    && super.isApplicableToBuilder(builderClass);
        }
    }
}
