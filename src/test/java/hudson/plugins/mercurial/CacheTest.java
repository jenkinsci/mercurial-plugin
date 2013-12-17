package hudson.plugins.mercurial;

import com.cloudbees.plugins.credentials.CredentialsScope;
import com.cloudbees.plugins.credentials.common.StandardUsernamePasswordCredentials;
import com.cloudbees.plugins.credentials.impl.BaseStandardCredentials;
import hudson.util.Secret;
import static org.junit.Assert.*;
import org.junit.Test;
import org.jvnet.hudson.test.Bug;

public class CacheTest {

    @Test public void hashSource() throws Exception {
        assertEquals("5439A9B4063BB8F4885037E71B5079E1913DB6CA-core-main", Cache.hashSource("http://hg.netbeans.org/core-main/", null));
        assertEquals("5439A9B4063BB8F4885037E71B5079E1913DB6CA-core-main", Cache.hashSource("http://hg.netbeans.org/core-main", null));
        assertEquals("5731708C5EEAF9F1320B57D5F6A21E85EA5ADF2D-project", Cache.hashSource("ssh://dude@math.utexas.edu/some/project/", null));
        assertEquals("210ED9E2610F74A473985D8D9EF4483D5D30265E-project", Cache.hashSource("ssh://dudette@math.utexas.edu/some/project/", null));
        // Cannot use UsernamePasswordCredentialsImpl from a unit test, since it tries to actually decrypt the password, which requires Jenkins.instance.
        assertEquals("0D1FD823FDA2F7144C463007FEAF9F824333B3D2-core-main-bob-at-nowhere.net", Cache.hashSource("http://hg.netbeans.org/core-main/", new MockUsernamePasswordCredentials(CredentialsScope.GLOBAL, "what-ever", "bob@nowhere.net")));
    }

    @Bug(12544)
    @Test public void hashSource2() throws Exception {
        assertEquals("DA7E6A4632009859A61A551999EE2109EBB69267-ronaldradial", Cache.hashSource("http://ronaldradial:8000/", null));
    }

    private static class MockUsernamePasswordCredentials extends BaseStandardCredentials implements StandardUsernamePasswordCredentials {
        private final String username;
        MockUsernamePasswordCredentials(CredentialsScope scope, String id, String username) {
            super(scope, id, "");
            this.username = username;
        }
        @Override public String getUsername() {
            return username;
        }
        @Override public Secret getPassword() {
            return null;
        }
    }

}
