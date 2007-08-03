package hudson.plugins.mercurial;

import hudson.model.AbstractBuild;
import hudson.scm.ChangeLogParser;
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
        Digester digester = new Digester();
        ArrayList<MercurialChangeSet> r = new ArrayList<MercurialChangeSet>();
        digester.push(r);

        digester.addObjectCreate("*/changset", MercurialChangeSet.class);
        digester.addSetProperties("*/changset");
        digester.addBeanPropertySetter("*/changset/msg");
        digester.addBeanPropertySetter("*/changset/added");
        digester.addBeanPropertySetter("*/changset/deleted");
        digester.addBeanPropertySetter("*/changset/files");
        digester.addSetNext("*/changset","add");

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
