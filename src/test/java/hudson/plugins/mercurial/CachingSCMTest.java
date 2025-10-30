package hudson.plugins.mercurial;

import java.util.Collections;
import org.junit.jupiter.api.BeforeEach;

class CachingSCMTest extends SCMTestBase {

    private static final String CACHING_INSTALLATION = "caching";

    @BeforeEach
    void beforeEach() {
        j.jenkins
                .getDescriptorByType(MercurialInstallation.DescriptorImpl.class)
                .setInstallations(
                        new MercurialInstallation(CACHING_INSTALLATION, "",
                                "hg", false, true, false, Collections
                                        .emptyList()));
    }

    @Override
    protected String hgInstallation() {
        return CACHING_INSTALLATION;
    }

}
