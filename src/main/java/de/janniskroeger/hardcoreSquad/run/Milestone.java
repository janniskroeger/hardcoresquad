package de.janniskroeger.hardcoreSquad.run;

import lombok.Getter;

@Getter
public enum Milestone {
  STONE_AGE("Steinzeit", "Das Team hat erstmals Bruchstein gesammelt."),
  IRON_ACQUIRED("Eisen", "Das Team hat erstmals Eisen erhalten."),
  DIAMOND_ACQUIRED("Diamant", "Das Team hat erstmals einen Diamanten erhalten."),
  NETHER_ENTER("Nether", "Das Team hat erstmals den Nether betreten."),
  BLAZE_ROD("Blaze Rod", "Das Team hat erstmals eine Blaze Rod erhalten."),
  END_ENTER("Ende", "Das Team hat erstmals das Ende betreten."),
  DRAGON_KILLED("Drache", "Der Enderdrache wurde besiegt.");

  private final String displayName;
  private final String broadcastText;

  Milestone(String displayName, String broadcastText) {
    this.displayName = displayName;
    this.broadcastText = broadcastText;
  }
}

