/*
 * The MIT License
 *
 * Copyright 2017 CloudBees, Inc.
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

import com.cloudbees.plugins.credentials.Credentials;
import com.cloudbees.plugins.credentials.CredentialsScope;
import com.cloudbees.plugins.credentials.SystemCredentialsProvider;
import com.cloudbees.plugins.credentials.domains.Domain;
import com.cloudbees.plugins.credentials.impl.UsernamePasswordCredentialsImpl;
import hudson.model.Slave;
import hudson.plugins.sshslaves.SSHLauncher;
import hudson.slaves.DumbSlave;
import hudson.slaves.NodeProperty;
import hudson.slaves.NodePropertyDescriptor;
import hudson.tools.ToolLocationNodeProperty;
import hudson.util.DescribableList;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.apache.commons.lang3.ArrayUtils;
import org.jenkinsci.test.acceptance.docker.DockerFixture;
import org.jenkinsci.test.acceptance.docker.fixtures.JavaContainer;
import org.jvnet.hudson.test.JenkinsRule;

/**
 * Docker container containing all that is needed for an SSH agent running Mercurial.
 */
@DockerFixture(id="mercurial", ports=22)
public class MercurialContainer extends JavaContainer {

    @SuppressWarnings("deprecation")
    public Slave createSlave(JenkinsRule r) throws Exception {
        int num = r.jenkins.getNodes().size();
        String credentialsId = "test" + num;
        SystemCredentialsProvider.getInstance().setDomainCredentialsMap(Collections.singletonMap(Domain.global(), Collections.<Credentials>singletonList(new UsernamePasswordCredentialsImpl(CredentialsScope.GLOBAL, credentialsId, null, "test", "test"))));
        DumbSlave slave = new DumbSlave("agent" + num,"/home/test/agent", new SSHLauncher(ipBound(22), port(22), credentialsId));
        slave.setNumExecutors(1);
        slave.setLabelString("mercurial");
        r.jenkins.addNode(slave);
        r.waitOnline(slave);
        return slave;
    }

    public enum Version {
        // Latest in https://www.mercurial-scm.org/; MercurialContainer/Dockerfile must match:
        HG6("6.6.3");
        public final String exactVersion;
        Version(String exactVersion) {
            this.exactVersion = exactVersion;
        }
    }

    public MercurialInstallation createInstallation(JenkinsRule r, Version v, boolean debug, boolean useCaches, boolean useSharing, String config, Slave... slaves) throws IOException {
        MercurialInstallation.DescriptorImpl desc = r.jenkins.getDescriptorByType(MercurialInstallation.DescriptorImpl.class);
        ToolLocationNodeProperty.ToolLocation location = new ToolLocationNodeProperty.ToolLocation(desc, v.name(), "/opt/mercurial-" + v.exactVersion);
        MercurialInstallation inst = new MercurialInstallation(v.name(), "", "INSTALLATION/hg", debug, useCaches, useSharing, config, null);
        desc.setInstallations((MercurialInstallation[]) ArrayUtils.add(desc.getInstallations(), inst)); // TODO stop calling this here, should be responsibility of caller
        for (Slave slave : slaves) {
            DescribableList<NodeProperty<?>, NodePropertyDescriptor> props = slave.getNodeProperties();
            ToolLocationNodeProperty prop = props.get(ToolLocationNodeProperty.class);
            List<ToolLocationNodeProperty.ToolLocation> locations;
            if (prop == null) {
                locations = Collections.singletonList(location);
            } else {
                locations = new ArrayList<>(prop.getLocations());
                locations.add(location);
            }
            props.replace(new ToolLocationNodeProperty(locations));
        }
        return inst;
    }

}
