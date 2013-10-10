package hudson.plugins.mercurial;

import com.google.common.base.Objects;
import com.google.common.collect.Lists;
import hudson.Extension;
import hudson.model.AbstractModelObject;
import hudson.model.AbstractProject;
import hudson.model.Hudson;
import hudson.model.Item;
import hudson.model.UnprotectedRootAction;
import hudson.scm.SCM;
import hudson.security.ACL;
import hudson.triggers.SCMTrigger;
import org.kohsuke.stapler.HttpResponse;
import org.kohsuke.stapler.HttpResponses;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

import javax.annotation.Nonnull;
import javax.servlet.ServletException;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import static javax.servlet.http.HttpServletResponse.*;
import jenkins.scm.api.SCMSource;
import jenkins.scm.api.SCMSourceOwner;
import jenkins.scm.api.SCMSourceOwners;
import org.acegisecurity.context.SecurityContext;
import org.acegisecurity.context.SecurityContextHolder;
/**
 * Information screen for the use of Mercurial in Jenkins.
 */
@Extension
public class MercurialStatus extends AbstractModelObject implements UnprotectedRootAction {
    public String getDisplayName() {
        return Messages.MercurialStatus_mercurial();
    }

    public String getSearchUrl() {
        return getUrlName();
    }

    public String getIconFileName() {
        return null;
    }

    public String getUrlName() {
        return "mercurial";
    }
    
    private static int getPort(URI uri) {
        int port = uri.getPort();
        if ( port < 0 ){
            String scheme = uri.getScheme();
            if ("http".equals(scheme)){
                port = 80;
            } else if ("https".equals(scheme)) {
                port = 443;
            } else if ("ssh".equals(scheme)) {
                port = 22;
            }
        }
        return port;
    }

	@Nonnull
	private static String getScheme(URI uri) {
		String scheme = uri.getScheme();
		if (scheme == null) {
			return "file";
		}
		return scheme;
	}
    
    static boolean looselyMatches(URI notifyUri, String repository) {
        boolean result = false;
        try {
            URI repositoryUri = new URI(repository);
            result = getScheme(notifyUri).equals(getScheme(repositoryUri))
                && Objects.equal(notifyUri.getHost(), repositoryUri.getHost()) 
                && getPort(notifyUri) == getPort(repositoryUri)
                && Objects.equal(notifyUri.getPath(), repositoryUri.getPath())
                && Objects.equal(notifyUri.getQuery(), repositoryUri.getQuery());
        } catch ( URISyntaxException ex ) {
            LOGGER.log(Level.SEVERE, "could not parse repository uri " + repository, ex);
        }
        return result;
    }

    public HttpResponse doNotifyCommit(@QueryParameter(required=true) final String url) throws ServletException, IOException {
        // run in high privilege to see all the projects anonymous users don't see.
        // this is safe because we only initiate polling.
        SecurityContext securityContext = ACL.impersonate(ACL.SYSTEM);
        try {
            return handleNotifyCommit(new URI(url));
        } catch ( URISyntaxException ex ) {
            throw HttpResponses.error(SC_BAD_REQUEST, ex);
        } finally {
            SecurityContextHolder.setContext(securityContext);
        }
    }
    
    private HttpResponse handleNotifyCommit(URI url) throws ServletException, IOException {
        final List<Item> projects = Lists.newArrayList();
        boolean scmFound = false,
                urlFound = false;
        for (AbstractProject<?,?> project : Hudson.getInstance().getAllItems(AbstractProject.class)) {
            SCM scm = project.getScm();
            if (!(scm instanceof MercurialSCM)) {
                continue;
            }
            scmFound = true;

            MercurialSCM hg = (MercurialSCM) scm;
            String repository = hg.getSource();
            if (repository == null) {
                LOGGER.log(Level.WARNING, "project {0} is using source control but does not identify a repository", project.getFullName());
                continue;
            }
            LOGGER.log(Level.INFO, "for {0}: {1} vs. {2}", new Object[] {project.getFullName(), url, repository});
            if (!looselyMatches(url, repository)) {
                continue;
            }
            urlFound = true;
            SCMTrigger trigger = project.getTrigger(SCMTrigger.class);
            if (trigger == null || trigger.isIgnorePostCommitHooks()) {
                // Do not send message to HTTP response because this is the normal case for a multibranch component project.
                LOGGER.log(Level.INFO, "No SCMTrigger on {0}", project.getFullName());
                continue;
            }

            LOGGER.log(Level.INFO, "Triggering polling of {0}", project.getFullName());
            trigger.run();
            projects.add(project);
        }
        for (SCMSourceOwner project : SCMSourceOwners.all()) {
            for (SCMSource source : project.getSCMSources()) {
                if (!(source instanceof MercurialSCMSource)) {
                    continue;
                }
                scmFound = true;
                MercurialSCMSource hgSource = (MercurialSCMSource) source;
                String repository = hgSource.getSource();
                if (repository == null) {
                    LOGGER.log(Level.WARNING, "project {0} is using source control but does not identify a repository", project.getFullName());
                    continue;
                }
                LOGGER.log(Level.INFO, "for {0}: {1} vs. {2}", new Object[] {project.getFullName(), url, repository});
                if (!looselyMatches(url, repository)) {
                    continue;
                }
                urlFound = true;
                LOGGER.log(Level.INFO, "Scheduling {0} for refresh", project.getFullName());
                project.onSCMSourceUpdated(source);
                projects.add(project);
            }
        }

        final String msg;
        if (!scmFound) {
            msg = "No Mercurial jobs found";
        } else if (!urlFound) {
            msg = "No Mercurial jobs found using repository: " + url;
        } else {
            msg = null;
        }

        return new HttpResponse() {
            @SuppressWarnings("deprecation")
            public void generateResponse(StaplerRequest req, StaplerResponse rsp, Object node) throws IOException, ServletException {
                rsp.setStatus(SC_OK);
                rsp.setContentType("text/plain");
                for (Item p : projects) {
                    rsp.addHeader("Triggered", p.getAbsoluteUrl());
                }
                PrintWriter w = rsp.getWriter();
                for (Item p : projects) {
                    w.println("Scheduled polling of " + p.getFullName());
                }
                if (msg!=null)
                    w.println(msg);
            }
        };
    }

    private static final Logger LOGGER = Logger.getLogger(MercurialStatus.class.getName());
}
