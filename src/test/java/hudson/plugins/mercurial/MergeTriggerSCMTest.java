package hudson.plugins.mercurial;

import hudson.model.Hudson;
import hudson.tools.ToolProperty;

import java.util.Collections;

public class MergeTriggerSCMTest extends MercurialSCMTest {

    public static final String MERGE_TRIGGER_INSTALLATION = "ignoremerges";

    protected @Override
    void setUp() throws Exception {

        // Test without ignoring merges
        super.setUp();
        hgInstallation = MERGE_TRIGGER_INSTALLATION;
        Hudson.getInstance()
                .getDescriptorByType(MercurialInstallation.DescriptorImpl.class)
                .setInstallations(
                        new MercurialInstallation(MERGE_TRIGGER_INSTALLATION, "",
                                "hg", false, true, true, true, Collections
                                        .<ToolProperty<?>> emptyList()));
        this.ignoreMerges = false;
    }
}
