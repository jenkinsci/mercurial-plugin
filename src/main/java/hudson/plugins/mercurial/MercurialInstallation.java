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

import hudson.CopyOnWrite;
import hudson.EnvVars;
import hudson.Extension;
import hudson.Util;
import hudson.model.EnvironmentSpecific;
import hudson.model.TaskListener;
import hudson.model.Hudson;
import hudson.model.Node;
import hudson.slaves.NodeSpecific;
import hudson.tools.ToolDescriptor;
import hudson.tools.ToolProperty;
import hudson.tools.ToolInstallation;

import java.io.IOException;
import java.util.List;

import org.kohsuke.stapler.DataBoundConstructor;

import edu.umd.cs.findbugs.annotations.SuppressWarnings;

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
    private boolean disableChangeLog;

    @Deprecated
    public MercurialInstallation(String name, String home, String executable,
            boolean debug, boolean useCaches,
            boolean useSharing, List<? extends ToolProperty<?>> properties) {
        this(name, home, executable, debug, useCaches, useSharing, false, properties);
    }

    @DataBoundConstructor
    public MercurialInstallation(String name, String home, String executable,
            boolean debug, boolean useCaches,
            boolean useSharing, boolean disableChangeLog,
            List<? extends ToolProperty<?>> properties) {
        super(name, home, properties);
        this.executable = Util.fixEmpty(executable);
        this.debug = debug;
        this.useCaches = useCaches || useSharing;
        this.useSharing = useSharing;
        this.disableChangeLog = disableChangeLog;
    }

    public String getExecutable() {
        return executable != null ? executable : "INSTALLATION/bin/hg";
    }

    String executableWithSubstitution(String home) {
        return getExecutable().replace("INSTALLATION", home);
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

    public boolean isDisableChangeLog() {
        return disableChangeLog;
    }

    public static MercurialInstallation[] allInstallations() {
        return Hudson.getInstance().getDescriptorByType(DescriptorImpl.class)
                .getInstallations();
    }

    public MercurialInstallation forNode(Node node, TaskListener log)
            throws IOException, InterruptedException {
        return new MercurialInstallation(getName(), translateFor(node, log),
                executable, debug, useCaches, useSharing, disableChangeLog,
                getProperties().toList());
    }

    public MercurialInstallation forEnvironment(EnvVars environment) {
        return new MercurialInstallation(getName(),
                environment.expand(getHome()), executable,
                debug, useCaches, useSharing, disableChangeLog, getProperties().toList());
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

    }

}
