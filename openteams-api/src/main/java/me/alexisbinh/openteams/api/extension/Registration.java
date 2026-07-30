package me.alexisbinh.openteams.api.extension;

public interface Registration extends AutoCloseable {
    String owner();

    String key();

    @Override
    void close();
}
