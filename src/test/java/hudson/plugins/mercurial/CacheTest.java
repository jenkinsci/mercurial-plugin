package hudson.plugins.mercurial;

import junit.framework.TestCase;
import org.jvnet.hudson.test.Bug;

public class CacheTest extends TestCase {

    public CacheTest(String n) {
        super(n);
    }

    public void testHashSource() throws Exception {
        assertEquals("5439A9B4063BB8F4885037E71B5079E1913DB6CA-core-main", Cache.hashSource("http://hg.netbeans.org/core-main/"));
        assertEquals("5439A9B4063BB8F4885037E71B5079E1913DB6CA-core-main", Cache.hashSource("http://hg.netbeans.org/core-main"));
        assertEquals("5731708C5EEAF9F1320B57D5F6A21E85EA5ADF2D-project", Cache.hashSource("ssh://dude@math.utexas.edu/some/project/"));
        assertEquals("210ED9E2610F74A473985D8D9EF4483D5D30265E-project", Cache.hashSource("ssh://dudette@math.utexas.edu/some/project/"));
    }

    @Bug(12544)
    public void testHashSource2() throws Exception {
        assertEquals("DA7E6A4632009859A61A551999EE2109EBB69267-ronaldradial", Cache.hashSource("http://ronaldradial:8000/"));
    }

}
