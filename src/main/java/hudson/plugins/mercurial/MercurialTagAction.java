package hudson.plugins.mercurial;

import hudson.model.AbstractBuild;
import hudson.model.Action;

/**
 * Action contributed to {@link AbstractBuild} from Mercurial.
 *
 * <p>
 * Currently it just remembers the revision ID, but we want to extend this to cover tagging.
 *
 * @author Kohsuke Kawaguchi
 */
public class MercurialTagAction implements Action {
    // TODO: have this extends AbstractScmTagAction and offer after-the-fact tagging operation
    // for now, we just remember the mercurial revision that was built in a given build

    /**
     * 48-bit node name, like "272a7f93d92d" that are given from "hg id -i"
     * (but except trailing '+' that can be present.)
     */
    public final String id;

    public MercurialTagAction(String id) {
        this.id = id;
    }

    public String getIconFileName() {
        return null;
    }

    public String getDisplayName() {
        return null;
    }

    public String getUrlName() {
        return null;
    }
}
