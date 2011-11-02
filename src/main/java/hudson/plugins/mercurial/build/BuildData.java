package hudson.plugins.mercurial.build;

import static hudson.Util.fixNull;

import hudson.model.AbstractBuild;
import hudson.model.Action;
import hudson.plugins.mercurial.MercurialTagAction;

import java.io.Serializable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Capture the Mercurial related information for a build
 * @author <a href="mailto:nicolas.deloof@cloudbees.com">Nicolas De loof</a>
 */
public class BuildData implements Action, Serializable {
    private static final long serialVersionUID = 1L;

    /**
     * Map of branch name -> build (Branch name to last built SHA1).
     */
    Map<String,MercurialTagAction> buildsByBranchName = new HashMap<String,MercurialTagAction>();

    public final String scm;

    public BuildData(String scm) {
        this.scm = scm;
    }

    public void saveBuild(MercurialTagAction build) {
        buildsByBranchName.put(build.getBranch(), build);
    }

    /**
     * @return true if the changesetID has already been built in the past
     */
    public boolean hasBeenBuilt(String changesetID) {
        for (MercurialTagAction b : buildsByBranchName.values()) {
            if (b.getId().equals(changesetID)) return true;
        }
        return false;
    }

    public MercurialTagAction getLastBuildOfBranch(String branch) {
        return buildsByBranchName.get(branch);
    }

    public String getDisplayName() {
        return "Mercurial build data";
    }

    public String getIconFileName() {
        return "/plugin/mercurial/images/32x32/logo.png";
    }

    public String getUrlName() {
        return "mercurial";
    }

    public static BuildData getBuildData(String scm, AbstractBuild<?,?> build) {
        BuildData buildData = null;
        while (build != null) {
            List<BuildData> buildDataList = build.getActions(BuildData.class);
            for (BuildData bd : buildDataList) {
                if (bd != null && bd.scm.equals(scm)) {
                    buildData = bd;
                    break;
                }
            }
            if (buildData != null) {
                break;
            }
            build = build.getPreviousBuild();
        }

        return (buildData != null ? buildData : new BuildData(scm));
    }
}
