package hudson.plugins.mercurial;

import hudson.Util;
import hudson.model.Run;
import hudson.scm.ChangeLogParser;
import hudson.scm.RepositoryBrowser;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.Set;
import jenkins.util.xml.XMLUtils;

import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
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
        ArrayList<MercurialChangeSet> r = new ArrayList<>();

        Element changesetsE;
        try {
            changesetsE = XMLUtils.parse(changelogFile, "UTF-8").getDocumentElement();
        } catch (IOException e) {
            throw new IOException("Failed to parse " + changelogFile, e);
        } catch (SAXException e) {
            throw new IOException("Failed to parse " + changelogFile + ": '" + Util.loadFile(changelogFile) + "'", e);
        }
        NodeList changesetsNL = changesetsE.getChildNodes();
        for (int i = 0; i < changesetsNL.getLength(); i++) {
            if (changesetsNL.item(i).getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }
            Element changesetE = (Element) changesetsNL.item(i);
            MercurialChangeSet cs = new MercurialChangeSet();
            // See CHANGELOG_TEMPLATE:
            cs.setNode(changesetE.getAttribute("node"));
            if (changesetE.hasAttribute("user")) {
                cs.setAuthor(changesetE.getAttribute("user"));
            } else {
                cs.setAuthor(changesetE.getAttribute("author"));
            }
            if (changesetE.hasAttribute("rev")) { // unit tests omit it
                cs.setRev(Long.parseLong(changesetE.getAttribute("rev")));
            }
            cs.setDate(changesetE.getAttribute("date"));
            NodeList changesetNL = changesetE.getChildNodes();
            for (int j = 0; j < changesetNL.getLength(); j++) {
                if (changesetNL.item(j).getNodeType() != Node.ELEMENT_NODE) {
                    continue;
                }
                Element otherE = (Element) changesetNL.item(j);
                String text = otherE.getTextContent();
                switch (otherE.getTagName()) {
                case "msg":
                    cs.setMsg(text);
                    break;
                // Before JENKINS-55319:
                case "added":
                    cs.setAdded(text);
                    break;
                case "deleted":
                    cs.setDeleted(text);
                    break;
                case "files":
                    cs.setFiles(text);
                    break;
                // After JENKINS-55319:
                case "file":
                    cs.addFile(text);
                    break;
                case "addedFile":
                    cs.addAddedFile(text);
                    break;
                case "deletedFile":
                    cs.addDeletedFile(text);
                    break;
                case "parents":
                    cs.setParents(text);
                    break;
                default:
                    throw new IOException(otherE.getTagName());
                }
            }
            r.add(cs);
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
