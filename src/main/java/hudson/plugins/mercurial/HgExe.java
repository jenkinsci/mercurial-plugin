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

import com.cloudbees.plugins.credentials.CredentialsNameProvider;
import com.cloudbees.plugins.credentials.common.StandardUsernameCredentials;
import com.cloudbees.plugins.credentials.common.UsernamePasswordCredentials;
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
import hudson.util.ArgumentListBuilder;
import jenkins.model.Jenkins;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

/**
 * Encapsulates the invocation of "hg".
 *
 * <p>
 * This reduces the amount of code in the main logic.
 *
 * TODO: perhaps a subtype 'Repository' with a repository location field would be good.
 *
 * @author Kohsuke Kawaguchi
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

    @Deprecated
    public HgExe(MercurialSCM scm, Launcher launcher, AbstractBuild build, TaskListener listener) throws IOException, InterruptedException {
        this(MercurialSCM.findInstallation(scm.getInstallation()), scm.getCredentials(build.getProject()), launcher, build.getBuiltOn(), listener, build.getEnvironment(listener));
    }

    @Deprecated
    public HgExe(MercurialSCM scm, Launcher launcher, Node node, TaskListener listener, EnvVars env) throws IOException, InterruptedException {
        this(MercurialSCM.findInstallation(scm.getInstallation()), null, launcher, node, listener, env);
    }

    public HgExe(@CheckForNull MercurialInstallation inst, @CheckForNull StandardUsernameCredentials credentials, Launcher launcher, Node node, TaskListener listener, EnvVars env) throws IOException, InterruptedException {
        base = findHgExe(inst, credentials, node, listener, true);
        // TODO might be more efficient to have a single call returning ArgumentListBuilder[2]?
        baseNoDebug = findHgExe(inst, credentials, node, listener, false);
        this.node = node;
        this.env = env;
        env.put("HGPLAIN", "true");
        this.launcher = launcher;
        this.listener = listener;
        this.capability = Capability.get(this);
    }

    private static ArgumentListBuilder findHgExe(@CheckForNull MercurialInstallation inst, @CheckForNull StandardUsernameCredentials credentials, Node node, TaskListener listener, boolean allowDebug) throws IOException, InterruptedException {
        ArgumentListBuilder b = new ArgumentListBuilder();
        if (inst == null) {
            b.add(Jenkins.getInstance().getDescriptorByType(MercurialSCM.DescriptorImpl.class).getHgExe());
        } else {
            // TODO what about forEnvironment?
            b.add(inst.executableWithSubstitution(inst.forNode(node, listener).getHome()));
            if (allowDebug && inst.getDebug()) {
                b.add("--debug");
            }
        }
        if (credentials instanceof UsernamePasswordCredentials) {
            UsernamePasswordCredentials upc = (UsernamePasswordCredentials) credentials;
            b.add("--config", "auth.jenkins.prefix=*", "--config");
            b.addMasked("auth.jenkins.username=" + upc.getUsername());
            b.add("--config");
            b.addMasked("auth.jenkins.password=" + upc.getPassword().getPlainText());
            b.add("--config", "auth.jenkins.schemes=http https");
        } else if (credentials != null) {
            throw new IOException("Support for credentials currently limited to username/password: " + CredentialsNameProvider.name(credentials));
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
     * Gets the current value of a specified config item.
     */
    public String config(FilePath repository, String name) throws IOException, InterruptedException {
        return popen(repository, listener, false, new ArgumentListBuilder("showconfig", name)).trim();
    }

    /**
     * Runs the command and captures the output.
     */
    public String popen(FilePath repository, TaskListener listener, boolean useTimeout, ArgumentListBuilder args)
            throws IOException, InterruptedException {
        args = seed(false).add(args.toCommandArray());

        ByteArrayOutputStream data = new ByteArrayOutputStream();
        if (joinWithPossibleTimeout(launch(args).pwd(repository).stdout(data), useTimeout, listener) == 0) {
            return data.toString();
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
