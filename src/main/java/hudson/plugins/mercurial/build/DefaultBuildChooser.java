package hudson.plugins.mercurial.build;

import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.BuildListener;
import hudson.plugins.mercurial.MercurialSCM;
import hudson.plugins.mercurial.MercurialSCM.ChangeSet;
import hudson.plugins.mercurial.MercurialTagAction;
import hudson.scm.PollingResult.Change;

import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.logging.Logger;
import java.util.regex.Pattern;

import org.apache.commons.lang.StringUtils;

/**
 * @author <a href="mailto:nicolas.deloof@cloudbees.com">Nicolas De loof</a>
 */
public class DefaultBuildChooser extends BuildChooser {

    @Override
    public String getRevisionToBuild(AbstractBuild<?, ?> build, Launcher launcher, FilePath repository, BuildListener listener) throws IOException, InterruptedException {

        BuildData buildData = BuildData.getBuildData(scm.getSource(), build);
        Collection<MercurialTagAction> activeBranches = scm.getActiveBranches(build, launcher, listener);

        for (MercurialTagAction activeBranch : activeBranches) {
            String candidateBranch = activeBranch.getBranch();
            if (!scm.matches(candidateBranch)) {
                LOGGER.fine("ignore branch " + candidateBranch);
                continue;
            }
            MercurialTagAction lastBuild = buildData.getLastBuildOfBranch(candidateBranch);
            if (lastBuild == null) {
                LOGGER.info("new branch detected " + candidateBranch);
                return candidateBranch;
            }
            List<ChangeSet> incoming = scm.changeSet(launcher, repository, listener, candidateBranch, lastBuild.id, listener.getLogger(), build.getBuiltOn(), repository);

            for (ChangeSet changeSet : incoming) {
                if (scm.computeDegreeOfChanges(changeSet, listener.getLogger()) != Change.INSIGNIFICANT) {
                    LOGGER.info("change detected on branch " + candidateBranch);
                    return candidateBranch;
                }
            }
        }
        // No change detected, build the workspace tip
        return "tip";
    }


    private transient Pattern branchSpec;

    public boolean matches(String name) {
        if (branchSpec == null) {
            branchSpec = Pattern.compile(StringUtils.replace(scm.getBranch(), "*", ".*"));
        }
        return branchSpec.matcher(name).matches();
    }


    @Extension
    public static final class DescriptorImpl extends BuildChooserDescriptor {
        @Override
        public String getDisplayName() {
            return "Default";
        }
    }

}
