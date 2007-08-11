package hudson.plugins.mercurial;

import hudson.model.User;
import hudson.scm.ChangeLogSet;
import hudson.scm.EditType;

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
     * Lazily computed.
     */
    private volatile List<String> affectedPaths;

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
     * Short node ID that hg CLI uses.
     * The first 12 characters of {@link #getNode()}.
     */
    public String getShortNode() {
        return node.substring(0,12);
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

    @Override
    public Collection<String> getAffectedPaths() {
        if(affectedPaths==null) {
            List<String> r = new ArrayList<String>(added.size()+modified.size()+deleted.size());
            r.addAll(added);
            r.addAll(modified);
            r.addAll(deleted);
            affectedPaths = r;
        }
        return affectedPaths;
    }

    /**
     * Gets all the files that were added.
     */
    public List<String> getAddedPaths() {
        return added;
    }

    /**
     * Gets all the files that were deleted.
     */
    public List<String> getDeletedPaths() {
        return deleted;
    }

    /**
     * Gets all the files that were modified.
     */
    public List<String> getModifiedPaths() {
        return modified;
    }

    public List<String> getPaths(EditType kind) {
        if(kind==EditType.ADD)
            return getAddedPaths();
        if(kind==EditType.EDIT)
            return getModifiedPaths();
        if(kind==EditType.DELETE)
            return getDeletedPaths();
        return null;
    }

    /**
     * Returns all three variations of {@link EditType}.
     * Placed here to simplify access from views.
     */
    public List<EditType> getEditTypes() {
        return EditType.ALL;
    }

    protected void setParent(ChangeLogSet parent) {
        super.setParent(parent);
    }

//
// used by Digester 
//
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
        list = list.trim();
        if(list.length()==0) return Collections.emptyList();
        return Arrays.asList(list.split(" "));
    }

    static final String CHANGELOG_TEMPLATE = "<changeset node='{node}' author='{author}' rev='{rev}' date='{date}'><msg>{desc|escape}</msg><added>{files_added}</added><deleted>{file_dels}</deleted><files>{files}</files></changeset>\\n";
}
