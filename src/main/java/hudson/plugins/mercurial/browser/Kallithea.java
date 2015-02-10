package hudson.plugins.mercurial.browser;

import hudson.Extension;
import hudson.util.FormValidation;

import java.net.MalformedURLException;


import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

/**
 * Mercurial web interface served using a <a
 * href="https://kallithea-scm.org/">Kallithea</a> repository.
 */
public class Kallithea extends RhodeCode {

    @DataBoundConstructor
    public Kallithea(String url) throws MalformedURLException {
        super(url);
    }

    @Extension
    public static class DescriptorImpl extends HgBrowser.HgBrowserDescriptor {
        public String getDisplayName() {
            return "kallithea";
        }

        @Override public FormValidation doCheckUrl(@QueryParameter String url) {
            return _doCheckUrl(url);
        }

    }
}
