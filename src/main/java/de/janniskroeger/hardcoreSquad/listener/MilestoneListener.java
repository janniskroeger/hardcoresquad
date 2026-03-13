package de.janniskroeger.hardcoreSquad.listener;

import de.janniskroeger.hardcoreSquad.run.RunManager;
import lombok.RequiredArgsConstructor;
import org.bukkit.Material;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.ItemStack;

@RequiredArgsConstructor
public class MilestoneListener implements Listener {

  private final RunManager runManager;


  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void onPickup(EntityPickupItemEvent event) {
    if (!(event.getEntity() instanceof Player player)) {
      return;
    }

    ItemStack itemStack = event.getItem().getItemStack();
    runManager.handleDirectMaterialGain(itemStack.getType());
    player.getServer().getScheduler().runTaskLater(
        org.bukkit.plugin.java.JavaPlugin.getProvidingPlugin(getClass()),
        () -> runManager.checkInventoryMilestones(player),
        1L);
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void onInventoryClick(InventoryClickEvent event) {
    if (!(event.getWhoClicked() instanceof Player player)) {
      return;
    }

    player.getServer().getScheduler().runTaskLater(
        org.bukkit.plugin.java.JavaPlugin.getProvidingPlugin(getClass()),
        () -> runManager.checkInventoryMilestones(player),
        1L);
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void onInventoryDrag(InventoryDragEvent event) {
    if (!(event.getWhoClicked() instanceof Player player)) {
      return;
    }

    player.getServer().getScheduler().runTaskLater(
        org.bukkit.plugin.java.JavaPlugin.getProvidingPlugin(getClass()),
        () -> runManager.checkInventoryMilestones(player),
        1L);
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void onCraft(CraftItemEvent event) {
    ItemStack currentItem = event.getCurrentItem();
    if (currentItem == null) {
      return;
    }

    if (currentItem.getType() == Material.STONE_PICKAXE) {
      runManager.handleStonePickaxeCraft();
    }

    if (event.getWhoClicked() instanceof Player player) {
      player.getServer().getScheduler().runTaskLater(
          org.bukkit.plugin.java.JavaPlugin.getProvidingPlugin(getClass()),
          () -> runManager.checkInventoryMilestones(player),
          1L);
    }
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void onDragonDeath(EntityDeathEvent event) {
    if (event.getEntityType() == EntityType.ENDER_DRAGON) {
      runManager.handleDragonKilled(event.getEntity().getWorld());
    }
  }
}

