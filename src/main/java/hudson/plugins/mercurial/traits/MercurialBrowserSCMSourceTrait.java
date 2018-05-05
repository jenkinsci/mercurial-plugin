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

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.model.Descriptor;
import hudson.plugins.mercurial.MercurialSCM;
import hudson.plugins.mercurial.MercurialSCMBuilder;
import hudson.plugins.mercurial.MercurialSCMSource;
import hudson.plugins.mercurial.browser.HgBrowser;
import hudson.scm.RepositoryBrowser;
import hudson.scm.RepositoryBrowsers;
import hudson.scm.SCM;
import java.util.List;
import javax.annotation.Nonnull;
import jenkins.scm.api.trait.SCMBuilder;
import jenkins.scm.api.trait.SCMSourceTrait;
import jenkins.scm.api.trait.SCMSourceTraitDescriptor;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.DataBoundConstructor;

/**
 * Exposes {@link HgBrowser} configuration of a {@link MercurialSCMSource} as a {@link SCMSourceTrait}.
 *
 * @since 2.0
 */
public class MercurialBrowserSCMSourceTrait extends SCMSourceTrait {

    /**
     * The configured {@link HgBrowser} or {@code null} to use the "auto" browser.
     */
    @CheckForNull
    private final HgBrowser browser;

    /**
     * Stapler constructor.
     *
     * @param browser the {@link HgBrowser} or {@code null} to use the "auto" browser.
     */
    @DataBoundConstructor
    public MercurialBrowserSCMSourceTrait(@CheckForNull HgBrowser browser) {
        this.browser = browser;
    }

    /**
     * Gets the {@link HgBrowser}..
     *
     * @return the {@link HgBrowser} or {@code null} to use the "auto" browser.
     */
    @CheckForNull
    public HgBrowser getBrowser() {
        return browser;
    }

    /**
     * {@inheritDoc}
     */
    @Override protected void decorateBuilder(SCMBuilder<?, ?> builder) {
        ((MercurialSCMBuilder<?>) builder).withBrowser(browser);
    }

    /**
     * Our {@link Descriptor}
     */
    @Extension public static class DescriptorImpl extends SCMSourceTraitDescriptor {

        /**
         * {@inheritDoc}
         */
        @Override public @Nonnull String getDisplayName() {
            return Messages.MercurialBrowserSCMSourceTrait_displayName();
        }

        /**
         * Expose the {@link HgBrowser} instances to stapler.
         *
         * @return the {@link HgBrowser} instances
         */
        @Restricted(NoExternalUse.class) // stapler
        public List<Descriptor<RepositoryBrowser<?>>> getBrowserDescriptors() {
            return RepositoryBrowsers.filter(HgBrowser.class);
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
