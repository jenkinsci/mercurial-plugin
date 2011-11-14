package hudson.plugins.mercurial.build;

import hudson.EnvVars;
import hudson.Extension;
import hudson.plugins.mercurial.MercurialTagAction;
import hudson.util.ArgumentListBuilder;

import java.util.logging.Logger;
import java.util.regex.Pattern;

import org.kohsuke.stapler.DataBoundConstructor;

/**
 * @author <a href="mailto:nicolas.deloof@cloudbees.com">Nicolas De loof</a>
 */
public class PatternMatcher extends BranchSelector {


    private transient Pattern branchSpec;

    private final String pattern;

    public String getPattern() {
        return pattern;
    }

    @DataBoundConstructor
    public PatternMatcher(String pattern) {
        this.pattern = pattern;
        this.branchSpec = Pattern.compile(pattern);
    }

    @Override
    public void select(ArgumentListBuilder cmd, EnvVars env) {
        // nop
    }

    @Override
    public void select(ArgumentListBuilder cmd) {
        // nop
    }

    @Override
    public boolean monitored(String branch) {
        return branchSpec.matcher(branch).matches();
    }

    @Override
    public String getDefaultRevisionToBuild(EnvVars env) {
        return "tip";
    }

    private Object readResolve() {
        this.branchSpec = Pattern.compile(pattern);
        return this;
    }

    @Extension
    public static final class DescriptorImpl extends BranchSelectorDescriptor {
        @Override
        public String getDisplayName() {
            return "Pattern Matcher";
        }
    }

    private static final Logger LOGGER = Logger.getLogger(PatternMatcher.class.getName());
}
