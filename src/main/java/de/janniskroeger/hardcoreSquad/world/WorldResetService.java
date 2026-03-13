package de.janniskroeger.hardcoreSquad.world;

import de.janniskroeger.hardcoreSquad.HardcoreSquad;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

@RequiredArgsConstructor
public class WorldResetService {

  private static final String PREFIX = "§c[Hardcore Team] §7";

  private final HardcoreSquad plugin;
  @Getter
  private boolean resetInProgress;


  public void initiateReset(CommandSender sender) {
    if (resetInProgress) {
      sender.sendMessage(PREFIX + "Ein Welt-Reset läuft bereits.");
      return;
    }

    List<String> worldsToReset = resolveWorldNames();
    if (worldsToReset.isEmpty()) {
      sender.sendMessage(PREFIX + "§cKeine gültigen Weltnamen für den Reset gefunden.");
      return;
    }

    resetInProgress = true;
    sender.sendMessage(PREFIX + "Welt-Reset wird vorbereitet. Der Server fährt danach herunter.");
    Bukkit.broadcastMessage(PREFIX + "Welt-Reset wird vorbereitet. Der Server startet gleich neu.");

    List<Player> players = new ArrayList<>(Bukkit.getOnlinePlayers());
    for (Player player : players) {
      player.setGameMode(GameMode.SPECTATOR);
      player.kickPlayer("§cDie Welt wird zurückgesetzt. Bitte verbinde dich nach dem Neustart erneut.");
    }

    Bukkit.getScheduler().runTaskLater(plugin, () -> unloadDeleteAndShutdown(worldsToReset), 20L);
  }

  private List<String> resolveWorldNames() {
    Set<String> names = new LinkedHashSet<>();
    addWorldName(names, plugin.getConfig().getString("worlds.overworld"), "world");
    addWorldName(names, plugin.getConfig().getString("worlds.nether"), "world_nether");
    addWorldName(names, plugin.getConfig().getString("worlds.end"), "world_the_end");
    return new ArrayList<>(names);
  }

  private void addWorldName(Set<String> names, String configuredValue, String fallback) {
    String worldName = configuredValue == null ? fallback : configuredValue.trim();
    if (worldName.isEmpty()) {
      worldName = fallback;
    }
    names.add(worldName);
  }

  private void unloadDeleteAndShutdown(List<String> worldsToReset) {
    String primaryWorldName = Bukkit.getWorlds().isEmpty() ? "world" : Bukkit.getWorlds().getFirst().getName();
    boolean unloadFailed = false;

    for (String worldName : worldsToReset) {
      World world = Bukkit.getWorld(worldName);
      if (world == null) {
        continue;
      }

      world.save();
      boolean unloaded = Bukkit.unloadWorld(world, false);
      if (!unloaded || Bukkit.getWorld(worldName) != null) {
        if (worldName.equals(primaryWorldName)) {
          // Primary level world is often not unloadable during runtime; continue with shutdown-based cleanup.
          plugin.getLogger().warning("Primäre Welt konnte nicht entladen werden (erwartbar): " + worldName);
        } else {
          unloadFailed = true;
          plugin.getLogger().severe("Konnte Welt nicht zuverlässig entladen: " + worldName);
        }
      }
    }

    if (unloadFailed) {
      Bukkit.broadcastMessage(PREFIX + "§cReset abgebrochen: Mindestens eine Welt konnte nicht entladen werden.");
      resetInProgress = false;
      return;
    }

    if (!startPostShutdownDeletion(worldsToReset)) {
      Bukkit.broadcastMessage(PREFIX + "§cReset fehlgeschlagen: Externer Löschprozess konnte nicht gestartet werden.");
      resetInProgress = false;
      return;
    }

    Bukkit.broadcastMessage(PREFIX + "§aWelt-Reset geplant.§7 Server wird heruntergefahren...");
    Bukkit.getScheduler().runTaskLater(plugin, Bukkit::shutdown, 40L);
  }

  private boolean startPostShutdownDeletion(List<String> worldsToReset) {
    List<Path> worldPaths = new ArrayList<>();
    for (String worldName : worldsToReset) {
      worldPaths.add(plugin.getServer().getWorldContainer().toPath().resolve(worldName).toAbsolutePath().normalize());
    }

    String osName = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
    ProcessBuilder processBuilder;
    if (osName.contains("win")) {
      StringBuilder command = new StringBuilder("timeout /t 3 /nobreak > NUL");
      for (Path path : worldPaths) {
        command.append(" && rmdir /s /q \"").append(path).append("\"");
      }
      processBuilder = new ProcessBuilder("cmd", "/c", command.toString());
    } else {
      StringBuilder command = new StringBuilder("sleep 3");
      for (Path path : worldPaths) {
        command.append(" && rm -rf -- '").append(path.toString().replace("'", "'\\''")).append("'");
      }
      processBuilder = new ProcessBuilder("sh", "-c", command.toString());
    }

    processBuilder.redirectErrorStream(true);
    try {
      processBuilder.start();
      plugin.getLogger().info("Externer Löschprozess für Welt-Reset gestartet.");
      return true;
    } catch (IOException exception) {
      plugin.getLogger().severe("Konnte externen Löschprozess nicht starten: " + exception.getMessage());
      return false;
    }
  }

  private void deleteDirectory(Path path) throws IOException {
    if (!Files.exists(path)) {
      return;
    }

    Files.walkFileTree(path, new SimpleFileVisitor<>() {
      @Override
      public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
        Files.deleteIfExists(file);
        return FileVisitResult.CONTINUE;
      }

      @Override
      public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
        Files.deleteIfExists(dir);
        return FileVisitResult.CONTINUE;
      }
    });
  }
}

