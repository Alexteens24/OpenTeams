package me.alexisbinh.openteams.core.listener;

import java.util.UUID;
import me.alexisbinh.openteams.api.TeamRelation;
import me.alexisbinh.openteams.api.MembershipLookup;
import me.alexisbinh.openteams.api.TeamService;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Tameable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;

public final class FriendlyFireListener implements Listener {
    private final TeamService teams;
    private final boolean defaultAllowed;

    public FriendlyFireListener(TeamService teams, boolean defaultAllowed) {
        this.teams = teams;
        this.defaultAllowed = defaultAllowed;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) {
            return;
        }
        var attacker = responsiblePlayer(event.getDamager());
        if (attacker == null) {
            attacker = responsiblePlayer(event.getDamageSource().getCausingEntity());
        }
        if (attacker == null) {
            return;
        }
        var attackerState = teams.membershipCached(attacker);
        var victimState = teams.membershipCached(victim.getUniqueId());
        if (notReady(attackerState) || notReady(victimState)) {
            event.setCancelled(true);
            return;
        }
        if (teams.relationCached(attacker, victim.getUniqueId()) == TeamRelation.SAME) {
            var team = attackerState.optionalTeam().orElseThrow();
            var allowed = Boolean.parseBoolean(team.settingOrDefault(
                    "openteams:friendly-fire", Boolean.toString(defaultAllowed)));
            if (!allowed) {
                event.setCancelled(true);
            }
        }
    }

    private static boolean notReady(MembershipLookup lookup) {
        return lookup.status() == MembershipLookup.Status.LOADING
                || lookup.status() == MembershipLookup.Status.FAILED;
    }

    private static UUID responsiblePlayer(Entity entity) {
        if (entity instanceof Player player) {
            return player.getUniqueId();
        }
        if (entity instanceof Projectile projectile && projectile.getShooter() instanceof Player player) {
            return player.getUniqueId();
        }
        if (entity instanceof Tameable tameable && tameable.getOwnerUniqueId() != null) {
            return tameable.getOwnerUniqueId();
        }
        return null;
    }
}
