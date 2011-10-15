package hudson.plugins.mercurial.browser;

import hudson.Extension;
import hudson.model.Descriptor;
import hudson.plugins.mercurial.MercurialChangeSet;
import hudson.scm.RepositoryBrowser;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;

import net.sf.json.JSONObject;

import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest;

/**
 * Mercurial web interface served using Rhodecode
 */
public class Rhodecode extends HgBrowser {

    @DataBoundConstructor
    public Rhodecode(String url) throws MalformedURLException {
        super(url);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public URL getChangeSetLink(MercurialChangeSet changeSet)
            throws IOException {
        current = changeSet;
        // TODO: consider verifying the repository connection to tip at configuration time?
        return new URL(getUrl(), "changeset/" + changeSet.getShortNode());
    }

    /**
     * {@inheritDoc}
     *
     * Throws {@link IllegalStateException} when this method is called before at least one call
     * to {@literal getChangeSetLink(MercurialChangeSet)}.
     */
    @Override
    public URL getFileLink(String path) throws MalformedURLException {
        checkCurrentIsNotNull();
        return new URL(getUrl(), "files/" + current.getShortNode() + "/" + path);
    }

    /**
     * {@inheritDoc}
     *
     * Throws {@link IllegalStateException} when this method is called before at least one call
     * to {@literal getChangeSetLink(MercurialChangeSet)}.
     */
    @Override
    public URL getDiffLink(String path) throws MalformedURLException {
        checkCurrentIsNotNull();
        return new URL(getUrl(), "changeset/" + current.getShortNode() + "#C" + path.replace("/", "-"));
    }

    @Extension
    public static class DescriptorImpl extends Descriptor<RepositoryBrowser<?>> {
        public String getDisplayName() {
            return "rhodecode";
        }

        public @Override Rhodecode newInstance(StaplerRequest req, JSONObject json) throws FormException {
            return req.bindParameters(Rhodecode.class,"rhodecode.");
        }
    }

    private static final long serialVersionUID = 1L;
}

