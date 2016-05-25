package hudson.plugins.mercurial;

import com.cloudbees.plugins.credentials.common.StandardUsernameCredentials;
import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.EnvVars;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.Node;
import hudson.model.TaskListener;
import hudson.util.ArgumentListBuilder;

import java.io.IOException;
import java.math.BigInteger;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.locks.ReentrantLock;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import jenkins.model.Jenkins;

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

    private final StandardUsernameCredentials credentials;

    /**
     * Hashed value of {@link #remote} that only contains characters that are safe as a directory name.
     */
    private final String hash;

    /**
     * Mutual exclusion to the access to the cache.
     */
    private final ReentrantLock masterLock = new ReentrantLock(true);
    private final Map<String, ReentrantLock> slaveNodesLocksMap = new HashMap<String, ReentrantLock>();

    private Cache(String remote, String hash, StandardUsernameCredentials credentials) {
        this.remote = remote;
        this.hash = hash;
        this.credentials = credentials;
    }

    private static final Map<String, Cache> CACHES = new HashMap<String, Cache>();

    public synchronized static @NonNull Cache fromURL(String remote, StandardUsernameCredentials credentials) {
        String h = hashSource(remote, credentials);
        Cache cache = CACHES.get(h);
        if (cache == null) {
            CACHES.put(h, cache = new Cache(remote, h, credentials));
        }
        return cache;
    }

    /**
     * Gets a lock for the given slave node.
     * @param node Name of the slave node.
     * @return The {@link ReentrantLock} instance.
     */
    private synchronized ReentrantLock getLockForSlaveNode(String node) {
        ReentrantLock lock = slaveNodesLocksMap.get(node);
        if (lock == null) {
            slaveNodesLocksMap.put(node, lock = new ReentrantLock(true));
        }
    
        return lock;
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
    @CheckForNull FilePath repositoryCache(MercurialInstallation inst, Node node, Launcher launcher, TaskListener listener, boolean useTimeout)
            throws IOException, InterruptedException {
        boolean masterWasLocked = masterLock.isLocked();
        if (masterWasLocked) {
            listener.getLogger().println("Waiting for master lock on hgcache/" + hash + " " + masterLock + "...");
        }

            // Always update master cache first.
            final Node master = Jenkins.getInstance();
            if (master == null) { // Should not happen
                throw new IOException("Cannot retrieve the Jenkins master node");
            }
            final FilePath rootPath = master.getRootPath();
            if (rootPath == null) {
                throw new IOException("Cannot retrieve the root directory of the Jenkins master node");
            }
            
            FilePath masterCaches = rootPath.child("hgcache");
            FilePath masterCache = masterCaches.child(hash);
            Launcher masterLauncher = node == master ? launcher : master.createLauncher(listener);

            // hg invocation on master
            // do we need to pass in EnvVars from a build too?
            HgExe masterHg = new HgExe(inst, credentials, masterLauncher, master, listener, new EnvVars());
            try {

        // Lock the block used to verify we end up having a cloned repo in the master,
        // whether if it was previously cloned in a different build or if it's 
        // going to be cloned right now.
        masterLock.lockInterruptibly();
        try {
            listener.getLogger().println("Acquired master cache lock.");
            // TODO use getCredentials()
            if (masterCache.isDirectory()) {
                ArgumentListBuilder args = masterHg.seed(true).add("pull");
                if (HgExe.joinWithPossibleTimeout(masterHg.launch(args).pwd(masterCache), true, listener) != 0) {
                    listener.error("Failed to update " + masterCache);
                    return null;
                }
            } else {
                masterCaches.mkdirs();
                ArgumentListBuilder args = masterHg.seed(true).add("clone").add("--noupdate").add(remote);
                if (HgExe.joinWithPossibleTimeout(masterHg.launch(args.add(masterCache.getRemote())), useTimeout, listener) != 0) {
                    listener.error("Failed to clone " + remote);
                    return null;
                }
            }
        } finally {
            masterLock.unlock();
            listener.getLogger().println("Master cache lock released.");
        }
            if (node == master) {
                return masterCache;
            }
            // Not on master, so need to create/update local cache as well.

        // We are in a slave node that will need also an updated local cache: clone it or 
        // pull pending changes, if any. This can be safely done in parallel in
        // different slave nodes for a given repo, so we'll use different
        // node-specific locks to achieve this.
        ReentrantLock slaveNodeLock = getLockForSlaveNode(node.getNodeName());
        
        boolean slaveNodeWasLocked = slaveNodeLock.isLocked();
        if (slaveNodeWasLocked) {
            listener.getLogger().println("Waiting for slave node cache lock in " + node.getNodeName() + " on hgcache/" + hash + " " + slaveNodeWasLocked + "...");
        }
        
        slaveNodeLock.lockInterruptibly();
        try {
            listener.getLogger().println("Acquired slave node cache lock for node " + node.getNodeName() + ".");            

            final FilePath nodeRootPath = node.getRootPath();
            if (nodeRootPath == null) {
                throw new IOException("Cannot retrieve the root directory of the Jenkins node");
            }
            FilePath localCaches = nodeRootPath.child("hgcache");
            FilePath localCache = localCaches.child(hash);
            
            // Bundle name is node-specific, as we may have more than one
            // node being updated in parallel, and each one will use its own
            // bundle.
            String bundleFileName = "xfer-" + node.getNodeName() + ".hg";
            FilePath masterTransfer = masterCache.child(bundleFileName);
            FilePath localTransfer = localCache.child("xfer.hg");
            try {
                // hg invocation on the slave
                HgExe slaveHg = new HgExe(inst, credentials, launcher, node, listener, new EnvVars());
                try {

                if (localCache.isDirectory()) {
                    // Need to transfer just newly available changesets.
                    Set<String> masterHeads = masterHg.heads(masterCache, useTimeout);
                    Set<String> localHeads = slaveHg.heads(localCache, useTimeout);
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
                        if (HgExe.joinWithPossibleTimeout(masterHg.bundle(localHeads,bundleFileName).
                                pwd(masterCache), useTimeout, listener) != 0) {
                            listener.error("Failed to send outgoing changes");
                            return null;
                        }
                    }
                } else {
                    // Need to transfer entire repo.
                    if (HgExe.joinWithPossibleTimeout(masterHg.bundleAll(bundleFileName).pwd(masterCache), useTimeout, listener) != 0) {
                        listener.error("Failed to bundle repo");
                        return null;
                    }
                    localCaches.mkdirs();
                    if (HgExe.joinWithPossibleTimeout(slaveHg.init(localCache), useTimeout, listener) != 0) {
                        listener.error("Failed to create local cache");
                        return null;
                    }
                }
                if (masterTransfer.exists()) {
                    masterTransfer.copyTo(localTransfer);
                    if (HgExe.joinWithPossibleTimeout(slaveHg.unbundle("xfer.hg").pwd(localCache), useTimeout, listener) != 0) {
                        listener.error("Failed to unbundle " + localTransfer);
                        return null;
                    }
                }
                } finally {
                    slaveHg.close();
                }
            } finally {
                masterTransfer.delete();
                localTransfer.delete();
            }
            return localCache;
        } finally {
            slaveNodeLock.unlock();
            listener.getLogger().println("Slave node cache lock released for node " + node.getNodeName() + ".");
        }
            } finally {
            masterHg.close();
        }
    }


    /**
     * Hash a URL into a string that only contains characters that are safe as directory names.
     */
    static String hashSource(String source, StandardUsernameCredentials credentials) {
        if (!source.endsWith("/")) {
            source += "/";
        }
        Matcher m = Pattern.compile(".+[/]([^/:]+)(:\\d+)?[/]?").matcher(source);
        String digestible = credentials == null ? source : source + '#' + credentials.getId();
        BigInteger hash;
        try {
            hash = new BigInteger(1, MessageDigest.getInstance("SHA-1").digest(digestible.getBytes("UTF-8")));
        } catch (Exception x) {
            throw new AssertionError(x);
        }
        return String.format("%040X%s%s", hash, m.matches() ? "-" + m.group(1) : "", credentials == null ? "" : "-" + credentials.getUsername().replace("@", "-at-"));
    }

}
