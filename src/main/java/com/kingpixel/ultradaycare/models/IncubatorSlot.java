package com.kingpixel.ultradaycare.models;

import com.cobblemon.mod.common.api.pokemon.stats.Stats;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.kingpixel.ultradaycare.UltraDaycare;
import com.kingpixel.ultradaycare.config.IncubatorHatchMode;
import lombok.Data;
import lombok.ToString;

import java.util.List;

/**
 * Represents a single incubator slot owned by a player.
 *
 * @author Carlos Varas Alonso
 */
@Data
@ToString
public class IncubatorSlot {
  private static final List<Stats> STATS = List.of(
      Stats.HP,
      Stats.ATTACK,
      Stats.DEFENCE,
      Stats.SPECIAL_ATTACK,
      Stats.SPECIAL_DEFENCE,
      Stats.SPEED
  );

  private int slotIndex;
  private boolean hasIncubator;
  private int remainingUses;
  private Pokemon egg;
  private Pokemon caretaker;
  private double accumulatedSteps;
  private long elapsedActiveTimeMillis;
  private long requiredTimeMillis;
  private double requiredSteps;

  public IncubatorSlot() {
    this(0);
  }

  public IncubatorSlot(int slotIndex) {
    this.slotIndex = slotIndex;
    this.hasIncubator = false;
    this.remainingUses = 0;
    this.egg = null;
    this.caretaker = null;
    this.accumulatedSteps = 0;
    this.elapsedActiveTimeMillis = 0;
    this.requiredTimeMillis = 0;
    this.requiredSteps = 0;
  }

  public boolean isIncubatorActive() {
    return hasIncubator && remainingUses > 0;
  }

  public boolean hasEgg() {
    return egg != null;
  }

  public boolean hasCaretaker() {
    return caretaker != null;
  }

  /**
   * Checks if the assigned caretaker has an approved thermal acceleration ability.
   */
  public boolean isValidCaretaker() {
    if (caretaker == null || caretaker.getAbility() == null) {
      return false;
    }
    String ability = caretaker.getAbility().getName().replace(" ", "").toLowerCase();
    return UltraDaycare.config.getIncubators().getCaretakerAbilities().contains(ability);
  }

  public boolean isIncubating() {
    return isIncubatorActive() && hasEgg() && isValidCaretaker();
  }

  /**
   * Calculates the average IV across all 6 stats for the caretaker Pokémon.
   *
   * @return double value between 0.0 and 31.0
   */
  public double getCaretakerIvAverage() {
    if (caretaker == null) {
      return 0.0;
    }
    int sum = 0;
    for (Stats stat : STATS) {
      sum += caretaker.getIvs().getOrDefault(stat);
    }
    return sum / 6.0;
  }

  /**
   * Calculates the speed multiplier based on the caretaker's IV average.
   */
  public double getSpeedMultiplier() {
    if (!isValidCaretaker()) {
      return 1.0;
    }
    double avgIv = getCaretakerIvAverage();
    double ratio = Math.max(0.0, Math.min(1.0, avgIv / 31.0));
    double min = UltraDaycare.config.getIncubators().getMinIvMultiplier();
    double max = UltraDaycare.config.getIncubators().getMaxIvMultiplier();
    return min + (ratio * (max - min));
  }

  /**
   * Recalculates the required time and steps based on current configuration and caretaker speed multiplier.
   */
  public void recalculateRequirements() {
    double multiplier = Math.max(1.0, getSpeedMultiplier());
    long baseTime = UltraDaycare.config.getIncubators().getBaseHatchTime().toMillis();
    double baseSteps = UltraDaycare.config.getIncubators().getBaseHatchSteps();

    this.requiredTimeMillis = (long) (baseTime / multiplier);
    this.requiredSteps = baseSteps / multiplier;
  }

  /**
   * Verifies if the egg inside this incubator has fulfilled the hatching condition.
   */
  public boolean isHatchReady(IncubatorHatchMode mode) {
    if (!isIncubating()) {
      return false;
    }
    if (requiredTimeMillis <= 0 || requiredSteps <= 0) {
      recalculateRequirements();
    }

    boolean timeMet = elapsedActiveTimeMillis >= requiredTimeMillis;
    boolean stepsMet = accumulatedSteps >= requiredSteps;

    return switch (mode) {
      case TIME -> timeMet;
      case STEPS -> stepsMet;
      case TIME_OR_STEPS -> timeMet || stepsMet;
      case TIME_AND_STEPS -> timeMet && stepsMet;
    };
  }

  /**
   * Advances incubation progress during a game tick.
   */
  public void tick(double deltaSteps, long deltaMillis) {
    if (!isIncubating()) {
      return;
    }
    if (requiredTimeMillis <= 0 || requiredSteps <= 0) {
      recalculateRequirements();
    }

    if (deltaSteps > 0) {
      this.accumulatedSteps += deltaSteps;
    }
    if (deltaMillis > 0) {
      this.elapsedActiveTimeMillis += deltaMillis;
    }
  }

  public void resetProgress() {
    this.accumulatedSteps = 0;
    this.elapsedActiveTimeMillis = 0;
    this.requiredTimeMillis = 0;
    this.requiredSteps = 0;
  }

  /**
   * Calculates overall progress percentage (0 to 100).
   */
  public int getProgressPercentage(IncubatorHatchMode mode) {
    if (!isIncubating()) {
      return 0;
    }
    if (requiredTimeMillis <= 0 || requiredSteps <= 0) {
      recalculateRequirements();
    }

    double timePct = requiredTimeMillis > 0 ? (elapsedActiveTimeMillis * 100.0) / requiredTimeMillis : 0.0;
    double stepsPct = requiredSteps > 0 ? (accumulatedSteps * 100.0) / requiredSteps : 0.0;

    double result = switch (mode) {
      case TIME -> timePct;
      case STEPS -> stepsPct;
      case TIME_OR_STEPS -> Math.max(timePct, stepsPct);
      case TIME_AND_STEPS -> Math.min(timePct, stepsPct);
    };

    return (int) Math.max(0, Math.min(100, Math.round(result)));
  }
}
