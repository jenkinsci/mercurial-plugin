package hudson.plugins.mercurial.build;

import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.BuildListener;
import hudson.model.TaskListener;
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

import org.kohsuke.stapler.DataBoundConstructor;

/**
 * @author <a href="mailto:nicolas.deloof@cloudbees.com">Nicolas De loof</a>
 */
public class DefaultBuildChooser extends BuildChooser {

    @DataBoundConstructor
    public DefaultBuildChooser() {
    }

    @Override
    public String getRevisionToBuild(AbstractBuild<?, ?> build, Launcher launcher, FilePath repository, TaskListener listener) throws IOException, InterruptedException {

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
        return DEFAULT_REVISION;
    }

    @Extension
    public static final class DescriptorImpl extends BuildChooserDescriptor {
        @Override
        public String getDisplayName() {
            return "Default";
        }
    }

    private static final Logger LOGGER = Logger.getLogger(DefaultBuildChooser.class.getName());

}
