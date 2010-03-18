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
import java.util.Iterator;
import java.util.Set;

/**
 * Parses the changelog.xml.
 *
 * See {@link MercurialChangeSet#CHANGELOG_TEMPLATE} for the format.
 */
public class MercurialChangeLogParser extends ChangeLogParser {

    private final Set<String> modules;

    public MercurialChangeLogParser(Set<String> modules) {
        this.modules = modules;
    }

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

        if (modules != null) {
            Iterator<MercurialChangeSet> it = r.iterator();
            while (it.hasNext()) {
                boolean include = false;
                INCLUDE: for (String path : it.next().getAffectedPaths()) {
                    for (String module : modules) {
                        if (path.startsWith(module)) {
                            include = true;
                            break INCLUDE;
                        }
                    }
                }
                if (!include) {
                    it.remove();
                }
            }
        }

        return new MercurialChangeSetList(build,r);
    }

}
