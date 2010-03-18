package hudson.plugins.mercurial;

import hudson.scm.EditType;
import hudson.scm.ChangeLogSet.AffectedFile;

/**
 * Implementation of {@link AffectedFile} for Mercurial.
 */
public final class MercurialAffectedFile implements AffectedFile {

    /** the editType of the affected file. */
    private transient final EditType editType;

    /** the path of the affected file. */
    private transient final String path;

    /**
     * @param editType
     *            the type of the affected file.
     * @param path
     *            the path of the affected file.
     */
    MercurialAffectedFile(final EditType editType, final String path) {
        this.editType = editType;
        this.path = path;
    }

    /** {@inheritDoc} */
    public EditType getEditType() {
        return editType;
    }

    /** {@inheritDoc} */
    public String getPath() {
        return path;
    }
}
