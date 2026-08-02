package me.alexisbinh.openteams.core;

import me.alexisbinh.openteams.api.OpenTeams;
import me.alexisbinh.openteams.api.TeamService;
import me.alexisbinh.openteams.api.PlayerDirectory;
import me.alexisbinh.openteams.api.extension.CommandRegistry;
import me.alexisbinh.openteams.api.extension.PlaceholderRegistry;
import me.alexisbinh.openteams.api.extension.MutationPolicyRegistry;
import me.alexisbinh.openteams.api.extension.TeamPermissionRegistry;
import me.alexisbinh.openteams.api.extension.TeamSettingRegistry;
import me.alexisbinh.openteams.api.extension.TeamUiRegistry;
import me.alexisbinh.openteams.api.extension.TranslationRegistry;
import me.alexisbinh.openteams.core.extension.ExtensionRegistries;
import me.alexisbinh.openteams.core.runtime.RuntimeController;

public final class OpenTeamsImpl implements OpenTeams {
    private final TeamService teams;
    private final PlayerDirectory players;
    private final ExtensionRegistries registries;
    private final RuntimeController runtime;

    public OpenTeamsImpl(
            TeamService teams,
            PlayerDirectory players,
            ExtensionRegistries registries,
            RuntimeController runtime
    ) {
        this.teams = teams;
        this.players = players;
        this.registries = registries;
        this.runtime = runtime;
    }

    @Override
    public String apiVersion() {
        return "1.0.0";
    }

    @Override
    public TeamService teams() {
        return teams;
    }

    @Override
    public PlayerDirectory players() {
        return players;
    }

    @Override
    public CommandRegistry commands() {
        return registries.commands();
    }

    @Override
    public PlaceholderRegistry placeholders() {
        return registries.placeholders();
    }

    @Override
    public TeamSettingRegistry settings() {
        return registries.settings();
    }

    @Override
    public TeamPermissionRegistry permissions() {
        return registries.permissions();
    }

    @Override
    public TeamUiRegistry userInterface() {
        return registries.userInterface();
    }

    @Override
    public TranslationRegistry translations() {
        return registries.translations();
    }

    @Override
    public MutationPolicyRegistry policies() {
        return registries.policies();
    }

    @Override
    public boolean readOnly() {
        return !runtime.writable();
    }
}
