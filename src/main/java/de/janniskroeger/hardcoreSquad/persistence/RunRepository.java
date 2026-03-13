package de.janniskroeger.hardcoreSquad.persistence;

import de.janniskroeger.hardcoreSquad.HardcoreSquad;
import de.janniskroeger.hardcoreSquad.run.Milestone;
import de.janniskroeger.hardcoreSquad.run.RunState;
import lombok.RequiredArgsConstructor;
import java.io.File;
import java.io.IOException;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

@RequiredArgsConstructor
public class RunRepository {

  private final HardcoreSquad plugin;

  public RunSnapshot load() {
    File stateFile = getStateFile();
    if (!stateFile.exists()) {
      return createDefaultSnapshot();
    }

    YamlConfiguration config = YamlConfiguration.loadConfiguration(stateFile);

    RunState runState = parseRunState(config.getString("runState", RunState.PRESTART.name()));
    int teamLives = Math.max(0, config.getInt("teamLives", 0));
    long runStartTimestamp = Math.max(0L, config.getLong("runStartTimestamp", 0L));
    long runEndTimestamp = Math.max(0L, config.getLong("runEndTimestamp", 0L));
    long lastRunDurationMillis = Math.max(0L, config.getLong("lastRunDurationMillis", 0L));
    int attemptCount = Math.max(0, config.getInt("attemptCount", 0));

    Map<UUID, Integer> playerDeathCounts = new HashMap<>();
    ConfigurationSection playerDeathsSection = config.getConfigurationSection("playerDeaths");
    if (playerDeathsSection != null) {
      for (String key : playerDeathsSection.getKeys(false)) {
        try {
          UUID uuid = UUID.fromString(key);
          int deaths = Math.max(0, playerDeathsSection.getInt(key, 0));
          if (deaths > 0) {
            playerDeathCounts.put(uuid, deaths);
          }
        } catch (IllegalArgumentException ignored) {
          // Ignore invalid UUID entries to keep loading robust.
        }
      }
    }

    EnumMap<Milestone, Long> milestoneDurations = new EnumMap<>(Milestone.class);
    ConfigurationSection milestoneSection = config.getConfigurationSection("milestones");
    if (milestoneSection != null) {
      for (Milestone milestone : Milestone.values()) {
        if (milestoneSection.contains(milestone.name())) {
          milestoneDurations.put(milestone, Math.max(0L, milestoneSection.getLong(milestone.name())));
        }
      }
    }

    return new RunSnapshot(
        runState,
        teamLives,
        runStartTimestamp,
        runEndTimestamp,
        lastRunDurationMillis,
        attemptCount,
        playerDeathCounts,
        milestoneDurations);
  }

  public void save(RunSnapshot snapshot) {
    File stateFile = getStateFile();
    if (!plugin.getDataFolder().exists() && !plugin.getDataFolder().mkdirs()) {
      plugin.getLogger().warning("Konnte Plugin-Datenordner nicht erstellen.");
    }

    YamlConfiguration config = new YamlConfiguration();
    config.set("runState", snapshot.getRunState().name());
    config.set("teamLives", snapshot.getTeamLives());
    config.set("runStartTimestamp", snapshot.getRunStartTimestamp());
    config.set("runEndTimestamp", snapshot.getRunEndTimestamp());
    config.set("lastRunDurationMillis", snapshot.getLastRunDurationMillis());
    config.set("attemptCount", snapshot.getAttemptCount());

    for (Map.Entry<UUID, Integer> entry : snapshot.getPlayerDeathCounts().entrySet()) {
      config.set("playerDeaths." + entry.getKey(), Math.max(0, entry.getValue()));
    }

    for (Milestone milestone : Milestone.values()) {
      Long duration = snapshot.getMilestoneDurations().get(milestone);
      if (duration != null) {
        config.set("milestones." + milestone.name(), duration);
      }
    }

    try {
      config.save(stateFile);
    } catch (IOException exception) {
      plugin.getLogger().severe("Konnte Run-Zustand nicht speichern: " + exception.getMessage());
    }
  }

  private RunSnapshot createDefaultSnapshot() {
    return new RunSnapshot(RunState.PRESTART, 0, 0L, 0L, 0L, 0, new HashMap<>(), new EnumMap<>(Milestone.class));
  }

  private RunState parseRunState(String input) {
    try {
      return RunState.valueOf(input.toUpperCase());
    } catch (IllegalArgumentException exception) {
      return RunState.PRESTART;
    }
  }

  private File getStateFile() {
    return new File(plugin.getDataFolder(), "run-state.yml");
  }
}

