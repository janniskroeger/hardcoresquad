package de.janniskroeger.hardcoreSquad.command;

import de.janniskroeger.hardcoreSquad.run.RunManager;
import lombok.RequiredArgsConstructor;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

@RequiredArgsConstructor
public class HcCommand implements CommandExecutor, TabCompleter {

  private static final String PREFIX = "§7";

  private final RunManager runManager;


  @Override
  public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
    if (args.length == 0) {
      sender.sendMessage(PREFIX + "Verwendung: §e/hc <start|status|reset|hardreset>");
      return true;
    }

    String subCommand = args[0].toLowerCase(Locale.ROOT);
    switch (subCommand) {
      case "start" -> handleStart(sender, args);
      case "status" -> runManager.sendStatus(sender);
      case "reset" -> handleReset(sender, args);
      case "hardreset" -> handleHardReset(sender);
      default -> sender.sendMessage(PREFIX + "§cUnbekannter Unterbefehl.§7 Nutze §e/hc <start|status|reset|hardreset>");
    }

    return true;
  }

  private void handleStart(CommandSender sender, String[] args) {
    if (!sender.hasPermission("hardcoreteam.admin")) {
      sender.sendMessage(PREFIX + "§cDafür hast du keine Berechtigung.");
      return;
    }

    if (args.length < 2) {
      sender.sendMessage(PREFIX + "Verwendung: §e/hc start <lives>");
      return;
    }

    int lives;
    try {
      lives = Integer.parseInt(args[1]);
    } catch (NumberFormatException exception) {
      sender.sendMessage(PREFIX + "§cDie Leben müssen eine ganze Zahl sein.");
      return;
    }

    if (lives <= 0) {
      sender.sendMessage(PREFIX + "§cDie Anzahl der Leben muss größer als 0 sein.");
      return;
    }

    runManager.startRun(lives);
    sender.sendMessage(PREFIX + "§aRun erfolgreich gestartet.");
  }

  private void handleReset(CommandSender sender, String[] args) {
    if (!sender.hasPermission("hardcoreteam.admin")) {
      sender.sendMessage(PREFIX + "§cDafür hast du keine Berechtigung.");
      return;
    }

    if (!runManager.canReset()) {
      sender.sendMessage(runManager.getCannotResetReason());
      return;
    }

    if (args.length >= 2) {
      if ("hard".equalsIgnoreCase(args[1])) {
        runManager.executeHardReset(sender);
        return;
      }

      sender.sendMessage(PREFIX + "Verwendung: §e/hc reset [hard]");
      return;
    }

    runManager.executeReset(sender);
  }

  private void handleHardReset(CommandSender sender) {
    if (!sender.hasPermission("hardcoreteam.admin")) {
      sender.sendMessage(PREFIX + "§cDafür hast du keine Berechtigung.");
      return;
    }

    if (!runManager.canReset()) {
      sender.sendMessage(runManager.getCannotResetReason());
      return;
    }

    runManager.executeHardReset(sender);
  }

  @Override
  public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
    List<String> suggestions = new ArrayList<>();
    if (args.length == 1) {
      suggestions.add("start");
      suggestions.add("status");
      suggestions.add("reset");
      suggestions.add("hardreset");
      return suggestions.stream()
          .filter(entry -> entry.startsWith(args[0].toLowerCase(Locale.ROOT)))
          .toList();
    }

    if (args.length == 2 && "reset".equalsIgnoreCase(args[0]) && sender.hasPermission("hardcoreteam.admin")) {
      return Stream.of("hard")
          .filter(entry -> entry.startsWith(args[1].toLowerCase(Locale.ROOT)))
          .toList();
    }

    if (args.length == 2 && "start".equalsIgnoreCase(args[0]) && sender.hasPermission("hardcoreteam.admin")) {
      return List.of("3", "5", "10");
    }

    return List.of();
  }
}

