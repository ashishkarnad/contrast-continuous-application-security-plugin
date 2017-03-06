package com.aspectsecurity.contrast.contrastjenkins;

import com.contrastsecurity.sdk.ContrastSDK;
import com.google.inject.Inject;
import hudson.AbortException;
import hudson.Extension;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.util.ListBoxModel;
import jenkins.model.Jenkins;
import lombok.Getter;
import org.apache.commons.io.FileUtils;
import org.jenkinsci.plugins.workflow.steps.AbstractStepDescriptorImpl;
import org.jenkinsci.plugins.workflow.steps.AbstractStepImpl;
import org.jenkinsci.plugins.workflow.steps.AbstractSynchronousStepExecution;
import org.jenkinsci.plugins.workflow.steps.Step;
import org.jenkinsci.plugins.workflow.steps.StepContextParameter;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

import java.io.File;
import java.io.IOException;
import java.util.Map;

@Getter
public class ContrastAgentStep extends AbstractStepImpl {

    private String profile;

    @DataBoundSetter
    public void setProfile(String profile) {
        this.profile = profile;
    }

    private String outputDirectory;

    @DataBoundSetter
    public void setOutputDirectory(String outputDirectory) {
        this.outputDirectory = outputDirectory;
    }

    private String agentType;

    @DataBoundSetter
    public void setAgentType(String agentType) {
        this.agentType = agentType;
    }

    @DataBoundConstructor
    public ContrastAgentStep(String profile, String outputDirectory, String agentType) {
        this.profile = profile;
        this.outputDirectory = outputDirectory;
        this.agentType = agentType;
    }

    @Override
    public ContrastAgentStepDescriptorImpl getDescriptor() {
        Jenkins instance = Jenkins.getInstance();

        if (instance != null) {
            return (ContrastAgentStepDescriptorImpl) instance.getDescriptor(getClass());
        } else {
            return null;
        }
    }

    @Extension
    public static class ContrastAgentStepDescriptorImpl extends AbstractStepDescriptorImpl {

        public ContrastAgentStepDescriptorImpl() {
            super(Execution.class);
        }

        @Override
        public String getFunctionName() {
            return "contrastAgent";
        }

        @Override
        public String getDisplayName() {
            return "Download latest Contrast agent";
        }

        @Override
        public Step newInstance(Map<String, Object> arguments) throws Exception {
            String dir, profile, agentType;

            if (!arguments.containsKey("outputDirectory")) {
                throw new IllegalArgumentException("Output directory must be set.");
            } else {
                dir = (String) arguments.get("outputDirectory");
            }

            if (!arguments.containsKey("profile")) {
                throw new IllegalArgumentException("Profile must be set.");
            } else {
                profile = (String) arguments.get("profile");
            }

            if (!arguments.containsKey("agentType")) {
                throw new IllegalArgumentException("Agent Type must be set.");
            } else {
                agentType = (String) arguments.get("agentType");
            }

            return new ContrastAgentStep(profile, dir, agentType);
        }

        @SuppressWarnings("unused")
        public ListBoxModel doFillProfileItems() {
            return VulnerabilityTrendHelper.getProfileNames();
        }

        @SuppressWarnings("unused")
        public ListBoxModel doFillAgentTypeItems() {
            return VulnerabilityTrendHelper.getAgentTypeListBoxModel();
        }

    }

    public static class Execution extends AbstractSynchronousStepExecution<Void> {
        private static final long serialVersionUID = 1L;

        @StepContextParameter
        private transient Run<?, ?> build;

        @StepContextParameter
        private transient TaskListener taskListener;

        @Inject
        private transient ContrastAgentStep step;

        @Override
        public Void run() throws AbortException {

            TeamServerProfile teamServerProfile = VulnerabilityTrendHelper.getProfile(step.getProfile());
            File agentFile = new File(step.getOutputDirectory() + "/" + "contrast.jar");

            if (teamServerProfile == null) {
                throw new AbortException("Unable to find TeamServer profile.");
            }

            VulnerabilityTrendHelper.logMessage(taskListener, "Building connected to TeamServer with profile " + step.getProfile());
            ContrastSDK contrastSDK = VulnerabilityTrendHelper.createSDK(teamServerProfile.getUsername(), teamServerProfile.getServiceKey(),
                    teamServerProfile.getApiKey(), teamServerProfile.getTeamServerUrl());

            VulnerabilityTrendHelper.logMessage(taskListener, "Downloading the agent to " + agentFile.getAbsolutePath());

            byte[] agent;

            try {
                agent = contrastSDK.getAgent(VulnerabilityTrendHelper.getAgentTypeFromString(step.getAgentType()),
                        teamServerProfile.getOrgUuid());
            } catch (Exception e) {
                VulnerabilityTrendHelper.logMessage(taskListener, e.getMessage());
                throw new AbortException("Unable to download agent from TeamServer.");
            }

            VulnerabilityTrendHelper.logMessage(taskListener, "Saving agent to file.");

            try {
                // Write bytes to file
                FileUtils.writeByteArrayToFile(agentFile, agent);
            } catch (IOException e) {
                throw new AbortException("Unable to save file to " + step.getOutputDirectory());
            }

            return null;
        }
    }
}