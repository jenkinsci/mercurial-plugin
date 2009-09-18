/*
 * BitBucketTest.java 07.09.2009
 */
package hudson.plugins.mercurial.browser;

import static org.junit.Assert.assertEquals;
import hudson.plugins.mercurial.MercurialChangeSet;

import java.io.IOException;

import org.junit.Test;

public class BitBucketTest {

    @Test
    @SuppressWarnings("deprecation")
    public void testGetChangeSetLinkMercurialChangeSet() throws IOException {
        final BitBucket browser = new BitBucket("http://www.example.org/hg/repos");
        assertEquals("http://www.example.org/hg/repos/", browser.getUrl().toExternalForm());
        final MercurialChangeSet changeSet = new MercurialChangeSet();
        changeSet.setNode("6704efde87541766fadba17f66d04b926cd4d343");
        assertEquals("http://www.example.org/hg/repos/changeset/6704efde8754/", browser.getChangeSetLink(changeSet).toExternalForm());
    }
}
