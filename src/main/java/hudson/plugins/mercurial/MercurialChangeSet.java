package hudson.plugins.mercurial;

import hudson.scm.ChangeLogSet;
import hudson.model.User;

import java.util.Collection;

/**
 * Represents a change set.
 *
 * <p>
 * The object should be treated like an immutable object.
 * The setters are only provided for digester.
 *
 * @author Kohsuke Kawaguchi
 */
public class MercurialChangeSet extends ChangeLogSet.Entry {
    private String node;
    private String author;
    private long rev;
    private String date;
    private String msg;

    /**
     * Commit message.
     */
    public String getMsg() {
        return msg;
    }

    /**
     * Gets the user who made this change.
     */
    public User getAuthor() {
        return User.get(author);
    }

    /**
     * Gets the globally unique changeset ID.
     */
    public String getNode() {
        return node;
    }

    /**
     * Gets repository revision number, which is local in the current repository.
     */
    public long getRev() {
        return rev;
    }

    public String getDate() {
        return date;
    }

    public Collection<String> getAffectedPaths() {
        throw new UnsupportedOperationException();
    }

    protected void setParent(ChangeLogSet parent) {
        super.setParent(parent);
    }

    @Deprecated
    public void setMsg(String msg) {
        this.msg = msg;
    }

    @Deprecated
    public void setNode(String node) {
        this.node = node;
    }

    @Deprecated
    public void setAuthor(String author) {
        this.author = author;
    }

    @Deprecated
    public void setRev(long rev) {
        this.rev = rev;
    }

    @Deprecated
    public void setDate(String date) {
        this.date = date;
    }
    
    static final String CHANGELOG_TEMPLATE = "<changeset node='{node}' author='{author}' rev='{rev}' date='{date}'><msg>{desc|escape}</msg><added>{files_added}</added><deleted>{file_dels}</deleted><files>{files}</files></changeset>\\n";
}
