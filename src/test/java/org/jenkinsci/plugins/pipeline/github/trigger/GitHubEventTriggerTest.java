package org.jenkinsci.plugins.pipeline.github.trigger;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class GitHubEventTriggerTest {
    Map<String, Object> payloadIsh = Map.of(
            "action", "created",
            "repository", Map.of(
                    "id", 123456,
                    "name", "pipeline-github-plugin",
                    "owner", Map.of("login", "notMe")
            )
    );

    @Test
    void simple_matchesPayload() {
        assertTrue(new GitHubEventTrigger("label", Map.of("action", "created"), "positive").matchesPayload(payloadIsh));

        assertFalse(new GitHubEventTrigger("label", Map.of("action", "WILL_NOT_FIND"), "negative").matchesPayload(payloadIsh));

        assertTrue(new GitHubEventTrigger("label", null, "noFilter").matchesPayload(payloadIsh));

        assertFalse(new GitHubEventTrigger("label", Map.of("", "NO_NEEDLE"), "emptyNeedle").matchesPayload(payloadIsh));

    }

    @Test
    void deep_matchesPayload() {
        assertTrue(new GitHubEventTrigger("label", Map.of("repository.owner.login", "notMe"), "positive").matchesPayload(payloadIsh));

        assertFalse(new GitHubEventTrigger("label", Map.of("repository.owner.login", "WILL_NOT_FIND"), "negative").matchesPayload(payloadIsh));

        assertTrue(new GitHubEventTrigger("label", Map.of("repository.id", "123456"), "number").matchesPayload(payloadIsh));

        assertFalse(new GitHubEventTrigger("label", Map.of("repository.nope", "DOES_NOT_EXIST"), "missingKey").matchesPayload(payloadIsh));

        assertFalse(new GitHubEventTrigger("label", Map.of("repository.id.nope", "DOES_NOT_EXIST"), "keyNotMap").matchesPayload(payloadIsh));

        assertFalse(new GitHubEventTrigger("label", Map.of("repository.id.", "DOES_NOT_EXIST"), "keyNotMap").matchesPayload(payloadIsh));
    }
}
