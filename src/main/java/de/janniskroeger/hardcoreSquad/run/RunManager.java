package de.janniskroeger.hardcoreSquad.run;

import de.janniskroeger.hardcoreSquad.HardcoreSquad;
import de.janniskroeger.hardcoreSquad.persistence.RunRepository;
import de.janniskroeger.hardcoreSquad.persistence.RunSnapshot;
import de.janniskroeger.hardcoreSquad.scoreboard.SidebarManager;
import de.janniskroeger.hardcoreSquad.util.TimeUtil;
import de.janniskroeger.hardcoreSquad.world.WorldResetService;
import lombok.RequiredArgsConstructor;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.Setter;
import org.bukkit.Bukkit;
import org.bukkit.Difficulty;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;

@RequiredArgsConstructor
public class RunManager {

  private static final String PREFIX = "§c[Hardcore Team] §7";

  private final HardcoreSquad plugin;
  private final RunRepository repository;
  private final WorldResetService worldResetService;
  private final Random random = new Random();
  private final EnumMap<Milestone, Long> milestoneDurations = new EnumMap<>(Milestone.class);
  private final Set<UUID> pendingRespawns = new HashSet<>();
  private final Map<UUID, BukkitTask> respawnTasks = new HashMap<>();
  private final Map<UUID, Location> pendingDeathLocations = new HashMap<>();
  private final Map<UUID, Location> pendingRespawnLocations = new HashMap<>();

  @Setter
  private SidebarManager sidebarManager;
  private RunState runState = RunState.PRESTART;
  private int teamLives;
  private long runStartTimestamp;
  private long runEndTimestamp;
  private long lastRunDurationMillis;
  private int attemptCount;
  private final Map<UUID, Integer> playerDeathCounts = new HashMap<>();


  public void loadState() {
    RunSnapshot snapshot = repository.load();
    runState = snapshot.getRunState();
    teamLives = snapshot.getTeamLives();
    runStartTimestamp = snapshot.getRunStartTimestamp();
    runEndTimestamp = snapshot.getRunEndTimestamp();
    lastRunDurationMillis = snapshot.getLastRunDurationMillis();
    attemptCount = snapshot.getAttemptCount();
    playerDeathCounts.clear();
    playerDeathCounts.putAll(snapshot.getPlayerDeathCounts());
    milestoneDurations.clear();
    milestoneDurations.putAll(snapshot.getMilestoneDurations());
  }

  public void shutdown() {
    cancelAllRespawnTasks();
    saveState();
  }

  public long getRunDurationMillis() {
    if (runStartTimestamp <= 0L) {
      return 0L;
    }

    if (runState == RunState.RUNNING) {
      return Math.max(0L, System.currentTimeMillis() - runStartTimestamp);
    }

    if (runEndTimestamp > 0L) {
      return Math.max(0L, runEndTimestamp - runStartTimestamp);
    }

    return 0L;
  }

  public boolean isMovementLocked() {
    return runState == RunState.PRESTART;
  }

  public void handleJoin(Player player) {
    if (sidebarManager != null) {
      sidebarManager.applyToPlayer(player);
    }

    if (runState == RunState.GAMEOVER || runState == RunState.SUCCESS || pendingRespawns.contains(player.getUniqueId())) {
      player.setGameMode(GameMode.SPECTATOR);
      if (runState == RunState.GAMEOVER) {
        player.sendMessage(PREFIX + "Der Run ist vorbei. Nutze §e/hc status §7oder warte auf §e/hc reset§7.");
      } else if (runState == RunState.SUCCESS) {
        player.sendMessage(PREFIX + "Der Run wurde geschafft. Nutze §e/hc status §7oder warte auf §e/hc reset§7.");
      } else {
        player.sendMessage(PREFIX + "Du wartest noch auf deinen Wiedereinstieg.");
      }
    } else if (runState == RunState.RUNNING) {
      if (player.getGameMode() == GameMode.SPECTATOR) {
        player.setGameMode(GameMode.SURVIVAL);
      }
      player.sendMessage(PREFIX + "Der Run läuft bereits. Du spielst direkt mit.");
      checkInventoryMilestones(player);
      checkWorldMilestones(player);
    } else {
      if (player.getGameMode() == GameMode.SPECTATOR) {
        player.setGameMode(GameMode.SURVIVAL);
      }
      player.sendMessage(PREFIX + "Der Run ist noch nicht gestartet. Bewegung ist bis zum Start gesperrt.");
    }

    refreshScoreboard();
  }

  public void startRun(int lives) {
    cancelAllRespawnTasks();
    pendingRespawns.clear();
    milestoneDurations.clear();
    teamLives = lives;
    runStartTimestamp = System.currentTimeMillis();
    runEndTimestamp = 0L;
    attemptCount++;
    runState = RunState.RUNNING;

    for (World world : Bukkit.getWorlds()) {
      world.setDifficulty(Difficulty.HARD);
    }

    for (Player player : Bukkit.getOnlinePlayers()) {
      player.setGameMode(GameMode.SURVIVAL);
    }

    Bukkit.broadcastMessage(PREFIX + "Der Hardcore-Team-Run wurde gestartet. Team-Leben: §e" + teamLives + "§7.");
    saveState();
    refreshScoreboard();

    for (Player player : Bukkit.getOnlinePlayers()) {
      checkInventoryMilestones(player);
      checkWorldMilestones(player);
    }
  }

  public void handlePlayerDeath(PlayerDeathEvent event) {
    if (runState != RunState.RUNNING) {
      return;
    }

    Player player = event.getEntity();
    UUID playerId = player.getUniqueId();
    Location deathLocation = player.getLocation().clone();

    // Close the death screen automatically so players enter spectator flow immediately.
    Bukkit.getScheduler().runTaskLater(plugin, () -> {
      if (player.isOnline() && player.isDead()) {
        player.spigot().respawn();
      }
    }, 2L);

    pendingRespawns.add(playerId);
    cancelRespawnTask(playerId);
    pendingDeathLocations.put(playerId, deathLocation);

    event.setKeepInventory(true);
    event.setKeepLevel(true);
    event.setDeathMessage(null);
    event.getDrops().clear();
    event.setDroppedExp(0);
    event.setNewExp(0);
    event.setNewLevel(0);
    event.setNewTotalExp(0);

    teamLives = Math.max(0, teamLives - 1);
    playerDeathCounts.merge(playerId, 1, Integer::sum);

    String deathReason = resolveDeathReason(player);
    Bukkit.broadcastMessage(PREFIX + "§c" + player.getName() + "§7 ist gestorben (§e" + deathReason
        + "§7). Verbleibende Team-Leben: §e" + teamLives);
    saveState();
    refreshScoreboard();

    if (teamLives <= 0) {
      enterGameOver("Die Team-Leben sind aufgebraucht.");
      return;
    }

    if (!hasAlivePlayer() && !isSoloRespawnScenario(playerId)) {
      enterGameOver("Es ist kein lebender Spieler mehr übrig.");
      return;
    }

    scheduleRespawnRelease(playerId);
  }

  public void handlePlayerRespawn(PlayerRespawnEvent event) {
    Player player = event.getPlayer();
    UUID playerId = player.getUniqueId();

    if (runState == RunState.RUNNING && pendingRespawns.contains(playerId)) {
      pendingRespawnLocations.put(playerId, event.getRespawnLocation().clone());
      Location deathLocation = pendingDeathLocations.get(playerId);
      if (deathLocation != null) {
        event.setRespawnLocation(deathLocation.clone());
      }
    }

    Bukkit.getScheduler().runTask(plugin, () -> {
      clearInventoryAndExperience(player);

      if (runState == RunState.GAMEOVER || pendingRespawns.contains(playerId)) {
        player.setGameMode(GameMode.SPECTATOR);
      }

      if (runState == RunState.RUNNING && pendingRespawns.contains(playerId)) {
        long delaySeconds = plugin.getConfig().getLong("respawn-delay-seconds", 30L);
        player.sendMessage(PREFIX + "Du bist für §e" + delaySeconds + "§7 Sekunden im Spectator.");
      }

      refreshScoreboard();
    });
  }

  public void checkInventoryMilestones(Player player) {
    if (runState != RunState.RUNNING) {
      return;
    }

    if (containsMaterial(player, Material.COBBLESTONE)) {
      markMilestone(Milestone.STONE_AGE);
    }
    if (containsMaterial(player, Material.IRON_INGOT)) {
      markMilestone(Milestone.IRON_ACQUIRED);
    }
    if (containsMaterial(player, Material.DIAMOND)) {
      markMilestone(Milestone.DIAMOND_ACQUIRED);
    }
    if (containsMaterial(player, Material.BLAZE_ROD)) {
      markMilestone(Milestone.BLAZE_ROD);
    }
  }

  public void handleDirectMaterialGain(Material material) {
    if (runState != RunState.RUNNING) {
      return;
    }

    if (material == Material.COBBLESTONE) {
      markMilestone(Milestone.STONE_AGE);
    } else if (material == Material.IRON_INGOT) {
      markMilestone(Milestone.IRON_ACQUIRED);
    } else if (material == Material.DIAMOND) {
      markMilestone(Milestone.DIAMOND_ACQUIRED);
    } else if (material == Material.BLAZE_ROD) {
      markMilestone(Milestone.BLAZE_ROD);
    }
  }

  public void handleStonePickaxeCraft() {
    if (runState == RunState.RUNNING) {
      markMilestone(Milestone.STONE_AGE);
    }
  }

  public void checkWorldMilestones(Player player) {
    if (runState != RunState.RUNNING) {
      return;
    }

    String netherWorld = plugin.getConfig().getString("worlds.nether", "world_nether");
    String endWorld = plugin.getConfig().getString("worlds.end", "world_the_end");
    String currentWorld = player.getWorld().getName();

    if (Objects.equals(currentWorld, netherWorld)) {
      markMilestone(Milestone.NETHER_ENTER);
    }
    if (Objects.equals(currentWorld, endWorld)) {
      markMilestone(Milestone.END_ENTER);
    }
  }

  public void handleDragonKilled(World world) {
    if (runState != RunState.RUNNING) {
      return;
    }

    String endWorld = plugin.getConfig().getString("worlds.end", "world_the_end");
    if (Objects.equals(world.getName(), endWorld)) {
      markMilestone(Milestone.DRAGON_KILLED);
      enterSuccess("Der Enderdrache wurde besiegt.");
    }
  }

  public void checkEndReturnVictory() {
    if (runState != RunState.RUNNING || !milestoneDurations.containsKey(Milestone.END_ENTER)) {
      return;
    }

    String endWorld = plugin.getConfig().getString("worlds.end", "world_the_end");
    String overworld = plugin.getConfig().getString("worlds.overworld", "world");

    List<Player> activePlayers = Bukkit.getOnlinePlayers().stream()
        .filter(this::isAlivePlayer)
        .collect(Collectors.toList());

    if (activePlayers.isEmpty()) {
      return;
    }

    boolean someoneInEnd = activePlayers.stream().anyMatch(player -> Objects.equals(player.getWorld().getName(), endWorld));
    if (someoneInEnd) {
      return;
    }

    boolean allInOverworld = activePlayers.stream().allMatch(player -> Objects.equals(player.getWorld().getName(), overworld));
    if (allInOverworld) {
      enterSuccess("Das Team hat das Ende erfolgreich verlassen.");
    }
  }

  public List<String> getSidebarLines() {
    List<String> lines = new ArrayList<>();
    lines.add("§8+------------------+");
    lines.add("§7Status: " + getDecoratedStateDisplay());
    lines.add("§7Leben: " + (teamLives > 0 ? "§c" + teamLives : "§4" + teamLives));
    lines.add("§7Zeit: §e" + TimeUtil.formatDuration(getRunDurationMillis()));
    lines.add("§7Versuche: §b#" + attemptCount);
    lines.add("§7Letzter Run: §d" + formatArchivedRunDuration(lastRunDurationMillis));
    lines.add("§7Suendenbock: §c" + getWorstPlayerDisplay());
    lines.add("§8Milestones:");

    for (Milestone milestone : Milestone.values()) {
      lines.add(formatMilestoneSidebarLine(milestone));
    }

    return lines;
  }

  public List<String> getStatusLines() {
    List<String> lines = new ArrayList<>();
    lines.add("§c§lHardcore Team Status");
    lines.add("§7Zustand: §f" + getStateDisplayName());
    lines.add("§7Team-Leben: §f" + teamLives);
    lines.add("§7Run-Zeit: §f" + TimeUtil.formatDuration(getRunDurationMillis()));
    lines.add("§7Versuche: §f" + attemptCount);
    lines.add("§7Letzter Run: §f" + formatArchivedRunDuration(lastRunDurationMillis));
    lines.add("§7Schlechtester: §f" + getWorstPlayerDisplay());
    if (runState == RunState.PRESTART) {
      lines.add("§7Warte auf §e/hc start§7");
    }
    lines.add("§7Top Tode:");
    lines.addAll(getTopDeathLines(3));
    lines.add("§7Meilensteine:");

    for (Milestone milestone : Milestone.values()) {
      String value = milestoneDurations.containsKey(milestone)
          ? TimeUtil.formatDuration(milestoneDurations.get(milestone))
          : "nicht erreicht";
      lines.add(" §8- §f" + milestone.getDisplayName() + ": §7" + value);
    }

    return lines;
  }

  public void sendStatus(CommandSender sender) {
    getStatusLines().forEach(sender::sendMessage);
  }

  public void executeReset(CommandSender sender) {
    worldResetService.initiateReset(sender, () -> prepareStateForFreshRun(false));
  }

  public void executeHardReset(CommandSender sender) {
    worldResetService.initiateReset(sender, () -> prepareStateForFreshRun(true));
  }

  public boolean canReset() {
    return (runState == RunState.GAMEOVER || runState == RunState.SUCCESS) && !worldResetService.isResetInProgress();
  }

  public String getCannotResetReason() {
    if (worldResetService.isResetInProgress()) {
      return "§7Ein Reset läuft bereits.";
    }
    if (runState != RunState.GAMEOVER && runState != RunState.SUCCESS) {
      return "§7§c/hc reset§7 ist nur nach Ende des Runs verfügbar.";
    }
    return "§7Reset nicht möglich.";
  }

  private boolean containsMaterial(Player player, Material material) {
    return player.getInventory().contains(material);
  }

  private void scheduleRespawnRelease(UUID playerId) {
    long delayTicks = Math.max(1L, plugin.getConfig().getLong("respawn-delay-seconds", 30L) * 20L);
    BukkitTask task = Bukkit.getScheduler().runTaskLater(plugin, () -> releaseRespawn(playerId), delayTicks);
    respawnTasks.put(playerId, task);
  }

  private void releaseRespawn(UUID playerId) {
    respawnTasks.remove(playerId);
    pendingRespawns.remove(playerId);

    if (runState != RunState.RUNNING) {
      refreshScoreboard();
      return;
    }

    List<Player> alivePlayers = Bukkit.getOnlinePlayers().stream()
        .filter(this::isAlivePlayer)
        .collect(Collectors.toList());

    Player player = Bukkit.getPlayer(playerId);
    if (player == null || !player.isOnline()) {
      refreshScoreboard();
      return;
    }

    if (alivePlayers.isEmpty()) {
      if (!isSoloRespawnScenario(playerId)) {
        pendingDeathLocations.remove(playerId);
        pendingRespawnLocations.remove(playerId);
        enterGameOver("Es ist kein lebender Spieler mehr übrig.");
        return;
      }

      pendingDeathLocations.remove(playerId);
      Location respawnLocation = pendingRespawnLocations.remove(playerId);
      if (respawnLocation != null) {
        player.teleport(respawnLocation);
      } else {
        World fallbackWorld = Bukkit.getWorld(plugin.getConfig().getString("worlds.overworld", "world"));
        if (fallbackWorld != null) {
          player.teleport(fallbackWorld.getSpawnLocation());
        }
      }

      player.setGameMode(GameMode.SURVIVAL);
      AttributeInstance maxHealthAttribute = player.getAttribute(Attribute.MAX_HEALTH);
      if (maxHealthAttribute != null) {
        player.setHealth(Math.min(maxHealthAttribute.getValue(), maxHealthAttribute.getDefaultValue()));
      }
      player.setFoodLevel(20);
      player.setSaturation(20f);
      player.sendMessage(PREFIX + "Du bist wieder im Run. Natürlicher Respawn im Solo-Modus.");
      refreshScoreboard();
      return;
    }

    Player target = alivePlayers.get(random.nextInt(alivePlayers.size()));
    pendingDeathLocations.remove(playerId);
    pendingRespawnLocations.remove(playerId);
    player.teleport(target.getLocation());
    player.setGameMode(GameMode.SURVIVAL);
    AttributeInstance maxHealthAttribute = player.getAttribute(Attribute.MAX_HEALTH);
    if (maxHealthAttribute != null) {
      player.setHealth(Math.min(maxHealthAttribute.getValue(), maxHealthAttribute.getDefaultValue()));
    }
    player.setFoodLevel(20);
    player.setSaturation(20f);
    player.sendMessage(PREFIX + "Du bist wieder im Run. Teleportiert zu §e" + target.getName() + "§7.");
    refreshScoreboard();
  }

  private boolean isAlivePlayer(Player player) {
    return player.isOnline()
        && !pendingRespawns.contains(player.getUniqueId())
        && (player.getGameMode() == GameMode.SURVIVAL || player.getGameMode() == GameMode.ADVENTURE);
  }

  private boolean hasAlivePlayer() {
    Collection<? extends Player> players = Bukkit.getOnlinePlayers();
    for (Player player : players) {
      if (isAlivePlayer(player)) {
        return true;
      }
    }
    return false;
  }

  private boolean isSoloRespawnScenario(UUID playerId) {
    Collection<? extends Player> onlinePlayers = Bukkit.getOnlinePlayers();
    return onlinePlayers.size() == 1 && onlinePlayers.iterator().next().getUniqueId().equals(playerId);
  }

  private void markMilestone(Milestone milestone) {
    if (milestoneDurations.containsKey(milestone)) {
      return;
    }

    long duration = getRunDurationMillis();
    milestoneDurations.put(milestone, duration);
    Bukkit.broadcastMessage(PREFIX + "Meilenstein erreicht: §e" + milestone.getDisplayName()
        + " §7(" + TimeUtil.formatDuration(duration) + ") §8- §7" + milestone.getBroadcastText());
    saveState();
    refreshScoreboard();
  }

  private void enterGameOver(String reason) {
    if (runState == RunState.GAMEOVER) {
      return;
    }

    runState = RunState.GAMEOVER;
    runEndTimestamp = System.currentTimeMillis();
    archiveFinishedRun();
    cancelAllRespawnTasks();

    for (Player player : Bukkit.getOnlinePlayers()) {
      player.setGameMode(GameMode.SPECTATOR);
    }

    Bukkit.broadcastMessage(PREFIX + "§4§lGAME OVER! §7" + reason);
    Bukkit.broadcastMessage(PREFIX + "§7Nutze §e/hc reset §7um die Welt zurückzusetzen.");
    saveState();
    refreshScoreboard();
  }

  private void enterSuccess(String reason) {
    if (runState == RunState.SUCCESS || runState == RunState.GAMEOVER) {
      return;
    }

    runState = RunState.SUCCESS;
    runEndTimestamp = System.currentTimeMillis();
    archiveFinishedRun();
    cancelAllRespawnTasks();

    for (Player player : Bukkit.getOnlinePlayers()) {
      player.setGameMode(GameMode.SPECTATOR);
    }

    Bukkit.broadcastMessage(PREFIX + "§a§lRUN GESCHAFFT! §7" + reason);
    Bukkit.broadcastMessage(PREFIX + "§7Nutze §e/hc reset §7um eine neue Welt zu starten.");
    saveState();
    refreshScoreboard();
  }

  private void cancelRespawnTask(UUID playerId) {
    BukkitTask existingTask = respawnTasks.remove(playerId);
    if (existingTask != null) {
      existingTask.cancel();
    }
  }

  private void cancelAllRespawnTasks() {
    for (BukkitTask task : respawnTasks.values()) {
      task.cancel();
    }
    respawnTasks.clear();
    pendingRespawns.clear();
    pendingDeathLocations.clear();
    pendingRespawnLocations.clear();
  }

  private void clearInventoryAndExperience(Player player) {
    player.getInventory().clear();
    player.getInventory().setArmorContents(new ItemStack[4]);
    player.setTotalExperience(0);
    player.setExp(0f);
    player.setLevel(0);
  }

  private String resolveDeathReason(Player player) {
    EntityDamageEvent lastDamage = player.getLastDamageCause();
    if (lastDamage == null) {
      return "unbekannte Ursache";
    }

    if (lastDamage instanceof EntityDamageByEntityEvent byEntityEvent) {
      Entity damager = byEntityEvent.getDamager();
      if (damager instanceof Projectile projectile && projectile.getShooter() instanceof Entity shooter) {
        damager = shooter;
      }

      String damagerName = getEntityName(damager);
      if (damagerName != null && !damagerName.isEmpty()) {
        return "von " + damagerName;
      }
    }

    return lastDamage.getCause().name().toLowerCase().replace('_', ' ');
  }

  private String getEntityName(Entity entity) {
    switch (entity) {
      case null -> {
        return null;
      }
      case Player killerPlayer -> {
        return killerPlayer.getName();
      }
      case LivingEntity livingEntity -> {
        if (livingEntity.getCustomName() != null && !livingEntity.getCustomName().isBlank()) {
          return livingEntity.getCustomName();
        }
      }
      default -> {
      }
    }

    String raw = entity.getType().name().toLowerCase().replace('_', ' ');
    if (raw.isEmpty()) {
      return "einer Kreatur";
    }
    return raw;
  }

  private String getStateDisplayName() {
    return switch (runState) {
      case PRESTART -> "Vorstart";
      case RUNNING -> "Läuft";
      case GAMEOVER -> "Game Over";
      case SUCCESS -> "Geschafft";
    };
  }

  private String formatArchivedRunDuration(long durationMillis) {
    if (durationMillis <= 0L) {
      return "--:--";
    }
    return TimeUtil.formatDuration(durationMillis);
  }

  private String getDecoratedStateDisplay() {
    return switch (runState) {
      case PRESTART -> "§eVorstart §7(§e/hc start§7)";
      case RUNNING -> "§aLaeuft";
      case GAMEOVER -> "§4§lGame Over";
      case SUCCESS -> "§b§lGeschafft";
    };
  }

  private String formatMilestoneSidebarLine(Milestone milestone) {
    Long duration = milestoneDurations.get(milestone);
    if (duration != null) {
      return "§a[OK] §f" + milestone.getDisplayName() + " §8@ §e" + TimeUtil.formatDuration(duration);
    }

    return "§8[--] §7" + milestone.getDisplayName() + " §8@ §7--:--";
  }

  private void archiveFinishedRun() {
    long finishedDuration = getRunDurationMillis();
    if (finishedDuration <= 0L) {
      return;
    }

    lastRunDurationMillis = finishedDuration;
  }

  private String getWorstPlayerDisplay() {
    if (playerDeathCounts.isEmpty()) {
      return "--";
    }

    UUID worstId = null;
    int maxDeaths = -1;
    for (Map.Entry<UUID, Integer> entry : playerDeathCounts.entrySet()) {
      if (entry.getValue() > maxDeaths) {
        maxDeaths = entry.getValue();
        worstId = entry.getKey();
      }
    }

    if (worstId == null) {
      return "--";
    }

    return resolvePlayerName(worstId) + " (" + maxDeaths + ")";
  }

  private List<String> getTopDeathLines(int maxEntries) {
    List<Map.Entry<UUID, Integer>> sorted = new ArrayList<>(playerDeathCounts.entrySet());
    sorted.sort((left, right) -> Integer.compare(right.getValue(), left.getValue()));

    List<String> lines = new ArrayList<>();
    if (sorted.isEmpty()) {
      lines.add(" §8- §7Keine Tode erfasst");
      return lines;
    }

    int limit = Math.min(maxEntries, sorted.size());
    for (int index = 0; index < limit; index++) {
      Map.Entry<UUID, Integer> entry = sorted.get(index);
      lines.add(" §8- §f" + resolvePlayerName(entry.getKey()) + ": §c" + entry.getValue());
    }

    return lines;
  }

  private String resolvePlayerName(UUID playerId) {
    String name = Bukkit.getOfflinePlayer(playerId).getName();
    if (name != null && !name.isBlank()) {
      return name;
    }
    return playerId.toString().substring(0, 8);
  }

  private void refreshScoreboard() {
    if (sidebarManager != null) {
      sidebarManager.updateAll();
    }
  }

  private void saveState() {
    repository.save(new RunSnapshot(
        runState,
        teamLives,
        runStartTimestamp,
        runEndTimestamp,
        lastRunDurationMillis,
        attemptCount,
        playerDeathCounts,
        milestoneDurations));
  }

  private void prepareStateForFreshRun(boolean clearHistory) {
    cancelAllRespawnTasks();
    runState = RunState.PRESTART;
    teamLives = 0;
    runStartTimestamp = 0L;
    runEndTimestamp = 0L;
    milestoneDurations.clear();

    if (clearHistory) {
      lastRunDurationMillis = 0L;
      attemptCount = 0;
      playerDeathCounts.clear();
    }

    saveState();
    refreshScoreboard();
  }
}

