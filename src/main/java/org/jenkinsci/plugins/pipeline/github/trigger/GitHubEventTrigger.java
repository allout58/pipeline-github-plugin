package org.jenkinsci.plugins.pipeline.github.trigger;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.Extension;
import hudson.model.Item;
import hudson.triggers.Trigger;
import hudson.triggers.TriggerDescriptor;
import jenkins.scm.api.SCMHead;
import jenkins.scm.api.SCMSource;
import org.jenkinsci.Symbol;
import org.jenkinsci.plugins.github_branch_source.GitHubSCMSource;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.kohsuke.github.GHEvent;
import org.kohsuke.stapler.DataBoundConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Collectors;

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
    private static final Map<String, GHEvent> GH_EVENT_MAP;

    static {
        GH_EVENT_MAP = Arrays.stream(GHEvent.values()).collect(Collectors.toUnmodifiableMap(ev -> ev.name().toLowerCase(Locale.ENGLISH), Function.identity()));
    }

    private final GHEvent event;
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
        final String lowerEventName = eventName.toLowerCase(Locale.ENGLISH);
        this.event = GH_EVENT_MAP.get(lowerEventName);
        if (this.event == null) {
            LOG.warn("Unknown GitHub event type: {}. Known event types are [{}]", lowerEventName, String.join(", ", GH_EVENT_MAP.keySet()));
        }
        this.eventPayloadFilter = eventPayloadFilter;
        this.triggerName = triggerName;
    }

    public GHEvent getEventName() {
        return event;
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
        if (SCMHead.HeadByItem.findHead(job) != null) {
            DescriptorImpl.jobs.computeIfAbsent(getKey(project), x -> new HashSet<>())
                    .add(project);
            DescriptorImpl.watchEvent(event);
        }
    }

    @Override
    public void stop() {
        if (SCMHead.HeadByItem.findHead(job) != null) {
            DescriptorImpl.jobs.getOrDefault(getKey(job), Collections.emptySet())
                    .remove(job);
            DescriptorImpl.unwatchEvent(event);
        }
    }

    @SuppressFBWarnings("NP_NULL_ON_SOME_PATH_FROM_RETURN_VALUE")
    private String getKey(final WorkflowJob project) {
        final GitHubSCMSource scmSource = (GitHubSCMSource) SCMSource.SourceByItem.findSource(project);

        return String.format("%s/%s/%s",
                scmSource.getRepoOwner(),
                scmSource.getRepository(),
                event.name().toLowerCase(Locale.ENGLISH)).toLowerCase();
    }

    @SuppressWarnings("unchecked")
    boolean matchesPayload(Map<String, Object> payload) {
        if (eventPayloadFilter == null || eventPayloadFilter.isEmpty()) {
            return true;
        }
        for (String needle : eventPayloadFilter.keySet()) {
            if(needle.isBlank()) {
                LOG.warn("Empty needle, will match no events");
                return false;
            }
            String filter = eventPayloadFilter.get(needle);
            List<String> keys = Arrays.stream(needle.split("\\.")).collect(Collectors.toList());
            Map<String, Object> currentMap = payload;
            int depth = 0;
            while (!keys.isEmpty()) {
                String currentKey = keys.remove(0);
                if (currentKey.isBlank()) {
                    return false;
                }
                if (!currentMap.containsKey(currentKey)) {
                    LOG.debug("Could not find key {} of needle {} at depth {}", currentKey, needle, depth);
                    return false;
                }
                Object val = currentMap.get(currentKey);
                if (val instanceof Map) {
                    currentMap = (Map<String, Object>) val;
                }
                else if (!keys.isEmpty()) {
                    LOG.warn("Could not find needle {} at depth {}, not a Map", needle, depth + 1);
                    return false;
                }
                else {
                    // Okay, we are finally at the thing we want to compare
                    // Match nulls or the string version of the value
                    return (val == null && filter == null) ||
                            (val != null && val.toString().equals(filter));
                }

                depth++;
            }
        }
        return false;
    }

    @Symbol("githubEventTrigger")
    @Extension
    public static class DescriptorImpl extends TriggerDescriptor {
        private static final Map<String, Set<WorkflowJob>> jobs = new ConcurrentHashMap<>();
        private static final ConcurrentHashMap<GHEvent, Integer> eventCount = new ConcurrentHashMap<>();

        @Override
        public boolean isApplicable(final Item item) {
            return false; // this is not configurable from the ui.
        }

        public static void watchEvent(GHEvent event) {
            eventCount.merge(event, 1, Integer::sum);
        }

        public static void unwatchEvent(GHEvent event) {
            synchronized (eventCount) {
                var current = eventCount.get(event);
                if (current != null) {
                    current--;
                    if (current > 0) {
                        eventCount.put(event, current);
                    }
                    else {
                        eventCount.remove(event);
                    }
                }
                else {
                    LOG.warn("Tried to unwatch event {} that was not already watched", event.name());
                }
            }
        }

        public static Set<GHEvent> getWatchedEvents() {
            return eventCount.keySet();
        }

        public Set<WorkflowJob> getJobs(final String key) {
            return jobs.getOrDefault(key.toLowerCase(), Collections.emptySet());
        }
    }
}
