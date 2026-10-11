package com.kingpixel.ultradaycare.config;

import com.kingpixel.cobbleutils.Model.DurationValue;
import com.kingpixel.cobbleutils.Model.EconomyUse;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Configuration options for the Incubators Module.
 *
 * @author Carlos Varas Alonso
 */
@Getter
@Setter
public class IncubatorConfig {
  private boolean enabled = false;
  private boolean restrictEggsInParty = true;
  private IncubatorHatchMode hatchMode = IncubatorHatchMode.TIME_OR_STEPS;
  private DurationValue baseHatchTime = DurationValue.parse("15m");
  private double baseHatchSteps = 2560.0;
  private int defaultIncubatorUses = 3;

  // Economy & Daily limits
  private double price = 5000.0;
  private EconomyUse economyUse = new EconomyUse("IMPACTOR", "impactor:dollars");
  private Map<String, Integer> dailyLimits = new HashMap<>(Map.of(
      "", 1,
      "group.vip", 3,
      "group.master", 5
  ));

  // Caretaker settings
  private List<String> caretakerAbilities = new ArrayList<>(List.of("magmaarmor", "flamebody", "steamengine"));
  private double minIvMultiplier = 1.0;
  private double maxIvMultiplier = 2.5;

  // Slot GUI positions and permissions
  private List<Integer> slots = new ArrayList<>(List.of(10, 12, 14, 16));
  private List<String> commands = new ArrayList<>(List.of("incubators", "incubator"));


  public void check() {
    if (hatchMode == null) {
      hatchMode = IncubatorHatchMode.TIME_OR_STEPS;
    }
    if (baseHatchTime == null) {
      baseHatchTime = DurationValue.parse("15m");
    }
    if (baseHatchSteps <= 0) {
      baseHatchSteps = 2560.0;
    }
    if (defaultIncubatorUses <= 0) {
      defaultIncubatorUses = 3;
    }
    if (dailyLimits == null) {
      dailyLimits = new HashMap<>(Map.of("", 1));
    }
    if (caretakerAbilities == null || caretakerAbilities.isEmpty()) {
      caretakerAbilities = new ArrayList<>(List.of("magmaarmor", "flamebody", "steamengine"));
    }
    if (minIvMultiplier <= 0) {
      minIvMultiplier = 1.0;
    }
    if (maxIvMultiplier < minIvMultiplier) {
      maxIvMultiplier = minIvMultiplier;
    }
    if (slots == null || slots.isEmpty()) {
      slots = new ArrayList<>(List.of(10, 12, 14, 16));
    }
    if (commands == null || commands.isEmpty()) {
      commands = new ArrayList<>(List.of("incubators", "incubator"));
    }
  }

  public static String incubatorSlotPermission(int index) {
    return "ultradaycare.incubator.slot." + (index + 1);
  }
}
