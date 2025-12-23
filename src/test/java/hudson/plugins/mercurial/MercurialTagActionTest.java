package hudson.plugins.mercurial;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MercurialTagActionTest {

    @Test
    void getShortIdReturnsFirstTwelveCharactersOfId(){
        MercurialTagAction action = new MercurialTagAction("1234567890121627e63489b4096a8858e559a456", "", "", null);
        assertEquals("123456789012", action.getShortId());
    }
}
