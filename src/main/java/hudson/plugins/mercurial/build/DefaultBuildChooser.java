package hudson.plugins.mercurial.build;

import hudson.Extension;

import java.io.IOException;
import java.util.Collection;

/**
 * @author <a href="mailto:nicolas.deloof@cloudbees.com">Nicolas De loof</a>
 */
public class DefaultBuildChooser extends BuildChooser {

    @Override
    public Collection<String> getCandidateRevisions(String branchSpec) throws IOException {

        return null;
    }

    @Extension
    public static final class DescriptorImpl extends BuildChooserDescriptor {
        @Override
        public String getDisplayName() {
            return "Default";
        }
    }
}
