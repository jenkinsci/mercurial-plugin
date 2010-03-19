package hudson.plugins.mercurial;

import hudson.AbortException;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.Hudson;
import hudson.model.Node;
import hudson.model.TaskListener;
import hudson.util.ArgumentListBuilder;
import java.io.IOException;
import java.math.BigInteger;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** HUDSON-4794: manages repository caches. */
class Cacher {

    private Cacher() {}

    private static final Map<String,ReentrantLock> locks = new HashMap<String,ReentrantLock>();

    static String repositoryCache(MercurialSCM config, Node node, String remote, Launcher launcher, TaskListener listener, boolean fromPolling)
            throws IOException, InterruptedException {
        String hashSource = hashSource(remote);
        ReentrantLock lock;
        synchronized (locks) {
            lock = locks.get(hashSource);
            if (lock == null) {
                lock = new ReentrantLock(true);
                locks.put(hashSource, lock);
            }
        }
        boolean wasLocked = lock.isLocked();
        if (wasLocked) {
            listener.getLogger().println("Waiting for lock on hgcache/" + hashSource + "...");
        }
        lock.lockInterruptibly();
        try {
            if (wasLocked) {
                listener.getLogger().println("...acquired cache lock.");
            }
            // Always update master cache first.
            Node master = Hudson.getInstance();
            FilePath masterCaches = master.getRootPath().child("hgcache");
            FilePath masterCache = masterCaches.child(hashSource);
            String masterCacheS = masterCache.getRemote();
            Launcher masterLauncher = node == master ? launcher : master.createLauncher(listener);
            // do we need to pass in EnvVars from a build too?
            if (masterCache.isDirectory()) {
                if (MercurialSCM.joinWithPossibleTimeout(MercurialSCM.launch(masterLauncher).cmds(config.findHgExe(master, listener, true).
                        add("pull")).pwd(masterCache).stdout(listener), fromPolling, listener) != 0) {
                    listener.error("Failed to update " + masterCache);
                    return null;
                }
            } else {
                masterCaches.mkdirs();
                if (MercurialSCM.joinWithPossibleTimeout(MercurialSCM.launch(masterLauncher).cmds(config.findHgExe(master, listener, true).
                        add("clone", "--noupdate", remote, masterCacheS)).stdout(listener), fromPolling, listener) != 0) {
                    listener.error("Failed to clone " + remote);
                    return null;
                }
            }
            if (node == master) {
                return masterCacheS;
            }
            // Not on master, so need to create/update local cache as well.
            FilePath localCaches = node.getRootPath().child("hgcache");
            FilePath localCache = localCaches.child(hashSource);
            FilePath masterTransfer = masterCache.child("xfer.hg");
            FilePath localTransfer = localCache.child("xfer.hg");
            try {
                if (localCache.isDirectory()) {
                    // Need to transfer just newly available changesets.
                    Set<String> masterHeads = headsOf(masterCache, config, master, masterLauncher, listener, fromPolling);
                    Set<String> localHeads = headsOf(localCache, config, node, launcher, listener, fromPolling);
                    if (localHeads.equals(masterHeads)) {
                        listener.getLogger().println("Local cache is up to date.");
                    } else {
                        // If there are some local heads not in master, they must be ancestors of new heads.
                        // If there are some master heads not in local, they could be descendants of old heads,
                        // or they could be new branches.
                        // Issue1910: in Hg 1.4.3 and earlier, passing --base $h for h in localHeads will fail
                        // to actually exclude those head sets, but not a big deal. (Hg 1.5 fixes that but leaves
                        // a major bug that if no csets are selected, the whole repo will be bundled; fortunately
                        // this case should be caught by equality check above.)
                        ArgumentListBuilder args = config.findHgExe(master, listener, true).add("bundle");
                        for (String head : localHeads) {
                            args.add("--base", head);
                        }
                        args.add("xfer.hg");
                        if (MercurialSCM.joinWithPossibleTimeout(MercurialSCM.launch(masterLauncher).cmds(args).
                                pwd(masterCache).stdout(listener), fromPolling, listener) != 0) {
                            listener.error("Failed to send outgoing changes");
                            return null;
                        }
                    }
                } else {
                    // Need to transfer entire repo.
                    if (MercurialSCM.joinWithPossibleTimeout(MercurialSCM.launch(masterLauncher).cmds(config.findHgExe(master, listener, true).
                            add("bundle", "--all", "xfer.hg")).pwd(masterCache).stdout(listener), fromPolling, listener) != 0) {
                        listener.error("Failed to bundle repo");
                        return null;
                    }
                    localCaches.mkdirs();
                    if (MercurialSCM.joinWithPossibleTimeout(MercurialSCM.launch(launcher).cmds(config.findHgExe(node, listener, true).
                            add("init", localCache.getRemote())).stdout(listener), fromPolling, listener) != 0) {
                        listener.error("Failed to create local cache");
                        return null;
                    }
                }
                if (masterTransfer.exists()) {
                    masterTransfer.copyTo(localTransfer);
                    if (MercurialSCM.joinWithPossibleTimeout(MercurialSCM.launch(launcher).cmds(config.findHgExe(node, listener, true).
                            add("unbundle", "xfer.hg")).pwd(localCache).stdout(listener), fromPolling, listener) != 0) {
                        listener.error("Failed to unbundle " + localTransfer);
                        return null;
                    }
                }
            } finally {
                masterTransfer.delete();
                localTransfer.delete();
            }
            return localCache.getRemote();
        } finally {
            lock.unlock();
        }
    }

    private static final Map<Node,Map<List<String>,Boolean>> supportsHg15Syntax = new WeakHashMap<Node,Map<List<String>,Boolean>>();
    private static Set<String> headsOf(FilePath repo, MercurialSCM config, Node node, Launcher launcher, TaskListener listener, boolean fromPolling)
            throws IOException, InterruptedException {
        // Unfortunately Hg 1.5 completely changes the meaning of the heads command (Issue1893).
        // To avoid printing an error message for every build & poll, we try to remember whether each Hg configuration was 1.5+.
        List<String> hgConfig = config.findHgExe(node, listener, false).toList();
        Map<List<String>,Boolean> supportsHg15SyntaxForNode = supportsHg15Syntax.get(node);
        if (supportsHg15SyntaxForNode == null) {
            supportsHg15SyntaxForNode = new HashMap<List<String>,Boolean>();
            supportsHg15Syntax.put(node, supportsHg15SyntaxForNode);
        }
        Boolean using15Syntax = supportsHg15SyntaxForNode.get(hgConfig);
        String output;
        if (using15Syntax == null) {
            try {
                output = runHeadsCommand(repo, config, node, launcher, listener, fromPolling, true);
                supportsHg15SyntaxForNode.put(hgConfig, true);
            } catch (AbortException x) {
                output = runHeadsCommand(repo, config, node, launcher, listener, fromPolling, false);
                supportsHg15SyntaxForNode.put(hgConfig, false);
            }
        } else if (using15Syntax) {
            output = runHeadsCommand(repo, config, node, launcher, listener, fromPolling, true);
        } else {
            output = runHeadsCommand(repo, config, node, launcher, listener, fromPolling, false);
        }
        Set<String> heads = new LinkedHashSet<String>(Arrays.asList(output.split("\n")));
        heads.remove("");
        return heads;
    }
    private static String runHeadsCommand(FilePath repo, MercurialSCM config, Node node, Launcher launcher, TaskListener listener,
            boolean fromPolling, boolean usingHg15Syntax) throws IOException, InterruptedException {
        if (usingHg15Syntax) {
            return config.runHgAndCaptureOutput(node, launcher, repo, listener, fromPolling, "heads", "--template", "{node}\\n", "--topo", "--closed");
        } else {
            return config.runHgAndCaptureOutput(node, launcher, repo, listener, fromPolling, "heads", "--template", "{node}\\n");
        }
    }

    static String hashSource(String source) {
        if (!source.endsWith("/")) {
            source += "/";
        }
        Matcher m = Pattern.compile(".+[/]([^/]+)[/]?").matcher(source);
        BigInteger hash;
        try {
            hash = new BigInteger(1, MessageDigest.getInstance("SHA-1").digest(source.getBytes("UTF-8")));
        } catch (Exception x) {
            throw new AssertionError(x);
        }
        return String.format("%040X%s", hash, m.matches() ? "-" + m.group(1) : "");
    }

}
