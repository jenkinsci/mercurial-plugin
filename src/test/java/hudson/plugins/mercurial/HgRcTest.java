package hudson.plugins.mercurial;

import java.io.ByteArrayInputStream;
import org.junit.Test;
import static org.junit.Assert.*;

public class HgRcTest {

    @Test
    public void parsing() throws Exception {
        HgRc rc = new HgRc(new ByteArrayInputStream("[foo]\nbar=baz\n\n#comment\n[encode]\n*.{x,y} = run: gzopp\n[extensions]\nfrobnitz = ".getBytes()), null);
        assertEquals("{encode={*.{x,y}=run: gzopp}, extensions={frobnitz=}, foo={bar=baz}}", rc.toString());
    }

}