/*
 * The MIT License
 *
 * Copyright 2012 Jesse Glick.
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
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package hudson.plugins.mercurial;

import static org.junit.Assert.*;
import org.junit.Test;

public class HgExeTest {
    @Test public void pathEquals() {
        assertTrue(HgExe.pathEquals("http://nowhere.net/hg/", "http://nowhere.net/hg/"));
        assertTrue(HgExe.pathEquals("http://nowhere.net/hg", "http://nowhere.net/hg/"));
        assertTrue(HgExe.pathEquals("http://nowhere.net/hg/", "http://nowhere.net/hg"));
        assertTrue(HgExe.pathEquals("http://nowhere.net/hg", "http://nowhere.net/hg"));
        assertFalse(HgExe.pathEquals("https://nowhere.net/hg/", "http://nowhere.net/hg/"));
        if (  org.apache.commons.lang.SystemUtils.IS_OS_UNIX ) {
            assertTrue(HgExe.pathEquals("file:/var/hg/stuff", "/var/hg/stuff"));
            assertTrue(HgExe.pathEquals("file:///var/hg/stuff", "/var/hg/stuff"));
            assertFalse(HgExe.pathEquals("file:/var/hg/stuff", "/var/hg/other"));
            assertTrue(HgExe.pathEquals("/var/hg/stuff", "file:/var/hg/stuff"));
            assertTrue(HgExe.pathEquals("/var/hg/stuff", "file:///var/hg/stuff"));
            assertFalse(HgExe.pathEquals("/var/hg/other", "file:/var/hg/stuff"));
        }
    }
}
