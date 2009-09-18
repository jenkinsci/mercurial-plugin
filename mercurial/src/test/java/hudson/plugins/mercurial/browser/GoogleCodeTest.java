package hudson.plugins.mercurial.browser;

import static org.junit.Assert.*;

import hudson.plugins.mercurial.MercurialChangeSet;

import java.io.IOException;
import java.net.MalformedURLException;

import org.junit.Test;

public class GoogleCodeTest {

    @Test
    @SuppressWarnings("deprecation")
    public void testGetChangeSetLinkMercurialChangeSet() throws IOException {
        final GoogleCode browser = new GoogleCode("http://code.google.com/p/PROJECTNAME/source");
        final MercurialChangeSet changeSet = new MercurialChangeSet();
        final String node = "6704efde87541766fadba17f66d04b926cd4d343";
        changeSet.setNode(node);
        assertEquals("http://code.google.com/p/PROJECTNAME/source/detail?r=" + node, browser.getChangeSetLink(changeSet).toExternalForm());
    }

    @Test
    public void testGoogleCode() throws MalformedURLException {
        final GoogleCode browser = new GoogleCode("http://code.google.com/p/PROJECTNAME/source");
        assertEquals("http://code.google.com/p/PROJECTNAME/source/", browser.getUrl().toExternalForm());
    }

    @Test(expected=MalformedURLException.class)
    public void testGoogleCodeMustEndWithSource() throws MalformedURLException {
        new GoogleCode("http://code.google.com/p/PROJECTNAME");        
    }
}
