package hudson.plugins.mercurial;

import edu.umd.cs.findbugs.annotations.SuppressWarnings;
import org.apache.commons.io.IOUtils;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.util.Map;
import java.util.TreeMap;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

/**
 * Parses the <tt>.hgrc</tt> file.
 */
final class HgRc {
    private final Map<String,Section> sections = new TreeMap<String,Section>();

    public HgRc(File repository) throws IOException {
        this(load(repository), getHgRcFile(repository));
    }
    private static Reader load(File repository) throws IOException {
        File hgrc = getHgRcFile(repository);
        if(!hgrc.exists())
            throw new IOException("No such file: "+hgrc);
        // TODO: what is the encoding of hgrc?
        return new FileReader(hgrc);
    }

    @SuppressWarnings("SBSC_USE_STRINGBUFFER_CONCATENATION")
    HgRc(Reader input, File hgrc) throws IOException {
        try {
            BufferedReader r = new BufferedReader(input);
            String line;
            Section current=null;
            String key=null,value=null;

            while((line=r.readLine())!=null) {
                if(line.length()==0)    continue;
                switch(line.charAt(0)) {
                case ';':
                case '#':
                    // comment
                    break;
                case '[':
                    // section
                    if(key!=null) {
                        // commit the previous value
                        current.add(key,value);
                        key=null;
                    }

                    Matcher m = SECTION_HEADER.matcher(line);
                    if(!m.matches()) {
                        LOGGER.warning("Failed to parse "+hgrc+" : "+line);
                        continue;
                    }
                    current = createSection(m.group(1));
                    break;
                case ' ':
                case '\t':
                    // continuation of previous value
                    while(line.length()>0 && Character.isWhitespace(line.charAt(0)))
                        line = line.substring(1);
                    value += line;
                    continue;
                default:
                    // key=value line
                    m = KEY_VALUE.matcher(line);
                    if(!m.matches()) {
                        LOGGER.warning("Failed to parse "+hgrc+" : "+line);
                        continue;
                    }
                    if(key!=null)   // commit the previous value
                        current.add(key,value);
                    key = m.group(1);
                    value = m.group(2);
                    continue;
                }
            }

            if(key!=null)
                // commit the last value
                current.add(key,value);
        } finally {
            IOUtils.closeQuietly(input);
        }
    }

    public static File getHgRcFile(File repository) {
        return new File(repository,".hg/hgrc");
    }

    public static File getShareFile(File repository) {
        return new File(repository, ".hg/sharedpath");
    }

    private Section createSection(String name) {
        Section s = sections.get(name);
        if(s==null)
            sections.put(name,s=new Section());
        return s;
    }

    /**
     * Gets the section. If no such section exists, return an empty constant section.
     */
    public Section getSection(String name) {
        Section s = sections.get(name);
        if(s==null) s=NULL;
        return s;
    }

    @Override
    public String toString() {
        return sections.toString();
    }

    public static final class Section {
        private final Map<String,String> values = new TreeMap<String, String>();

        public void add(String key, String value) {
            values.put(key,value);
        }

        public String get(String key) {
            return values.get(key);
        }

        @Override
        public String toString() {
            return values.toString();
        }
    }

    private static final Pattern SECTION_HEADER = Pattern.compile("\\[(\\w+)\\].*");
    private static final Pattern KEY_VALUE = Pattern.compile("([^:=\\s][^:=]*?)\\s*[=:]\\s*(.*)");

    private static final Logger LOGGER = Logger.getLogger(HgRc.class.getName());

    private static final Section NULL = new Section();
}
