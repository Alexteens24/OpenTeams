package me.alexisbinh.openteams.core.extension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import me.alexisbinh.openteams.api.extension.CommandRegistry;
import me.alexisbinh.openteams.api.extension.MutationPolicyRegistry;
import me.alexisbinh.openteams.api.mutation.MutationIntent;
import me.alexisbinh.openteams.api.mutation.MutationType;
import me.alexisbinh.openteams.api.mutation.PolicyDecision;
import org.junit.jupiter.api.Test;
import org.bukkit.plugin.Plugin;
import java.lang.reflect.Proxy;

class ExtensionRegistriesTest {
    @Test
    void registrationIsOwnedAndClosable() {
        var registries = new ExtensionRegistries();
        var contribution = new CommandRegistry.CommandContribution(
                "bank", List.of(), "openteams.bank", "bank.help",
                (sender, arguments) -> CompletableFuture.completedFuture(1));

        var registration = registries.commands().register(plugin("bank-addon"), contribution);
        assertThat(registries.commandContributions()).containsKey("bank");

        registration.close();
        assertThat(registries.commandContributions()).isEmpty();
    }

    @Test
    void duplicateCommandIsRejected() {
        var registries = new ExtensionRegistries();
        var contribution = new CommandRegistry.CommandContribution(
                "bank", List.of(), "openteams.bank", "bank.help",
                (sender, arguments) -> CompletableFuture.completedFuture(1));
        registries.commands().register(plugin("first-addon"), contribution);

        assertThatThrownBy(() -> registries.commands().register(plugin("second-addon"), contribution))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void policyCanDenyAndTimedOutPolicyFailsOpen() {
        var warnings = new java.util.ArrayList<String>();
        var registries = new ExtensionRegistries(warnings::add);
        registries.policies().register(plugin("slow-addon"),
                new MutationPolicyRegistry.PolicyContribution(
                        "slow", 0, Duration.ofMillis(1),
                        intent -> new CompletableFuture<>()));
        registries.policies().register(plugin("rules-addon"),
                new MutationPolicyRegistry.PolicyContribution(
                        "deny-create", 10, Duration.ofMillis(100),
                        intent -> CompletableFuture.completedFuture(
                                PolicyDecision.deny("rules.create-denied"))));

        var result = registries.evaluatePolicies(new MutationIntent(
                UUID.randomUUID(), MutationType.TEAM_CREATE, UUID.randomUUID(),
                null, null, Map.of()));

        assertThat(result.allowed()).isFalse();
        assertThat(result.messageKey()).isEqualTo("rules.create-denied");
        assertThat(warnings).hasSize(1);
    }

    @Test
    void disablingOwnerRemovesAllContributions() {
        var registries = new ExtensionRegistries();
        var owner = plugin("Bank Addon");
        registries.commands().register(owner, new CommandRegistry.CommandContribution(
                "bank", List.of(), "openteams.bank", "bank.help",
                (sender, arguments) -> CompletableFuture.completedFuture(1)));

        registries.unregisterOwner(owner);

        assertThat(registries.commandContributions()).isEmpty();
    }

    private static Plugin plugin(String name) {
        return (Plugin) Proxy.newProxyInstance(
                Plugin.class.getClassLoader(),
                new Class<?>[]{Plugin.class},
                (proxy, method, arguments) -> {
                    if (method.getName().equals("getName")) {
                        return name;
                    }
                    if (method.getReturnType() == boolean.class) {
                        return false;
                    }
                    if (method.getReturnType() == int.class) {
                        return 0;
                    }
                    return null;
                });
    }
}
