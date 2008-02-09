package hudson.plugins.mercurial;

import hudson.Plugin;
import hudson.plugins.mercurial.browser.HgWeb;
import hudson.scm.RepositoryBrowsers;
import hudson.scm.SCMS;

/**
 * Plugin entry point.
 *
 * @author Kohsuke Kawaguchi
 * @plugin
 */
public class PluginImpl extends Plugin {
    public void start() throws Exception {
        SCMS.SCMS.add(MercurialSCM.DescriptorImpl.DESCRIPTOR);
        RepositoryBrowsers.LIST.add(HgWeb.DESCRIPTOR);
     }
}
