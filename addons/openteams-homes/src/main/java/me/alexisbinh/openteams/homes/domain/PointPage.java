package me.alexisbinh.openteams.homes.domain;

import java.util.List;

public record PointPage(List<TeleportPoint> entries, int page, int pageSize, long total) {
    public PointPage {
        entries = List.copyOf(entries);
    }

    public int pages() {
        return total == 0 ? 1 : (int) Math.ceil((double) total / pageSize);
    }
}
