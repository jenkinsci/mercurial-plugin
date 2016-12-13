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

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.scm.SCM;
import java.util.Collections;
import java.util.Map;
import jenkins.scm.api.SCMHead;
import jenkins.scm.api.SCMHeadEvent;
import jenkins.scm.api.SCMNavigator;
import jenkins.scm.api.SCMRevision;
import jenkins.scm.api.SCMSource;

/**
 * Implementation of {@link SCMHeadEvent} for {@link MercurialSCM} / {@link MercurialSCMSource}.
 *
 * @since FIXME
 */
public class MercurialSCMHeadEvent extends SCMHeadEvent<MercurialCommitPayload> {
    private final MercurialCommitPayload payload;

    public MercurialSCMHeadEvent(Type type, MercurialCommitPayload payload) {
        super(type, payload);
        this.payload = payload;
    }

    @Override
    public boolean isMatch(@NonNull SCMNavigator navigator) {
        return false; // because we do not have a Mercurial SCM Navigator
    }

    @NonNull
    @Override
    public String getSourceName() {
        return null; // because we do not have a Mercurial SCM Navigator
    }

    @NonNull
    @Override
    public Map<SCMHead, SCMRevision> heads(@NonNull SCMSource source) {
        if (source instanceof MercurialSCMSource) {
            MercurialSCMSource hg = (MercurialSCMSource) source;
            String repository = hg.getSource();
            if (repository != null) {
                if (MercurialStatus.looselyMatches(payload.getUrl(), repository)) {
                    SCMHead head = new SCMHead(getPayload().getBranch());
                    SCMRevision revision = new MercurialSCMSource.MercurialRevision(
                            head, getPayload().getChangesetId()
                    );
                    return Collections.singletonMap(head, revision);
                }
            }
        }
        return Collections.emptyMap();
    }

    @Override
    public boolean isMatch(@NonNull SCM scm) {
        if (scm instanceof MercurialSCM) {
            MercurialSCM hg = (MercurialSCM) scm;
            String repository = hg.getSource();
            if (repository != null) {
                if (MercurialStatus.looselyMatches(payload.getUrl(), repository)) {
                    return hg.getRevisionType() == MercurialSCM.RevisionType.BRANCH
                            && payload.getBranch().equals(hg.getRevision());
                }
            }
        }
        return false;
    }
}
