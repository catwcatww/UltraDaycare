package com.kingpixel.ultradaycare.models;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.api.pokemon.PokemonProperties;
import com.cobblemon.mod.common.api.pokemon.egg.EggGroup;
import com.cobblemon.mod.common.pokemon.Gender;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.kingpixel.cobbleutils.CobbleUtils;
import com.kingpixel.cobbleutils.Model.CobbleUtilsTags;
import com.kingpixel.cobbleutils.api.PermissionApi;
import com.kingpixel.cobbleutils.util.PlayerUtils;
import com.kingpixel.cobbleutils.util.PokemonUtils;
import com.kingpixel.cobbleutils.util.TypeMessage;
import com.kingpixel.ultradaycare.UltraDaycare;
import com.kingpixel.ultradaycare.mechanics.pokemon.DayCarePokemon;
import com.kingpixel.ultradaycare.mechanics.Mechanics;
import lombok.Data;
import lombok.ToString;
import net.minecraft.registry.DynamicRegistryManager;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * @author Carlos Varas Alonso - 31/01/2025 1:17
 */
@Data
@ToString
public class Plot {
  private Pokemon male;
  private Pokemon female;
  private CopyOnWriteArrayList<Pokemon> eggs;
  private long timeToHatch;
  private long canOpen;

  // New economy and training state
  private double malePendingXp;
  private double femalePendingXp;
  private boolean breedingPaid;
  private boolean eggProducedSincePayment;

  public Plot() {
    this.male = null;
    this.female = null;
    this.eggs = new CopyOnWriteArrayList<>();
    this.timeToHatch = 0;
    this.canOpen = 0;
    this.malePendingXp = 0;
    this.femalePendingXp = 0;
    this.breedingPaid = false;
    this.eggProducedSincePayment = false;
  }

  public static boolean isNotBreedable(Pokemon pokemon) {
    return pokemon.getForm().getEggGroups().contains(EggGroup.UNDISCOVERED)
        || UltraDaycare.config.getBlackList().isBlackListed(pokemon);
  }

  public static String plotPermission(int i) {
    return "ultradaycare.plot." + (i + 1);
  }

  public synchronized void setMale(Pokemon male) {
    if (this.male != male) {
      this.malePendingXp = 0;
    }
    this.male = male;
  }

  public synchronized void setFemale(Pokemon female) {
    if (this.female != female) {
      this.femalePendingXp = 0;
    }
    this.female = female;
  }

  private boolean isDitto(Pokemon pokemon) {
    if (pokemon == null)
      return false;
    return pokemon.getForm().getEggGroups().contains(EggGroup.DITTO);
  }

  public synchronized boolean canBreed(Pokemon pokemon, SelectGender gender) {
    if (pokemon == null)
      return false;
    UltraDaycare.fixBreedable(pokemon);
    if (isNotBreedable(pokemon))
      return false;
    Pokemon other = gender == SelectGender.MALE ? female : male;

    boolean otherIsDitto = isDitto(other);
    boolean pokemonIsDitto = isDitto(pokemon);
    if (pokemonIsDitto && otherIsDitto)
      return UltraDaycare.config.isDobbleDitto();
    if (!pokemon.getPersistentData().getBoolean(CobbleUtilsTags.BREEDABLE_TAG))
      return false;
    Gender pokemonGender = pokemon.getGender();
    if (gender == SelectGender.MALE) {
      if (!pokemonGender.equals(Gender.MALE) && !pokemonGender.equals(Gender.GENDERLESS) && !otherIsDitto)
        return false;
    } else {
      if (!pokemonGender.equals(Gender.FEMALE) && !pokemonGender.equals(Gender.GENDERLESS) && !otherIsDitto)
        return false;
    }
    if (other == null)
      return true;
    if (other.getGender().equals(Gender.GENDERLESS)) {
      if (otherIsDitto) {
        return true;
      } else {
        return pokemonIsDitto;
      }
    }
    if (pokemon.getGender().equals(Gender.GENDERLESS) && !pokemon.getForm().getEggGroups().contains(EggGroup.DITTO))
      return false;
    for (EggGroup eggGroup : pokemon.getForm().getEggGroups()) {
      if (other.getForm().getEggGroups().contains(eggGroup)
          || pokemon.getForm().getEggGroups().contains(EggGroup.DITTO))
        return true;
    }
    return false;
  }

  public synchronized void setTime(ServerPlayerEntity player) {
    if (hasTwoParents()) {
      long cooldown = PlayerUtils.getCooldown(UltraDaycare.config.getCooldowns(), UltraDaycare.config.getCooldown(),
          player);
      timeToHatch = System.currentTimeMillis() + cooldown;
    } else {
      timeToHatch = 0;
    }
  }

  public boolean hasEggs() {
    return !eggs.isEmpty();
  }

  public boolean hasTwoParents() {
    return male != null && female != null;
  }

  public boolean notParents() {
    return male == null && female == null;
  }

  public synchronized boolean giveEggs(ServerPlayerEntity player) {
    if (!hasEggs())
      return false;
    boolean removeEggs = false;
    List<Pokemon> remove = new ArrayList<>();
    for (Pokemon egg : eggs) {
      if (egg == null)
        continue;
      CobbleUtils.server.execute(() -> Cobblemon.INSTANCE.getStorage().getParty(player).add(egg));
      remove.add(egg);
    }
    if (!remove.isEmpty()) {
      eggs.removeAll(remove);
      removeEggs = true;
    }
    return removeEggs;
  }

  public boolean check(ServerPlayerEntity player) {
    return false;
  }

  private boolean hasCooldown(ServerPlayerEntity player) {
    return timeToHatch > System.currentTimeMillis();
  }

  public int limitEggs(ServerPlayerEntity player) {
    int limit = 1;
    for (Map.Entry<String, Integer> limitEgg : UltraDaycare.config.getLimitEggs().entrySet()) {
      if (limit > limitEgg.getValue())
        continue;
      if (UltraDaycare.hasPermission(player, limitEgg.getKey(), 4) ||
          PermissionApi.hasPermission(player, "cobbleutils.breeding." + limitEgg.getValue(), 4)) {
        limit = limitEgg.getValue();
      }
    }
    return limit;
  }

  public synchronized void checkRefund(ServerPlayerEntity player) {
    if (UltraDaycare.config.isEnableBreedingFee() && breedingPaid && !eggProducedSincePayment) {
      com.kingpixel.cobbleutils.api.EconomyApi.addMoney(
          player.getUuid(),
          java.math.BigDecimal.valueOf(UltraDaycare.config.getBreedingFeePrice()),
          UltraDaycare.config.getEconomyUse());
      com.kingpixel.cobbleutils.util.PlayerUtils.sendMessage(
          player,
          UltraDaycare.language.getMessageBreedingEntranceRefunded()
              .replace("%price%", String.valueOf(UltraDaycare.config.getBreedingFeePrice())),
          UltraDaycare.language.getPrefix(),
          com.kingpixel.cobbleutils.util.TypeMessage.CHAT);
    }
    this.breedingPaid = false;
    this.eggProducedSincePayment = false;
  }

  public synchronized void addFemale(ServerPlayerEntity player, Pokemon female) {
    checkRefund(player);
    setFemale(female);
    setTime(player);
  }

  public synchronized void addMale(ServerPlayerEntity player, Pokemon male) {
    checkRefund(player);
    setMale(male);
    setTime(player);
  }

  public synchronized void checkOfflineBreeding(ServerPlayerEntity player, User user, long elapsedMillis) {
    if (!UltraDaycare.getActiveMode().isPassiveBreedingEnabled())
      return;
    if (!UltraDaycare.config.isEnableOfflineBreeding())
      return;
    if (!hasTwoParents())
      return;
    if (UltraDaycare.config.isEnableBreedingFee() && !breedingPaid)
      return;

    long interval = UltraDaycare.config.getOfflineBreedingInterval().toMillis();
    if (interval <= 0)
      return;

    long checks = elapsedMillis / interval;
    if (checks <= 0)
      return;

    int maxOffline = UltraDaycare.config.getMaxOfflineEggsPerPlot();
    int currentEggs = eggs.size();
    int limit = limitEggs(player);

    for (int i = 0; i < checks && currentEggs < limit && (i < maxOffline); i++) {
      Pokemon egg = createEgg(player);
      eggs.add(egg);
      currentEggs++;
      eggProducedSincePayment = true;
      user.markDirty();
    }
  }

  public synchronized boolean checkEgg(ServerPlayerEntity player, User user) {
    try {
      boolean update = false;

      if (!UltraDaycare.getActiveMode().isPassiveBreedingEnabled())
        return update;

      if (!hasTwoParents())
        return update;

      // Check if breeding is paid
      if (UltraDaycare.config.isEnableBreedingFee() && !breedingPaid) {
        return update;
      }

      int index = user.getPlots().indexOf(this) + 1;
      int sizeEggs = eggs.size();
      if (sizeEggs >= limitEggs(player))
        return update;

      boolean femaleCanBreed = canBreed(female, SelectGender.FEMALE);
      boolean maleCanBreed = canBreed(male, SelectGender.MALE);
      var party = Cobblemon.INSTANCE.getStorage().getParty(player);
      if (!femaleCanBreed) {
        PlayerUtils.sendMessage(
            player,
            PokemonUtils.replace(UltraDaycare.language.getMessageRemovedFemale(), female)
                .replace("%plot%", index + ""),
            UltraDaycare.language.getPrefix(),
            TypeMessage.CHAT);
        checkRefund(player);
        Pokemon pokemonFemale = female.clone(false, DynamicRegistryManager.EMPTY);
        CobbleUtils.server.execute(() -> party.add(pokemonFemale));
        setFemale(null);
        user.markDirty();
      }
      if (!maleCanBreed) {
        PlayerUtils.sendMessage(
            player,
            PokemonUtils.replace(UltraDaycare.language.getMessageRemovedMale(), male)
                .replace("%plot%", index + ""),
            UltraDaycare.language.getPrefix(),
            TypeMessage.CHAT);
        checkRefund(player);
        Pokemon pokemonMale = male.clone(false, DynamicRegistryManager.EMPTY);
        CobbleUtils.server.execute(() -> party.add(pokemonMale));
        setMale(null);
        user.markDirty();
      }
      if (!maleCanBreed || !femaleCanBreed)
        return true;
      fixCooldown(player);
      if (!hasCooldown(player)) {
        Pokemon egg = createEgg(player);
        if (!egg.getSpecies().showdownId().equals("egg")) {
          PlayerUtils.sendMessage(
              player,
              "You need install the datapacks to use this feature",
              UltraDaycare.language.getPrefix(),
              TypeMessage.CHAT);
        } else {
          if (user.isNotifyCreateEgg()) {
            List<Pokemon> pokemons = new ArrayList<>();
            pokemons.add(male);
            pokemons.add(female);
            pokemons.add(egg);
            PlayerUtils.sendMessage(
                player,
                PokemonUtils.replace(UltraDaycare.language.getMessageEggCreated()
                    .replace("%pokemon3%", egg.getPersistentData().getString(DayCarePokemon.TAG_POKEMON)), pokemons),
                UltraDaycare.language.getPrefix(),
                TypeMessage.CHAT);
          }
          if (UltraDaycare.hasPermission(player, "ultradaycare.autoclaim", 4)) {
            CobbleUtils.server.execute(() -> Cobblemon.INSTANCE.getStorage().getParty(player).add(egg));
          } else {
            eggs.add(egg);
          }
          update = true;
          eggProducedSincePayment = true;
          processFertility(male);
          processFertility(female);
          setTime(player);
        }
      }
      if (update)
        user.markDirty();
      return update;
    } catch (Exception e) {
      UltraDaycare.LOGGER.error("Error updating plot breeding: ", e);
      return false;
    }
  }

  private synchronized void fixCooldown(ServerPlayerEntity player) {
    long correctCooldown = PlayerUtils.getCooldown(UltraDaycare.config.getCooldowns(),
        UltraDaycare.config.getCooldown(), player);
    long correctTimeToHatch = System.currentTimeMillis() + correctCooldown;
    if (timeToHatch > correctTimeToHatch)
      timeToHatch = correctTimeToHatch;
  }

  public synchronized Pokemon createEgg(ServerPlayerEntity player) {
    Pokemon egg = PokemonProperties.Companion.parse("egg").create();
    egg.setUuid(UUID.randomUUID());
    Pokemon firstEvolution = female;
    EggBuilder eggBuilder = EggBuilder.builder()
      .firstEvolution(firstEvolution)
      .egg(egg)
      .female(female)
      .male(male)
      .player(player)
      .build();
    for (Mechanics mechanic : UltraDaycare.mechanics) {
      try {
        if (mechanic.isActive())
          mechanic.applyEgg(eggBuilder);
      } catch (Exception e) {
        UltraDaycare.LOGGER.error("Error applying mechanic " + mechanic.getClass().getSimpleName() + ": ", e);
      }
    }

    if (!male.getTradeable() || !female.getTradeable()) {
      egg.setTradeable(false);
    }

    return egg;
  }

  public static final String FERTILITY_TAG = "fertility";

  private void processFertility(Pokemon pokemon) {
    if (pokemon == null || !UltraDaycare.config.isEnableFertility()) return;

    var nbt = pokemon.getPersistentData();
    int fertility = nbt.contains(FERTILITY_TAG) ? nbt.getInt(FERTILITY_TAG) : UltraDaycare.config.getMaxFertility();
    fertility = Math.max(0, fertility - 1);
    nbt.putInt(FERTILITY_TAG, fertility);

    if (fertility <= 0) {
      UltraDaycare.setBreedable(pokemon, false);
      nbt.putBoolean(CobbleUtilsTags.BREEDABLE_BUILDER_TAG, true);
    }
  }

  public synchronized void addPokemon(ServerPlayerEntity player, Pokemon pokemon, SelectGender gender,
      User user) {
    if (gender == SelectGender.FEMALE)
      addFemale(player, pokemon);
    else
      addMale(player, pokemon);
    user.markDirty();
    CobbleUtils.server.execute(() -> {
      Cobblemon.INSTANCE.getStorage().getParty(player).remove(pokemon);
      Cobblemon.INSTANCE.getStorage().getPC(player).remove(pokemon);
    });
  }
}
