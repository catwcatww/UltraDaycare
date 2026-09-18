package com.kingpixel.ultradaycare.mechanics.pokemon;

import com.kingpixel.ultradaycare.mechanics.Mechanics;

import com.cobblemon.mod.common.api.Priority;
import com.cobblemon.mod.common.api.abilities.Ability;
import com.cobblemon.mod.common.api.abilities.Abilities;
import com.cobblemon.mod.common.api.abilities.AbilityTemplate;
import com.cobblemon.mod.common.api.abilities.PotentialAbility;
import com.cobblemon.mod.common.api.pokemon.PokemonProperties;
import com.cobblemon.mod.common.api.pokemon.egg.EggGroup;
import com.cobblemon.mod.common.pokemon.Gender;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.cobblemon.mod.common.pokemon.abilities.HiddenAbilityType;
import com.kingpixel.cobbleutils.CobbleUtils;
import com.kingpixel.cobbleutils.util.PokemonUtils;
import com.kingpixel.cobbleutils.util.Utils;
import com.kingpixel.ultradaycare.UltraDaycare;
import com.kingpixel.ultradaycare.models.EggBuilder;
import com.kingpixel.ultradaycare.models.HatchBuilder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Carlos Varas Alonso - 11/03/2025 9:09
 */
@EqualsAndHashCode(callSuper = true)
@Data
public class DayCareAbility extends Mechanics {
  public static final String TAG = "ability";
  public static final String TAG_HA = "ha";
  private double percentageTransmitHAFemale;
  private double percentageTransmitHAMale;
  private boolean dittoTransmitHA;
  private boolean eggGroupTransmitHA;

  public DayCareAbility() {
    this.percentageTransmitHAFemale = 60;
    this.percentageTransmitHAMale = 40;
    this.dittoTransmitHA = false;
    this.eggGroupTransmitHA = false;
  }

  private static boolean isDitto(Pokemon pokemon) {
    if (pokemon == null)
      return false;
    return pokemon.getForm().getEggGroups().contains(EggGroup.DITTO);
  }

  @Override
  public String replace(String text, ServerPlayerEntity player) {
    String femaleHA = String.format("%.2f", percentageTransmitHAFemale);
    String maleHA = String.format("%.2f", percentageTransmitHAMale);
    String maleGender = PokemonUtils.getGenderTranslate(Gender.MALE);
    String femaleGender = PokemonUtils.getGenderTranslate(Gender.FEMALE);

    String ability = UltraDaycare.language.getInfoAbility()
        .replace("%female%", femaleGender)
        .replace("%male%", maleGender)
        .replace("%femaleHA%", femaleHA)
        .replace("%maleHA%", maleHA);
    return text
        .replace("%ability%", ability)
        .replace("%activeAbility%", isActive() ? CobbleUtils.language.getYes() : CobbleUtils.language.getNo())
        .replace("%activeDitto%", dittoTransmitHA ? CobbleUtils.language.getYes() : CobbleUtils.language.getNo());
  }

  private List<Ability> getStandardAbilities(Pokemon pokemon) {
    List<Ability> list = new ArrayList<>();
    if (pokemon == null)
      return list;
    for (PotentialAbility ability : pokemon.getForm().getAbilities()) {
      if (!(ability.getType() instanceof HiddenAbilityType)) {
        list.add(ability.getTemplate().create(false, Priority.NORMAL));
      }
    }
    return list;
  }

  private boolean shouldInheritHA(Pokemon male, Pokemon female, boolean maleHA, boolean femaleHA,
                                  boolean maleIsDitto, boolean femaleIsDitto) {
    boolean hasHA = maleHA || femaleHA;
    if (!hasHA)
      return false;

    boolean giveHA = false;
    if (male.getSpecies().equals(female.getSpecies())) {
      if (UltraDaycare.config.isDebug())
        UltraDaycare.LOGGER.info("Same Species");
      giveHA = true;
    } else if (maleIsDitto ^ femaleIsDitto) {
      boolean nonDittoHA = maleIsDitto ? femaleHA : maleHA;
      giveHA = this.dittoTransmitHA || nonDittoHA;
    } else if (eggGroupTransmitHA) {
      giveHA = true;
    } else if (femaleHA) {
      if (UltraDaycare.config.isDebug())
        UltraDaycare.LOGGER.info("Female HA");
      giveHA = true;
    }

    if (!giveHA)
      return false;

    if (femaleHA) {
      return Utils.getRandom().nextDouble() < percentageTransmitHAFemale / 100;
    } else {
      return Utils.getRandom().nextDouble() < percentageTransmitHAMale / 100;
    }
  }

  private String determineAbilityName(Pokemon male, Pokemon female, boolean femaleIsDitto,
                                      Pokemon firstEvolution, boolean isHA) {
    if (firstEvolution == null)
      return "";

    if (isHA) {
      Ability ha = getHa(firstEvolution);
      if (ha != null) {
        return ha.getName();
      }
    }

    // Try standard inheritance
    List<Ability> stdAbilities = getStandardAbilities(firstEvolution);
    if (!stdAbilities.isEmpty()) {
      Pokemon nonDittoParent = femaleIsDitto ? male : female;
      String parentAbilityName = nonDittoParent.getAbility().getName();
      for (Ability ab : stdAbilities) {
        if (ab.getName().equalsIgnoreCase(parentAbilityName)) {
          if (Utils.getRandom().nextDouble() < 0.8) {
            return ab.getName();
          }
          break;
        }
      }
      // Pick randomly from standard abilities
      int index = Utils.getRandom().nextInt(stdAbilities.size());
      return stdAbilities.get(index).getName();
    }

    // Default fallback
    return firstEvolution.getAbility().getName();
  }

  @Override
  public void applyEgg(EggBuilder builder) {
    Pokemon male = builder.getMale();
    Pokemon female = builder.getFemale();
    if (male == null || female == null)
      return;

    boolean maleHA = PokemonUtils.isAH(male);
    boolean femaleHA = PokemonUtils.isAH(female);
    boolean maleIsDitto = isDitto(male);
    boolean femaleIsDitto = isDitto(female);

    boolean isHA = shouldInheritHA(male, female, maleHA, femaleHA, maleIsDitto, femaleIsDitto);
    builder.getEgg().getPersistentData().putBoolean(TAG_HA, isHA);
    if(!male.getTradeable() || !female.getTradeable()){
      isHA = false;
    }
    String chosenAbilityName = determineAbilityName(male, female, femaleIsDitto, builder.getFirstEvolution(), isHA);
    if (!chosenAbilityName.isEmpty()) {
      builder.getEgg().getPersistentData().putString(TAG, chosenAbilityName);
    }
  }

  @Override
  public void applyHatch(HatchBuilder builder) {
    Pokemon egg = builder.getEgg();
    Pokemon pokemon = builder.getPokemon();
    String abilityName = egg.getPersistentData().getString(TAG);

    if (!abilityName.isEmpty()) {
      PokemonProperties.Companion.parse("ability=" + abilityName).apply(pokemon);
    } else {
      boolean isHA = egg.getPersistentData().getBoolean(TAG_HA);
      String s = isHA ? "yes" : "no";
      PokemonProperties.Companion.parse("hiddenability=" + s).apply(pokemon);
    }
    egg.getPersistentData().remove(TAG);
    egg.getPersistentData().remove(TAG_HA);
  }

  private Ability getHa(Pokemon pokemon) {
    if (pokemon == null)
      return null;
    for (PotentialAbility ability : pokemon.getForm().getAbilities()) {
      if (ability.getType() instanceof HiddenAbilityType) {
        return ability.getTemplate().create(false, Priority.NORMAL);
      }
    }
    return null;
  }

  @Override
  public void createEgg(ServerPlayerEntity player, Pokemon pokemon, Pokemon egg) {
    boolean isHA = PokemonUtils.isAH(pokemon);
    egg.getPersistentData().putString(TAG, pokemon.getAbility().getName());
    egg.getPersistentData().putBoolean(TAG_HA, isHA);
  }

  @Override
  public String getEggInfo(String s, NbtCompound nbt) {
    String abilityStr = nbt.getString(TAG);
    if (abilityStr.isEmpty())
      return s;

    AbilityTemplate template = Abilities.get(abilityStr);
    if (template == null) {
      template = Abilities.get(abilityStr.toLowerCase());
    }

    String translatedAbility = abilityStr;
    if (template != null) {
      Ability abilityObj = template.create(false, Priority.NORMAL);
      if (abilityObj != null) {
        translatedAbility = PokemonUtils.getAbilityTranslate(abilityObj);
      }
    }

    String ha = nbt.getBoolean(TAG_HA) ? CobbleUtils.language.getHA() : "";
    return s
        .replace("%ability%", translatedAbility)
        .replace("%ha%", ha)
        .replace("%ah%", ha);
  }

  @Override
  public void validateData() {
  }

  @Override
  public String fileName() {
    return TAG;
  }
}
