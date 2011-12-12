package com.urbancode.ds.jenkins.plugins.urbandeploypublisher;

import hudson.Util;
import hudson.model.AbstractProject;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Publisher;
import hudson.util.CopyOnWriteList;
import hudson.util.FormFieldValidator;
import hudson.util.FormValidation;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

import javax.servlet.ServletException;
import java.io.IOException;
import java.util.Iterator;
import java.util.ArrayList;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class UrbanDeployPublisherDescriptor extends BuildStepDescriptor<Publisher> {
    /**
     * <p> This class holds the metadata for the UrbanDeployPublisher. </p>
     */
    private final CopyOnWriteList<UrbanDeploySite> sites = new CopyOnWriteList<UrbanDeploySite>();
    /**
     * The default constructor.
     */
    public UrbanDeployPublisherDescriptor() {
        super(UrbanDeployPublisher.class);
        load();
    }

    /**
     * The name of the plugin to display them on the project configuration web page.
     * <p/>
     * {@inheritDoc}
     *
     * @return {@inheritDoc}
     * @see hudson.model.Descriptor#getDisplayName()
     */
    @Override
    public String getDisplayName() {
        return "Publish artifacts to UrbanDeploy";
    }

    /**
     * Return the location of the help document for this publisher.
     * <p/>
     * {@inheritDoc}
     *
     * @return {@inheritDoc}
     * @see hudson.model.Descriptor#getHelpFile()
     */
    @Override
    public String getHelpFile() {
        return "/plugin/urbandeploypublisher/help.html";
    }

    @Override
    public boolean isApplicable(Class<? extends AbstractProject> jobType) {
        return true;
    }


    /**
     * The getter of the sites field.
     *
     * @return the value of the sites field.
     */
    public UrbanDeploySite[] getSites() {
        Iterator<UrbanDeploySite> it = sites.iterator();
        int size = 0;
        while (it.hasNext()) {
            it.next();
            size++;
        }
        return sites.toArray(new UrbanDeploySite[size]);
    }

    /**
     * {@inheritDoc}
     *
     * @param req {@inheritDoc}
     * @return {@inheritDoc}
     * @see hudson.model.Descriptor#configure(org.kohsuke.stapler.StaplerRequest)
     */
    @Override
    public boolean configure(StaplerRequest req, JSONObject formData) {
        sites.replaceBy(req.bindParametersToList(UrbanDeploySite.class, "ud."));
        save();
        return true;
    }

    public void doTestConnection(StaplerRequest req, StaplerResponse rsp, @QueryParameter("ud.url") final String url,
                                 @QueryParameter("ud.user") final String user,
                                 @QueryParameter("ud.password") final String password)
            throws IOException, ServletException {
        new FormFieldValidator(req, rsp, true) {
            protected void check()
                    throws IOException, ServletException {
                try {
                    UrbanDeploySite site = new UrbanDeploySite(null, url, user, password);
                    site.verifyConnection();
                    ok("Success");
                }
                catch (Exception e) {
                    error(e.getMessage());
                }
            }
        }.process();
    }
}