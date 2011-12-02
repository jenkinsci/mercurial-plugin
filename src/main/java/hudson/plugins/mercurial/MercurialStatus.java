package hudson.plugins.mercurial;

import com.google.common.collect.Lists;
import hudson.Extension;
import hudson.model.AbstractModelObject;
import hudson.model.UnprotectedRootAction;
import hudson.Extension;
import hudson.model.AbstractModelObject;
import hudson.model.AbstractProject;
import hudson.model.Hudson;
import hudson.model.UnprotectedRootAction;
import hudson.scm.SCM;
import hudson.triggers.SCMTrigger;
import org.kohsuke.stapler.HttpResponse;
import org.kohsuke.stapler.HttpResponses;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

import javax.servlet.ServletException;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.URISyntaxException;
import java.util.List;
import java.util.logging.Logger;

import static javax.servlet.http.HttpServletResponse.*;
/**
 * Information screen for the use of Mercurial in Jenkins.
 */
@Extension
public class MercurialStatus extends AbstractModelObject implements UnprotectedRootAction {
    public String getDisplayName() {
        return "Mercurial";
    }

    public String getSearchUrl() {
        return getUrlName();
    }

    public String getIconFileName() {
        // TODO
        return null;
    }

    public String getUrlName() {
        return "mercurial";
    }

    public HttpResponse doNotifyCommit(@QueryParameter(required=true) String url) throws ServletException, IOException {
        final List<AbstractProject<?,?>> projects = Lists.newArrayList();
        boolean scmFound = false,
                triggerFound = false,
                urlFound = false;
        for (AbstractProject<?,?> project : Hudson.getInstance().getAllItems(AbstractProject.class)) {
            SCM scm = project.getScm();
            if (scm instanceof MercurialSCM) scmFound = true; else continue;

            MercurialSCM hg = (MercurialSCM) scm;
            String repository = hg.getSource();
            if (url.equals(repository)) urlFound = true; else continue;
            SCMTrigger trigger = project.getTrigger(SCMTrigger.class);
            if (trigger!=null) triggerFound = true; else continue;

            LOGGER.info("Triggering the polling of "+project.getFullDisplayName());
            trigger.run();
            projects.add(project);
        }

        final String msg;
        if (!scmFound)  msg = "No mercurial jobs found";
        else if (!urlFound) msg = "No mercurial jobs using repository: " + url;
        else if (!triggerFound) msg = "Jobs found but they aren't configured for polling";
        else msg = null;

        return new HttpResponse() {
            public void generateResponse(StaplerRequest req, StaplerResponse rsp, Object node) throws IOException, ServletException {
                rsp.setStatus(SC_OK);
                rsp.setContentType("text/plain");
                for (AbstractProject<?, ?> p : projects) {
                    rsp.addHeader("Triggered", p.getAbsoluteUrl());
                }
                PrintWriter w = rsp.getWriter();
                for (AbstractProject<?, ?> p : projects) {
                    w.println("Scheduled polling of "+p.getFullDisplayName());
                }
                if (msg!=null)
                    w.println(msg);
            }
        };
    }

    private static final Logger LOGGER = Logger.getLogger(MercurialStatus.class.getName());
}
