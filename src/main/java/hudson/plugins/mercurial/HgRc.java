package hudson.plugins.mercurial;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.TreeMap;
import org.apache.commons.io.IOUtils;
import org.ini4j.ConfigParser;

/**
 * Parses the <tt>.hgrc</tt> file.
 */
final class HgRc {
    private final ConfigParser p;

    HgRc(File repository) throws IOException {
        this(load(repository), getHgRcFile(repository));
    }

    private static InputStream load(File repository) throws IOException {
        File hgrc = getHgRcFile(repository);
        if (!hgrc.exists()) {
            throw new IOException("No such file: " + hgrc);
        }
        return new FileInputStream(hgrc);
    }

    HgRc(InputStream input, File hgrc) throws IOException {
        try {
            p = new ConfigParser();
            p.read(input);
        } finally {
            IOUtils.closeQuietly(input);
        }
    }

    public static File getHgRcFile(File repository) {
        return new File(repository, ".hg/hgrc");
    }

    public static File getShareFile(File repository) {
        return new File(repository, ".hg/sharedpath");
    }

    /**
     * Gets the section. If no such section exists, return an empty constant
     * section.
     */
    public Section getSection(String name) {
        return new Section(p, name);
    }

    @Override
    public String toString() {
        Map<String,Section> sections = new TreeMap<String,Section>();
        for (String s : p.sections()) {
            sections.put(s, getSection(s));
        }
        return sections.toString();
    }

    public static final class Section {

        private final ConfigParser p;
        private final String name;

        private Section(ConfigParser p, String name) {
            this.p = p;
            this.name = name;
        }

        public String get(String key) {
            try {
                return p.get(name, key);
            } catch (ConfigParser.ConfigParserException x) {
                return null;
            }
        }

        @Override
        public String toString() {
            Map<String,String> values = new TreeMap<String,String>();
            try {
                for (String s : p.options(name)) {
                    values.put(s, p.get(name, s));
                }
            } catch (ConfigParser.ConfigParserException x) {
                // ignore
            }
            return values.toString();
        }
    }

}
