package hudson.plugins.mercurial;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class MercurialTagActionTest {
    @Test public void getShortIdReturnsFirstTwelveCharactersOfId(){
        MercurialTagAction action = new MercurialTagAction("1234567890121627e63489b4096a8858e559a456", "", "");

        assertEquals("123456789012", action.getShortId());
    }
}
