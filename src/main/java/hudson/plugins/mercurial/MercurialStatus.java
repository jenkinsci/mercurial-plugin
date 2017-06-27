package hudson.plugins.mercurial;

import com.google.common.base.Objects;
import com.google.common.collect.Lists;
import hudson.Extension;
import hudson.model.AbstractModelObject;
import hudson.model.Item;
import hudson.model.UnprotectedRootAction;
import hudson.scm.SCM;
import hudson.security.ACL;
import hudson.triggers.SCMTrigger;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.servlet.ServletException;
import jenkins.model.Jenkins;
import jenkins.scm.api.SCMEvent;
import jenkins.scm.api.SCMHeadEvent;
import jenkins.scm.api.SCMSource;
import jenkins.scm.api.SCMSourceOwner;
import jenkins.scm.api.SCMSourceOwners;
import jenkins.triggers.SCMTriggerItem;
import org.acegisecurity.context.SecurityContext;
import org.acegisecurity.context.SecurityContextHolder;
import org.apache.commons.lang.StringUtils;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.HttpResponse;
import org.kohsuke.stapler.HttpResponses;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.Stapler;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

import static javax.servlet.http.HttpServletResponse.SC_BAD_REQUEST;
import static javax.servlet.http.HttpServletResponse.SC_OK;
import static javax.servlet.http.HttpServletResponse.SC_SERVICE_UNAVAILABLE;
/**
 * Information screen for the use of Mercurial in Jenkins.
 */
@Extension
public class MercurialStatus extends AbstractModelObject implements UnprotectedRootAction {

    public static final String URL_NAME = "mercurial";

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
        return URL_NAME;
    }

    static private boolean isUnexpandedEnvVar(String str) {
        return str.startsWith("$");
    }

    static boolean looselyMatches(URI notifyUri, String repository) {
        boolean result = false;
        try {
            if (!isUnexpandedEnvVar(repository)) {
                URI repositoryUri = new URI(repository);
                result = Objects.equal(notifyUri.getHost(), repositoryUri.getHost())
                    && Objects.equal(StringUtils.stripEnd(notifyUri.getPath(), "/"), StringUtils.stripEnd(repositoryUri.getPath(), "/"))
                    && Objects.equal(notifyUri.getQuery(), repositoryUri.getQuery());
            }
        } catch ( URISyntaxException ex ) {
            LOGGER.log(Level.SEVERE, "could not parse repository uri " + repository, ex);
        }
        return result;
    }

    /**
     * Handles the incoming commit notification. <strong>NOTE:</strong> This handles two types of notification:
     * <ul>
     * <li>Legacy notification such as from hooks like:
     * <pre>
     * commit.jenkins = wget -q -O /dev/null &lt;jenkins root&gt;/mercurial/notifyCommit?url=&lt;repository remote url&gt;
     * </pre>
     * </li>
     * <li>Modern notifications such as from hooks like:
     * <pre>
     * commit.jenkins = python:&lt;path to hook.py&gt;
     * </pre>
     * using an in-process hook such as either
     * <pre>
     * import urilib
     * import urilib2
     *
     * def commit(ui, repo, node, **kwargs):
     *     data = {
     *         'url': '&lt;repository remote url&gt;',
     *         'branch': repo[node].branch(),
     *         'changesetId': node,
     *     }
     *     req = urllib2.Request('&lt;jenkins root&gt;/mercurial/notifyCommit')
     *     urllib2.urlopen(req, urllib.urlencode(data)).read()
     *     pass
     * </pre>
     * or
     * <pre>
     * import requests
     *
     * def commit(ui, repo, node, **kwargs):
     *     requests.post('&lt;jenkins root&gt;/mercurial/notifyCommit', data={"url":"&lt;repository remote url&gt;","branch":repo[node].branch(),"changesetId":node})
     *     pass
     * </pre>
     * </li>
     * </ul>
     * When used with a legacy notification, multi-branch jobs will be forced to perform full indexing, whereas when
     * used with a modern notification that includes the branch and changesetId then the notification will be processed
     * using the SCM API event subsystem resulting in a much more scoped and efficient processing of the event.
     *
     * @param url the URL of the mercurial repository
     * @param branch (optional) branch name of the commit.
     * @param changesetId (optional) changesetId of the commit.
     * @return the HTTP response
     * @throws ServletException if something goes wrong.
     * @throws IOException if something goes wrong.
     */
    @Restricted(NoExternalUse.class) // Exposed by Stapler, not for direct invocation
    public HttpResponse doNotifyCommit(@QueryParameter(required=true) final String url,
                                       @QueryParameter String branch,
                                       @QueryParameter String changesetId) throws ServletException, IOException {
        String origin = SCMEvent.originOf(Stapler.getCurrentRequest());
        // run in high privilege to see all the projects anonymous users don't see.
        // this is safe because we only initiate polling.
        SecurityContext securityContext = ACL.impersonate(ACL.SYSTEM);
        try {
            if (StringUtils.isNotBlank(branch) && StringUtils.isNotBlank(changesetId)) {
                SCMHeadEvent.fireNow(new MercurialSCMHeadEvent(
                        SCMEvent.Type.UPDATED, new MercurialCommitPayload(new URI(url), branch, changesetId),
                        origin));
                return HttpResponses.ok();
            }
            return handleNotifyCommit(origin, new URI(url));
        } catch ( URISyntaxException ex ) {
            throw HttpResponses.error(SC_BAD_REQUEST, ex);
        } finally {
            SecurityContextHolder.setContext(securityContext);
        }
    }

    private HttpResponse handleNotifyCommit(String origin, URI url) throws ServletException, IOException {
        final List<Item> projects = Lists.newArrayList();
        boolean scmFound = false,
                urlFound = false;
        final Jenkins jenkins = Jenkins.getInstance();
        if (jenkins == null) {
            return HttpResponses.error(SC_SERVICE_UNAVAILABLE, "Jenkins instance is not ready");
        }

        for (Item project : jenkins.getAllItems()) {
            SCMTriggerItem scmTriggerItem = SCMTriggerItem.SCMTriggerItems.asSCMTriggerItem(project);
            if (scmTriggerItem == null) {
                continue;
            }
            SCMS: for (SCM scm : scmTriggerItem.getSCMs()) {
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
            SCMTrigger trigger = scmTriggerItem.getSCMTrigger();
            if (trigger == null || trigger.isIgnorePostCommitHooks()) {
                // Do not send message to HTTP response because this is the normal case for a multibranch component project.
                LOGGER.log(Level.INFO, "No SCMTrigger on {0}", project.getFullName());
                continue;
            }

            LOGGER.log(Level.INFO, "Triggering polling of {0} after event from {1}", new Object[]{
                    project.getFullName(), origin
            });
            trigger.run();
            projects.add(project);
            break SCMS;
            }
        }
        for (SCMSourceOwner project : SCMSourceOwners.all()) {
            for (SCMSource source : project.getSCMSources()) {
                if (!(source instanceof MercurialSCMSource)) {
                    continue;
                }
                scmFound = true;
                MercurialSCMSource hgSource = (MercurialSCMSource) source;
                String repository = hgSource.getSource();
                if (StringUtils.isBlank(repository)) {
                    LOGGER.log(Level.WARNING, "project {0} is using source control but does not identify a repository", project.getFullName());
                    continue;
                }
                LOGGER.log(Level.INFO, "for {0}: {1} vs. {2}", new Object[] {project.getFullName(), url, repository});
                if (!looselyMatches(url, repository)) {
                    continue;
                }
                urlFound = true;
                LOGGER.log(Level.INFO, "Scheduling {0} for refresh after event from {1}", new Object[]{
                        project.getFullName(), origin
                });
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
