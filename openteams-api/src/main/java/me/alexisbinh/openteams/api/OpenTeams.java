package me.alexisbinh.openteams.api;

import me.alexisbinh.openteams.api.extension.CommandRegistry;
import me.alexisbinh.openteams.api.extension.PlaceholderRegistry;
import me.alexisbinh.openteams.api.extension.MutationPolicyRegistry;
import me.alexisbinh.openteams.api.extension.TeamPermissionRegistry;
import me.alexisbinh.openteams.api.extension.TeamSettingRegistry;
import me.alexisbinh.openteams.api.extension.TeamUiRegistry;
import me.alexisbinh.openteams.api.extension.TranslationRegistry;

public interface OpenTeams {
    String apiVersion();

    TeamService teams();

    PlayerDirectory players();

    CommandRegistry commands();

    PlaceholderRegistry placeholders();

    TeamSettingRegistry settings();

    TeamPermissionRegistry permissions();

    TeamUiRegistry userInterface();

    TranslationRegistry translations();

    MutationPolicyRegistry policies();

    boolean readOnly();
}
