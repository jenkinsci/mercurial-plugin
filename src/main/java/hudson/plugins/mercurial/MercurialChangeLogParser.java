package hudson.plugins.mercurial;

import hudson.model.AbstractBuild;
import hudson.scm.ChangeLogParser;
import org.xml.sax.SAXException;

import java.io.File;
import java.io.IOException;

/**
 * @author Kohsuke Kawaguchi
 */
public class MercurialChangeLogParser extends ChangeLogParser {
    public MercurialChangeSetList parse(AbstractBuild build, File changelogFile) throws IOException, SAXException {
        // TODO
        throw new UnsupportedOperationException();
    }
}
