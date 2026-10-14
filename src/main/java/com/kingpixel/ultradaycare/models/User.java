package com.kingpixel.ultradaycare.models;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.api.storage.party.PlayerPartyStore;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.kingpixel.cobbleutils.CobbleUtils;
import com.kingpixel.cobbleutils.util.PlayerUtils;
import com.kingpixel.cobbleutils.util.TypeMessage;
import com.kingpixel.cobbleutils.util.UtilsFile;
import com.kingpixel.ultradaycare.UltraDaycare;
import com.kingpixel.ultradaycare.mechanics.pokemon.DayCarePokemon;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import net.minecraft.server.network.ServerPlayerEntity;
import org.bson.Document;

import java.util.ArrayList;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @author Carlos Varas Alonso - 31/01/2025 1:16
 */
@Getter
@Setter
@Data
@ToString
public class User {
  private UUID playerUUID;
  private String playerName;
  private String country;
  private long connectedTime;
  private boolean notifyCreateEgg;
  private boolean notifyLimitEggs;
  private boolean notifyBanPokemon;
  private boolean actionBar;
  private float multiplierSteps;
  private long timeMultiplierSteps;
  private long cooldownHatch;
  private long cooldownBreed;
  private long lastActiveTime;
  private ArrayList<Plot> plots;
  private ArrayList<IncubatorSlot> incubatorSlots;
  private ArrayList<Pokemon> storedEggs;
  private int dailyPurchasedIncubators;
  private long lastDailyResetTimestamp;
  private transient AtomicBoolean dirty = new AtomicBoolean(false);

  public User() {
    this.playerUUID = null;
    this.playerName = null;
    this.multiplierSteps = 1.0f;
    this.timeMultiplierSteps = 0;
    this.cooldownHatch = 0;
    this.cooldownBreed = 0;
    this.lastActiveTime = System.currentTimeMillis();
    this.lastDailyResetTimestamp = System.currentTimeMillis();
    this.dailyPurchasedIncubators = 0;
    var userInfoOptions = UltraDaycare.config.getUserInfoOptions();
    this.notifyBanPokemon = userInfoOptions.isNotifyBanPokemon();
    this.notifyCreateEgg = userInfoOptions.isNotifyCreateEgg();
    this.actionBar = userInfoOptions.isActionBar();
    this.plots = new ArrayList<>();
    this.incubatorSlots = new ArrayList<>();
    this.storedEggs = new ArrayList<>();
    this.dirty = new AtomicBoolean(false);
  }

  public User(ServerPlayerEntity player) {
    super();
    this.playerUUID = player.getUuid();
    this.playerName = player.getGameProfile().getName();
  }

  public synchronized static User fromDocument(Document document) {
    return UtilsFile.getGson().fromJson(document.toJson(), User.class);
  }

  public AtomicBoolean getDirty() {
    if (dirty == null) dirty = new AtomicBoolean(false);
    return dirty;
  }

  public void markDirty() {
    getDirty().set(true);
  }

  public synchronized Document toDocument() {
    return Document.parse(UtilsFile.getGson().toJson(this));
  }

  public float getActualMultiplier(ServerPlayerEntity player) {
    float multiplier = UltraDaycare.config.getMultiplierSteps();
    for (Map.Entry<String, Float> entry : UltraDaycare.config.getMultiplierStepsPermission().entrySet()) {
      if (entry.getValue() <= multiplier) continue;
      if (UltraDaycare.hasPermission(player, entry.getKey(), 2)) {
        multiplier = entry.getValue();
      }
    }
    return Math.max(multiplier, this.multiplierSteps);
  }

  public boolean hasCooldownHatch(ServerPlayerEntity player) {
    if (UltraDaycare.hasPermission(player, "ultradaycare.hatch.bypass", 4)) return false;
    return cooldownHatch > System.currentTimeMillis();
  }

  public void setCooldownHatch(ServerPlayerEntity player) {
    this.cooldownHatch = System.currentTimeMillis() +
      PlayerUtils.getCooldown(UltraDaycare.config.getCooldownsHatch(),
        UltraDaycare.config.getDefaultCooldownHatch(), player);
  }

  public boolean hasCooldownBreed(ServerPlayerEntity player) {
    if (UltraDaycare.hasPermission(player, "ultradaycare.breed.bypass", 4)) return false;
    return cooldownBreed > System.currentTimeMillis();
  }

  public void setCooldownBreed(ServerPlayerEntity player) {
    this.cooldownBreed =
      System.currentTimeMillis() + PlayerUtils.getCooldown(UltraDaycare.config.getCooldownsBreed(),
        UltraDaycare.config.getDefaultCooldownBreed(), player);
  }

  public synchronized boolean check(int numPlots, ServerPlayerEntity player) {
    if (player == null) throw new IllegalArgumentException("The player cannot be null.");

    boolean update = false;


    if (plots == null) {
      UltraDaycare.LOGGER.error("UserInformation plots was null for player " + player.getGameProfile().getName() + " fixing...");
      plots = new ArrayList<>();
      update = true;
    }

    String username = player.getGameProfile().getName();
    if (playerName == null || !playerName.equals(username)) {
      playerName = username;
      update = true;
    }

    if (playerUUID == null) {
      playerUUID = player.getUuid();
      update = true;
    }

    int currentSize = plots.size();

    if (currentSize < numPlots) {
      for (int i = currentSize; i < numPlots; i++) {
        plots.add(new Plot());
      }
      update = true;
    } else if (currentSize > numPlots) {
      var party = Cobblemon.INSTANCE.getStorage().getParty(player);
      for (int i = currentSize - 1; i >= numPlots; i--) {
        Plot plot = plots.remove(i);
        if (plot == null) continue;

        returnToParty(player, party, plot.getMale(), "male");
        returnToParty(player, party, plot.getFemale(), "female");
        plot.giveEggs(player);
      }
      update = true;
    }

    return update;
  }

  private void returnToParty(ServerPlayerEntity player, PlayerPartyStore party, Pokemon pokemon, String type) {
    if (pokemon == null) return;

    String name = type.equals("egg") ?
      pokemon.getPersistentData().getString(DayCarePokemon.TAG_POKEMON) :
      pokemon.getDisplayName(false).getString();

    PlayerUtils.sendMessage(
      player,
      "Your " + type + " " + name + " has been returned to your party because the plot has been removed.",
      UltraDaycare.language.getPrefix(),
      TypeMessage.CHAT
    );
    CobbleUtils.server.execute(() -> party.add(pokemon));
  }


  public synchronized boolean checkIncubators(int numSlots, ServerPlayerEntity player) {
    if (player == null) throw new IllegalArgumentException("The player cannot be null.");

    boolean update = false;
    checkDailyReset();

    if (incubatorSlots == null) {
      incubatorSlots = new ArrayList<>();
      update = true;
    }
    if (storedEggs == null) {
      storedEggs = new ArrayList<>();
      update = true;
    }

    int currentSize = incubatorSlots.size();
    if (currentSize < numSlots) {
      for (int i = currentSize; i < numSlots; i++) {
        incubatorSlots.add(new IncubatorSlot(i));
      }
      update = true;
    } else if (currentSize > numSlots) {
      var party = Cobblemon.INSTANCE.getStorage().getParty(player);
      for (int i = currentSize - 1; i >= numSlots; i--) {
        IncubatorSlot slot = incubatorSlots.remove(i);
        if (slot == null) continue;
        if (slot.getCaretaker() != null) {
          returnToParty(player, party, slot.getCaretaker(), "caretaker");
          slot.setCaretaker(null);
        }
        if (slot.getEgg() != null) {
          storedEggs.add(slot.getEgg());
          slot.setEgg(null);
        }
      }
      update = true;
    }
    return update;
  }

  public synchronized void checkDailyReset() {
    long now = System.currentTimeMillis();
    // 24 hours in millis = 86,400,000
    if (now - lastDailyResetTimestamp >= 86_400_000L) {
      dailyPurchasedIncubators = 0;
      lastDailyResetTimestamp = now;
      markDirty();
    }
  }

  public synchronized int getDailyLimit(ServerPlayerEntity player) {
    int limit = 1;
    for (Map.Entry<String, Integer> entry : UltraDaycare.config.getIncubators().getDailyLimits().entrySet()) {
      if (limit > entry.getValue()) continue;
      if (entry.getKey().isEmpty() || UltraDaycare.hasPermission(player, entry.getKey(), 4)) {
        limit = entry.getValue();
      }
    }
    return limit;
  }

  public synchronized boolean canBuyIncubator(ServerPlayerEntity player) {
    checkDailyReset();
    return dailyPurchasedIncubators < getDailyLimit(player);
  }

  public synchronized void recordIncubatorPurchase() {
    checkDailyReset();
    dailyPurchasedIncubators++;
    markDirty();
  }

  public synchronized boolean fix(ServerPlayerEntity player) {
    boolean update = false;
    if (plots == null) {
      UltraDaycare.LOGGER.error("UserInformation plots was null for player " + player.getGameProfile().getName() + " fixing...");
      plots = new ArrayList<>();
      update = true;
    }
    if (incubatorSlots == null) {
      incubatorSlots = new ArrayList<>();
      update = true;
    }
    if (storedEggs == null) {
      storedEggs = new ArrayList<>();
      update = true;
    }
    checkDailyReset();

    for (Plot plot : plots) {
      var eggsIterator = plot.getEggs().iterator();
      while (eggsIterator.hasNext()) {
        Pokemon egg = eggsIterator.next();
        if (egg == null) {
          eggsIterator.remove();
          update = true;
        }
      }
      if (plot.checkEgg(player, this)) update = true;
    }
    return update;
  }

  public CharSequence getIndexPlot(Plot plot) {
    int index = plots.indexOf(plot) + 1;
    return index + "";
  }

  public CompletableFuture<Void> save() {
    if (getDirty().getAndSet(false)) {
      return UltraDaycare.database.saveOrUpdateUser(this);
    }
    return CompletableFuture.completedFuture(null);
  }
}
