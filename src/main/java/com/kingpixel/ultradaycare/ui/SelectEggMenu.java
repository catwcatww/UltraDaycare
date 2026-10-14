package com.kingpixel.ultradaycare.ui;

import ca.landonjw.gooeylibs2.api.UIManager;
import ca.landonjw.gooeylibs2.api.button.Button;
import ca.landonjw.gooeylibs2.api.button.GooeyButton;
import ca.landonjw.gooeylibs2.api.page.GooeyPage;
import ca.landonjw.gooeylibs2.api.template.types.ChestTemplate;
import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.item.PokemonItem;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.kingpixel.cobbleutils.CobbleUtils;
import com.kingpixel.cobbleutils.Model.ItemModel;
import com.kingpixel.cobbleutils.Model.PanelsConfig;
import com.kingpixel.cobbleutils.Model.Rectangle;
import com.kingpixel.cobbleutils.util.AdventureTranslator;
import com.kingpixel.cobbleutils.util.PokemonUtils;
import com.kingpixel.ultradaycare.UltraDaycare;
import com.kingpixel.ultradaycare.mechanics.pokemon.DayCarePokemon;
import com.kingpixel.ultradaycare.models.IncubatorSlot;
import com.kingpixel.ultradaycare.models.User;
import lombok.Data;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Menu to select an Egg to place into an incubator slot.
 *
 * @author Carlos Varas Alonso
 */
@Data
public class SelectEggMenu {
  private static final int EGGS_PER_PAGE = 45;

  private final int rows;
  private final String title;
  private final Rectangle rectangle;
  private final ItemModel previous;
  private final ItemModel close;
  private final ItemModel next;
  private final List<PanelsConfig> panels;

  public SelectEggMenu() {
    this.rows = 6;
    this.title = "&6Select Egg for Incubation";
    this.rectangle = new Rectangle(rows);
    this.previous = new ItemModel(45, "minecraft:arrow", "&6Previous", Collections.emptyList(), 1);
    this.close = new ItemModel(49, "minecraft:barrier", "&cClose", Collections.emptyList(), 1);
    this.next = new ItemModel(53, "minecraft:arrow", "&6Next", Collections.emptyList(), 1);
    this.panels = new ArrayList<>();
    panels.add(new PanelsConfig(new ItemModel("minecraft:yellow_stained_glass_pane"), rows));
  }

  public void open(ServerPlayerEntity player, IncubatorSlot slot, User user, int position) {
    if (UltraDaycare.config.hasOpenCooldown(player)) return;
    UltraDaycare.getAsyncContext().runAsync(() -> {
      ChestTemplate template = ChestTemplate.builder(rows).build();
      PanelsConfig.applyConfig(template, panels);
      rectangle.apply(template);

      List<Button> buttons = getButtons(slot, player, user);

      close.applyTemplate(template, close.getButton(action -> UltraDaycare.language.getIncubatorsMenu().open(player, user)));

      if (position > 0) {
        previous.applyTemplate(template, previous.getButton(action -> open(player, slot, user, Math.max(0, position - EGGS_PER_PAGE))));
      }

      if (buttons.size() > EGGS_PER_PAGE + position) {
        next.applyTemplate(template, next.getButton(action -> open(player, slot, user, position + EGGS_PER_PAGE)));
      }

      GooeyPage page = GooeyPage.builder()
          .template(template)
          .title(AdventureTranslator.toNative(title))
          .build();

      List<Button> sublist = buttons.subList(position, Math.min(buttons.size(), position + EGGS_PER_PAGE));
      for (int i = 0; i < sublist.size(); i++) {
        template.set(i, sublist.get(i));
      }

      CobbleUtils.server.execute(() -> UIManager.openUIForcefully(player, page));
    });
  }

  private List<Button> getButtons(IncubatorSlot slot, ServerPlayerEntity player, User user) {
    List<Button> buttons = new ArrayList<>();

    // 1. Stored eggs from daycare / incubator inventory
    if (user.getStoredEggs() != null) {
      for (Pokemon egg : new ArrayList<>(user.getStoredEggs())) {
        if (egg != null && "egg".equalsIgnoreCase(egg.showdownId())) {
          addEggButton(egg, slot, player, user, buttons, true);
        }
      }
    }

    // 2. Party eggs
    for (Pokemon egg : Cobblemon.INSTANCE.getStorage().getParty(player)) {
      if (egg != null && "egg".equalsIgnoreCase(egg.showdownId())) {
        addEggButton(egg, slot, player, user, buttons, false);
      }
    }

    // 3. PC eggs
    for (Pokemon egg : Cobblemon.INSTANCE.getStorage().getPC(player)) {
      if (egg != null && "egg".equalsIgnoreCase(egg.showdownId())) {
        addEggButton(egg, slot, player, user, buttons, false);
      }
    }

    return buttons;
  }

  private void addEggButton(@NotNull Pokemon egg, IncubatorSlot slot, ServerPlayerEntity player,
                            User user, List<Button> buttons, boolean isFromStorage) {
    ItemStack display = PokemonItem.from(egg);
    List<String> lore = new ArrayList<>();
    String speciesName = egg.getPersistentData().getString(DayCarePokemon.TAG_POKEMON);
    if (speciesName.isEmpty()) {
      speciesName = "Unknown Species";
    }
    lore.add("&7Species: &6" + speciesName);
    lore.add("");
    lore.add("&eClick to place in Incubator!");

    GooeyButton button = GooeyButton.builder()
        .display(display)
        .with(DataComponentTypes.CUSTOM_NAME, AdventureTranslator.toNative("&6Pokémon Egg (" + speciesName + ")"))
        .with(DataComponentTypes.LORE, new LoreComponent(AdventureTranslator.toNativeL(lore)))
        .onClick(action -> UltraDaycare.getAsyncContext().runAsync(() -> {
          // If slot already had an egg, return it to storage
          if (slot.getEgg() != null) {
            Pokemon oldEgg = slot.getEgg();
            user.getStoredEggs().add(oldEgg);
          }

          slot.setEgg(egg);
          slot.resetProgress();
          slot.recalculateRequirements();

          if (isFromStorage) {
            user.getStoredEggs().remove(egg);
          } else {
            CobbleUtils.server.execute(() -> {
              Cobblemon.INSTANCE.getStorage().getParty(player).remove(egg);
              Cobblemon.INSTANCE.getStorage().getPC(player).remove(egg);
            });
          }

          user.markDirty();
          UltraDaycare.language.getIncubatorsMenu().open(player, user);
        }))
        .build();

    buttons.add(button);
  }
}
