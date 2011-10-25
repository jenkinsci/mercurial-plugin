package hudson.plugins.mercurial.build;

import static hudson.Util.fixNull;

<<<<<<< HEAD
import hudson.model.AbstractBuild;
import hudson.model.Action;
import hudson.plugins.mercurial.MercurialTagAction;

import java.io.Serializable;
import java.util.HashMap;
import java.util.List;
=======
import hudson.model.Action;

import java.io.Serializable;
import java.util.HashMap;
>>>>>>> 386bffe... BuildChooser extension point (mimic git-plugin)
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
<<<<<<< HEAD
    Map<String,MercurialTagAction> buildsByBranchName = new HashMap<String,MercurialTagAction>();

    public final String scm;

    public BuildData(String scm) {
        this.scm = scm;
    }

    public void saveBuild(MercurialTagAction build) {
        buildsByBranchName.put(build.getBranch(), build);
=======
    Map<String,Build> buildsByBranchName = new HashMap<String,Build>();

    public void saveBuild(Build build) {
        buildsByBranchName.put(build.branch, build);
>>>>>>> 386bffe... BuildChooser extension point (mimic git-plugin)
    }

    /**
     * @return true if the changesetID has already been built in the past
     */
    public boolean hasBeenBuilt(String changesetID) {
<<<<<<< HEAD
        for (MercurialTagAction b : buildsByBranchName.values()) {
            if (b.getId().equals(changesetID)) return true;
=======
        for (Build b : buildsByBranchName.values()) {
            if (b.changesetID.equals(changesetID)) return true;
>>>>>>> 386bffe... BuildChooser extension point (mimic git-plugin)
        }
        return false;
    }

<<<<<<< HEAD
    public MercurialTagAction getLastBuildOfBranch(String branch) {
=======
    public Build getLastBuildOfBranch(String branch) {
>>>>>>> 386bffe... BuildChooser extension point (mimic git-plugin)
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
<<<<<<< HEAD

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
=======
>>>>>>> 386bffe... BuildChooser extension point (mimic git-plugin)
}
