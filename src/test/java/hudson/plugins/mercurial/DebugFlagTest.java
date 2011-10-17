package hudson.plugins.mercurial;

import hudson.model.Hudson;
import hudson.tools.ToolProperty;

import java.util.Collections;

public class DebugFlagTest extends MercurialSCMTest {

    public static final String DEBUG_INSTALLATION = "debug";

    protected @Override
    void setUp() throws Exception {
        super.setUp();
        hgInstallation = DEBUG_INSTALLATION;
        Hudson.getInstance()
                .getDescriptorByType(MercurialInstallation.DescriptorImpl.class)
                .setInstallations(
                        new MercurialInstallation(DEBUG_INSTALLATION, "", "hg",
                                null, true, false, false, Collections
                                        .<ToolProperty<?>> emptyList()));
    }

}
