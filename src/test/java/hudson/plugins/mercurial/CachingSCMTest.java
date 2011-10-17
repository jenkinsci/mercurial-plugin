package hudson.plugins.mercurial;

import hudson.model.Hudson;
import hudson.tools.ToolProperty;

import java.util.Collections;

public class CachingSCMTest extends MercurialSCMTest {

    public static final String CACHING_INSTALLATION = "caching";

    protected @Override
    void setUp() throws Exception {
        super.setUp();
        hgInstallation = CACHING_INSTALLATION;
        Hudson.getInstance()
                .getDescriptorByType(MercurialInstallation.DescriptorImpl.class)
                .setInstallations(
                        new MercurialInstallation(CACHING_INSTALLATION, "",
                                "hg", false, true, false, Collections
                                        .<ToolProperty<?>> emptyList()));
        MercurialSCM.CACHE_LOCAL_REPOS = true;
    }

}
