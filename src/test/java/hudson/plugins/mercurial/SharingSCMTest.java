package hudson.plugins.mercurial;

import hudson.tools.ToolProperty;

import java.util.Collections;
import static org.junit.Assert.*;
import org.junit.Before;

public class SharingSCMTest extends SCMTestBase {

    private static final String SHARING_INSTALLATION = "sharing";

    @Override @Before public void setUp() throws Exception {
        super.setUp();
        j.jenkins
                .getDescriptorByType(MercurialInstallation.DescriptorImpl.class)
                .setInstallations(
                        new MercurialInstallation(SHARING_INSTALLATION, "",
                                "hg", false, true, true, Collections
                                        .<ToolProperty<?>> emptyList()));
    }

    @Override protected String hgInstallation() {
        return SHARING_INSTALLATION;
    }

    @Override protected void assertClone(String log, boolean cloneExpected) {
        if (cloneExpected) {
            assertTrue(log, log.contains(" share --"));
        } else {
            assertTrue(log, log.contains(" update --"));
            assertFalse(log, log.contains(" share --"));
        }
    }

}
