package hudson.plugins.mercurial;

import junit.framework.TestCase;

public class CacherTest extends TestCase {

    public CacherTest(String n) {
        super(n);
    }

    public void testHashSource() throws Exception {
        assertEquals("CCC9A13E-core-main", Cacher.hashSource("http://hg.netbeans.org/core-main/"));
        assertEquals("CCC9A13E-core-main", Cacher.hashSource("http://hg.netbeans.org/core-main"));
        assertEquals("EDA2F178-project", Cacher.hashSource("ssh://dude@math.utexas.edu/some/project/"));
        assertEquals("783CAF55-project", Cacher.hashSource("ssh://dudette@math.utexas.edu/some/project/"));
    }

}
