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
