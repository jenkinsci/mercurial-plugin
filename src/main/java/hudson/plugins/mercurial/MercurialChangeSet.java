package hudson.plugins.mercurial;

import hudson.model.User;
import hudson.scm.ChangeLogSet;
import hudson.scm.ChangeLogSet.AffectedFile;
import hudson.scm.EditType;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import org.kohsuke.stapler.export.Exported;

/**
 * Represents a change set.
 *
 * <p>
 * The object should be treated like an immutable object.
 * The setters are only provided for digester.
 */
public class MercurialChangeSet extends ChangeLogSet.Entry {
    private String node;
    private String author;
    private long rev;
    private String date;
    private String msg;
    private boolean merge;

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
    @Exported
    public String getMsg() {
        return msg;
    }

    /**
     * Gets the user who made this change.
     */
    @Exported
    public User getAuthor() {
        return User.get(author);
    }

    /**
     * Gets the globally unique changeset ID.
     */
    @Exported
    public String getNode() {
        return node;
    }

    /**
     * Short node ID that hg CLI uses.
     * The first 12 characters of {@link #getNode()}.
     */
    public String getShortNode() {
        // TODO: consider getting this value using the changelog template
        // via {node|short}
        // this will ensure that Hudson is consistent with the Mercurial project
        // should they change the length of short nodes.
    	
        return node.substring(0,12);
    }

    /**
     * Gets repository revision number, which is local in the current repository.
     */
    @Exported
    public long getRev() {
        return rev;
    }

    /**
     * Gets the globally unique changeset ID.  For general purpose use, use {@link #getNode()}.  This method was intended
     * for use via reflection by the email-ext plugin, but versions 1.40 and later no longer need it.
     */
    @Deprecated
    public String getRevision() {
        return node;
    }

    @Override
    public String getCommitId() {
        return node;
    }

    @Override
    public long getTimestamp() {
        //By default, the String 'date' is in the format '[TIMESTAMP].0[TIMEZONE_OFFSET]' where TIMESTAMP is the number
        //of seconds (not milliseconds) since the epoch.
        return Long.parseLong(date.split("\\.")[0]) * 1000;
    }

    /**
     * Returns the timestamp of the changeset as a string.
     */
    @Exported
    public String getDate() {
        return date;
    }

    /** {@inheritDoc} */
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

    /** {@inheritDoc} */
    @Override
    public Collection<? extends AffectedFile> getAffectedFiles() {
        final List<MercurialAffectedFile> affected = new ArrayList<MercurialAffectedFile>(added.size() + modified.size()
                + deleted.size());
        for (EditType editType : EditType.ALL) {
            for (String path : getPaths(editType)) {
                affected.add(new MercurialAffectedFile(editType, path));
            }
        }
        return affected;
    }
    
    /**
     * Gets all the files that were added.
     */
    @Exported
    public List<String> getAddedPaths() {
        return added;
    }

    /**
     * Gets all the files that were deleted.
     */
    @Exported
    public List<String> getDeletedPaths() {
        return deleted;
    }

    /**
     * Gets all the files that were modified.
     */
    @Exported
    public List<String> getModifiedPaths() {
        return modified;
    }

    /**
     * Checks if this is a merge changeset.
     */
    @Exported
    public boolean isMerge() {
        return merge;
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
        // return EditType.ALL;
        return Arrays.asList(EditType.ADD,EditType.EDIT,EditType.DELETE);
    }

    protected @Override void setParent(ChangeLogSet parent) {
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
        if (merge) {
            return;
        }
        added = toList(list);
    }

    @Deprecated
    public void setDeleted(String list) {
        if (merge) {
            return;
        }
        deleted = toList(list);
    }

    @Deprecated
    public void setFiles(String list) {
        if (merge) {
            return;
        }
        modified = toList(list);
        if(!added.isEmpty() || !deleted.isEmpty()) {
            modified = new ArrayList<String>(modified);
            modified.removeAll(added);
            modified.removeAll(deleted);
        }
    }

    @Deprecated
    public void setParents(String parents) {
        // Possible values for parents when not using --debug:
        // ""                                     - commit made in succession
        // "6019:b70a530bdb93 "                   - commit with older parent
        // "6021:df659eb23360 6027:b7f44f01a632 " - merge
        // Possible values for parents when using --debug:
        // "6031:36a60bd5b70715aea20bb3b4da56cd27c5fade20 -1:0000000000000000000000000000000000000000 "   - commit
        // "6029:dd3267698d84458686b3c5682ce027438900ffbd 6030:cee68264ed92444e59a9bd5cf9519702b092363e " - merge
        // Would be nicer if --debug did not matter: http://www.selenic.com/mercurial/bts/issue1435
        merge = parents.indexOf(':') != parents.lastIndexOf(':') && !parents.contains("-1");
        if (merge) {
            added = Collections.emptyList();
            deleted = Collections.emptyList();
            modified = Collections.emptyList();
        }
    }

    private List<String> toList(String list) {
        list = list.trim();
        if(list.length()==0) return Collections.emptyList();
        return Arrays.asList(list.split(" "));
    }

    /** |xmlescape handles a few cases that |escape does not */
    static final String CHANGELOG_TEMPLATE =
            "<changeset node='{node}' author='{author|xmlescape}' rev='{rev}' date='{date}'>" +
            // TODO {file_adds} and {file_dels} seem to be far slower to process than {files}
            "<msg>{desc|xmlescape}</msg><added>{file_adds|stringify|xmlescape}</added><deleted>{file_dels|stringify|xmlescape}</deleted>" +
            "<files>{files|stringify|xmlescape}</files><parents>{parents}</parents></changeset>\\n";
}
