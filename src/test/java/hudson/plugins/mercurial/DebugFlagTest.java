package hudson.plugins.mercurial;

import java.util.Collections;
import org.junit.jupiter.api.BeforeEach;

class DebugFlagTest extends SCMTestBase {

    private static final String DEBUG_INSTALLATION = "debug";

    @BeforeEach
    void beforeEach() {
        j.jenkins
                .getDescriptorByType(MercurialInstallation.DescriptorImpl.class)
                .setInstallations(
                        new MercurialInstallation(DEBUG_INSTALLATION, "", "hg",
                                true, false, false, Collections
                                        .emptyList()));
    }

    @Override
    protected String hgInstallation() {
        return DEBUG_INSTALLATION;
    }

}
