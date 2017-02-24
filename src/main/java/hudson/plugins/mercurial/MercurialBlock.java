package hudson.plugins.mercurial;

import hudson.FilePath;

import javax.annotation.CheckForNull;
import java.io.IOException;

public interface MercurialBlock<T> {
    @CheckForNull
    T invoke(HgExe hg, FilePath cache) throws IOException, InterruptedException;

}
