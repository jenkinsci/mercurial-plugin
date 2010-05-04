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
import hudson.FilePath;
import hudson.Launcher;
import hudson.Launcher.ProcStarter;
import hudson.model.Node;
import hudson.model.TaskListener;
import hudson.util.ArgumentListBuilder;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Collection;
import java.util.List;

/**
 * Encapsulates the invocation of "hg".
 *
 * <p>
 * This reduces the amount of code in the main logic.
 *
 * @author Kohsuke Kawaguchi
 */
public class HgExe {
    private final ArgumentListBuilder base;
    private final Launcher launcher;

    public HgExe(MercurialSCM scm, Launcher launcher, Node node, TaskListener listener) throws IOException, InterruptedException {
        base = scm.findHgExe(node, listener, true);
        this.launcher = launcher;
    }

    private ProcStarter l(ArgumentListBuilder args) {
        return MercurialSCM.launch(launcher).cmds(args);
    }

    private ArgumentListBuilder seed() {
        return base.clone();
    }

    public ProcStarter pull() {
        return l(seed().add("pull"));
    }

    public ProcStarter clone(String... args) {
        return l(seed().add("clone").add(args));
    }

    public ProcStarter bundleAll(String file) {
        return l(seed().add("bundle","--all",file));
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
        return l(seed().add("init",path.getRemote()));
    }

    public ProcStarter unbundle(String bundleFile) {
        return l(seed().add("unbundle",bundleFile));
    }

    public List<String> toArgList() {
        return base.toList();
    }

    /**
     * Runs the command and captures the output.
     */
    public String popen(FilePath repository, TaskListener listener, boolean fromPolling, ArgumentListBuilder args)
            throws IOException, InterruptedException {
        args = seed().add(args.toList());

        ByteArrayOutputStream rev = new ByteArrayOutputStream();
        if (MercurialSCM.joinWithPossibleTimeout(l(args).pwd(repository).stdout(rev), fromPolling, listener) == 0) {
            return rev.toString();
        } else {
            listener.error("Failed to run " + args.toStringWithQuote());
            listener.getLogger().write(rev.toByteArray());
            throw new AbortException();
        }
    }

}
