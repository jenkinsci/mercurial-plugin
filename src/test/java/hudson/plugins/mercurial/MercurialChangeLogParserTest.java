/*
 * The MIT License
 *
 * Copyright 2013 Jesse Glick.
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

import hudson.model.User;
import hudson.scm.ChangeLogParser;
import hudson.scm.ChangeLogSet;
import java.io.File;
import java.io.PrintWriter;
import java.util.Iterator;
import static org.junit.Assert.*;
import org.junit.Test;
import org.junit.Rule;
import org.junit.rules.TemporaryFolder;
import org.jvnet.hudson.test.Bug;
import org.jvnet.hudson.test.JenkinsRule;

public class MercurialChangeLogParserTest {

    @Rule public JenkinsRule j = new JenkinsRule(); // otherwise CanonicalIdResolver are missing
    @Rule public TemporaryFolder tmp = new TemporaryFolder();

    @Bug(16332)
    @Test public void parseAddressFromChangeLog() throws Exception {
        File changelogXml = tmp.newFile("changelog.xml");
        PrintWriter pw = new PrintWriter(changelogXml, "UTF-8");
        try {
            pw.println("<?xml version='1.0' encoding='UTF-8'?>");
            pw.println("<changesets>");
            pw.println("<changeset author='joe.schmo &lt;joe.schmo@example.com&gt;'/>");
            pw.println("</changesets>");
            pw.flush();
        } finally {
            pw.close();
        }
        ChangeLogParser clp = new MercurialChangeLogParser(null);
        ChangeLogSet<? extends ChangeLogSet.Entry> cls = clp.parse(null, changelogXml);
        Iterator<? extends ChangeLogSet.Entry> it = cls.iterator();
        assertTrue(it.hasNext());
        ChangeLogSet.Entry entry = it.next();
        assertFalse(it.hasNext());
        User author = entry.getAuthor();
        assertEquals("joe.schmo _joe.schmo@example.com_", author.getId());
        assertEquals("joe.schmo <joe.schmo@example.com>", author.getFullName());
    }

}
