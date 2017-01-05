/*
 * The MIT License
 *
 * Copyright (c) 2009, Sun Microsystems, Inc., Jesse Glick
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package hudson.plugins.mercurial;

import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.SuppressWarnings;
import hudson.CopyOnWrite;
import hudson.EnvVars;
import hudson.Extension;
import hudson.Util;
import hudson.model.EnvironmentSpecific;
import hudson.model.Node;
import hudson.model.TaskListener;
import hudson.slaves.NodeSpecific;
import hudson.tools.ToolDescriptor;
import hudson.tools.ToolInstallation;
import hudson.tools.ToolProperty;
import hudson.util.FormValidation;
import java.io.IOException;
import java.io.StringReader;
import java.util.List;
import javax.annotation.CheckForNull;
import jenkins.model.Jenkins;
import org.ini4j.Ini;
import org.ini4j.InvalidFileFormatException;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

/**
 * Installation of Mercurial.
 */
@SuppressWarnings("SE_NO_SERIALVERSIONID")
public class MercurialInstallation extends ToolInstallation implements
        NodeSpecific<MercurialInstallation>,
        EnvironmentSpecific<MercurialInstallation> {
    // old fields are left so that old config data can be read in, but
    // they are deprecated. transient so that they won't show up in XML
    // when writing back
    @Deprecated
    private transient String downloadForest;

    private String executable;
    private boolean debug;
    private boolean useCaches;
    private boolean useSharing;
    private final String config;

    @Deprecated
    public MercurialInstallation(String name, String home, String executable,
            boolean debug, boolean useCaches,
            boolean useSharing, List<? extends ToolProperty<?>> properties) {
        this(name, home, executable, debug, useCaches, useSharing, null, properties);
    }

    @DataBoundConstructor public MercurialInstallation(String name, String home, String executable, boolean debug, boolean useCaches, boolean useSharing, String config, @CheckForNull List<? extends ToolProperty<?>> properties) {
        super(name, home, properties);
        this.executable = Util.fixEmpty(executable);
        this.debug = debug;
        this.useCaches = useCaches || useSharing;
        this.config = Util.fixEmptyAndTrim(config);
        this.useSharing = useSharing;
    }

    public String getExecutable() {
        return executable != null ? executable : "INSTALLATION/bin/hg";
    }

    String executableWithSubstitution(String home) {
        String _executable = getExecutable();
        if (home.isEmpty() && _executable.contains("INSTALLATION")) {
            // No home location defined on this node, so fall back to looking for Mercurial in the path.
            return "hg";
        }
        return _executable.replace("INSTALLATION", home);
    }

    public boolean getDebug() {
        return debug;
    }

    public boolean isUseCaches() {
        return useCaches;
    }

    public boolean isUseSharing() {
        return useSharing;
    }

    public @CheckForNull String getConfig() {
        return config;
    }

    @NonNull
    public static MercurialInstallation[] allInstallations() {
        final Jenkins jenkins = Jenkins.getInstance();
        if (jenkins == null) {
            return new MercurialInstallation[0];
        }
        return jenkins.getDescriptorByType(DescriptorImpl.class)
                .getInstallations();
    }

    public MercurialInstallation forNode(Node node, TaskListener log)
            throws IOException, InterruptedException {
        return new MercurialInstallation(getName(), translateFor(node, log),
                executable, debug, useCaches, useSharing,
                getProperties().toList());
    }

    public MercurialInstallation forEnvironment(EnvVars environment) {
        return new MercurialInstallation(getName(),
                environment.expand(getHome()), executable,
                debug, useCaches, useSharing, getProperties().toList());
    }

    @Extension
    public static class DescriptorImpl extends
            ToolDescriptor<MercurialInstallation> {

        @CopyOnWrite
        @SuppressWarnings("VO_VOLATILE_REFERENCE_TO_ARRAY")
        private volatile MercurialInstallation[] installations = new MercurialInstallation[0];

        public DescriptorImpl() {
            load();
        }

        public String getDisplayName() {
            return Messages.MercurialInstallation_mercurial();
        }

        @Override
        @SuppressWarnings(value = "EI_EXPOSE_REP")
        public MercurialInstallation[] getInstallations() {
            return installations;
        }

        @Override
        public void setInstallations(MercurialInstallation... installations) {
            this.installations = installations;
            save();
        }

        @java.lang.SuppressWarnings("ResultOfObjectAllocationIgnored")
        public FormValidation doCheckConfig(@QueryParameter String value) {
            if (value == null) {
                return FormValidation.ok();
            }
            try {
                new Ini(new StringReader(value));
                return FormValidation.ok();
            } catch (InvalidFileFormatException x) {
                return FormValidation.error(x.getMessage());
            } catch (IOException x) {
                return FormValidation.error(x, "should not happen");
            }
        }

    }

}
