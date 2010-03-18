package hudson.plugins.mercurial;

import hudson.model.Hudson;
import hudson.tools.ToolProperty;
import java.util.Collections;

public class CachingSCMTest extends MercurialSCMTest {

    protected @Override void setUp() throws Exception {
        super.setUp();
        hgInstallation = "caching";
        Hudson.getInstance().getDescriptorByType(MercurialInstallation.DescriptorImpl.class).setInstallations(
                new MercurialInstallation("caching", "", "hg", null, false, true, Collections.<ToolProperty<?>>emptyList()));
        MercurialSCM.CACHE_LOCAL_REPOS = true;
    }

}
