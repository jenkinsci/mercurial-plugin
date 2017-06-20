/*
 * The MIT License
 *
 * Copyright (c) 2017 CloudBees, Inc.
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
 *
 */

package hudson.plugins.mercurial.traits;

import hudson.Extension;
import hudson.Util;
import hudson.plugins.mercurial.MercurialInstallation;
import hudson.plugins.mercurial.MercurialSCM;
import hudson.plugins.mercurial.MercurialSCMBuilder;
import hudson.plugins.mercurial.MercurialSCMSource;
import hudson.plugins.mercurial.MercurialSCMSourceContext;
import hudson.scm.SCM;
import hudson.util.ListBoxModel;
import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import jenkins.scm.api.SCMSource;
import jenkins.scm.api.trait.SCMBuilder;
import jenkins.scm.api.trait.SCMSourceContext;
import jenkins.scm.api.trait.SCMSourceRequest;
import jenkins.scm.api.trait.SCMSourceTrait;
import jenkins.scm.api.trait.SCMSourceTraitDescriptor;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.DataBoundConstructor;

/**
 * Exposes {@link MercurialInstallation} configuration of a {@link MercurialSCMSource} as a {@link SCMSourceTrait}.
 *
 * @since 2.0
 */
public class MercurialInstallationSCMSourceTrait extends SCMSourceTrait {
    /**
     * The {@link MercurialInstallation#getName()} or {@code null} to use the "system" default.
     */
    private final @CheckForNull String installation;

    /**
     * Stapler constructor.
     *
     * @param installation the {@link MercurialInstallation#getName()} or {@code null} to use the "system" default.
     */
    @DataBoundConstructor public MercurialInstallationSCMSourceTrait(@CheckForNull String installation) {
        this.installation = Util.fixEmpty(installation);
    }

    /**
     * Returns the {@link MercurialInstallation#getName()}.
     *
     * @return the {@link MercurialInstallation#getName()} or {@code null} to use the "system" default.
     */
    public @CheckForNull String getInstallation() {
        return installation;
    }

    /**
     * {@inheritDoc}
     */
    @Override protected void decorateContext(SCMSourceContext<?, ?> context) {
        ((MercurialSCMSourceContext<?>)context).withInstallation(installation);
    }

    /**
     * {@inheritDoc}
     */
    @Override protected void decorateBuilder(SCMBuilder<?, ?> builder) {
        ((MercurialSCMBuilder<?>) builder).withInstallation(installation);
    }

    /**
     * Our {@link hudson.model.Descriptor}
     */
    @Extension public static class DescriptorImpl extends SCMSourceTraitDescriptor {

        /**
         * {@inheritDoc}
         */
        @Override public @Nonnull String getDisplayName() {
            return Messages.MercurialInstallationSCMSourceTrait_displayName();
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
        @Override public Class<? extends SCMSourceContext> getContextClass() {
            return MercurialSCMSourceContext.class;
        }

        /**
         * {@inheritDoc}
         */
        @Override public Class<? extends SCM> getScmClass() {
            return MercurialSCM.class;
        }

        /**
         * {@inheritDoc}
         */
        @Override public boolean isApplicableToBuilder(@Nonnull Class<? extends SCMBuilder> builderClass) {
            if (super.isApplicableToBuilder(builderClass)) {
                for (MercurialInstallation i : MercurialInstallation.allInstallations()) {
                    if (i.isUseCaches()) {
                        return true;
                    }
                }
            }
            return false;
        }

        /**
         * {@inheritDoc}
         */
        @Override public boolean isApplicableToContext(@Nonnull Class<? extends SCMSourceContext> contextClass) {
            if (super.isApplicableToContext(contextClass)) {
                for (MercurialInstallation i : MercurialInstallation.allInstallations()) {
                    if (i.isUseCaches()) {
                        return true;
                    }
                }
            }
            return false;
        }

        /**
         * {@inheritDoc}
         */
        @Override public boolean isApplicableToSCM(@Nonnull Class<? extends SCM> scmClass) {
            if (super.isApplicableToSCM(scmClass)) {
                for (MercurialInstallation i : MercurialInstallation.allInstallations()) {
                    if (i.isUseCaches()) {
                        return true;
                    }
                }
            }
            return false;
        }

        /**
         * Returns the list of {@link MercurialInstallation} items.
         *
         * @return the list of {@link MercurialInstallation} items.
         */
        @Restricted(NoExternalUse.class) // stapler
        public ListBoxModel doFillInstallationItems() {
            ListBoxModel result = new ListBoxModel();
            for (MercurialInstallation i: MercurialInstallation.allInstallations()) {
                if (i.isUseCaches()) {
                    result.add(i.getName());
                }
            }
            return result;
        }

    }
}
