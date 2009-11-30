package hudson.plugins.mercurial;

import hudson.model.Hudson;
import hudson.tools.ToolProperty;
import java.util.Collections;

public class DebugFlagTest extends MercurialSCMTest {

    protected @Override void setUp() throws Exception {
        super.setUp();
        hgInstallation = "debug";
        Hudson.getInstance().getDescriptorByType(MercurialInstallation.DescriptorImpl.class).setInstallations(
                new MercurialInstallation("debug", "", "hg", null, true, Collections.<ToolProperty<?>>emptyList()));
    }

}
