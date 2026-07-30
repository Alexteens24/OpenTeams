package me.alexisbinh.openteams.ui;

import java.util.UUID;
import java.util.concurrent.CompletionStage;
import org.bukkit.entity.Player;

public interface TeamUserInterface {
    void openDashboard(Player viewer);

    CompletionStage<Boolean> confirm(Player viewer, UUID token);

    String mode();
}
