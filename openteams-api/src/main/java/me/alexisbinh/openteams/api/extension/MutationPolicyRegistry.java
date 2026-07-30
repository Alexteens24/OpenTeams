package me.alexisbinh.openteams.api.extension;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.CompletionStage;
import me.alexisbinh.openteams.api.mutation.MutationIntent;
import me.alexisbinh.openteams.api.mutation.PolicyDecision;
import org.bukkit.plugin.Plugin;

public interface MutationPolicyRegistry {
    Registration register(Plugin owner, PolicyContribution contribution);

    record PolicyContribution(
            String key,
            int priority,
            Duration timeout,
            MutationPolicy policy
    ) {
        public PolicyContribution {
            Objects.requireNonNull(key, "key");
            Objects.requireNonNull(timeout, "timeout");
            Objects.requireNonNull(policy, "policy");
            if (timeout.isNegative() || timeout.isZero() || timeout.compareTo(Duration.ofSeconds(2)) > 0) {
                throw new IllegalArgumentException("Policy timeout must be between 1ms and 2s");
            }
        }
    }

    @FunctionalInterface
    interface MutationPolicy {
        CompletionStage<PolicyDecision> evaluate(MutationIntent intent);
    }
}
