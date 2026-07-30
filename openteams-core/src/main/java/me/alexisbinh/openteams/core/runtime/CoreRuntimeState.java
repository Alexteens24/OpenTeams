package me.alexisbinh.openteams.core.runtime;

public enum CoreRuntimeState {
    STARTING,
    WRITABLE,
    DEGRADED_READ_ONLY,
    RECOVERING,
    STOPPING
}
