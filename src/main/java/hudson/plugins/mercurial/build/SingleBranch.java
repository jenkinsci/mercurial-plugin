package hudson.plugins.mercurial.build;

import hudson.EnvVars;
import hudson.Extension;
import hudson.Util;
import hudson.plugins.mercurial.MercurialTagAction;
import hudson.util.ArgumentListBuilder;

import java.util.logging.Logger;

import org.kohsuke.stapler.DataBoundConstructor;

import com.sun.istack.internal.NotNull;

/**
 * @author <a href="mailto:nicolas.deloof@cloudbees.com">Nicolas De loof</a>
 */
public class SingleBranch extends BranchSelector {

    private final String branch;

    public String getBranch() {
        return branch;
    }

    @DataBoundConstructor
    public SingleBranch(String branch) {
        branch = Util.fixEmpty(branch);
        if (branch == null) {
            branch = "default";
        }
        this.branch = branch;
    }

    @Override
    public boolean monitored(String branch) {
        return this.branch.equals(branch);
    }

    @Override
    public void select(ArgumentListBuilder cmd) {
        // Only can select the branch if definition isn't environment dependent
        if (!branch.contains("$")) {
            cmd.add("--branch", branch);
        }
    }

    @Override
    public void select(ArgumentListBuilder cmd, @NotNull EnvVars env) {
        cmd.add("--branch", env.expand(branch));
    }

    @Override
    public String getDefaultRevisionToBuild(EnvVars env) {
        return env.expand(branch);
    }

    @Extension
    public static final class DescriptorImpl extends BranchSelectorDescriptor {
        @Override
        public String getDisplayName() {
            return "Single branch";
        }
    }

    private static final Logger LOGGER = Logger.getLogger(SingleBranch.class.getName());
}
