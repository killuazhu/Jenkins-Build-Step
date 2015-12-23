/*
 * Licensed Materials - Property of IBM Corp.
 * IBM UrbanCode Deploy
 * IBM AnthillPro
 * (c) Copyright IBM Corporation 2002, 2016. All Rights Reserved.
 *
 * U.S. Government Users Restricted Rights - Use, duplication or disclosure restricted by
 * GSA ADP Schedule Contract with IBM Corp.
 */
package com.urbancode.jenkins.plugins.deploy;

import hudson.Extension;
import hudson.model.AbstractProject;
import hudson.model.JobProperty;
import hudson.model.JobPropertyDescriptor;
import hudson.model.Job;
import hudson.util.CopyOnWriteList;
import hudson.util.FormFieldValidator;

import java.io.IOException;
import java.util.Iterator;
import java.util.Map;

import javax.servlet.ServletException;

import jenkins.model.Jenkins;
import net.sf.json.JSONObject;

import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;


/**
 * This class stores the global list of all user configured site objects
 *
 */
public class GlobalConfig extends JobProperty<Job<?, ?>> {
    /**
     * {@inheritDoc}
     *
     * @return {@inheritDoc}
     */
    @Override
    public GlobalConfigDescriptor getDescriptor() {
        return (GlobalConfigDescriptor) Jenkins.getInstance().getDescriptor(getClass());
    }

    /**
     * Explicitly returns the GlobalConfigDescriptor
     *
     * @return The GlobalConfigDescriptor
     */
    public static GlobalConfigDescriptor getGlobalConfigDescriptor() {
        return (GlobalConfigDescriptor) Jenkins.getInstance().getDescriptor(GlobalConfig.class);
    }

    @Extension
    public static final class GlobalConfigDescriptor extends JobPropertyDescriptor {

        private CopyOnWriteList<UrbanDeploySite> sites = new CopyOnWriteList<UrbanDeploySite>();

        public GlobalConfigDescriptor() {
            super(GlobalConfig.class);
            load();
        }

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
         * Replace sites with user defined sites
         *
         * @param req {@inheritDoc}
         * @param formData {@inheritDoc}
         * @return {@inheritDoc}
         * @throws FormException
         */
        @Override
        public boolean configure(StaplerRequest req, JSONObject formData) throws FormException {
            sites.replaceBy(req.bindJSONToList(UrbanDeploySite.class, formData.get("sites")));
            save();
            return super.configure(req, formData);
        }

        /**
         * {@inheritDoc}
         *
         * @return {@inheritDoc}
         */
        @Override
        public String getDisplayName() {
            return "UrbanCode Deploy Server";
        }

        /**
         * Verify connectivity to the UCD site
         * @param req
         * @param rsp
         * @param url
         * @param user
         * @param password
         * @param trustAllCerts
         * @throws IOException
         * @throws ServletException
         * @deprecated FormFieldValidator
         */
        @Deprecated
        public void doTestConnection(
                StaplerRequest req,
                StaplerResponse rsp,
                @QueryParameter("url") final String url,
                @QueryParameter("user") final String user,
                @QueryParameter("password") final String password,
                @QueryParameter("trustAllCerts") final boolean trustAllCerts)
        throws IOException, ServletException {
            new FormFieldValidator(req, rsp, true) {
                @Override
                protected void check() throws IOException, ServletException {
                    try {
                        UrbanDeploySite site = new UrbanDeploySite(null, url, user, password, trustAllCerts);
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
}