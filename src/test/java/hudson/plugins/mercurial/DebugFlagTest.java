package hudson.plugins.mercurial;

import hudson.tools.ToolProperty;

import java.util.Collections;
import org.junit.Before;

public class DebugFlagTest extends SCMTestBase {

    private static final String DEBUG_INSTALLATION = "debug";

    @Override @Before public void setUp() throws Exception {
        super.setUp();
        j.jenkins
                .getDescriptorByType(MercurialInstallation.DescriptorImpl.class)
                .setInstallations(
                        new MercurialInstallation(DEBUG_INSTALLATION, "", "hg",
                                true, false, false, false, Collections
                                        .<ToolProperty<?>> emptyList()));
    }

    @Override protected String hgInstallation() {
        return DEBUG_INSTALLATION;
    }

}
