package org.jenkinsci.plugins.pipeline.github.trigger;

import hudson.model.Cause;
import org.jenkinsci.plugins.scriptsecurity.sandbox.whitelists.Whitelisted;

import javax.annotation.Nonnull;
import java.util.Map;

/**
 * Represents the GitHub event that triggered the build
 *
 * @author James Hollowell
 */
public class GitHubEventCause extends Cause {
    private final String eventName;
    private final Map<String, Object> eventPayload;
    private final String triggerName;

    public GitHubEventCause(@Nonnull final String eventName, final Map<String, Object> eventPayload, final String triggerName) {
        this.eventName = eventName;
        this.eventPayload = eventPayload;
        this.triggerName = triggerName;
    }

    @Whitelisted
    public String getEventName() {
        return eventName;
    }

    @Whitelisted
    public Map<String, Object> getEventPayload() {
        return eventPayload;
    }

    @Whitelisted
    public String getTriggerName() {
        return triggerName;
    }

    @Override
    public String getShortDescription() {
        if(triggerName != null) {
            return String.format("[%s] Received matching event: %s", triggerName, eventName);
        }
        return "Received matching event: " + eventName;
    }
}
