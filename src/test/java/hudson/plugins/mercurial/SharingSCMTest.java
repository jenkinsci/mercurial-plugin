package hudson.plugins.mercurial;

import java.util.Collections;
import org.junit.jupiter.api.BeforeEach;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;

class SharingSCMTest extends SCMTestBase {

    private static final String SHARING_INSTALLATION = "sharing";

    @BeforeEach
    void beforeEach() {
        j.jenkins
                .getDescriptorByType(MercurialInstallation.DescriptorImpl.class)
                .setInstallations(
                        new MercurialInstallation(SHARING_INSTALLATION, "",
                                "hg", false, true, true, Collections
                                        .emptyList()));
    }

    @Override
    protected String hgInstallation() {
        return SHARING_INSTALLATION;
    }

    @Override
    protected void assertClone(String log, boolean cloneExpected) {
        if (cloneExpected) {
            assertTrue(log.contains(" share --"), log);
        } else {
            assertTrue(log.contains(" update --"), log);
            assertFalse(log.contains(" share --"), log);
        }
    }

}
