package hudson.plugins.mercurial;

import hudson.tools.ToolProperty;

import java.util.Collections;
import org.junit.Before;

public class SharingSCMTest extends SCMTestBase {

    private static final String SHARING_INSTALLATION = "sharing";

    @Override @Before public void setUp() throws Exception {
        super.setUp();
        j.jenkins
                .getDescriptorByType(MercurialInstallation.DescriptorImpl.class)
                .setInstallations(
                        new MercurialInstallation(SHARING_INSTALLATION, "",
                                "hg", false, true, true, false, Collections
                                        .<ToolProperty<?>> emptyList()));
        MercurialSCM.CACHE_LOCAL_REPOS = true;
    }

    @Override protected String hgInstallation() {
        return SHARING_INSTALLATION;
    }

}
