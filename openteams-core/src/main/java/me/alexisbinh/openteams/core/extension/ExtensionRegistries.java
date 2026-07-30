package me.alexisbinh.openteams.core.extension;

import java.util.Locale;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import me.alexisbinh.openteams.api.extension.CommandRegistry;
import me.alexisbinh.openteams.api.extension.PlaceholderRegistry;
import me.alexisbinh.openteams.api.extension.MutationPolicyRegistry;
import me.alexisbinh.openteams.api.extension.Registration;
import me.alexisbinh.openteams.api.extension.TeamPermissionRegistry;
import me.alexisbinh.openteams.api.extension.TeamSettingRegistry;
import me.alexisbinh.openteams.api.extension.TeamUiRegistry;
import me.alexisbinh.openteams.api.extension.TranslationRegistry;
import me.alexisbinh.openteams.api.mutation.MutationIntent;
import me.alexisbinh.openteams.api.mutation.PolicyDecision;
import org.bukkit.plugin.Plugin;

public final class ExtensionRegistries {
    private static final Set<String> RESERVED_COMMANDS = Set.of(
            "create", "info", "invite", "accept", "leave", "kick", "transfer",
            "rename", "tag", "disband", "chat", "request", "approve", "ban",
            "unban", "role", "setting", "help");

    private final Map<String, OwnedCommand> commands = new ConcurrentHashMap<>();
    private final Map<String, OwnedUiAction> uiActions = new ConcurrentHashMap<>();
    private final Map<String, OwnedValue<PlaceholderRegistry.Placeholder>> placeholders =
            new ConcurrentHashMap<>();
    private final Map<String, OwnedValue<TeamSettingRegistry.Setting<?>>> settings =
            new ConcurrentHashMap<>();
    private final Map<String, OwnedValue<TeamPermissionRegistry.Permission>> permissions =
            new ConcurrentHashMap<>();
    private final Map<String, OwnedValue<Map<String, String>>> translations =
            new ConcurrentHashMap<>();
    private final Map<String, OwnedValue<MutationPolicyRegistry.PolicyContribution>> policies =
            new ConcurrentHashMap<>();
    private final Consumer<String> warningSink;

    public ExtensionRegistries() {
        this(message -> { });
    }

    public ExtensionRegistries(Consumer<String> warningSink) {
        this.warningSink = Objects.requireNonNull(warningSink, "warningSink");
    }

    private final CommandRegistry commandRegistry = this::registerCommand;

    private final TeamUiRegistry uiRegistry = (owner, action) -> {
        var ownerId = ownerId(owner);
        var key = namespaced(ownerId, action.key());
        var owned = new OwnedUiAction(ownerId, action);
        if (uiActions.putIfAbsent(key, owned) != null) {
            throw new IllegalArgumentException("UI contribution already registered: " + key);
        }
        return registration(ownerId, key, uiActions);
    };

    private final PlaceholderRegistry placeholderRegistry = (owner, placeholder) ->
            registerOwned(placeholders, owner, placeholder.key(), placeholder);

    private final TeamSettingRegistry settingRegistry = new TeamSettingRegistry() {
        @Override
        public <T> Registration register(Plugin owner, Setting<T> setting) {
            return registerOwned(settings, owner, setting.key(), setting);
        }
    };

    private final TeamPermissionRegistry permissionRegistry = (owner, permission) ->
            registerOwned(permissions, owner, permission.key(), permission);

    private final TranslationRegistry translationRegistry = (owner, locale, entries) ->
            registerOwned(translations, owner, locale.toLanguageTag(), Map.copyOf(entries));
    private final MutationPolicyRegistry policyRegistry = (owner, policy) ->
            registerOwned(policies, owner, policy.key(), policy);

    public CommandRegistry commands() {
        return commandRegistry;
    }

    public TeamUiRegistry userInterface() {
        return uiRegistry;
    }

    public PlaceholderRegistry placeholders() {
        return placeholderRegistry;
    }

    public TeamSettingRegistry settings() {
        return settingRegistry;
    }

    public TeamPermissionRegistry permissions() {
        return permissionRegistry;
    }

    public TranslationRegistry translations() {
        return translationRegistry;
    }

    public MutationPolicyRegistry policies() {
        return policyRegistry;
    }

    public PolicyDecision evaluatePolicies(MutationIntent intent) {
        var ordered = policies.values().stream()
                .sorted(java.util.Comparator.comparingInt(value -> value.value().priority()))
                .toList();
        for (var owned : ordered) {
            var contribution = owned.value();
            try {
                var decision = contribution.policy().evaluate(intent).toCompletableFuture().get(
                        contribution.timeout().toMillis(), TimeUnit.MILLISECONDS);
                if (!decision.allowed()) {
                    return decision;
                }
            } catch (Exception exception) {
                warningSink.accept("Mutation policy " + owned.owner() + ":" + contribution.key()
                        + " failed or timed out; continuing fail-open: " + exception.getMessage());
            }
        }
        return PolicyDecision.allow();
    }

    public SettingValidation validateSetting(String key, String encodedValue) {
        var owned = settings.get(key);
        if (owned == null) {
            return new SettingValidation(false, "");
        }
        try {
            if (!validEncodedValue(owned.value(), encodedValue)) {
                return new SettingValidation(false, "");
            }
            return new SettingValidation(true, owned.value().permission());
        } catch (RuntimeException exception) {
            return new SettingValidation(false, "");
        }
    }

    public void reportWarning(String message) {
        warningSink.accept(message);
    }

    public Set<String> defaultPermissions(String roleKey) {
        return permissions.values().stream()
                .map(OwnedValue::value)
                .filter(permission -> permission.defaultRoles().contains(roleKey))
                .map(TeamPermissionRegistry.Permission::key)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    private static <T> boolean validEncodedValue(
            TeamSettingRegistry.Setting<T> setting,
            String encodedValue
    ) {
        return setting.validator().test(setting.codec().decode(encodedValue));
    }

    public Map<String, OwnedCommand> commandContributions() {
        return Map.copyOf(commands);
    }

    private synchronized Registration registerCommand(
            Plugin owner,
            CommandRegistry.CommandContribution contribution
    ) {
        var ownerId = ownerId(owner);
        var names = new HashSet<String>();
        names.add(contribution.name().toLowerCase(Locale.ROOT));
        contribution.aliases().stream()
                .map(alias -> alias.toLowerCase(Locale.ROOT))
                .forEach(names::add);
        if (names.stream().anyMatch(RESERVED_COMMANDS::contains)) {
            throw new IllegalArgumentException("Addon command conflicts with a Core command");
        }
        if (names.stream().anyMatch(commands::containsKey)) {
            throw new IllegalArgumentException("Addon command or alias is already registered");
        }
        var owned = new OwnedCommand(ownerId, contribution);
        names.forEach(name -> commands.put(name, owned));
        return new Registration() {
            @Override
            public String owner() {
                return ownerId;
            }

            @Override
            public String key() {
                return contribution.name();
            }

            @Override
            public void close() {
                commands.entrySet().removeIf(entry -> entry.getValue() == owned);
            }
        };
    }

    public Map<String, OwnedUiAction> uiContributions() {
        return Map.copyOf(uiActions);
    }

    public void unregisterOwner(String owner) {
        commands.entrySet().removeIf(entry -> entry.getValue().owner().equals(owner));
        uiActions.entrySet().removeIf(entry -> entry.getValue().owner().equals(owner));
        placeholders.entrySet().removeIf(entry -> entry.getValue().owner().equals(owner));
        settings.entrySet().removeIf(entry -> entry.getValue().owner().equals(owner));
        permissions.entrySet().removeIf(entry -> entry.getValue().owner().equals(owner));
        translations.entrySet().removeIf(entry -> entry.getValue().owner().equals(owner));
        policies.entrySet().removeIf(entry -> entry.getValue().owner().equals(owner));
    }

    private static <T> Registration registerOwned(
            Map<String, OwnedValue<T>> target,
            Plugin owner,
            String rawKey,
            T value
    ) {
        var ownerId = ownerId(owner);
        var key = namespaced(ownerId, rawKey);
        if (target.putIfAbsent(key, new OwnedValue<>(ownerId, value)) != null) {
            throw new IllegalArgumentException("Extension already registered: " + key);
        }
        return registration(ownerId, key, target);
    }

    private static <T> Registration registration(String owner, String key, Map<String, T> entries) {
        return new Registration() {
            @Override
            public String owner() {
                return owner;
            }

            @Override
            public String key() {
                return key;
            }

            @Override
            public void close() {
                entries.remove(key);
            }
        };
    }

    private static String ownerId(Plugin owner) {
        Objects.requireNonNull(owner, "owner");
        var normalized = owner.getName().toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9._-]", "-");
        if (normalized.isBlank()) {
            throw new IllegalArgumentException("Plugin name cannot produce an empty owner");
        }
        return normalized;
    }

    public void unregisterOwner(Plugin owner) {
        unregisterOwner(ownerId(owner));
    }

    private static String namespaced(String owner, String key) {
        Objects.requireNonNull(key, "key");
        if (key.contains(":") && !key.startsWith(owner + ":")) {
            throw new IllegalArgumentException("Extension cannot register another owner's namespace");
        }
        return key.contains(":") ? key : owner + ":" + key;
    }

    public record OwnedCommand(String owner, CommandRegistry.CommandContribution contribution) {
    }

    public record OwnedUiAction(String owner, TeamUiRegistry.UiAction action) {
    }

    public record OwnedValue<T>(String owner, T value) {
    }

    public record SettingValidation(boolean valid, String permission) {
    }
}
