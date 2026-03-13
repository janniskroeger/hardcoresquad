package de.janniskroeger.hardcoreSquad.listener;

import de.janniskroeger.hardcoreSquad.run.RunManager;
import de.janniskroeger.hardcoreSquad.scoreboard.SidebarManager;
import lombok.RequiredArgsConstructor;
import org.bukkit.Location;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerRespawnEvent;

@RequiredArgsConstructor
public class PlayerStateListener implements Listener {

  private final RunManager runManager;
  private final SidebarManager sidebarManager;


  @EventHandler
  public void onJoin(PlayerJoinEvent event) {
    runManager.handleJoin(event.getPlayer());
  }

  @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
  public void onMove(PlayerMoveEvent event) {
    if (!runManager.isMovementLocked() || event.getTo() == null) {
      return;
    }

    Location from = event.getFrom();
    Location to = event.getTo();
    if (from.getBlockX() == to.getBlockX()
        && from.getBlockY() == to.getBlockY()
        && from.getBlockZ() == to.getBlockZ()) {
      return;
    }

    Location lockedLocation = from.clone();
    lockedLocation.setYaw(to.getYaw());
    lockedLocation.setPitch(to.getPitch());
    event.setTo(lockedLocation);
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void onDeath(PlayerDeathEvent event) {
    runManager.handlePlayerDeath(event);
  }

  @EventHandler
  public void onRespawn(PlayerRespawnEvent event) {
    runManager.handlePlayerRespawn(event);
  }

  @EventHandler
  public void onWorldChange(PlayerChangedWorldEvent event) {
    runManager.checkWorldMilestones(event.getPlayer());
    runManager.checkEndReturnVictory();
    sidebarManager.updatePlayer(event.getPlayer());
  }
}

