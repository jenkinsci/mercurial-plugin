package hudson.plugins.mercurial;

import hudson.Plugin;
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
    }
}
