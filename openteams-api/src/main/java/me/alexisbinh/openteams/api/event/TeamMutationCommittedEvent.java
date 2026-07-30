package me.alexisbinh.openteams.api.event;

import java.util.Objects;
import java.util.Optional;
import me.alexisbinh.openteams.api.TeamSnapshot;
import me.alexisbinh.openteams.api.mutation.MutationIntent;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * Async notification emitted after the database commit and cache publication.
 * This event is observational: cancelling it cannot roll back the mutation.
 */
public final class TeamMutationCommittedEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();

    private final MutationIntent intent;
    private final TeamSnapshot before;
    private final TeamSnapshot after;

    public TeamMutationCommittedEvent(
            MutationIntent intent,
            TeamSnapshot before,
            TeamSnapshot after
    ) {
        super(true);
        this.intent = Objects.requireNonNull(intent, "intent");
        this.before = before;
        this.after = Objects.requireNonNull(after, "after");
    }

    public MutationIntent intent() {
        return intent;
    }

    public Optional<TeamSnapshot> before() {
        return Optional.ofNullable(before);
    }

    public TeamSnapshot after() {
        return after;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
