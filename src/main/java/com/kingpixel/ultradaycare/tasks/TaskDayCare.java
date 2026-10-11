package com.kingpixel.ultradaycare.tasks;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.api.storage.party.PlayerPartyStore;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.kingpixel.cobbleutils.CobbleUtils;
import com.kingpixel.cobbleutils.util.PlayerUtils;
import com.kingpixel.cobbleutils.util.TypeMessage;
import com.kingpixel.ultradaycare.UltraDaycare;
import com.kingpixel.ultradaycare.api.PokeMMODaycareMode;
import com.kingpixel.ultradaycare.config.EggHatchMethod;
import com.kingpixel.ultradaycare.mechanics.pokemon.DayCarePokemon;
import com.kingpixel.ultradaycare.models.EggData;
import com.kingpixel.ultradaycare.models.Plot;
import com.kingpixel.ultradaycare.models.Position;
import com.kingpixel.ultradaycare.models.User;
import lombok.Data;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityPose;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Unique;

import java.util.Collections;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;

/**
 * @author Carlos Varas Alonso - 24/11/2025 8:05
 */
@Data
public class TaskDayCare implements Runnable {
  public static final ConcurrentMap<ServerPlayerEntity, Integer> cobbleDaycare$playerTeleport = new ConcurrentHashMap<>();
  private static final long TICKS_TO_MILLISECONDS = 50;
  private static final ConcurrentMap<ServerPlayerEntity, Position> cobbleDaycare$playerPositions = new ConcurrentHashMap<>();

  public TaskDayCare() {
    UltraDaycare.getAsyncContext().scheduleAtFixedRate(this, 5, 1, TimeUnit.SECONDS);
  }

  private static void sendMessageMultiplierSteps(User user, ServerPlayerEntity player) {
    boolean activeMultiplier = UltraDaycare.config.isGlobalMultiplierSteps();
    float multiplier = user.getActualMultiplier(player);
    boolean hasMultiplier = multiplier >= UltraDaycare.config.getMultiplierSteps();

    if (activeMultiplier || hasMultiplier) {
      long cooldown = user.getTimeMultiplierSteps() * TICKS_TO_MILLISECONDS;
      String cooldownMessage = PlayerUtils.getCooldown(System.currentTimeMillis() + cooldown);

      PlayerUtils.sendMessage(
          player,
          UltraDaycare.language.getMessageActiveStepsMultiplier()
              .replace("%multiplier%", String.format("%.2f", multiplier))
              .replace("%cooldown%", cooldownMessage)
              .replace("%time%", cooldownMessage),
          UltraDaycare.language.getPrefix(),
          TypeMessage.ACTIONBAR);
    }
  }

  @Unique
  private static boolean isVehiclePermitted(ServerPlayerEntity player) {
    String vehicleId = player.getVehicle() == null ? "" : player.getVehicle().getSavedEntityId();
    if (vehicleId == null)
      vehicleId = "";
    return UltraDaycare.config.getPermittedVehicles().contains(vehicleId) || vehicleId.isEmpty();
  }

  private boolean isPlayerEligibleForStepUpdate(ServerPlayerEntity player) {
    return (UltraDaycare.config.isAllowElytra() || !player.isInPose(EntityPose.FALL_FLYING))
        && !player.getAbilities().flying
        && isVehiclePermitted(player)
        && (!player.isTouchingWater() || player.isInPose(EntityPose.SWIMMING));
  }

  private Entity getEffectiveEntity(ServerPlayerEntity player) {
    return player.getVehicle() != null ? player.getVehicle() : player;
  }

  /**
   * Retrieves the hatching acceleration multiplier based on the player's party
   * abilities.
   *
   * @param party the player's party store to check
   * @return the ability acceleration multiplier (e.g. 2.0 for Flame Body, or 1.0
   *         if none)
   */
  private double getAbilityMultiplier(PlayerPartyStore party) {
    if (party == null)
      return 1.0;
    try {
      for (Pokemon pokemon : party) {
        if (pokemon == null || "egg".equals(pokemon.showdownId()))
          continue;
        if (pokemon.getAbility() != null) {
          String abilityName = pokemon.getAbility().getName().replace(" ", "").toLowerCase();
          if (UltraDaycare.config.getAbilityAcceleration().contains(abilityName)) {
            return UltraDaycare.config.getMultiplierAbilityAcceleration();
          }
        }
      }
    } catch (Exception e) {
      UltraDaycare.LOGGER.error("Error in getAbilityMultiplier", e);
    }
    return 1.0;
  }

  /**
   * Updates standard movement-based egg steps for a player's party.
   *
   * @param party         the player's party store
   * @param deltaMovement the movement delta
   * @param player        the player entity
   */
  private void updateEggSteps(PlayerPartyStore party, double deltaMovement, ServerPlayerEntity player) {
    if (UltraDaycare.config.getIncubators().isEnabled() && UltraDaycare.config.getIncubators().isRestrictEggsInParty()) {
      return;
    }
    if (PokeMMODaycareMode.ID.equalsIgnoreCase(UltraDaycare.config.getDaycareMode())
        && UltraDaycare.config.getPokemmoEggHatchMethod() == EggHatchMethod.TIME) {
      return;
    }
    User user = UltraDaycare.database.getUser(player);
    if (user == null)
      return;
    double finalMovement = deltaMovement * getAbilityMultiplier(party);
    for (Pokemon pokemon : party) {
      if (pokemon != null && "egg".equals(pokemon.showdownId())) {
        EggData.steps(player, pokemon, finalMovement, user);
      }
    }
  }

  private void updateIncubatorTime(User user) {
    if (!UltraDaycare.config.getIncubators().isEnabled() || user == null || user.getIncubatorSlots() == null) {
      return;
    }
    boolean changed = false;
    for (com.kingpixel.ultradaycare.models.IncubatorSlot slot : user.getIncubatorSlots()) {
      if (slot != null && slot.isIncubating()) {
        slot.tick(0, 1000L);
        changed = true;
      }
    }
    if (changed) {
      user.markDirty();
    }
  }

  private void updateIncubatorSteps(User user, double deltaMovement) {
    if (!UltraDaycare.config.getIncubators().isEnabled() || user == null || user.getIncubatorSlots() == null || deltaMovement <= 0) {
      return;
    }
    boolean changed = false;
    for (com.kingpixel.ultradaycare.models.IncubatorSlot slot : user.getIncubatorSlots()) {
      if (slot != null && slot.isIncubating()) {
        slot.tick(deltaMovement, 0);
        changed = true;
      }
    }
    if (changed) {
      user.markDirty();
    }
  }

  /**
   * Updates PokeMMO time-based eggs for an online player.
   *
   * @param player the player entity
   * @param user   the user daycare data
   */
  private void updatePokeMMOEggs(ServerPlayerEntity player, User user) {
    if (!PokeMMODaycareMode.ID.equalsIgnoreCase(UltraDaycare.config.getDaycareMode()))
      return;
    if (UltraDaycare.config.getPokemmoEggHatchMethod() == EggHatchMethod.STEPS)
      return;

    CobbleUtils.server.execute(() -> {
      try {
        PlayerPartyStore party = Cobblemon.INSTANCE.getStorage().getParty(player);
        double multiplier = getAbilityMultiplier(party);
        for (Pokemon pokemon : party) {
          if (pokemon == null || !"egg".equals(pokemon.showdownId()))
            continue;

          int referenceCycles = pokemon.getPersistentData().getInt(DayCarePokemon.TAG_REFERENCE_CYCLES);
          int stepsPerCycle = (int) pokemon.getPersistentData().getDouble(DayCarePokemon.TAG_REFERENCE_STEPS);
          if (stepsPerCycle <= 0)
            stepsPerCycle = 128;

          int totalReferenceSteps = referenceCycles * stepsPerCycle;
          long durationMs = PlayerUtils.getCooldown(
              UltraDaycare.config.getPokemmoEggHatchTimePermissions(),
              UltraDaycare.config.getPokemmoEggHatchTime(),
              player);

          double durationSeconds = durationMs / 1000.0;
          if (durationSeconds <= 0)
            durationSeconds = 600.0;

          double stepsPerSecond = (totalReferenceSteps / durationSeconds) * multiplier;
          EggData.steps(player, pokemon, stepsPerSecond, user);
        }
      } catch (Exception e) {
        UltraDaycare.LOGGER.error("Error in updatePokeMMOEggs", e);
      }
    });
  }

  private void updatePlotsXp(ServerPlayerEntity player, double deltaMovement) {
    User user = UltraDaycare.database.getUser(player);
    if (user == null || user.getPlots().isEmpty())
      return;
    double xpAccumulated = deltaMovement * UltraDaycare.config.getXpPerStep();
    int maxLevel = UltraDaycare.config.getMaxLevelTraining();

    for (Plot plot : user.getPlots()) {
      if (plot.getMale() != null && plot.getMale().getLevel() < maxLevel) {
        plot.setMalePendingXp(plot.getMalePendingXp() + xpAccumulated);
      }
      if (plot.getFemale() != null && plot.getFemale().getLevel() < maxLevel) {
        plot.setFemalePendingXp(plot.getFemalePendingXp() + xpAccumulated);
      }
    }
  }

  private void updatePlotsBreeding(ServerPlayerEntity player, User user) {
    if (user == null || user.getPlots().isEmpty())
      return;
    for (Plot plot : user.getPlots()) {
      // Preliminary quick checks before calling the heavier logic in Plot.checkEgg
      if (plot.hasTwoParents()) {
        plot.checkEgg(player, user);
      }
    }
  }

  private void sendMessage(ServerPlayerEntity player) {
    User user = UltraDaycare.database.getUser(player);
    if (user == null)
      return;
    long timeMultiplierSteps = user.getTimeMultiplierSteps();

    if (timeMultiplierSteps > 0) {
      timeMultiplierSteps -= UltraDaycare.config.getTicksToWalking();
      user.setTimeMultiplierSteps(timeMultiplierSteps);

      if (timeMultiplierSteps <= 0) {
        user.setMultiplierSteps(UltraDaycare.config.getMultiplierSteps());
        user.setTimeMultiplierSteps(0);
      }

      if (user.isActionBar()) {
        sendMessageMultiplierSteps(user, player);
      }
    }
  }

  private void processPlayers() {
    if (CobbleUtils.server == null)
      return;
    long currentTime = System.currentTimeMillis();
    var playerManager = CobbleUtils.server.getPlayerManager();
    if (playerManager == null || playerManager.getPlayerList().isEmpty())
      return;

    for (ServerPlayerEntity player : Collections.synchronizedList(playerManager.getPlayerList())) {
      if (player == null || !player.isAlive() || player.isRemoved())
        continue;
      try {
        User user = UltraDaycare.database.getUser(player);
        if (user == null)
          continue;

        updatePokeMMOEggs(player, user);
        updateIncubatorTime(user);

        if (!isPlayerEligibleForStepUpdate(player))
          continue;

        Entity entity = getEffectiveEntity(player);
        if (entity == null)
          continue;

        Position pos = cobbleDaycare$playerPositions.computeIfAbsent(player,
            p -> new Position(entity.getX(), entity.getZ(), currentTime));

        double deltaX = entity.getX() - pos.getX();
        double deltaZ = entity.getZ() - pos.getZ();
        double deltaMovement = Math.hypot(deltaX, deltaZ);

        int teleportCount = cobbleDaycare$playerTeleport.getOrDefault(player, 0);
        if (deltaMovement <= 0 || teleportCount > 0) {
          if (teleportCount > 0)
            cobbleDaycare$playerTeleport.put(player, teleportCount - 1);
          pos.setX(entity.getX());
          pos.setZ(entity.getZ());
          pos.setLastUpdate(currentTime);
          continue;
        }

        CobbleUtils.server.execute(() -> {
          try {
            PlayerPartyStore party = Cobblemon.INSTANCE.getStorage().getParty(player);
            updateEggSteps(party, deltaMovement, player);
            updateIncubatorSteps(user, deltaMovement);
            updatePlotsXp(player, deltaMovement);
            updatePlotsBreeding(player, user);
            sendMessage(player);
          } catch (Exception e) {
            UltraDaycare.LOGGER.error("Error processing player tasks: ", e);
          }
        });

        pos.setX(entity.getX());
        pos.setZ(entity.getZ());
        pos.setLastUpdate(currentTime);
      } catch (Exception e) {
        UltraDaycare.LOGGER.error("Error processing player movement: ", e);
      }
    }
  }

  @Override
  public void run() {
    try {
      processPlayers();
    } catch (Exception e) {
      UltraDaycare.LOGGER.error("Error running TaskDayCare: ", e);
    }
  }
}
