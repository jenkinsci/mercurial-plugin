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
import hudson.plugins.mercurial.MercurialSCM;
import hudson.plugins.mercurial.MercurialSCMBuilder;
import hudson.plugins.mercurial.MercurialSCMSource;
import hudson.plugins.mercurial.MercurialSCMSourceContext;
import hudson.scm.SCM;
import edu.umd.cs.findbugs.annotations.NonNull;
import jenkins.scm.api.SCMSource;
import jenkins.scm.api.trait.SCMBuilder;
import jenkins.scm.api.trait.SCMSourceContext;
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
        @Override public @NonNull String getDisplayName() {
            return Messages.CleanMercurialSCMSourceTrait_displayName();
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
        @Override
        public Class<? extends SCMSourceContext> getContextClass() {
            return MercurialSCMSourceContext.class;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public Class<? extends SCMSource> getSourceClass() {
            return MercurialSCMSource.class;
        }

        /**
         * {@inheritDoc}
         */
        @Override public Class<? extends SCM> getScmClass() {
            return MercurialSCM.class;
        }
    }
}
