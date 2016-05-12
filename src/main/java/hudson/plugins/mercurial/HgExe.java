/*
 * The MIT License
 *
 * Copyright (c) 2004-2010, InfraDNA, Inc.
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

import com.cloudbees.jenkins.plugins.sshcredentials.SSHUserPrivateKey;
import com.cloudbees.plugins.credentials.CredentialsNameProvider;
import com.cloudbees.plugins.credentials.common.StandardUsernameCredentials;
import com.cloudbees.plugins.credentials.common.UsernamePasswordCredentials;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.KeyPair;
import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import hudson.AbortException;
import hudson.EnvVars;
import hudson.FilePath;
import hudson.Launcher;
import hudson.Launcher.ProcStarter;
import hudson.model.AbstractBuild;
import hudson.model.Node;
import hudson.model.TaskListener;
import hudson.remoting.VirtualChannel;
import hudson.util.ArgumentListBuilder;
import hudson.util.Secret;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.StringReader;
import java.net.URI;
import java.nio.charset.Charset;
import java.nio.charset.UnsupportedCharsetException;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

import jenkins.MasterToSlaveFileCallable;
import jenkins.model.Jenkins;
import org.ini4j.Ini;

/**
 * Encapsulates the invocation of the Mercurial command.
 */
public class HgExe {
    private final ArgumentListBuilder base;
    private final ArgumentListBuilder baseNoDebug;
    /**
     * Environment variables to invoke hg with.
     */
    private final EnvVars env;
    public final Launcher launcher;
    public final Node node;
    public final TaskListener listener;
    private final Capability capability;
    private final FilePath sshPrivateKey;

    @Deprecated
    public HgExe(MercurialSCM scm, Launcher launcher, AbstractBuild build, TaskListener listener) throws IOException, InterruptedException {
        this(MercurialSCM.findInstallation(scm.getInstallation()), scm.getCredentials(build.getProject(), build.getEnvironment(listener)), launcher, build.getBuiltOn(), listener, build.getEnvironment(listener));
    }

    @Deprecated
    public HgExe(MercurialSCM scm, Launcher launcher, Node node, TaskListener listener, EnvVars env) throws IOException, InterruptedException {
        this(MercurialSCM.findInstallation(scm.getInstallation()), null, launcher, node, listener, env);
    }

    /**
     * Creates a new launcher.
     * You <strong>must</strong> call {@link #close} in a {@code finally} block.
     * @param inst a particular Mercurial installation to use (optional)
     * @param credentials username/password or SSH private key credentials (optional)
     * @param launcher a way to run commands
     * @param node the machine to run commands on
     * @param listener a place to print errors
     * @param env environment variables to pass to the command
     * @throws IOException for various reasons
     * @throws InterruptedException for various reasons
     */
    public HgExe(@CheckForNull MercurialInstallation inst, @CheckForNull StandardUsernameCredentials credentials, Launcher launcher, Node node, TaskListener listener, EnvVars env) throws IOException, InterruptedException {
        base = findHgExe(inst, credentials, node, listener, true);
        // TODO might be more efficient to have a single call returning ArgumentListBuilder[2]?
        baseNoDebug = findHgExe(inst, credentials, node, listener, false);
        if (credentials instanceof SSHUserPrivateKey) {
            final SSHUserPrivateKey cc = (SSHUserPrivateKey) credentials;
            List<String> keys = cc.getPrivateKeys();
            byte[] keyData;
            if (keys.isEmpty()) {
                throw new IOException("No private key available");
            } else if (keys.size() > 1) {
                throw new IOException("Multiple private keys found.");
            } else {
                keyData = keys.get(0).getBytes("US-ASCII");
            }
            
            final Secret passphrase = cc.getPassphrase();
            if (passphrase != null && /* TODO JENKINS-21283 */ passphrase.getPlainText().length() > 0) {
                try {
                    KeyPair kp = KeyPair.load(new JSch(), keyData, null);
                    if (!kp.decrypt(passphrase.getPlainText())) {
                        throw new IOException("Passphrase did not decrypt SSH private key");
                    }
                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    kp.writePrivateKey(baos);
                    keyData = baos.toByteArray();
                } catch (JSchException x) {
                    throw new IOException("Did not manage to decrypt SSH private key: " + x, x);
                }
            }
            FilePath slaveRoot = node.getRootPath();
            if (slaveRoot == null) {
                throw new IOException(node.getDisplayName() + " is offline");
            }
            sshPrivateKey = slaveRoot.createTempFile("jenkins-mercurial", ".sshkey");
            sshPrivateKey.chmod(0600);
            // just in case slave goes offline during command; createTempFile fails to do it:
            sshPrivateKey.act(new DeleteOnExit());
            OutputStream os = sshPrivateKey.write();
            try {
                os.write(keyData);
            } finally {
                os.close();
            }
            for (ArgumentListBuilder b : new ArgumentListBuilder[] {base, baseNoDebug}) {
                b.add("--config");
                // TODO do we really want to pass -l username? Usually the username is ‘hg’ and encoded in the URL. But seems harmless at least on bitbucket.
                b.addMasked(String.format("ui.ssh=ssh -i %s -l %s", sshPrivateKey.getRemote(), cc.getUsername()));
            }
        } else {
            sshPrivateKey = null;
        }
        this.node = node;
        this.env = env;
        env.put("HGPLAIN", "true");
        this.launcher = launcher;
        this.listener = listener;
        this.capability = Capability.get(this);
    }
    private static final class DeleteOnExit extends MasterToSlaveFileCallable<Void> {
        private static final long serialVersionUID = 1;
        @Override public Void invoke(File f, VirtualChannel channel) throws IOException, InterruptedException {
            f.deleteOnExit();
            return null;
        }
    }

    public void close() throws IOException, InterruptedException { // TODO implement AutoCloseable in Java 7+
        if (sshPrivateKey != null) {
            sshPrivateKey.delete();
        }
    }

    private static ArgumentListBuilder findHgExe(@CheckForNull MercurialInstallation inst, @CheckForNull StandardUsernameCredentials credentials, Node node, TaskListener listener, boolean allowDebug) throws IOException, InterruptedException {
        ArgumentListBuilder b = new ArgumentListBuilder();
        if (inst == null) {
            final Jenkins jenkins = Jenkins.getInstance();
            if (jenkins == null) {
                throw new IOException("Jenkins instance is not ready");
            }
            b.add(jenkins.getDescriptorByType(MercurialSCM.DescriptorImpl.class).getHgExe());
        } else {
            // TODO what about forEnvironment?
            final String toolHome = inst.forNode(node, listener).getHome();
            if (toolHome == null) {
                throw new IOException("Cannot determine tool home for " + inst);
            }
            b.add(inst.executableWithSubstitution(toolHome));
            if (allowDebug && inst.getDebug()) {
                b.add("--debug");
            }
            String config = inst.getConfig();
            if (config != null) {
                for (Map.Entry<String,? extends Map<String,String>> entry : new Ini(new StringReader(config)).entrySet()) {
                    String sectionName = entry.getKey();
                    for (Map.Entry<String,String> entry2 : entry.getValue().entrySet()) {
                        b.add("--config", sectionName + '.' + entry2.getKey() + '=' + entry2.getValue());
                    }
                }
            }
        }
        if (credentials instanceof UsernamePasswordCredentials) {
            UsernamePasswordCredentials upc = (UsernamePasswordCredentials) credentials;
            b.add("--config", "auth.jenkins.prefix=*", "--config");
            b.addMasked("auth.jenkins.username=" + upc.getUsername());
            b.add("--config");
            b.addMasked("auth.jenkins.password=" + upc.getPassword().getPlainText());
            b.add("--config", "auth.jenkins.schemes=http https");
        } else if (credentials != null && !(credentials instanceof SSHUserPrivateKey)) {
            throw new IOException("Support for credentials currently limited to username/password and SSH private key: " + CredentialsNameProvider.name(credentials));
        }
        return b;
    }

    /**
     * Prepares to start the Mercurial command.
     * @param args some arguments as created by {@link #seed} and then appended to
     * @return a process starter with the correct launcher, arguments, listener, and environment variables configured
     */
    public ProcStarter launch(ArgumentListBuilder args) {
        // set the default stdout
        return launcher.launch().cmds(args).stdout(listener).envs(env);
    }

    /**
     * For use with {@link #launch} (or similar) when running commands not inside a build and which therefore might not be easily killed.
     */
    public static int joinWithPossibleTimeout(ProcStarter proc, boolean useTimeout, final TaskListener listener) throws IOException, InterruptedException {
        return useTimeout ? proc.start().joinWithTimeout(60 * 60, TimeUnit.SECONDS, listener) : proc.join();
    }
    
    /**
     * Starts creating an argument list.
     * Initially adds only the Mercurial executable itself, possibly with a debug flag.
     * @param allowDebug whether to add a debug flag if the configured installation requested it
     * @return a builder
     */
    public ArgumentListBuilder seed(boolean allowDebug) {
        return (allowDebug ? base : baseNoDebug).clone();
    }

    @Deprecated
    /**
     * @deprecated Unused, since we need more control over the argument list in order to support credentials.
     */
    public ProcStarter pull() {
        return run("pull");
    }

    @Deprecated
    /**
     * @deprecated Unused, since we need more control over the argument list in order to support credentials.
     */
    public ProcStarter clone(String... args) {
        return launch(seed(true).add("clone").add(args));
    }

    public ProcStarter bundleAll(String file) {
        return run("bundle","--all",file);
    }

    public ProcStarter bundle(Collection<String> bases, String file) {
        ArgumentListBuilder args = seed(true).add("bundle");
        for (String head : bases) {
            args.add("--base", head);
        }
        args.add(file);
        return launch(args);
    }

    public ProcStarter init(FilePath path) {
        return run("init",path.getRemote());
    }

    public ProcStarter unbundle(String bundleFile) {
        return run("unbundle",bundleFile);
    }

    public ProcStarter cleanAll() {
        return run("--config", "extensions.purge=", "clean", "--all");
    }

    /**
     * Runs arbitrary command.
     */
    public ProcStarter run(String... args) {
        return launch(seed(true).add(args));
    }

    /**
     * @deprecated Use {@link #seed} and {@link #launch} instead.
     */
    @Deprecated
    public ProcStarter run(ArgumentListBuilder args) {
        return launch(seed(true).add(args.toCommandArray()));
    }

    /**
     * Obtains the heads of the repository.
     */
    public Set<String> heads(FilePath repo, boolean useTimeout) throws IOException, InterruptedException {
        if (capability.headsIn15 == null) {
            try {
                Set<String> output = heads(repo, useTimeout, true);
                capability.headsIn15 = true;
                return output;
            } catch (AbortException x) {
                Set<String> output = heads(repo, useTimeout, false);
                capability.headsIn15 = false;
                return output;
            }
        } else {
            return heads(repo, useTimeout, capability.headsIn15);
        }
    }

    private Set<String> heads(FilePath repo, boolean useTimeout, boolean usingHg15Syntax) throws IOException, InterruptedException {
        ArgumentListBuilder args = new ArgumentListBuilder("heads", "--template", "{node}\\n");
        if(usingHg15Syntax)
            args.add("--topo", "--closed");
        String output = popen(repo,listener,useTimeout,args);

        Set<String> heads = new LinkedHashSet<String>(Arrays.asList(output.split("\n")));
        heads.remove("");
        return heads;

    }

    /**
     * Gets the revision ID or node of the tip of the workspace.
     * A 40-character hexadecimal string
     * @param rev the revision to identify; defaults to {@code .}, i.e. working copy
     */
    public @CheckForNull String tip(FilePath repository, @Nullable String rev) throws IOException, InterruptedException {
        String id = popen(repository, listener, false, new ArgumentListBuilder("log", "--rev", rev != null ? rev : ".", "--template", "{node}"));
        if (!NODEID_PATTERN.matcher(id).matches()) {
            listener.error("Expected to get an id but got '" + id + "' instead.");
            return null; // HUDSON-7723
        }
        return id;
    }

    /**
     * Gets the revision number of the tip of the workspace.
     * @param rev the revision to identify; defaults to {@code .}, i.e. working copy
     */
    public @CheckForNull String tipNumber(FilePath repository, @Nullable String rev) throws IOException, InterruptedException {
        String id = popen(repository, listener, false, new ArgumentListBuilder("log", "--rev", rev != null ? rev : ".", "--template", "{rev}"));
        if (!REVISION_NUMBER_PATTERN.matcher(id).matches()) {
            listener.error(Messages.HgExe_expected_to_get_a_revision_number_but_got_instead(id));
            return null;
        }
        return id;
    }

    /**
     * Gets the branch name of given revision number or of the current workspace.
     * @param rev the revision to identify; defaults to current working copy
     */
    public @CheckForNull String branch(FilePath repository, @CheckForNull String rev) throws IOException, InterruptedException {
        ArgumentListBuilder builder = new ArgumentListBuilder("id", "--branch");
        if (rev != null)
            builder.add("--rev", rev);
        String branch = popen(repository, listener, false, builder).trim();
        if (branch.isEmpty()) {
            listener.error(Messages.HgExe_expected_to_get_a_branch_name_but_got_nothing());
            return null;
        }
        return branch;
    }

    /**
     * Gets the version of used Mercurial installation.
     */
    public @CheckForNull String version() throws IOException, InterruptedException {
        String version = popen(null, listener, false, new ArgumentListBuilder("version"));
        if (version.isEmpty()) {
            listener.error(Messages.HgExe_expected_to_get_hg_version_name_but_got_nothing());
            return null;
        }
        Matcher m = Pattern.compile("^Mercurial Distributed SCM \\(version ([0-9][^)]*)\\)").matcher(version);
        if (!m.lookingAt() || m.groupCount() < 1)
        {
            listener.error(Messages.HgExe_cannot_extract_hg_version());
            return null;
        }
        return m.group(1);
    }

    /**
     * Gets the current value of a specified config item.
     */
    public String config(FilePath repository, String name) throws IOException, InterruptedException {
        return popen(repository, listener, false, new ArgumentListBuilder("showconfig", name)).trim();
    }

    /**
     * Runs the command and captures the output.
     */
    @NonNull
    public String popen(FilePath repository, TaskListener listener, boolean useTimeout, ArgumentListBuilder args)
            throws IOException, InterruptedException {
        args = seed(false).add(args.toCommandArray());

        ByteArrayOutputStream data = new ByteArrayOutputStream();
        if (joinWithPossibleTimeout(launch(args).pwd(repository).stdout(data), useTimeout, listener) == 0) {
            try {
                //TODO: consider using another charset
                return data.toString(Charset.defaultCharset().name());
            } catch (UnsupportedCharsetException ex) { // Should never happen
                throw new IOException("Cannot perform a conversion using the default charset", ex);
            }
        } else {
            listener.error("Failed to run " + args.toStringWithQuote());
            listener.getLogger().write(data.toByteArray());
            throw new AbortException();
        }
    }

    /**
     * Capability of a particular hg invocation configuration (and location) on a specific node. Cached.
     */
    private static final class Capability {
        /**
         * Whether this supports 1.5-style "heads --topo ..." syntax.
         */
        volatile Boolean headsIn15;

        private static final Map<Node, Map<List<String>, Capability>> MAP = new WeakHashMap<Node,Map<List<String>,Capability>>();

        synchronized static Capability get(HgExe hg) {
            Map<List<String>,Capability> m = MAP.get(hg.node);
            if (m == null) {
                m = new HashMap<List<String>,Capability>();
                MAP.put(hg.node, m);
            }

            List<String> hgConfig = hg.seed(false).toList();
            Capability cap = m.get(hgConfig);
            if (cap==null)
                m.put(hgConfig,cap = new Capability());
            return cap;
        }
    }

    /**
     * Pattern that matches revision ID.
     */
    private static final Pattern NODEID_PATTERN = Pattern.compile("[0-9a-f]{40}");
    private static final Pattern REVISION_NUMBER_PATTERN = Pattern.compile("[0-9]+");

    /**
     * Checks whether a normalized path URL matches what a config file requested.
     * @param pathURL a URL (using {@code file} protocol if local)
     * @param pathAsInConfig a repository path as in {@link #config} on {@code paths.*}
     * @return true if the paths are similar, false if they are different locations
     */
    static boolean pathEquals(@NonNull String pathURL, @NonNull String pathAsInConfig) {
        if (pathAsInConfig.equals(pathURL)) {
            return true;
        }
        if ((pathAsInConfig + '/').equals(pathURL)) {
            return true;
        }
        if (pathAsInConfig.equals(pathURL + '/')) {
            return true;
        }
        if (pathURL.startsWith("file:/") && URI.create(pathURL).equals(new File(pathAsInConfig).toURI())) {
            return true;
        }
        if (pathAsInConfig.startsWith("file:/") && URI.create(pathAsInConfig).equals(new File(pathURL).toURI())) {
            return true;
        }
        return false;
    }
}
