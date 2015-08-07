/**
 * The MIT License
 *
 * Copyright (c) 2012, Sebastian Sdorra
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package hudson.plugins.mercurial;

import java.net.URI;
import java.net.URISyntaxException;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 *
 * @author Sebastian Sdorra
 */
public class MercurialStatusTest {
  
  @Test public void testLooselyMatches() throws URISyntaxException {
      assertTrue( MercurialStatus.looselyMatches(new URI("ssh://somehost/"), "ssh://somehost/"));
      assertTrue( MercurialStatus.looselyMatches(new URI("http://somehost/"), "http://somehost/"));
      assertTrue( MercurialStatus.looselyMatches(new URI("http://somehost:80/"), "http://somehost/"));
      assertTrue( MercurialStatus.looselyMatches(new URI("http://somehost/"), "http://somehost:80/"));
      assertTrue( MercurialStatus.looselyMatches(new URI("https://somehost:443/"), "https://somehost/"));
      assertTrue( MercurialStatus.looselyMatches(new URI("https://somehost/"), "https://somehost:443/"));
      assertTrue( MercurialStatus.looselyMatches(new URI("https://somehost:443/"), "https://somehost:443/"));
      assertTrue( MercurialStatus.looselyMatches(new URI("http://somehost/jenkins"), "http://somehost/jenkins"));
      assertTrue( MercurialStatus.looselyMatches(new URI("http://somehost:80/jenkins"), "http://somehost:80/jenkins"));
      assertTrue( MercurialStatus.looselyMatches(new URI("https://somehost/jenkins"), "https://somehost/jenkins"));
      assertTrue( MercurialStatus.looselyMatches(new URI("https://somehost/jenkins?query=true"), "https://somehost/jenkins?query=true"));
      assertTrue( MercurialStatus.looselyMatches(new URI("https://somehost/jenkins?query=some%20path"), "https://somehost/jenkins?query=some%20path"));
      assertTrue( MercurialStatus.looselyMatches(new URI("https://somehost/jenkins"), "https://user@somehost/jenkins"));
      assertTrue( MercurialStatus.looselyMatches(new URI("https://somehost/jenkins"), "https://user:password@somehost/jenkins"));
      assertTrue( MercurialStatus.looselyMatches(new URI("ssh://somehost/path"), "ssh://user:password@somehost:22/path"));

      assertFalse( MercurialStatus.looselyMatches(new URI("ssh://somehost/path"), "invalid/url") );
      assertFalse( MercurialStatus.looselyMatches(new URI("ssh://somehost/path"), "ssh://somehost/other/path") );
      assertFalse( MercurialStatus.looselyMatches(new URI("ssh://somehost/path"), "ssh://somehost/other/path") );
      assertFalse( MercurialStatus.looselyMatches(new URI("http://somehost/path"), "http://somehost/") );
      assertFalse( MercurialStatus.looselyMatches(new URI("http://somehost/path"), "http://somehost/path?query=test") );
      assertTrue( MercurialStatus.looselyMatches(new URI("/var/hg/stuff"), "/var/hg/stuff") );
      assertTrue( MercurialStatus.looselyMatches(new URI("file:///var/hg/stuff"), "/var/hg/stuff") );
      assertTrue( MercurialStatus.looselyMatches(new URI("file:/var/hg/stuff"), "/var/hg/stuff") );
      assertTrue( MercurialStatus.looselyMatches(new URI("/var/hg/stuff"), "file:/var/hg/stuff") );
      assertTrue( MercurialStatus.looselyMatches(new URI("/var/hg/stuff"), "file:///var/hg/stuff") );
      assertTrue( MercurialStatus.looselyMatches(new URI("file:///var/hg/stuff"), "file:///var/hg/stuff") );

      assertTrue( MercurialStatus.looselyMatches(new URI("http://somehost/"), "ssh://somehost/") );
      assertTrue( MercurialStatus.looselyMatches(new URI("https://somehost/"), "http://somehost/") );
      assertTrue( MercurialStatus.looselyMatches(new URI("ssh://somehost/"), "https://somehost/") );
      assertTrue( MercurialStatus.looselyMatches(new URI("http://somehost:80/"), "ssh://somehost:22/") );
      assertTrue( MercurialStatus.looselyMatches(new URI("https://somehost:443/"), "http://somehost:80/") );
      assertTrue( MercurialStatus.looselyMatches(new URI("ssh://somehost:22/"), "https://somehost:443/") );
      assertTrue( MercurialStatus.looselyMatches(new URI("http://somehost/path"), "ssh://somehost/path") );
      assertTrue( MercurialStatus.looselyMatches(new URI("https://somehost/path"), "http://somehost/path") );
      assertTrue( MercurialStatus.looselyMatches(new URI("ssh://somehost/path"), "https://somehost/path") );

      assertFalse( MercurialStatus.looselyMatches(new URI("http://scm.foocompany.com/hg/foocomponent/"), "${REPO_URL}") );
      assertFalse( MercurialStatus.looselyMatches(new URI("http://scm.foocompany.com/hg/foocomponent/"), "$REPO_URL") );
  }
  
}
