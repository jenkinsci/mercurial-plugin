package hudson.plugins.mercurial;

import hudson.scm.ChangeLogSet;
import hudson.model.User;

import java.util.Collection;

/**
 * @author Kohsuke Kawaguchi
 */
public class MercurialChangeSet extends ChangeLogSet.Entry {
    private String node;
    private String author;
    private long rev;
    private String date;
    private String msg;

    public String getMsg() {
        return msg;
    }

    public User getAuthor() {
        return User.get(author);
    }

    public Collection<String> getAffectedPaths() {
        throw new UnsupportedOperationException();
    }

    protected void setParent(ChangeLogSet parent) {
        super.setParent(parent);
    }

    static final String CHANGELOG_TEMPLATE = "<changeset node='{node}' author='{author}' rev='{rev}' date='{date}'><msg>{desc|escape}</msg><added>{files_added}</added><deleted>{file_dels}</deleted><files>{files}</files}</changeset>\\n";
}
