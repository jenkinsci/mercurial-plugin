package hudson.plugins.mercurial;

import hudson.model.User;
import hudson.scm.ChangeLogSet;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

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

    private List<String> added = Collections.emptyList();
    private List<String> deleted = Collections.emptyList();
    private List<String> modified = Collections.emptyList();

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
    public void setUser(String author) {
        this.author = author;
    }

    @Deprecated
    public String getUser() {
        return author;
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

    @Deprecated
    public void setAdded(String list) {
        added = toList(list);
    }

    @Deprecated
    public void setDeleted(String list) {
        deleted = toList(list);
    }

    @Deprecated
    public void setFiles(String list) {
        modified = toList(list);
        if(!added.isEmpty() || !deleted.isEmpty()) {
            modified = new ArrayList<String>(modified);
            modified.removeAll(added);
            modified.removeAll(deleted);
        }
    }

    private List<String> toList(String list) {
        return Arrays.asList(list.split(" "));
    }

    static final String CHANGELOG_TEMPLATE = "<changeset node='{node}' author='{author}' rev='{rev}' date='{date}'><msg>{desc|escape}</msg><added>{files_added}</added><deleted>{file_dels}</deleted><files>{files}</files></changeset>\\n";
}
