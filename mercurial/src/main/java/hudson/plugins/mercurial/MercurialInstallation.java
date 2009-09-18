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
import hudson.model.EnvironmentSpecific;
import hudson.model.Hudson;
import hudson.model.Node;
import hudson.model.TaskListener;
import hudson.slaves.NodeSpecific;
import hudson.tools.ToolDescriptor;
import hudson.tools.ToolInstallation;
import hudson.tools.ToolProperty;
import java.io.IOException;
import java.util.List;
import org.kohsuke.stapler.DataBoundConstructor;

/**
 * Installation of Mercurial.
 * Expects bin/hg (bin\hg.exe) to exist. XXX should you be able to point to a raw executable?
 */
public class MercurialInstallation extends ToolInstallation implements NodeSpecific<MercurialInstallation>, EnvironmentSpecific<MercurialInstallation> {

    @DataBoundConstructor
    public MercurialInstallation(String name, String home, List<? extends ToolProperty<?>> properties) {
        super(name, home, properties);
    }

    public static MercurialInstallation[] allInstallations() {
        return Hudson.getInstance().getDescriptorByType(DescriptorImpl.class).getInstallations();
    }

    public MercurialInstallation forNode(Node node, TaskListener log) throws IOException, InterruptedException {
        return new MercurialInstallation(getName(), translateFor(node, log), getProperties().toList());
    }

    public MercurialInstallation forEnvironment(EnvVars environment) {
        return new MercurialInstallation(getName(), environment.expand(getHome()), getProperties().toList());
    }

    @Extension
    public static class DescriptorImpl extends ToolDescriptor<MercurialInstallation> {

        @CopyOnWrite
        private volatile MercurialInstallation[] installations = new MercurialInstallation[0];

        public DescriptorImpl() {
            load();
        }

        public String getDisplayName() {
            return "Mercurial";
        }

        @Override
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
