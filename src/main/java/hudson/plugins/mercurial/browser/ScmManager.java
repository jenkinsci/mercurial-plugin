package hudson.plugins.mercurial.browser;

import hudson.Extension;
import hudson.plugins.mercurial.MercurialChangeSet;
import hudson.util.FormValidation;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;

/**
 * Mercurial web interface served using a <a href="https://scm-manager.org/">SCM-Manager</a> repository.
 */
public class ScmManager extends HgBrowser {

  @DataBoundConstructor
  public ScmManager(String url) throws MalformedURLException {
    super(url);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public URL getChangeSetLink(MercurialChangeSet changeSet) throws IOException {
    current = changeSet;
    return new URL(getUrl(), "code/changeset/" + changeSet.getNode());
  }

  /**
   * {@inheritDoc}
   *
   * Throws {@link IllegalStateException} when this method is called before at least one call
   * to {@link #getChangeSetLink}.
   */
  @Override
  public URL getFileLink(String path) throws MalformedURLException {
    checkCurrentIsNotNull();
    return new URL(getUrl(), "code/sources/" + current.getNode() + "/" + path);
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
    return new URL(getUrl(), "code/changeset/" + current.getNode() + "/#diff-" + path);
  }

  @Extension
  public static class DescriptorImpl extends HgBrowser.HgBrowserDescriptor {

    public String getDisplayName() {
      return "SCM-Manager";
    }

    @Override
    public FormValidation doCheckUrl(@QueryParameter String url) {
      if (url.matches("https?://.*/repo/[^/]+/[^/]+/?")) {
        return FormValidation.ok();
      } else {
        return FormValidation.warning("Possibly incorrect root URL; expected url which starts with http or https and ends with /repo/namespace/name");
      }
    }

  }
}
