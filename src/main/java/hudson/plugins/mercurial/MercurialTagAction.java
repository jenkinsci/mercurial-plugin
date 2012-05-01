package hudson.plugins.mercurial;

import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import hudson.model.AbstractBuild;
import hudson.scm.SCMRevisionState;
import org.kohsuke.stapler.export.Exported;
import org.kohsuke.stapler.export.ExportedBean;

/**
 * Action contributed to {@link AbstractBuild} from Mercurial.
 *
 * <p>  
 * Currently it just remembers the revision ID, but we want to extend this to cover tagging.
 */
@ExportedBean(defaultVisibility = 999)
public class MercurialTagAction extends SCMRevisionState {
    // TODO: have this extends AbstractScmTagAction and offer after-the-fact tagging operation
    // for now, we just remember the mercurial revision that was built in a given build

    /**
     * 160-bit node name, e.g. {@code 5703b34f17d5fec7bbff2f360c0b6c3d0b952f65} from {@code hg log -r . --template '{node}'}
     */
    public final String id;

    /**
     * Integer revision number. The repository-local changeset number.
     */
    public final String rev;

    /**
     * Matches {@link MercurialSCM#subdir}.
     */
    public final String subdir;

    public MercurialTagAction(@NonNull String id, @NonNull String rev, @Nullable String subdir) {
        this.id = id;
        this.rev = rev;
        this.subdir = subdir;
    }

    @Exported(name = "mercurialNodeName")
    public String getId() {
        return id;
    }

    @Exported(name = "mercurialRevisionNumber")
    public String getRev() {
        return rev;
    }

    @Exported
    public String getSubdir() {
        return subdir;
    }

    /**
     * Mercurial often uses a short ID form that consists of 12 letters.
     */
    public String getShortId() {
        return id.substring(0,12);
    }

    public @Override String toString() {
        return "MercurialTagAction:" + id;
    }

    @Override
    public String getIconFileName() {
        return "/plugin/mercurial/images/32x32/logo.png";
    }

    @Override
    public String getDisplayName() {
        return Messages.MercurialTagAction_BuildData();
    }

    @Override
    public String getUrlName() {
        return "mercurial";
    }
}
