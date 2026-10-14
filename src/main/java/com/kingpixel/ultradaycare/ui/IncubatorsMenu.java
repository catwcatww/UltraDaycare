package com.kingpixel.ultradaycare.ui;

import ca.landonjw.gooeylibs2.api.UIManager;
import ca.landonjw.gooeylibs2.api.button.GooeyButton;
import ca.landonjw.gooeylibs2.api.page.GooeyPage;
import ca.landonjw.gooeylibs2.api.template.types.ChestTemplate;
import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.item.PokemonItem;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.kingpixel.cobbleutils.CobbleUtils;
import com.kingpixel.cobbleutils.Model.ItemModel;
import com.kingpixel.cobbleutils.api.EconomyApi;
import com.kingpixel.cobbleutils.util.AdventureTranslator;
import com.kingpixel.cobbleutils.util.PlayerUtils;
import com.kingpixel.cobbleutils.util.PokemonUtils;
import com.kingpixel.cobbleutils.util.TypeMessage;
import com.kingpixel.ultradaycare.UltraDaycare;
import com.kingpixel.ultradaycare.config.IncubatorConfig;
import com.kingpixel.ultradaycare.config.IncubatorHatchMode;
import com.kingpixel.ultradaycare.mechanics.pokemon.DayCarePokemon;
import com.kingpixel.ultradaycare.models.EggData;
import com.kingpixel.ultradaycare.models.IncubatorSlot;
import com.kingpixel.ultradaycare.models.User;
import lombok.Data;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Main GUI menu for the Incubators Module.
 *
 * @author Carlos Varas Alonso
 */
@Data
public class IncubatorsMenu {
  private int rows;
  private String title;
  private ItemModel buyIncubator;
  private ItemModel slotBlocked;
  private ItemModel slotEmpty;
  private ItemModel slotWithoutEgg;
  private ItemModel slotWithoutCaretaker;
  private ItemModel slotIncubating;
  private ItemModel slotHatchReady;
  private ItemModel close;

  public IncubatorsMenu() {
    this.rows = 4;
    this.title = "&6Incubators Hub";

    this.buyIncubator = new ItemModel(4, "minecraft:beacon", "&b&lBuy Incubator", List.of(
        "&7Price: &6%price%",
        "&7Uses: &e%uses% hatches",
        "&7Daily limit: &a%purchased%&7/&a%limit%",
        "",
        "&eClick to purchase an incubator!"
    ), 1);

    this.slotBlocked = new ItemModel("minecraft:barrier", "&cLocked Slot", List.of(
        "&7You need a higher rank to unlock this incubator slot."
    ), 1);

    this.slotEmpty = new ItemModel("minecraft:glass", "&7Empty Slot", List.of(
        "&7No incubator installed.",
        "&ePurchase an incubator above to use this slot!"
    ), 1);

    this.slotWithoutEgg = new ItemModel("minecraft:furnace", "&6Incubator &7(&e%uses% uses left&7)", List.of(
        "&7Caretaker: &a%caretaker%",
        "&7Egg: &cNone",
        "",
        "&eLeft-Click to insert an Egg!",
        "&cRight-Click to remove Caretaker!"
    ), 1);

    this.slotWithoutCaretaker = new ItemModel("minecraft:furnace", "&6Incubator &7(&e%uses% uses left&7)", List.of(
        "&7Egg: &6%egg%",
        "&7Caretaker: &cNone &8(Requires Flame Body, Magma Armor, Steam Engine)",
        "",
        "&eLeft-Click to assign Caretaker!",
        "&cRight-Click to remove Egg!"
    ), 1);

    this.slotIncubating = new ItemModel("minecraft:turtle_egg", "&a&lIncubating &f(%percentage%%)", List.of(
        "&7Egg: &6%egg%",
        "&7Caretaker: &b%caretaker% &8(&e%multiplier%x speed&8)",
        "&7Progress: &a%progress_bar% &f%percentage%%",
        "&7Steps Remaining: &e%steps%",
        "&7Time Remaining: &e%time%",
        "",
        "&cShift-Right-Click to retrieve Caretaker or Egg."
    ), 1);

    this.slotHatchReady = new ItemModel("minecraft:dragon_egg", "&d&lEgg Ready to Hatch!", List.of(
        "&7Egg: &6%egg%",
        "&7Caretaker: &b%caretaker%",
        "&7Incubator uses remaining: &e%uses%",
        "",
        "&a&lClick to Hatch your Pokémon!"
    ), 1);

    this.close = new ItemModel(31, "minecraft:barrier", "&cBack to Daycare", List.of(
        "&7Click to return to the main Daycare menu."
    ), 1);
  }

  public void open(ServerPlayerEntity player, User user) {
    if (UltraDaycare.config.hasOpenCooldown(player)) return;

    UltraDaycare.getAsyncContext().runAsync(() -> {
      ChestTemplate template = ChestTemplate.builder(rows).build();

      IncubatorConfig config = UltraDaycare.config.getIncubators();
      int slotSize = config.getSlots().size();
      user.checkIncubators(slotSize, player);

      // 1. Buy Incubator Button
      addBuyButton(template, player, user, config);

      // 2. Incubator Slots
      for (int i = 0; i < slotSize; i++) {
        int guiSlot = config.getSlots().get(i);
        String perm = IncubatorConfig.incubatorSlotPermission(i);

        if (!UltraDaycare.hasPermission(player, perm, 2) || i >= user.getIncubatorSlots().size()) {
          template.set(guiSlot, slotBlocked.getButton(action -> {}, 1, TimeUnit.SECONDS, 1));
          continue;
        }

        IncubatorSlot slot = user.getIncubatorSlots().get(i);
        addSlotButton(template, player, user, slot, guiSlot, config);
      }

      // 3. Close Button
      close.applyTemplate(template, close.getButton(
          action -> UltraDaycare.language.getPrincipalMenu().open(player),
          1, TimeUnit.SECONDS, 1
      ));

      GooeyPage page = GooeyPage.builder()
          .template(template)
          .title(AdventureTranslator.toNative(title))
          .build();

      CobbleUtils.server.execute(() -> UIManager.openUIForcefully(player, page));
    });
  }

  private void addBuyButton(ChestTemplate template, ServerPlayerEntity player, User user, IncubatorConfig config) {
    user.checkDailyReset();
    int purchased = user.getDailyPurchasedIncubators();
    int limit = user.getDailyLimit(player);
    double price = config.getPrice();

    List<String> lore = buyIncubator.getLore().stream()
        .map(line -> line
            .replace("%price%", String.format("%.2f", price))
            .replace("%uses%", String.valueOf(config.getDefaultIncubatorUses()))
            .replace("%purchased%", String.valueOf(purchased))
            .replace("%limit%", String.valueOf(limit)))
        .toList();

    GooeyButton button = GooeyButton.builder()
        .display(buyIncubator.getItemStack())
        .with(DataComponentTypes.CUSTOM_NAME, AdventureTranslator.toNative(buyIncubator.getDisplayname()))
        .with(DataComponentTypes.LORE, new LoreComponent(AdventureTranslator.toNativeL(lore)))
        .onClick(action -> UltraDaycare.getAsyncContext().runAsync(() -> {
          if (!user.canBuyIncubator(player)) {
            PlayerUtils.sendMessage(player, "&cYou have reached your daily purchase limit for incubators.",
                UltraDaycare.language.getPrefix(), TypeMessage.CHAT);
            return;
          }

          // Find first available slot without incubator
          IncubatorSlot targetSlot = null;
          for (int i = 0; i < config.getSlots().size(); i++) {
            if (UltraDaycare.hasPermission(player, IncubatorConfig.incubatorSlotPermission(i), 2)
                && i < user.getIncubatorSlots().size()) {
              IncubatorSlot s = user.getIncubatorSlots().get(i);
              if (!s.isHasIncubator()) {
                targetSlot = s;
                break;
              }
            }
          }

          if (targetSlot == null) {
            PlayerUtils.sendMessage(player, "&cYou do not have any empty unlocked slots to place an incubator.",
                UltraDaycare.language.getPrefix(), TypeMessage.CHAT);
            return;
          }

          if (EconomyApi.hasEnoughMoney(player.getUuid(), BigDecimal.valueOf(price), config.getEconomyUse(), true)) {
            EconomyApi.removeMoney(player.getUuid(), BigDecimal.valueOf(price), config.getEconomyUse());
            user.recordIncubatorPurchase();
            targetSlot.setHasIncubator(true);
            targetSlot.setRemainingUses(config.getDefaultIncubatorUses());
            user.markDirty();
            user.save();

            PlayerUtils.sendMessage(player, "&aSuccessfully purchased an incubator with &e" + config.getDefaultIncubatorUses() + " uses&a!",
                UltraDaycare.language.getPrefix(), TypeMessage.CHAT);
            open(player, user);
          }
        }))
        .build();

    buyIncubator.applyTemplate(template, button);
  }

  private void addSlotButton(ChestTemplate template, ServerPlayerEntity player, User user,
                             IncubatorSlot slot, int guiSlot, IncubatorConfig config) {
    if (!slot.isHasIncubator()) {
      template.set(guiSlot, slotEmpty.getButton(action -> {}, 1, TimeUnit.SECONDS, 1));
      return;
    }

    IncubatorHatchMode mode = config.getHatchMode();

    if (slot.isHatchReady(mode)) {
      // Hatch Ready state
      ItemStack display = slot.getEgg() != null ? PokemonItem.from(slot.getEgg()) : slotHatchReady.getItemStack();
      String eggName = getEggSpecies(slot.getEgg());
      String caretakerName = slot.getCaretaker() != null ? slot.getCaretaker().getDisplayName(false).getString() : "None";

      List<String> lore = slotHatchReady.getLore().stream()
          .map(line -> line
              .replace("%egg%", eggName)
              .replace("%caretaker%", caretakerName)
              .replace("%uses%", String.valueOf(slot.getRemainingUses())))
          .toList();

      GooeyButton button = GooeyButton.builder()
          .display(display)
          .with(DataComponentTypes.CUSTOM_NAME, AdventureTranslator.toNative(slotHatchReady.getDisplayname()))
          .with(DataComponentTypes.LORE, new LoreComponent(AdventureTranslator.toNativeL(lore)))
          .onClick(action -> UltraDaycare.getAsyncContext().runAsync(() -> {
            Pokemon eggToHatch = slot.getEgg();
            slot.setEgg(null);
            slot.resetProgress();
            slot.setRemainingUses(slot.getRemainingUses() - 1);

            if (slot.getRemainingUses() <= 0) {
              slot.setHasIncubator(false);
              PlayerUtils.sendMessage(player, "&cYour incubator has broken after depleting its final use!",
                  UltraDaycare.language.getPrefix(), TypeMessage.CHAT);
            }

            user.markDirty();
            user.save();

            if (eggToHatch != null) {
              EggData.hatch(player, eggToHatch);
            }

            open(player, user);
          }))
          .build();

      template.set(guiSlot, button);
      return;
    }

    if (!slot.hasCaretaker()) {
      // Missing Caretaker
      String eggName = slot.hasEgg() ? getEggSpecies(slot.getEgg()) : "None";
      List<String> lore = slotWithoutCaretaker.getLore().stream()
          .map(line -> line
              .replace("%egg%", eggName)
              .replace("%uses%", String.valueOf(slot.getRemainingUses())))
          .toList();

      GooeyButton button = GooeyButton.builder()
          .display(slotWithoutCaretaker.getItemStack())
          .with(DataComponentTypes.CUSTOM_NAME, AdventureTranslator.toNative(slotWithoutCaretaker.getDisplayname().replace("%uses%", String.valueOf(slot.getRemainingUses()))))
          .with(DataComponentTypes.LORE, new LoreComponent(AdventureTranslator.toNativeL(lore)))
          .onClick(action -> UltraDaycare.language.getSelectCaretakerMenu().open(player, slot, user, 0))
          .build();

      template.set(guiSlot, button);
      return;
    }

    if (!slot.hasEgg()) {
      // Missing Egg
      String caretakerName = slot.getCaretaker().getDisplayName(false).getString();
      List<String> lore = slotWithoutEgg.getLore().stream()
          .map(line -> line
              .replace("%caretaker%", caretakerName)
              .replace("%uses%", String.valueOf(slot.getRemainingUses())))
          .toList();

      GooeyButton button = GooeyButton.builder()
          .display(slotWithoutEgg.getItemStack())
          .with(DataComponentTypes.CUSTOM_NAME, AdventureTranslator.toNative(slotWithoutEgg.getDisplayname().replace("%uses%", String.valueOf(slot.getRemainingUses()))))
          .with(DataComponentTypes.LORE, new LoreComponent(AdventureTranslator.toNativeL(lore)))
          .onClick(action -> UltraDaycare.language.getSelectEggMenu().open(player, slot, user, 0))
          .build();

      template.set(guiSlot, button);
      return;
    }

    // Actively Incubating
    String eggName = getEggSpecies(slot.getEgg());
    String caretakerName = slot.getCaretaker().getDisplayName(false).getString();
    int percentage = slot.getProgressPercentage(mode);
    String progressBar = getProgressBar(percentage);

    long remainingTimeMillis = Math.max(0, slot.getRequiredTimeMillis() - slot.getElapsedActiveTimeMillis());
    double remainingSteps = Math.max(0, slot.getRequiredSteps() - slot.getAccumulatedSteps());
    String timeStr = PlayerUtils.getCooldown(System.currentTimeMillis() + remainingTimeMillis);

    List<String> lore = slotIncubating.getLore().stream()
        .map(line -> line
            .replace("%egg%", eggName)
            .replace("%caretaker%", caretakerName)
            .replace("%multiplier%", String.format("%.2f", slot.getSpeedMultiplier()))
            .replace("%progress_bar%", progressBar)
            .replace("%percentage%", String.valueOf(percentage))
            .replace("%steps%", String.format("%.0f", remainingSteps))
            .replace("%time%", timeStr))
        .toList();

    ItemStack display = PokemonItem.from(slot.getEgg());
    GooeyButton button = GooeyButton.builder()
        .display(display)
        .with(DataComponentTypes.CUSTOM_NAME, AdventureTranslator.toNative(slotIncubating.getDisplayname().replace("%percentage%", String.valueOf(percentage))))
        .with(DataComponentTypes.LORE, new LoreComponent(AdventureTranslator.toNativeL(lore)))
        .onClick(action -> UltraDaycare.getAsyncContext().runAsync(() -> {
          // Retrieve Caretaker and Egg
          if (slot.hasCaretaker()) {
            Pokemon caretaker = slot.getCaretaker();
            slot.setCaretaker(null);
            CobbleUtils.server.execute(() -> Cobblemon.INSTANCE.getStorage().getParty(player).add(caretaker));
          }
          if (slot.hasEgg()) {
            Pokemon egg = slot.getEgg();
            slot.setEgg(null);
            user.getStoredEggs().add(egg);
          }
          slot.resetProgress();
          user.markDirty();
          user.save();
          PlayerUtils.sendMessage(player, "&eIncubation paused. Egg and caretaker were retrieved.",
              UltraDaycare.language.getPrefix(), TypeMessage.CHAT);
          open(player, user);
        }))
        .build();

    template.set(guiSlot, button);
  }

  private String getEggSpecies(Pokemon egg) {
    if (egg == null) return "Unknown";
    String species = egg.getPersistentData().getString(DayCarePokemon.TAG_POKEMON);
    return species.isEmpty() ? "Unknown Egg" : species;
  }

  private String getProgressBar(int percentage) {
    int totalBars = 10;
    int filledBars = (percentage * totalBars) / 100;
    return "&a" + "■".repeat(Math.max(0, filledBars)) + "&7" + "■".repeat(Math.max(0, totalBars - filledBars));
  }
}
