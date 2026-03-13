package de.janniskroeger.hardcoreSquad.persistence;

import de.janniskroeger.hardcoreSquad.run.Milestone;
import de.janniskroeger.hardcoreSquad.run.RunState;
import lombok.AccessLevel;
import lombok.Getter;
import java.util.HashMap;
import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;

@Getter
public class RunSnapshot {

  private final RunState runState;
  private final int teamLives;
  private final long runStartTimestamp;
  private final long runEndTimestamp;
  private final long lastRunDurationMillis;
  private final int attemptCount;
  @Getter(AccessLevel.NONE)
  private final Map<UUID, Integer> playerDeathCounts;
  @Getter(AccessLevel.NONE)
  private final EnumMap<Milestone, Long> milestoneDurations;

  public RunSnapshot(
      RunState runState,
      int teamLives,
      long runStartTimestamp,
      long runEndTimestamp,
      long lastRunDurationMillis,
      int attemptCount,
      Map<UUID, Integer> playerDeathCounts,
      Map<Milestone, Long> milestoneDurations) {
    this.runState = runState;
    this.teamLives = teamLives;
    this.runStartTimestamp = runStartTimestamp;
    this.runEndTimestamp = runEndTimestamp;
    this.lastRunDurationMillis = lastRunDurationMillis;
    this.attemptCount = attemptCount;
    this.playerDeathCounts = new HashMap<>(playerDeathCounts);
    this.milestoneDurations = new EnumMap<>(Milestone.class);
    this.milestoneDurations.putAll(milestoneDurations);
  }


  public Map<UUID, Integer> getPlayerDeathCounts() {
    return new HashMap<>(playerDeathCounts);
  }

  public EnumMap<Milestone, Long> getMilestoneDurations() {
    return new EnumMap<>(milestoneDurations);
  }
}

