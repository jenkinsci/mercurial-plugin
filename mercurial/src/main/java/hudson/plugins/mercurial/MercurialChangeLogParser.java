package hudson.plugins.mercurial;

import hudson.model.AbstractBuild;
import hudson.scm.ChangeLogParser;
import hudson.util.Digester2;
import hudson.util.IOException2;
import org.apache.commons.digester.Digester;
import org.xml.sax.SAXException;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;

/**
 * Parses the changelog.xml.
 *
 * See {@link MercurialChangeSet#CHANGELOG_TEMPLATE} for the format.
 * 
 * @author Kohsuke Kawaguchi
 */
public class MercurialChangeLogParser extends ChangeLogParser {
    public MercurialChangeSetList parse(AbstractBuild build, File changelogFile) throws IOException, SAXException {
        Digester digester = new Digester2();
        ArrayList<MercurialChangeSet> r = new ArrayList<MercurialChangeSet>();
        digester.push(r);

        digester.addObjectCreate("*/changeset", MercurialChangeSet.class);
        digester.addSetProperties("*/changeset");
        digester.addSetProperties("*/changeset","author","user");
        digester.addBeanPropertySetter("*/changeset/msg");
        digester.addBeanPropertySetter("*/changeset/added");
        digester.addBeanPropertySetter("*/changeset/deleted");
        digester.addBeanPropertySetter("*/changeset/files");
        digester.addBeanPropertySetter("*/changeset/parents");
        digester.addSetNext("*/changeset","add");

        try {
            digester.parse(changelogFile);
        } catch (IOException e) {
            throw new IOException2("Failed to parse "+changelogFile,e);
        } catch (SAXException e) {
            throw new IOException2("Failed to parse "+changelogFile,e);
        }

        return new MercurialChangeSetList(build,r);
    }
}
