package hudson.plugins.mercurial;

import hudson.FilePath;
import hudson.Launcher;
import hudson.model.Computer;
import hudson.model.Hudson;
import hudson.model.Node;
import hudson.model.TaskListener;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** HUDSON-4794: manages repository caches. */
class Cacher {

    private Cacher() {}

    private static final Map<String,ReentrantLock> locks = new HashMap<String,ReentrantLock>();

    static String repositoryCache(String remote, Launcher launcher, TaskListener listener) throws IOException, InterruptedException {
        Node master = Hudson.getInstance();
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
            FilePath masterCaches = master.getRootPath().child("hgcache");
            masterCaches.mkdirs();
            File masterCache = new File(masterCaches.getRemote(), hashSource);
            String masterCacheS = masterCache.getAbsolutePath();
            Node node = Computer.currentComputer().getNode();
            Launcher masterLauncher = node == master ? launcher : master.createLauncher(listener);
            // XXX use equivalent of findHgExe from MercurialSCM rather than hardcoding hg command
            if (masterCache.isDirectory()) {
                if (MercurialSCM.launch(masterLauncher).pwd(masterCache).cmds("hg", "pull").stdout(listener).join() != 0) {
                    listener.error("Failed to update " + masterCache);
                    return null;
                }
            } else {
                if (MercurialSCM.launch(masterLauncher).cmds("hg", "clone", "--noupdate", remote, masterCacheS).stdout(listener).join() != 0) {
                    listener.error("Failed to clone " + remote);
                    return null;
                }
            }
            if (node == master) {
                return masterCacheS;
            }
            // Not on master, so need to create/update local cache as well.
            listener.error("XXX caching on slaves not yet implemented");
            return null;
        } finally {
            lock.unlock();
        }
    }

    static String hashSource(String source) {
        if (!source.endsWith("/")) {
            source += "/";
        }
        Matcher m = Pattern.compile(".+[/]([^/]+)[/]?").matcher(source);
        return String.format("%08X%s", source.hashCode(), m.matches() ? "-" + m.group(1) : "");
    }

}
