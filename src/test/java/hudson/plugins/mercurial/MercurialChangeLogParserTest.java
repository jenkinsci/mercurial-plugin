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
import java.util.TreeSet;
import static org.junit.Assert.*;
import org.junit.Test;
import org.junit.Rule;
import org.junit.rules.TemporaryFolder;
import org.jvnet.hudson.test.Bug;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.WithoutJenkins;

public class MercurialChangeLogParserTest {

    @Rule public JenkinsRule j = new JenkinsRule(); // otherwise CanonicalIdResolver are missing
    @Rule public TemporaryFolder tmp = new TemporaryFolder();

    @Bug(16332)
    @Test public void parseAddressFromChangeLog() throws Exception {
        File changelogXml = tmp.newFile("changelog.xml");
        try (PrintWriter pw = new PrintWriter(changelogXml, "UTF-8")) {
            pw.println("<?xml version='1.0' encoding='UTF-8'?>");
            pw.println("<changesets>");
            pw.println("<changeset author='joe.schmo &lt;joe.schmo@example.com&gt;'/>");
            pw.println("</changesets>");
            pw.flush();
        }
        ChangeLogParser clp = new MercurialChangeLogParser(null);
        ChangeLogSet<? extends ChangeLogSet.Entry> cls = clp.parse(null, null, changelogXml);
        Iterator<? extends ChangeLogSet.Entry> it = cls.iterator();
        assertTrue(it.hasNext());
        ChangeLogSet.Entry entry = it.next();
        assertFalse(it.hasNext());
        User author = entry.getAuthor();
        assertEquals("joe.schmo _joe.schmo@example.com_", author.getId());
        assertEquals("joe.schmo <joe.schmo@example.com>", author.getFullName());
    }

    @WithoutJenkins
    @Issue("JENKINS-55319")
    @Test public void oldAndNewFileFormats() throws Exception {
        File changelogXml = tmp.newFile("changelog.xml");
        try (PrintWriter pw = new PrintWriter(changelogXml, "UTF-8")) {
            pw.println("<?xml version='1.0' encoding='UTF-8'?>");
            pw.println("<changesets>");
            pw.println("  <changeset>");
            pw.println("    <added>two</added>");
            pw.println("    <deleted></deleted>");
            pw.println("    <files>one two three</files>");
            pw.println("  </changeset>");
            pw.println("</changesets>");
        }
        assertEquals("added=[two] deleted=[] modified=[one, three] ", summary(new MercurialChangeLogParser(null).parse(null, null, changelogXml)));
        try (PrintWriter pw = new PrintWriter(changelogXml, "UTF-8")) {
            pw.println("<?xml version='1.0' encoding='UTF-8'?>");
            pw.println("<changesets>");
            pw.println("  <changeset>");
            pw.println("    <added>one</added>");
            pw.println("    <deleted>two</deleted>");
            pw.println("    <files>three</files>");
            pw.println("    <parents>6021:df659eb23360 6027:b7f44f01a632 </parents>");
            pw.println("  </changeset>");
            pw.println("</changesets>");
        }
        assertEquals("added=[] deleted=[] modified=[] ", summary(new MercurialChangeLogParser(null).parse(null, null, changelogXml)));
        try (PrintWriter pw = new PrintWriter(changelogXml, "UTF-8")) {
            pw.println("<?xml version='1.0' encoding='UTF-8'?>");
            pw.println("<changesets>");
            pw.println("  <changeset>");
            pw.println("    <addedFile>two</addedFile>");
            pw.println("    <file>one</file>");
            pw.println("    <file>two</file>");
            pw.println("    <file>three</file>");
            pw.println("  </changeset>");
            pw.println("</changesets>");
        }
        assertEquals("added=[two] deleted=[] modified=[one, three] ", summary(new MercurialChangeLogParser(null).parse(null, null, changelogXml)));
        try (PrintWriter pw = new PrintWriter(changelogXml, "UTF-8")) {
            pw.println("<?xml version='1.0' encoding='UTF-8'?>");
            pw.println("<changesets>");
            pw.println("  <changeset>");
            pw.println("    <deletedFile>one</deletedFile>");
            pw.println("    <file>one</file>");
            pw.println("    <file>two</file>");
            pw.println("    <file>three</file>");
            pw.println("  </changeset>");
            pw.println("</changesets>");
        }
        assertEquals("added=[] deleted=[one] modified=[three, two] ", summary(new MercurialChangeLogParser(null).parse(null, null, changelogXml)));
    }

    static String summary(MercurialChangeSetList csl) {
        StringBuilder b = new StringBuilder();
        for (MercurialChangeSet mcs : csl) {
            b.append("added=").append(new TreeSet<>(mcs.getAddedPaths())).append(" deleted=").append(new TreeSet<>(mcs.getDeletedPaths())).append(" modified=").append(new TreeSet<>(mcs.getModifiedPaths())).append(" ");
        }
        return b.toString();
    }

}
