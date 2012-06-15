package hudson.plugins.mercurial;

import hudson.model.Hudson;
import hudson.tools.ToolProperty;

import java.util.Collections;

public class SharingSCMTest extends MercurialSCMTest {

    private static final String SHARING_INSTALLATION = "sharing";

    protected @Override void setUp() throws Exception {
        super.setUp();
        Hudson.getInstance()
                .getDescriptorByType(MercurialInstallation.DescriptorImpl.class)
                .setInstallations(
                        new MercurialInstallation(SHARING_INSTALLATION, "",
                                "hg", false, true, true, Collections
                                        .<ToolProperty<?>> emptyList()));
        MercurialSCM.CACHE_LOCAL_REPOS = true;
    }

    @Override protected String hgInstallation() {
        return SHARING_INSTALLATION;
    }

}
