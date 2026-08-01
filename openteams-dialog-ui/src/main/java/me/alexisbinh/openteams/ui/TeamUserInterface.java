package me.alexisbinh.openteams.ui;

import org.bukkit.entity.Player;

public interface TeamUserInterface {
    void openDashboard(Player viewer);

    String mode();
}
