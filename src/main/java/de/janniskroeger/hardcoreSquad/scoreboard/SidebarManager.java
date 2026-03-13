package de.janniskroeger.hardcoreSquad.scoreboard;

import de.janniskroeger.hardcoreSquad.HardcoreSquad;
import de.janniskroeger.hardcoreSquad.run.RunManager;
import lombok.RequiredArgsConstructor;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.ScoreboardManager;
import org.bukkit.scoreboard.Team;

@RequiredArgsConstructor
public class SidebarManager {

  private static final String OBJECTIVE_NAME = "hardcore_team";
  private static final String[] ENTRIES = {
      ChatColor.BLACK.toString(),
      ChatColor.DARK_BLUE.toString(),
      ChatColor.DARK_GREEN.toString(),
      ChatColor.DARK_AQUA.toString(),
      ChatColor.DARK_RED.toString(),
      ChatColor.DARK_PURPLE.toString(),
      ChatColor.GOLD.toString(),
      ChatColor.GRAY.toString(),
      ChatColor.DARK_GRAY.toString(),
      ChatColor.BLUE.toString(),
      ChatColor.GREEN.toString(),
      ChatColor.AQUA.toString(),
      ChatColor.RED.toString(),
      ChatColor.LIGHT_PURPLE.toString(),
      ChatColor.YELLOW.toString()
  };

  private final HardcoreSquad plugin;
  private final RunManager runManager;
  private final Map<UUID, Scoreboard> scoreboards = new HashMap<>();
  private int updateTaskId = -1;


  public void start() {
    updateAll();
    updateTaskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(this.plugin, this::updateAll, 20L, 20L);
  }

  public void stop() {
    if (updateTaskId != -1) {
      Bukkit.getScheduler().cancelTask(updateTaskId);
      updateTaskId = -1;
    }
  }

  public void applyToPlayer(Player player) {
    Scoreboard scoreboard = scoreboards.computeIfAbsent(player.getUniqueId(), ignored -> createScoreboard());
    if (scoreboard == null) {
      return;
    }
    player.setScoreboard(scoreboard);
    updatePlayer(player);
  }

  public void updateAll() {
    for (Player player : Bukkit.getOnlinePlayers()) {
      updatePlayer(player);
    }
  }

  public void updatePlayer(Player player) {
    Scoreboard scoreboard = scoreboards.computeIfAbsent(player.getUniqueId(), ignored -> createScoreboard());
    if (scoreboard == null) {
      return;
    }

    Objective objective = scoreboard.getObjective(OBJECTIVE_NAME);
    if (objective == null) {
      scoreboard = createScoreboard();
      if (scoreboard == null) {
        return;
      }
      scoreboards.put(player.getUniqueId(), scoreboard);
      player.setScoreboard(scoreboard);
      objective = scoreboard.getObjective(OBJECTIVE_NAME);
    }

    if (objective == null) {
      return;
    }

    objective.setDisplayName(colorize(plugin.getConfig().getString("scoreboard-title", "&cHardcore Team")));

    List<String> lines = runManager.getSidebarLines();
    for (int index = 0; index < ENTRIES.length; index++) {
      Team team = scoreboard.getTeam("line-" + index);
      if (team == null) {
        continue;
      }

      String line = index < lines.size() ? lines.get(index) : "";
      setTeamText(team, colorize(line));
      objective.getScore(ENTRIES[index]).setScore(ENTRIES.length - index);
    }
  }

  private Scoreboard createScoreboard() {
    ScoreboardManager scoreboardManager = Bukkit.getScoreboardManager();
    if (scoreboardManager == null) {
      return null;
    }

    Scoreboard scoreboard = scoreboardManager.getNewScoreboard();
    Objective objective = scoreboard.registerNewObjective(
        OBJECTIVE_NAME,
        Criteria.DUMMY,
        colorize(plugin.getConfig().getString("scoreboard-title", "&cHardcore Team")));
    objective.setDisplaySlot(DisplaySlot.SIDEBAR);

    for (int index = 0; index < ENTRIES.length; index++) {
      Team team = scoreboard.registerNewTeam("line-" + index);
      team.addEntry(ENTRIES[index]);
      objective.getScore(ENTRIES[index]).setScore(ENTRIES.length - index);
    }

    return scoreboard;
  }

  private void setTeamText(Team team, String text) {
    if (text.length() <= 64) {
      team.setPrefix(text);
      team.setSuffix("");
      return;
    }

    int splitIndex = 64;
    team.setPrefix(text.substring(0, splitIndex));
    team.setSuffix(text.substring(splitIndex, Math.min(text.length(), splitIndex + 64)));
  }

  private String colorize(String input) {
    return ChatColor.translateAlternateColorCodes('&', input);
  }
}

