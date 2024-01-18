package org.jenkinsci.plugins.pipeline.github.trigger;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.Extension;
import hudson.model.Item;
import hudson.triggers.Trigger;
import hudson.triggers.TriggerDescriptor;
import jenkins.scm.api.SCMSource;
import org.jenkinsci.Symbol;
import org.jenkinsci.plugins.github_branch_source.GitHubSCMSource;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.kohsuke.stapler.DataBoundConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A generic trigger for any specified GitHub event, to be used from pipeline scripts only.
 * <p>
 * This trigger will not show up on a jobs configuration page.
 *
 * @author James Hollowell
 * @see org.jenkinsci.plugins.workflow.job.properties.PipelineTriggersJobProperty
 */
public class GitHubEventTrigger extends Trigger<WorkflowJob> {
    private static final Logger LOG = LoggerFactory.getLogger(GitHubEventTrigger.class);

    private final String eventName;
    private final Map<String, String> eventPayloadFilter;
    private final String triggerName;

    /**
     *
     * @param eventName The main event name to trigger on
     * @param eventPayloadFilter Optional map of keys in the event's payload to match against
     * @param triggerName Optional name of this trigger to distinguish between multiple GitHubEventTriggers
     */
    @DataBoundConstructor
    public GitHubEventTrigger(@Nonnull final String eventName, final Map<String, String> eventPayloadFilter, final String triggerName) {
        this.eventName = eventName;
        this.eventPayloadFilter = eventPayloadFilter;
        this.triggerName = triggerName;
    }

    public String getEventName() {
        return eventName;
    }

    public Map<String, String> getEventPayloadFilter() {
        return eventPayloadFilter;
    }

    public String getTriggerName() {
        return triggerName;
    }

    @Override
    public void start(final WorkflowJob project, boolean newInstance) {
        super.start(project, newInstance);
        DescriptorImpl.jobs.computeIfAbsent(getKey(project), x -> new HashSet<>())
                .add(project);
    }

    @Override
    public void stop() {
        DescriptorImpl.jobs.getOrDefault(getKey(job), Collections.emptySet())
                .remove(job);
    }

    @SuppressFBWarnings("NP_NULL_ON_SOME_PATH_FROM_RETURN_VALUE")
    private String getKey(final WorkflowJob project) {
        final GitHubSCMSource scmSource = (GitHubSCMSource) SCMSource.SourceByItem.findSource(project);

        return String.format("%s/%s/%s",
                scmSource.getRepoOwner(),
                scmSource.getRepository(),
                eventName).toLowerCase();
    }

    boolean matchesPayload(Map<String, Object> payload) {
        return true;
    }

    @Symbol("githubEventTrigger")
    @Extension
    public static class DescriptorImpl extends TriggerDescriptor {
        private transient static final Map<String, Set<WorkflowJob>> jobs = new ConcurrentHashMap<>();

        @Override
        public boolean isApplicable(final Item item) {
            return false; // this is not configurable from the ui.
        }

        public Set<WorkflowJob> getJobs(final String key) {
            return jobs.getOrDefault(key.toLowerCase(), Collections.emptySet());
        }
    }
}
