package hudson.plugins.mercurial;

import hudson.Util;
import hudson.model.Run;
import hudson.scm.ChangeLogParser;
import hudson.scm.RepositoryBrowser;
import hudson.util.Digester2;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
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

    @Override public MercurialChangeSetList parse(Run build, RepositoryBrowser<?> browser, File changelogFile)
            throws IOException, SAXException {
        Digester digester = new Digester2();
        ArrayList<MercurialChangeSet> r = new ArrayList<MercurialChangeSet>();
        digester.push(r);

        digester.addObjectCreate("*/changeset", MercurialChangeSet.class);
        digester.addSetProperties("*/changeset");
        digester.addSetProperties("*/changeset", "author", "user");
        digester.addBeanPropertySetter("*/changeset/msg");
        // Before JENKINS-55319:
        digester.addBeanPropertySetter("*/changeset/added");
        digester.addBeanPropertySetter("*/changeset/deleted");
        digester.addBeanPropertySetter("*/changeset/files");
        // After JENKINS-55319:
        digester.addCallMethod("*/changeset/file", "addFile", 1);
        digester.addCallParam("*/changeset/file", 0);
        digester.addCallMethod("*/changeset/addedFile", "addAddedFile", 1);
        digester.addCallParam("*/changeset/addedFile", 0);
        digester.addCallMethod("*/changeset/deletedFile", "addDeletedFile", 1);
        digester.addCallParam("*/changeset/deletedFile", 0);
        digester.addBeanPropertySetter("*/changeset/parents");
        digester.addSetNext("*/changeset", "add");

        try {
            digester.parse(changelogFile);
        } catch (IOException e) {
            throw new IOException("Failed to parse " + changelogFile, e);
        } catch (SAXException e) {
            throw new IOException("Failed to parse " + changelogFile + ": '" + Util.loadFile(changelogFile) + "'", e);
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
        r.sort(Comparator.comparingLong(MercurialChangeSet::getRev));

        return new MercurialChangeSetList(build, browser, r);
    }

}
