package hudson.plugins.mercurial;

import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.common.StandardUsernameCredentials;
import com.cloudbees.plugins.credentials.common.StandardUsernameListBoxModel;
import com.cloudbees.plugins.credentials.domains.URIRequirementBuilder;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.EnvVars;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.RestrictedSince;
import hudson.Util;
import hudson.model.Action;
import hudson.model.Descriptor;
import hudson.model.Item;
import hudson.model.Node;
import hudson.model.TaskListener;
import hudson.plugins.mercurial.browser.HgBrowser;
import hudson.plugins.mercurial.traits.CleanMercurialSCMSourceTrait;
import hudson.plugins.mercurial.traits.MercurialBrowserSCMSourceTrait;
import hudson.plugins.mercurial.traits.MercurialInstallationSCMSourceTrait;
import hudson.scm.RepositoryBrowser;
import hudson.scm.RepositoryBrowsers;
import hudson.scm.SCM;
import hudson.util.ArgumentListBuilder;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import java.io.IOException;
import java.io.ObjectStreamException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
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
import jenkins.scm.api.trait.SCMSourceRequest;
import jenkins.scm.api.trait.SCMSourceTrait;
import jenkins.scm.api.trait.SCMSourceTraitDescriptor;
import jenkins.scm.impl.trait.RegexSCMHeadFilterTrait;
import org.apache.commons.lang.StringUtils;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.DoNotUse;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;

public final class MercurialSCMSource extends SCMSource {

    private final @Nonnull String source;
    private @CheckForNull
    String credentialsId;
    private @Nonnull List<SCMSourceTrait> traits;
    @Deprecated @Restricted(NoExternalUse.class) @RestrictedSince("2.0") private transient String installation;
    @Deprecated @Restricted(NoExternalUse.class) @RestrictedSince("2.0") private transient String branchPattern;
    // USELESS FIELD REMOVED: private final String modules;
    // USELESS FIELD REMOVED: private final String subdir;
    @Deprecated @Restricted(NoExternalUse.class) @RestrictedSince("2.0") private transient HgBrowser browser;
    @Deprecated @Restricted(NoExternalUse.class) @RestrictedSince("2.0") private transient boolean clean;

    @DataBoundConstructor
    public MercurialSCMSource(String source) {
        this.source = source;
        this.traits = new ArrayList<>();
    }

    @Deprecated
    public MercurialSCMSource(String id, String source) {
        this(source);
        setId(id);
    }

    @Deprecated @Restricted(NoExternalUse.class) @RestrictedSince("2.0") public MercurialSCMSource(
            String id, String installation, String source, String credentialsId, String branchPattern, String modules,
            String subdir, HgBrowser browser, boolean clean) {
        super(id);
        this.source = source;
        this.credentialsId = credentialsId;
        this.traits = new ArrayList<>();
        if (StringUtils.isNotBlank(branchPattern) && !".*".equals(branchPattern) && !".+".equals(branchPattern)) {
            traits.add(new RegexSCMHeadFilterTrait(branchPattern));
        }
        if (clean) {
            traits.add(new CleanMercurialSCMSourceTrait());
        }
        if (installation != null) {
            traits.add(new MercurialInstallationSCMSourceTrait(installation));
        }
        if (browser != null) {
            traits.add(new MercurialBrowserSCMSourceTrait(browser));
        }
    }

    @SuppressFBWarnings("RCN_REDUNDANT_NULLCHECK_OF_NONNULL_VALUE")
    @SuppressWarnings({"deprecation", "ConstantConditions"}) private Object readResolve() throws ObjectStreamException {
        if (traits == null) {
            traits = new ArrayList<>();
            if (branchPattern != null) {
                if (StringUtils.isNotBlank(branchPattern) && !".*".equals(branchPattern) && !".+"
                        .equals(branchPattern)) {
                    traits.add(new RegexSCMHeadFilterTrait(branchPattern));
                }
            }
            if (clean) {
                traits.add(new CleanMercurialSCMSourceTrait());
            }
            if (installation != null) {
                traits.add(new MercurialInstallationSCMSourceTrait(installation));
            }
            if (browser != null) {
                traits.add(new MercurialBrowserSCMSourceTrait(browser));
            }
        }
        return this;
    }

    public @Nonnull String getSource() {
        return source;
    }

    public @Nonnull List<SCMSourceTrait> getTraits() {
        return Collections.unmodifiableList(traits);
    }

    public @CheckForNull String getCredentialsId() {
        return credentialsId;
    }

    @DataBoundSetter
    public void setCredentialsId(@CheckForNull String credentialsId) {
        this.credentialsId = credentialsId;
    }

    @DataBoundSetter public void setTraits(@CheckForNull List<SCMSourceTrait> traits) {
        this.traits = new ArrayList<>(Util.fixNull(traits));
    }

    @Deprecated @Restricted(DoNotUse.class) @RestrictedSince("2.0") public String getInstallation() {
        for (SCMSourceTrait t: traits) {
            if (t instanceof MercurialInstallationSCMSourceTrait) {
                return ((MercurialInstallationSCMSourceTrait) t).getInstallation();
            }
        }
        return null;
    }

    @Deprecated @Restricted(DoNotUse.class) @RestrictedSince("2.0") public String getBranchPattern() {
        for (SCMSourceTrait t: traits) {
            if (t instanceof RegexSCMHeadFilterTrait) {
                return ((RegexSCMHeadFilterTrait) t).getRegex();
            }
        }
        return "";
    }

    @Deprecated @Restricted(DoNotUse.class) @RestrictedSince("2.0") public String getModules() {
        return "";
    }

    @Deprecated @Restricted(DoNotUse.class) @RestrictedSince("2.0") public String getSubdir() {
        return "";
    }

    @Deprecated @Restricted(DoNotUse.class) @RestrictedSince("2.0") public HgBrowser getBrowser() {
        for (SCMSourceTrait t: traits) {
            if (t instanceof MercurialBrowserSCMSourceTrait) {
                return ((MercurialBrowserSCMSourceTrait) t).getBrowser();
            }
        }
        return null;
    }

    @Deprecated @Restricted(DoNotUse.class) @RestrictedSince("2.0") public boolean isClean() {
        for (SCMSourceTrait t: traits) {
            if (t instanceof CleanMercurialSCMSourceTrait) {
                return true;
            }
        }
        return false;
    }

    @Override protected void retrieve(@CheckForNull SCMSourceCriteria criteria, @Nonnull SCMHeadObserver observer,
                            @CheckForNull SCMHeadEvent<?> event, @Nonnull final TaskListener listener)
            throws IOException, InterruptedException {
        try (MercurialSCMSourceRequest request= new MercurialSCMSourceContext<>(criteria, observer)
                .withTraits(traits)
                .newRequest(this, listener) ) {
            MercurialInstallation inst = MercurialSCM.findInstallation(request.installation());
            if (inst == null) {
                listener.error("No configured Mercurial installation");
                return;
            }
            if (!inst.isUseCaches()) {
                listener.error("Mercurial installation " + request.installation() + " does not support caches");
                return;
            }
            final Node node = Jenkins.getInstance();
            if (node == null) { // Should not happen BTW
                listener.error("Cannot retrieve the Jenkins master node");
                return;
            }
            Launcher launcher = node.createLauncher(listener);
            StandardUsernameCredentials credentials = getCredentials(request.credentialsId());
            final FilePath cache = Cache.fromURL(request.source(), credentials, inst.getMasterCacheRoot())
                    .repositoryCache(inst, node, launcher, listener, true);
            if (cache == null) {
                listener.error("Could not use caches, not fetching branch heads");
                return;
            }
            final HgExe hg = new HgExe(inst, credentials, launcher, node, listener, new EnvVars());
            try {
                String heads = hg.popen(cache, listener, true,
                        new ArgumentListBuilder("heads", "--template", "{node} {branch}\\n"));
                int count = 0;
                for (String line : heads.split("\r?\n")) {
                    final String[] nodeBranch = line.split(" ", 2);
                    final String name = nodeBranch[1];
                    count++;
                    if (request.process(new SCMHead(name),
                            new SCMSourceRequest.RevisionLambda<SCMHead, MercurialRevision>() {
                                @Override
                                public @Nonnull
                                MercurialRevision create(@Nonnull SCMHead branch) {
                                    return new MercurialRevision(branch, nodeBranch[0]);
                                }
                            }, new SCMSourceRequest.ProbeLambda<SCMHead, MercurialRevision>() {
                                @Override
                                public @Nonnull
                                SCMSourceCriteria.Probe create(@Nonnull SCMHead branch, @Nullable final
                                MercurialRevision revision) {
                                    return new SCMProbeImpl(hg, cache, listener, revision, name);
                                }
                            }, new SCMSourceRequest.Witness() {
                                @Override
                                public void record(@NonNull SCMHead branch, SCMRevision revision, boolean isMatch) {
                                    if (revision == null) {
                                        listener.getLogger().println("Ignored branch " + branch.getName());
                                    } else {
                                        listener.getLogger().println("Found branch " + branch.getName());
                                        if (isMatch) {
                                            listener.getLogger().println("  Met criteria");
                                        } else {
                                            listener.getLogger().println("  Does not meet criteria");
                                        }
                                    }
                                }
                            })) {
                        listener.getLogger().format("Processed %d branches (query complete)%n", count);
                        return;
                    }
                }
                listener.getLogger().format("Processed %d branches%n", count);
            } finally {
                hg.close();
            }
        }
    }

    protected @Nonnull MercurialSCMBuilder<?> newBuilder(@Nonnull SCMHead head, @CheckForNull SCMRevision revision) {
        return new MercurialSCMBuilder<>(head, revision, source, credentialsId);
    }

    protected void decorate(@Nonnull MercurialSCMBuilder<?> builder) {}

    @Override public @Nonnull SCM build(@Nonnull SCMHead head, @CheckForNull SCMRevision revision) {
        MercurialSCMBuilder<?> builder = newBuilder(head, revision).withTraits(traits);
        decorate(builder);
        return builder.build();
    }

    private @CheckForNull StandardUsernameCredentials getCredentials(@CheckForNull String credentialsId) {
        if (credentialsId != null) {
            for (StandardUsernameCredentials c : availableCredentials(getOwner(), source)) {
                if (c.getId().equals(credentialsId)) {
                    return c;
                }
            }
        }
        return null;
    }

    @Override protected @Nonnull List<Action> retrieveActions(@NonNull SCMHead head,
                                           @edu.umd.cs.findbugs.annotations.CheckForNull SCMHeadEvent event,
                                           @NonNull TaskListener listener) throws IOException, InterruptedException {
        // TODO for Mercurial 2.4+ check for the bookmark called @ and resolve that to determine the primary
        if ("default".equals(head.getName())) {
            return Collections.<Action>singletonList(new PrimaryInstanceMetadataAction());
        }
        return Collections.emptyList();
    }

    private static @Nonnull List<? extends StandardUsernameCredentials> availableCredentials(
            @CheckForNull SCMSourceOwner owner, @CheckForNull String source) {
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

        @Deprecated @Restricted(DoNotUse.class) @RestrictedSince("2.0") public FormValidation doCheckBranchPattern(
                @QueryParameter String value) {
            try {
                Pattern.compile(value);
                return FormValidation.ok();
            } catch (PatternSyntaxException x) {
                return FormValidation.error(x.getDescription());
            }
        }

        @Deprecated
        @Restricted(DoNotUse.class)
        @RestrictedSince("2.0")
        public List<Descriptor<RepositoryBrowser<?>>> getBrowserDescriptors() {
            return RepositoryBrowsers.filter(HgBrowser.class);
        }

        public List<SCMSourceTraitDescriptor> getTraitDescriptors() {
            return SCMSourceTrait._for(this, MercurialSCMSourceContext.class, MercurialSCMBuilder.class);
        }

        public List<SCMSourceTrait> getTraitDefaults() {
            return Collections.emptyList();
        }

    }

    public static final class MercurialRevision extends SCMRevision {
        @Nonnull private final String hash;

        public MercurialRevision(@Nonnull SCMHead branch, @Nonnull String hash) {
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
        public @Nonnull String getHash() {
            return hash;
        }
    }

    private static class SCMProbeImpl extends SCMProbe {
        private static final long serialVersionUID = 1L;
        private final transient HgExe hg;
        private final FilePath cache;
        private final TaskListener listener;
        private final MercurialRevision revision;
        private final String name;

        public SCMProbeImpl(HgExe hg, FilePath cache, TaskListener listener, MercurialRevision revision,
                            String name) {
            this.hg = hg;
            this.cache = cache;
            this.listener = listener;
            this.revision = revision;
            this.name = name;
        }

        @Override
        public @Nonnull
        SCMProbeStat stat(@Nonnull String path) throws IOException {
            try {
                String files = hg.popen(cache, listener, true,
                        new ArgumentListBuilder("locate",
                                "-r",
                                revision.getHash(),
                                "-I",
                                "path:" + path));
                if (StringUtils.isBlank(files)) {
                    return SCMProbeStat.fromType(SCMFile.Type.NONEXISTENT);
                }
                return SCMProbeStat.fromType(SCMFile.Type.REGULAR_FILE);
            } catch (InterruptedException e) {
                throw new IOException(e);
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
    }
}
