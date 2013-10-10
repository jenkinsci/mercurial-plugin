package hudson.plugins.mercurial;

import hudson.tools.ToolProperty;

import java.util.Collections;
import org.junit.Before;

public class CachingSCMTest extends SCMTestBase {

    private static final String CACHING_INSTALLATION = "caching";

    @Override @Before public void setUp() throws Exception {
        super.setUp();
        j.jenkins
                .getDescriptorByType(MercurialInstallation.DescriptorImpl.class)
                .setInstallations(
                        new MercurialInstallation(CACHING_INSTALLATION, "",
                                "hg", false, true, false, false, Collections
                                        .<ToolProperty<?>> emptyList()));
        MercurialSCM.CACHE_LOCAL_REPOS = true;
    }

    @Override protected String hgInstallation() {
        return CACHING_INSTALLATION;
    }

}
