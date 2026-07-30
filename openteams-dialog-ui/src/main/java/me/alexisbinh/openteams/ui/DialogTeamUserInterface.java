package me.alexisbinh.openteams.ui;

import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletionStage;
import java.util.function.Supplier;
import me.alexisbinh.openteams.api.TeamService;
import me.alexisbinh.openteams.api.extension.TeamUiRegistry;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;

/**
 * Paper Dialog API adapter. No Dialog API type is exposed by OpenTeams API.
 */
public final class DialogTeamUserInterface implements TeamUserInterface {
    private final TeamService teams;
    private final TeamUserInterface fallback;
    private final LocalizedMessages messages;
    private final Supplier<List<TeamUiRegistry.UiAction>> addonActions;

    public DialogTeamUserInterface(
            TeamService teams,
            TeamUserInterface fallback,
            LocalizedMessages messages,
            Supplier<List<TeamUiRegistry.UiAction>> addonActions
    ) {
        this.teams = teams;
        this.fallback = fallback;
        this.messages = messages;
        this.addonActions = addonActions;
    }

    @Override
    public void openDashboard(Player viewer) {
        try {
            viewer.showDialog(createDashboard(viewer));
        } catch (LinkageError | RuntimeException exception) {
            fallback.openDashboard(viewer);
        }
    }

    private Dialog createDashboard(Player viewer) {
        var team = teams.findByPlayerCached(viewer.getUniqueId());
        if (team.isEmpty()) {
            var body = DialogBody.plainMessage(messages.component(viewer, "dashboard.no-team"), 320);
            var create = button(
                    messages.component(viewer, "dashboard.create"),
                    "/team create ",
                    messages.component(viewer, "dashboard.create-tooltip"));
            return Dialog.create(factory -> factory.empty()
                    .base(DialogBase.builder(messages.component(viewer, "dashboard.title"))
                            .body(List.of(body))
                            .canCloseWithEscape(true)
                            .build())
                    .type(DialogType.notice(create)));
        }

        var snapshot = team.get();
        var summary = Component.text(snapshot.name(), NamedTextColor.AQUA)
                .append(Component.newline())
                .append(Component.text(
                        "Tag: " + (snapshot.tag() == null ? "—" : snapshot.tag()),
                        NamedTextColor.GRAY))
                .append(Component.newline())
                .append(Component.text(
                        "Members: " + snapshot.members().size() + "/" + snapshot.memberLimit(),
                        NamedTextColor.GRAY));
        var actions = new ArrayList<ActionButton>();
        actions.add(button(messages.component(viewer, "dashboard.info"), "/team info",
                messages.component(viewer, "dashboard.info-tooltip")));
        actions.add(button(messages.component(viewer, "dashboard.invite"), "/team invite ",
                messages.component(viewer, "dashboard.invite-tooltip")));
        actions.add(button(messages.component(viewer, "dashboard.chat"), "/team chat ",
                messages.component(viewer, "dashboard.chat-tooltip")));
        actions.add(button(messages.component(viewer, "dashboard.leave"), "/team leave",
                messages.component(viewer, "dashboard.leave-tooltip")));
        addonActions.get().stream()
                .filter(action -> action.visibility().visible(viewer.getUniqueId(), snapshot))
                .sorted(java.util.Comparator.comparingInt(TeamUiRegistry.UiAction::priority).reversed())
                .forEach(action -> actions.add(ActionButton.builder(Component.text(action.key()))
                        .tooltip(Component.text(action.labelTranslationKey()))
                        .width(150)
                        .action(DialogAction.customClick((response, audience) -> {
                            if (audience instanceof Player player) {
                                action.handler().execute(player.getUniqueId(), snapshot);
                            }
                        }, net.kyori.adventure.text.event.ClickCallback.Options.builder()
                                .uses(1)
                                .build()))
                        .build()));

        return Dialog.create(factory -> factory.empty()
                .base(DialogBase.builder(messages.component(viewer, "dashboard.title"))
                        .body(List.of(DialogBody.plainMessage(summary, 360)))
                        .canCloseWithEscape(true)
                        .build())
                .type(DialogType.multiAction(actions).columns(2).build()));
    }

    private static ActionButton button(Component label, String command, Component tooltip) {
        return ActionButton.builder(label)
                .tooltip(tooltip)
                .width(150)
                .action(DialogAction.staticAction(ClickEvent.suggestCommand(command)))
                .build();
    }

    @Override
    public CompletionStage<Boolean> confirm(Player viewer, UUID token) {
        return fallback.confirm(viewer, token);
    }

    @Override
    public String mode() {
        return "dialog";
    }
}
