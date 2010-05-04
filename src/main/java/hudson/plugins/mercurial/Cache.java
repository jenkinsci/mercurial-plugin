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

/**
 * Mercurial repository that serves as a cache to hg operations in the Hudson cluster.
 *
 * <p>
 * This substantially improves the performance by reducing the amount of data that needs to be transferred.
 * One cache will be built on the Hudson master, then per-slave cache is cloned from there.
 *
 * @see HUDSON-4794: manages repository caches.
 * @author Jesse Glick
 */
class Cache {
    /**
     * The remote source repository that this repository is caching.
     */
    private final String remote;

    /**
     * Hashed value of {@link #remote} that only contains characters that are safe as a directory name.
     */
    private final String hash;

    /**
     * Mutual exclusion to the access to the cache.
     */
    private final ReentrantLock lock = new ReentrantLock(true);

    private Cache(String remote, String hash) {
        this.remote = remote;
        this.hash = hash;
    }

    private static final Map<String, Cache> CACHES = new HashMap<String, Cache>();

    public synchronized static Cache fromURL(String remote) {
        String h = hashSource(remote);
        Cache cache = CACHES.get(h);
        if (cache==null)
            CACHES.put(h,cache=new Cache(remote,h));
        return cache;
    }

    /**
     * Returns a local hg repository cache of the remote repository specified in the given {@link MercurialSCM}
     * on the given {@link Node}, fully updated to the tip of the current remote repository.
     *
     * @param node
     *      The node that gets a local cached repository.
     *
     * @return
     *      The file path on the {@code node} to the local repository cache, cloned off from the master cache.
     */
    FilePath repositoryCache(MercurialSCM config, Node node, Launcher launcher, TaskListener listener, boolean fromPolling)
            throws IOException, InterruptedException {
        boolean wasLocked = lock.isLocked();
        if (wasLocked) {
            listener.getLogger().println("Waiting for lock on hgcache/" + hash + "...");
        }

        lock.lockInterruptibly();
        try {
            if (wasLocked) {
                listener.getLogger().println("...acquired cache lock.");
            }
            // Always update master cache first.
            Node master = Hudson.getInstance();
            FilePath masterCaches = master.getRootPath().child("hgcache");
            FilePath masterCache = masterCaches.child(hash);
            Launcher masterLauncher = node == master ? launcher : master.createLauncher(listener);

            // hg invocation on master
            HgExe masterHg = new HgExe(config,masterLauncher,master,listener);

            // do we need to pass in EnvVars from a build too?
            if (masterCache.isDirectory()) {
                if (MercurialSCM.joinWithPossibleTimeout(masterHg.pull().pwd(masterCache).stdout(listener), fromPolling, listener) != 0) {
                    listener.error("Failed to update " + masterCache);
                    return null;
                }
            } else {
                masterCaches.mkdirs();
                if (MercurialSCM.joinWithPossibleTimeout(masterHg.clone("clone", "--noupdate", remote, masterCache.getRemote()).stdout(listener), fromPolling, listener) != 0) {
                    listener.error("Failed to clone " + remote);
                    return null;
                }
            }
            if (node == master) {
                return masterCache;
            }
            // Not on master, so need to create/update local cache as well.
            FilePath localCaches = node.getRootPath().child("hgcache");
            FilePath localCache = localCaches.child(hash);
            FilePath masterTransfer = masterCache.child("xfer.hg");
            FilePath localTransfer = localCache.child("xfer.hg");
            try {
                // hg invocation on the slave
                HgExe slaveHg = new HgExe(config,launcher,node,listener);

                if (localCache.isDirectory()) {
                    // Need to transfer just newly available changesets.
                    Set<String> masterHeads = headsOf(masterHg, masterCache, master,  listener, fromPolling);
                    Set<String> localHeads = headsOf(slaveHg, localCache, node, listener, fromPolling);
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
                        if (MercurialSCM.joinWithPossibleTimeout(masterHg.bundle(localHeads,"xfer.hg").
                                pwd(masterCache).stdout(listener), fromPolling, listener) != 0) {
                            listener.error("Failed to send outgoing changes");
                            return null;
                        }
                    }
                } else {
                    // Need to transfer entire repo.
                    if (MercurialSCM.joinWithPossibleTimeout(masterHg.bundleAll("xfer.hg").pwd(masterCache).stdout(listener), fromPolling, listener) != 0) {
                        listener.error("Failed to bundle repo");
                        return null;
                    }
                    localCaches.mkdirs();
                    if (MercurialSCM.joinWithPossibleTimeout(slaveHg.init(localCache).stdout(listener), fromPolling, listener) != 0) {
                        listener.error("Failed to create local cache");
                        return null;
                    }
                }
                if (masterTransfer.exists()) {
                    masterTransfer.copyTo(localTransfer);
                    if (MercurialSCM.joinWithPossibleTimeout(slaveHg.unbundle("xfer.log").pwd(localCache).stdout(listener), fromPolling, listener) != 0) {
                        listener.error("Failed to unbundle " + localTransfer);
                        return null;
                    }
                }
            } finally {
                masterTransfer.delete();
                localTransfer.delete();
            }
            return localCache;
        } finally {
            lock.unlock();
        }
    }

    private static final Map<Node,Map<List<String>,Boolean>> supportsHg15Syntax = new WeakHashMap<Node,Map<List<String>,Boolean>>();
    private static Set<String> headsOf(HgExe hg, FilePath repo, Node node, TaskListener listener, boolean fromPolling)
            throws IOException, InterruptedException {
        // Unfortunately Hg 1.5 completely changes the meaning of the heads command (Issue1893).
        // To avoid printing an error message for every build & poll, we try to remember whether each Hg configuration was 1.5+.
        List<String> hgConfig = hg.toArgList();
        Map<List<String>,Boolean> supportsHg15SyntaxForNode = supportsHg15Syntax.get(node);
        if (supportsHg15SyntaxForNode == null) {
            supportsHg15SyntaxForNode = new HashMap<List<String>,Boolean>();
            supportsHg15Syntax.put(node, supportsHg15SyntaxForNode);
        }
        Boolean using15Syntax = supportsHg15SyntaxForNode.get(hgConfig);
        String output;
        if (using15Syntax == null) {
            try {
                output = runHeadsCommand(hg, repo, listener, fromPolling, true);
                supportsHg15SyntaxForNode.put(hgConfig, true);
            } catch (AbortException x) {
                output = runHeadsCommand(hg, repo, listener, fromPolling, false);
                supportsHg15SyntaxForNode.put(hgConfig, false);
            }
        } else {
            output = runHeadsCommand(hg, repo, listener, fromPolling, using15Syntax);
        }
        Set<String> heads = new LinkedHashSet<String>(Arrays.asList(output.split("\n")));
        heads.remove("");
        return heads;
    }
    
    private static String runHeadsCommand(HgExe hg, FilePath repo, TaskListener listener,
                                          boolean fromPolling, boolean usingHg15Syntax) throws IOException, InterruptedException {

        ArgumentListBuilder args = new ArgumentListBuilder("heads", "--template", "{node}\\n");
        if(usingHg15Syntax)
            args.add("--topo", "--closed");
        return hg.popen(repo,listener,fromPolling,args);
    }

    /**
     * Hash a URL into a string that only contains characters that are safe as directory names.
     */
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
