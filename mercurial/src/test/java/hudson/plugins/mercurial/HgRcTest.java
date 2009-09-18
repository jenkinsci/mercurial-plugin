package hudson.plugins.mercurial;

import java.io.StringReader;
import org.junit.Test;
import static org.junit.Assert.*;

public class HgRcTest {

    @Test
    public void parsing() throws Exception {
        HgRc rc = new HgRc(new StringReader("[foo]\nbar=baz\n\n#comment\n[encode]\n*.{x,y} = run: gzopp\n[extensions]\nfrobnitz = "), null);
        assertEquals("{encode={*.{x,y}=run: gzopp}, extensions={frobnitz=}, foo={bar=baz}}", rc.toString());
    }

}