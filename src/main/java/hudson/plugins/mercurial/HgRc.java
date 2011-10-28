package hudson.plugins.mercurial;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.TreeMap;
import org.apache.commons.io.IOUtils;
import org.ini4j.Ini;

/**
 * Parses the <tt>.hgrc</tt> file.
 */
final class HgRc {
    private final Ini ini;

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
            ini = new Ini(input);
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
        return new Section(ini, name);
    }

    @Override
    public String toString() {
        Map<String,Section> sections = new TreeMap<String,Section>();
        for (String s : ini.keySet()) {
            sections.put(s, getSection(s));
        }
        return sections.toString();
    }

    public static final class Section {

        private final Ini ini;
        private final String name;

        private Section(Ini ini, String name) {
            this.ini = ini;
            this.name = name;
        }

        public String get(String key) {
            return ini.get(name, key);
        }

        @Override
        public String toString() {
            Map<String,String> vals = ini.get(name);
            return vals != null ? new TreeMap<String,String>(vals).toString() : "{}";
        }
    }

}
