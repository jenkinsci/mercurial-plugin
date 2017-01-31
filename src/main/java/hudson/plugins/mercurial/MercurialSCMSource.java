package hudson.plugins.mercurial;

import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.common.StandardUsernameCredentials;
import com.cloudbees.plugins.credentials.common.StandardUsernameListBoxModel;
import com.cloudbees.plugins.credentials.domains.URIRequirementBuilder;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.SuppressWarnings;
import hudson.EnvVars;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.Util;
import hudson.AbortException;
import hudson.model.Action;
import hudson.model.Descriptor;
import hudson.model.Item;
import hudson.model.Node;
import hudson.model.TaskListener;
import hudson.plugins.mercurial.browser.HgBrowser;
import hudson.scm.RepositoryBrowser;
import hudson.scm.RepositoryBrowsers;
import hudson.scm.SCM;
import hudson.util.ArgumentListBuilder;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import hudson.util.VersionNumber;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import javax.annotation.CheckForNull;
import jenkins.model.Jenkins;
import jenkins.scm.api.SCMFile;
import jenkins.scm.api.SCMHead;
import jenkins.scm.api.SCMHeadEvent;
import jenkins.scm.api.SCMHeadObserver;
import jenkins.scm.api.SCMProbe;
import jenkins.scm.api.SCMProbeStat;
import jenkins.scm.api.SCMRevision;
import jenkins.scm.api.SCMSource;
import jenkins.scm.api.SCMSourceCriteria;
import jenkins.scm.api.SCMSourceDescriptor;
import jenkins.scm.api.SCMSourceOwner;
import jenkins.scm.api.metadata.PrimaryInstanceMetadataAction;
import org.apache.commons.lang.StringUtils;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

public final class MercurialSCMSource extends SCMSource {

    private final String installation;
    private final String source;
    private final String credentialsId;
    private final String branchPattern;
    private final String modules;
    private final String subdir;
    private final HgBrowser browser;
    private final boolean clean;

    @DataBoundConstructor
    public MercurialSCMSource(String id, String installation, String source, String credentialsId, String branchPattern, String modules, String subdir, HgBrowser browser, boolean clean) {
        super(id);
        this.installation = installation;
        this.source = source;
        this.credentialsId = credentialsId;
        this.branchPattern = branchPattern;
        this.modules = modules;
        this.subdir = subdir;
        this.browser = browser;
        this.clean = clean;
    }
    
    public String getInstallation() {
        return installation;
    }

    public String getSource() {
        return source;
    }

    public String getCredentialsId() {
        return credentialsId;
    }

    public String getBranchPattern() {
        return branchPattern;
    }

    public String getModules() {
        return modules;
    }

    public String getSubdir() {
        return subdir;
    }

    public HgBrowser getBrowser() {
        // Could default to HgWeb the way MercurialSCM does, but probably unnecessary.
        return browser;
    }

    public boolean isClean() {
        return clean;
    }

    @Override
    protected void retrieve(@edu.umd.cs.findbugs.annotations.CheckForNull SCMSourceCriteria criteria,
                            @NonNull SCMHeadObserver observer,
                            @edu.umd.cs.findbugs.annotations.CheckForNull SCMHeadEvent<?> event,
                            @NonNull final TaskListener listener) throws IOException, InterruptedException {
        MercurialInstallation inst = MercurialSCM.findInstallation(installation);
        if (inst == null) {
            listener.error("No configured Mercurial installation");
            return;
        }
        if (!inst.isUseCaches()) {
            listener.error("Mercurial installation " + installation + " does not support caches");
            return;
        }
        final Node node = Jenkins.getInstance();
        if (node == null) { // Should not happen BTW
            listener.error("Cannot retrieve the Jenkins master node");
            return;
        }
        Launcher launcher = node.createLauncher(listener);
        StandardUsernameCredentials credentials = getCredentials();
        final FilePath cache = Cache.fromURL(source, credentials, inst.getMasterCacheRoot()).repositoryCache(inst, node, launcher, listener, true);
        if (cache == null) {
            listener.error("Could not use caches, not fetching branch heads");
            return;
        }
        final HgExe hg = new HgExe(inst, credentials, launcher, node, listener, new EnvVars());
        try {
            String heads = hg.popen(cache, listener, true, new ArgumentListBuilder("heads", "--template", "{node} {branch}\\n"));
            Pattern p = Pattern.compile(Util.fixNull(branchPattern).length() == 0 ? ".+" : branchPattern);
            for (String line : heads.split("\r?\n")) {
                final String[] nodeBranch = line.split(" ", 2);
                final String name = nodeBranch[1];
                if (p.matcher(name).matches()) {
                    listener.getLogger().println("Found branch " + name);
                    SCMHead branch = new SCMHead(name);
                    if (criteria != null) {
                        SCMProbe probe = new SCMProbe() {
                            @NonNull
                            @Override
                            public SCMProbeStat stat(@NonNull String path) throws IOException {
                                try {
                                    String files = hg.popen(cache, listener, true,
                                            new ArgumentListBuilder("locate", "-r", nodeBranch[0], "-I", "path:" + path));
                                    if (StringUtils.isBlank(files)) {
                                        return SCMProbeStat.fromType(SCMFile.Type.NONEXISTENT);
                                    }
                                    return SCMProbeStat.fromType(SCMFile.Type.REGULAR_FILE);
                                } catch (InterruptedException e) {
                                    throw new IOException(e);
                                } catch (AbortException e) {
                                    return SCMProbeStat.fromType(SCMFile.Type.NONEXISTENT);
                                }
                            }

                            @Override
                            public void close() throws IOException {

                            }

                            @Override
                            public String name() {
                                return name;
                            }

                            @Override
                            public long lastModified() {
                                return 0;
                            }
                        };
                        if (criteria.isHead(probe, listener)) {
                            listener.getLogger().println("Met criteria");
                        } else {
                            listener.getLogger().println("Does not meet criteria");
                            continue;
                        }
                    }
                    observer.observe(branch, new MercurialRevision(branch, nodeBranch[0]));
                } else {
                    listener.getLogger().println("Ignoring branch " + name);
                }
            }
        } finally {
            hg.close();
        }
    }

    @SuppressWarnings("DB_DUPLICATE_BRANCHES")
    @Override public SCM build(SCMHead head, SCMRevision revision) {
        String rev = revision == null ? head.getName() : ((MercurialRevision) revision).hash;
        return new MercurialSCM(installation, source, revision == null ? MercurialSCM.RevisionType.BRANCH : MercurialSCM.RevisionType.CHANGESET, rev, modules, subdir, browser, clean, credentialsId, false);
    }

    private @CheckForNull StandardUsernameCredentials getCredentials() {
        if (credentialsId != null) {
            for (StandardUsernameCredentials c : availableCredentials(getOwner(), source)) {
                if (c.getId().equals(credentialsId)) {
                    return c;
                }
            }
        }
        return null;
    }

    @NonNull
    @Override
    protected List<Action> retrieveActions(@NonNull SCMHead head,
                                           @edu.umd.cs.findbugs.annotations.CheckForNull SCMHeadEvent event,
                                           @NonNull TaskListener listener) throws IOException, InterruptedException {
        // TODO for Mercurial 2.4+ check for the bookmark called @ and resolve that to determine the primary
        if ("default".equals(head.getName())) {
            return Collections.<Action>singletonList(new PrimaryInstanceMetadataAction());
        }
        return Collections.emptyList();
    }

    private static List<? extends StandardUsernameCredentials> availableCredentials(@CheckForNull SCMSourceOwner owner, @CheckForNull String source) {
        return CredentialsProvider.lookupCredentials(StandardUsernameCredentials.class, owner, null, URIRequirementBuilder.fromUri(source).build());
    }

    @Extension public static final class DescriptorImpl extends SCMSourceDescriptor {

        @Override public String getDisplayName() {
            return "Mercurial";
        }

        public ListBoxModel doFillCredentialsIdItems(@AncestorInPath SCMSourceOwner owner, @QueryParameter String source) {
            if (owner == null || !owner.hasPermission(Item.EXTENDED_READ)) {
                return new ListBoxModel();
            }
            return new StandardUsernameListBoxModel()
                    .withEmptySelection()
                    .withAll(availableCredentials(owner, source));
        }

        public FormValidation doCheckBranchPattern(@QueryParameter String value) {
            try {
                Pattern.compile(value);
                return FormValidation.ok();
            } catch (PatternSyntaxException x) {
                return FormValidation.error(x.getDescription());
            }
        }

        public List<Descriptor<RepositoryBrowser<?>>> getBrowserDescriptors() {
            return RepositoryBrowsers.filter(HgBrowser.class);
        }

    }

    /*package*/ static final class MercurialRevision extends SCMRevision {
        private final String hash;
        MercurialRevision(SCMHead branch, String hash) {
            super(branch);
            this.hash = hash;
        }
        @Override public boolean equals(Object obj) {
            return obj instanceof MercurialRevision && ((MercurialRevision) obj).hash.equals(hash);
        }
        @Override public int hashCode() {
            return hash.hashCode();
        }
        @Override public String toString() {
            return getHead().getName() + ":" + hash;
        }
    }

}
