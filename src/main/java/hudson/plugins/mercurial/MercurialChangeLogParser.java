package hudson.plugins.mercurial;

import hudson.model.AbstractBuild;
import hudson.scm.ChangeLogParser;
import hudson.util.Digester2;
import hudson.util.IOException2;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.Set;

import org.apache.commons.digester.Digester;
import org.xml.sax.SAXException;

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

    public MercurialChangeSetList parse(AbstractBuild build, File changelogFile)
            throws IOException, SAXException {
        Digester digester = new Digester2();
        ArrayList<MercurialChangeSet> r = new ArrayList<MercurialChangeSet>();
        digester.push(r);

        digester.addObjectCreate("*/changeset", MercurialChangeSet.class);
        digester.addSetProperties("*/changeset");
        digester.addSetProperties("*/changeset", "author", "user");
        digester.addBeanPropertySetter("*/changeset/msg");
        digester.addBeanPropertySetter("*/changeset/added");
        digester.addBeanPropertySetter("*/changeset/deleted");
        digester.addBeanPropertySetter("*/changeset/files");
        digester.addBeanPropertySetter("*/changeset/parents");
        digester.addSetNext("*/changeset", "add");

        try {
            digester.parse(changelogFile);
        } catch (IOException e) {
            throw new IOException2("Failed to parse " + changelogFile, e);
        } catch (SAXException e) {
            throw new IOException2("Failed to parse " + changelogFile, e);
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

        // sort the changes from oldest to newest, this gives the best result in
        // the Jenkins changes view,
        // and is like the old situation where 'hg incoming' was used to
        // determine the changelog
        Collections.sort(r, new Comparator<MercurialChangeSet>() {
            public int compare(MercurialChangeSet o1, MercurialChangeSet o2) {
                // don't do return o1.getRev() - o2.getRev(), as that is
                // susceptible to integer overflow
                if (o1.getRev() < o2.getRev()) {
                    return -1;
                }
                if (o1.getRev() == o2.getRev()) {
                    return 0;
                } else {
                    return 1;
                }
            }
        });

        return new MercurialChangeSetList(build, r);
    }

}
