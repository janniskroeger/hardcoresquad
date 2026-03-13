package de.janniskroeger.hardcoreSquad;

import de.janniskroeger.hardcoreSquad.command.HcCommand;
import de.janniskroeger.hardcoreSquad.listener.MilestoneListener;
import de.janniskroeger.hardcoreSquad.listener.PlayerStateListener;
import de.janniskroeger.hardcoreSquad.persistence.RunRepository;
import de.janniskroeger.hardcoreSquad.run.RunManager;
import de.janniskroeger.hardcoreSquad.scoreboard.SidebarManager;
import de.janniskroeger.hardcoreSquad.world.WorldResetService;
import lombok.Getter;
import org.bukkit.plugin.java.JavaPlugin;

@Getter
public final class HardcoreSquad extends JavaPlugin {

  private RunManager runManager;
  private SidebarManager sidebarManager;

  @Override
  public void onEnable() {
    saveDefaultConfig();

    RunRepository repository = new RunRepository(this);
    WorldResetService worldResetService = new WorldResetService(this);

    this.runManager = new RunManager(this, repository, worldResetService);
    this.sidebarManager = new SidebarManager(this, runManager);
    runManager.setSidebarManager(sidebarManager);
    runManager.loadState();

    getServer().getPluginManager().registerEvents(new PlayerStateListener(runManager, sidebarManager), this);
    getServer().getPluginManager().registerEvents(new MilestoneListener(runManager), this);

    HcCommand hcCommand = new HcCommand(runManager);
    if (getCommand("hc") != null) {
      getCommand("hc").setExecutor(hcCommand);
      getCommand("hc").setTabCompleter(hcCommand);
    } else {
      getLogger().severe("Befehl /hc ist nicht in plugin.yml registriert.");
    }

    sidebarManager.start();
    getServer().getOnlinePlayers().forEach(runManager::handleJoin);
  }

  @Override
  public void onDisable() {
    if (sidebarManager != null) {
      sidebarManager.stop();
    }

    if (runManager != null) {
      runManager.shutdown();
    }
  }
}
