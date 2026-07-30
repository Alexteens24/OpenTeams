package me.alexisbinh.openteams.core.runtime;

import java.util.concurrent.atomic.AtomicReference;

public final class RuntimeController {
    private final AtomicReference<CoreRuntimeState> state =
            new AtomicReference<>(CoreRuntimeState.STARTING);

    public CoreRuntimeState state() {
        return state.get();
    }

    public boolean writable() {
        return state.get() == CoreRuntimeState.WRITABLE;
    }

    public void writableAfterStartup() {
        state.compareAndSet(CoreRuntimeState.STARTING, CoreRuntimeState.WRITABLE);
    }

    public void degrade() {
        state.updateAndGet(current -> current == CoreRuntimeState.STOPPING
                ? current : CoreRuntimeState.DEGRADED_READ_ONLY);
    }

    public boolean beginRecovery() {
        return state.compareAndSet(
                CoreRuntimeState.DEGRADED_READ_ONLY, CoreRuntimeState.RECOVERING);
    }

    public void recoverySucceeded() {
        state.compareAndSet(CoreRuntimeState.RECOVERING, CoreRuntimeState.WRITABLE);
    }

    public void recoveryFailed() {
        state.compareAndSet(
                CoreRuntimeState.RECOVERING, CoreRuntimeState.DEGRADED_READ_ONLY);
    }

    public void stopping() {
        state.set(CoreRuntimeState.STOPPING);
    }
}
