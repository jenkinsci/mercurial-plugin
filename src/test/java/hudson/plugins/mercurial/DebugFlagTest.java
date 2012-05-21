package hudson.plugins.mercurial;

import hudson.model.Hudson;
import hudson.tools.ToolProperty;

import java.util.Collections;

public class DebugFlagTest extends MercurialSCMTest {

    private static final String DEBUG_INSTALLATION = "debug";

    protected @Override void setUp() throws Exception {
        super.setUp();
        Hudson.getInstance()
                .getDescriptorByType(MercurialInstallation.DescriptorImpl.class)
                .setInstallations(
                        new MercurialInstallation(DEBUG_INSTALLATION, "", "hg",
                                true, false, false, Collections
                                        .<ToolProperty<?>> emptyList()));
    }

    @Override protected String hgInstallation() {
        return DEBUG_INSTALLATION;
    }

}
