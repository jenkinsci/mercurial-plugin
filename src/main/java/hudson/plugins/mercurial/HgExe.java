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

import hudson.AbortException;
import hudson.EnvVars;
import hudson.FilePath;
import hudson.Launcher;
import hudson.Launcher.ProcStarter;
import hudson.model.AbstractBuild;
import hudson.model.Node;
import hudson.model.TaskListener;
import hudson.util.ArgumentListBuilder;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
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
    /**
     * Environment variables to invoke hg with.
     */
    private final EnvVars env;
    public final Launcher launcher;
    public final Node node;
    public final TaskListener listener;
    private final Capability capability;

    public HgExe(MercurialSCM scm, Launcher launcher, AbstractBuild build, TaskListener listener, EnvVars env) throws IOException, InterruptedException {
        this(scm,launcher,build.getBuiltOn(),listener,env);
    }

    public HgExe(MercurialSCM scm, Launcher launcher, Node node, TaskListener listener, EnvVars env) throws IOException, InterruptedException {
        base = scm.findHgExe(node, listener, true);
        this.node = node;
        this.env = env;
        this.launcher = launcher;
        this.listener = listener;
        this.capability = Capability.get(this);
    }

    private ProcStarter l(ArgumentListBuilder args) {
        // set the default stdout
        return MercurialSCM.launch(launcher).cmds(args).stdout(listener).envs(env);
    }

    private ArgumentListBuilder seed() {
        return base.clone();
    }

    public ProcStarter pull() {
        return run("pull");
    }

    public ProcStarter clone(String... args) {
        return l(seed().add("clone").add(args));
    }

    public ProcStarter bundleAll(String file) {
        return run("bundle","--all",file);
    }

    public ProcStarter bundle(Collection<String> bases, String file) {
        ArgumentListBuilder args = seed().add("bundle");
        for (String head : bases) {
            args.add("--base", head);
        }
        args.add(file);
        return l(args);
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
        return l(seed().add(args));
    }

    public ProcStarter run(ArgumentListBuilder args) {
        return l(seed().add(args.toCommandArray()));
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
     * Gets the revision ID of the tip of the workspace.
     */
    public String tip(FilePath repository) throws IOException, InterruptedException {
        String id = popen(repository, listener, false, new ArgumentListBuilder("log", "--rev", ".", "--template", "{node}"));
        if (!REVISIONID_PATTERN.matcher(id).matches()) {
            listener.error("Expected to get an id but got " + id + " instead.");
            throw new AbortException();
        }
        return id;
    }

    public List<String> toArgList() {
        return base.toList();
    }

    /**
     * Runs the command and captures the output.
     */
    public String popen(FilePath repository, TaskListener listener, boolean useTimeout, ArgumentListBuilder args)
            throws IOException, InterruptedException {
        args = seed().add(args.toCommandArray());

        ByteArrayOutputStream rev = new ByteArrayOutputStream();
        if (MercurialSCM.joinWithPossibleTimeout(l(args).pwd(repository).stdout(rev), useTimeout, listener) == 0) {
            return rev.toString();
        } else {
            listener.error("Failed to run " + args.toStringWithQuote());
            listener.getLogger().write(rev.toByteArray());
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

            List<String> hgConfig = hg.toArgList();
            Capability cap = m.get(hgConfig);
            if (cap==null)
                m.put(hgConfig,cap = new Capability());
            return cap;
        }
    }

    /**
     * Pattern that matches revision ID.
     */
    private static final Pattern REVISIONID_PATTERN = Pattern.compile("[0-9a-f]{40}");
}
