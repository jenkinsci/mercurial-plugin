/*
 * The MIT License
 *
 * Copyright (c) 2016 CloudBees, Inc.
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

import java.io.Serializable;
import java.net.URI;
import javax.annotation.Nonnull;
import jenkins.scm.api.SCMHeadEvent;

/**
 * Payload for a {@link SCMHeadEvent}
 *
 * @since 1.58-beta-1
 */
public class MercurialCommitPayload implements Serializable {
    @Nonnull
    private final URI url;
    @Nonnull
    private final String branch;
    @Nonnull
    private final String changesetId;

    public MercurialCommitPayload(@Nonnull URI url, @Nonnull String branch, @Nonnull String commitId) {
        this.url = url;
        this.branch = branch;
        this.changesetId = commitId;
    }

    @Nonnull
    public URI getUrl() {
        return url;
    }

    @Nonnull
    public String getBranch() {
        return branch;
    }

    @Nonnull
    public String getChangesetId() {
        return changesetId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        MercurialCommitPayload that = (MercurialCommitPayload) o;

        if (!url.equals(that.url)) {
            return false;
        }
        if (!branch.equals(that.branch)) {
            return false;
        }
        return changesetId.equals(that.changesetId);
    }

    @Override
    public int hashCode() {
        int result = url.hashCode();
        result = 31 * result + branch.hashCode();
        result = 31 * result + changesetId.hashCode();
        return result;
    }

    @Override
    public String toString() {
        return "MercurialCommitPayload{" +
                "url='" + url+ '\'' +
                ", branch='" + branch + '\'' +
                ", commitId='" + changesetId + '\'' +
                '}';
    }
}
