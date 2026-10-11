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
 * Menu to select a caretaker Pokémon for an incubator slot.
 *
 * @author Carlos Varas Alonso
 */
@Data
public class SelectCaretakerMenu {
  private static final int POKEMONS_PER_PAGE = 45;

  private final int rows;
  private final String title;
  private final Rectangle rectangle;
  private final ItemModel previous;
  private final ItemModel close;
  private final ItemModel next;
  private final List<PanelsConfig> panels;

  public SelectCaretakerMenu() {
    this.rows = 6;
    this.title = "&6Select Caretaker Pokémon";
    this.rectangle = new Rectangle(rows);
    this.previous = new ItemModel(45, "minecraft:arrow", "&6Previous", Collections.emptyList(), 1);
    this.close = new ItemModel(49, "minecraft:barrier", "&cClose", Collections.emptyList(), 1);
    this.next = new ItemModel(53, "minecraft:arrow", "&6Next", Collections.emptyList(), 1);
    this.panels = new ArrayList<>();
    panels.add(new PanelsConfig(new ItemModel("minecraft:orange_stained_glass_pane"), rows));
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
        previous.applyTemplate(template, previous.getButton(action -> open(player, slot, user, Math.max(0, position - POKEMONS_PER_PAGE))));
      }

      if (buttons.size() > POKEMONS_PER_PAGE + position) {
        next.applyTemplate(template, next.getButton(action -> open(player, slot, user, position + POKEMONS_PER_PAGE)));
      }

      GooeyPage page = GooeyPage.builder()
          .template(template)
          .title(AdventureTranslator.toNative(title))
          .build();

      List<Button> sublist = buttons.subList(position, Math.min(buttons.size(), position + POKEMONS_PER_PAGE));
      for (int i = 0; i < sublist.size(); i++) {
        template.set(i, sublist.get(i));
      }

      CobbleUtils.server.execute(() -> UIManager.openUIForcefully(player, page));
    });
  }

  private List<Button> getButtons(IncubatorSlot slot, ServerPlayerEntity player, User user) {
    List<Button> buttons = new ArrayList<>();

    for (Pokemon pokemon : Cobblemon.INSTANCE.getStorage().getParty(player)) {
      if (isValidCaretakerPokemon(pokemon)) {
        addPokemon(pokemon, slot, player, user, buttons);
      }
    }

    var pc = Cobblemon.INSTANCE.getStorage().getPC(player);
    for (Pokemon pokemon : pc) {
      if (isValidCaretakerPokemon(pokemon)) {
        addPokemon(pokemon, slot, player, user, buttons);
      }
    }

    return buttons;
  }

  private boolean isValidCaretakerPokemon(Pokemon pokemon) {
    if (pokemon == null || pokemon.getAbility() == null || "egg".equalsIgnoreCase(pokemon.showdownId())) {
      return false;
    }
    String ability = pokemon.getAbility().getName().replace(" ", "").toLowerCase();
    return UltraDaycare.config.getIncubators().getCaretakerAbilities().contains(ability);
  }

  private void addPokemon(@NotNull Pokemon pokemon, IncubatorSlot slot, ServerPlayerEntity player,
                          User user, List<Button> buttons) {
    ItemStack display = PokemonItem.from(pokemon);
    List<String> lore = new ArrayList<>(PokemonUtils.replaceLore(pokemon));

    // Calculate caretaker speed multiplier preview
    int sum = 0;
    for (var stat : com.cobblemon.mod.common.api.pokemon.stats.Stats.values()) {
      if (stat.ordinal() < 6) {
        sum += pokemon.getIvs().getOrDefault(stat);
      }
    }
    double avgIv = sum / 6.0;
    double ratio = Math.max(0.0, Math.min(1.0, avgIv / 31.0));
    double min = UltraDaycare.config.getIncubators().getMinIvMultiplier();
    double max = UltraDaycare.config.getIncubators().getMaxIvMultiplier();
    double multiplier = min + (ratio * (max - min));

    lore.add("");
    lore.add("&6Caretaker Ability: &e" + (pokemon.getAbility() != null ? pokemon.getAbility().getName() : "None"));
    lore.add("&7Average IVs: &e" + String.format("%.1f", avgIv) + " / 31.0");
    lore.add("&aHatch Speed Multiplier: &e" + String.format("%.2f", multiplier) + "x");
    lore.add("&eClick to assign as Caretaker!");

    GooeyButton button = GooeyButton.builder()
        .display(display)
        .with(DataComponentTypes.CUSTOM_NAME, AdventureTranslator.toNative(PokemonUtils.getTranslatedName(pokemon)))
        .with(DataComponentTypes.LORE, new LoreComponent(AdventureTranslator.toNativeL(lore)))
        .onClick(action -> UltraDaycare.getAsyncContext().runAsync(() -> {
          // If slot already had a caretaker, return it to party
          if (slot.getCaretaker() != null) {
            Pokemon old = slot.getCaretaker();
            CobbleUtils.server.execute(() -> Cobblemon.INSTANCE.getStorage().getParty(player).add(old));
          }

          slot.setCaretaker(pokemon);
          slot.recalculateRequirements();
          user.markDirty();

          CobbleUtils.server.execute(() -> {
            Cobblemon.INSTANCE.getStorage().getParty(player).remove(pokemon);
            Cobblemon.INSTANCE.getStorage().getPC(player).remove(pokemon);
          });

          UltraDaycare.language.getIncubatorsMenu().open(player, user);
        }))
        .build();

    buttons.add(button);
  }
}
