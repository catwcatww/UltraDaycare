package com.kingpixel.ultradaycare.ui;

import ca.landonjw.gooeylibs2.api.UIManager;
import ca.landonjw.gooeylibs2.api.button.RateLimitedButton;
import ca.landonjw.gooeylibs2.api.page.GooeyPage;
import ca.landonjw.gooeylibs2.api.template.types.ChestTemplate;
import com.cobblemon.mod.common.battles.BattleRegistry;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.kingpixel.cobbleutils.CobbleUtils;
import com.kingpixel.cobbleutils.Model.ItemModel;
import com.kingpixel.cobbleutils.util.AdventureTranslator;
import com.kingpixel.cobbleutils.util.PlayerUtils;
import com.kingpixel.cobbleutils.util.PokemonUtils;
import com.kingpixel.ultradaycare.UltraDaycare;
import com.kingpixel.ultradaycare.mechanics.Mechanics;
import com.kingpixel.ultradaycare.models.Plot;
import com.kingpixel.ultradaycare.models.User;
import lombok.Data;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * @author Carlos Varas Alonso - 11/03/2025 4:11
 */
@Data
public class PrincipalMenu {
  private int rows;
  private String title;
  private ItemModel info;
  private List<String> lore;
  private ItemModel plotWithEgg;
  private ItemModel plotWithOutEgg;
  private ItemModel plotWithOutParents;
  private ItemModel blockedPlot;
  private ItemModel profileOptions;
  private ItemModel incubatorsButton;
  private ItemModel close;

  public PrincipalMenu() {
    this.rows = 3;
    this.title = "&6Plots Menu";
    this.info = new ItemModel(5, "minecraft:book", "<#82d448>ɪɴꜰᴏʀᴍᴀᴛɪᴏɴ", List.of(
      "<#82d448>--- ᴀʙɪʟɪᴛʏ ---",
      "&7Transmit Ah: &6%ability% %activeAbility%",
      "&7Ditto Transmit: %activeDitto%",
      "<#82d448>---- ɪᴠꜱ ----",
      "&7Max Ivs Random: &6%maxivs%",
      "&7Destiny Knot: &6%destinyknot%",
      "&7Power Item: &6%poweritem%",
      "<#82d448>---- ɴᴀᴛᴜʀᴇ ----",
      "&7Ever Stone: &6%everstone%",
      "<#82d448>---- ꜱʜɪɴʏ ----",
      "&7Masuda: &6%masuda% &7Multiplier: &6%multipliermasuda%",
      "&7Shiny: &6%parentsShiny% &7Multiplier: &6%multipliershiny%",
      "&7ShinyRate: &6%shinyrate%",
      "<#82d448>---- ᴍᴏᴠᴇꜱ ----",
      "&7Egg Moves: &6%eggmoves%",
      "&7Mirror Herb: &6%mirrorherb%",
      "<#82d448>---- ᴄᴏᴏʟᴅᴏᴡɴ ----",
      "&7Cooldown: &6%cooldown%"
    ), 0);
    this.lore = List.of(
      "&7Male: %pokemon1% %form1% (%item1%)",
      "&7Female: %pokemon2% %form2% (%item2%)",
      "&7Eggs: %eggs%/%limiteggs%",
      "&7Time to hatch: %time%"
    );
    this.plotWithEgg = new ItemModel("item:1:minecraft:turtle_egg", "&6Plot with egg", List.of(
    ), 0);
    this.plotWithOutEgg = new ItemModel("item:1:cobblemon:pasture", "&6Plot without egg", List.of(

    ), 0);
    this.plotWithOutParents = new ItemModel("item:1:minecraft:gunpowder", "&6Plot without parents", List.of(

    ), 0);
    this.blockedPlot = new ItemModel("item:1:minecraft:barrier", "&cBlocked Plot", List.of(
      "&7Buy a rank to unlock this plot"
    ), 0);
    this.close = new ItemModel(22, "item:1:minecraft:barrier", "&cClose", List.of(
      "&7Click to close the menu"
    ), 0);
    this.profileOptions = new ItemModel(3, "item:1:minecraft:player_head", "&6Profile Options", List.of(
      "&7Click to open the profile options"
    ), 0);
    this.incubatorsButton = new ItemModel(21, "item:1:minecraft:furnace", "&6Incubators Hub", List.of(
      "&7Click to open the Incubators menu."
    ), 0);
  }

  private boolean isBattle(ServerPlayerEntity player) {
    var battle = BattleRegistry.getBattleByParticipatingPlayer(player);
    return battle != null;
  }

  public void open(ServerPlayerEntity player) {
    if (isBattle(player) || UltraDaycare.config.hasOpenCooldown(player)) return;
    UltraDaycare.getAsyncContext().runAsync(() -> {
      ChestTemplate template = ChestTemplate.builder(rows).build();
      User user = UltraDaycare.database.getUser(player);
      if (user == null) return;
      int slotSize = UltraDaycare.config.getSlotPlots().size();
      int plotSize = user.getPlots().size();

      for (int i = 0; i < slotSize; i++) {
        int slot = UltraDaycare.config.getSlotPlots().get(i);

        if (!UltraDaycare.hasPermission(player, Plot.plotPermission(i), 2) || i >= plotSize) {
          template.set(slot, blockedPlot.getButton(1, null, null, action -> {
          }, 1, TimeUnit.SECONDS, 1));
          continue;
        }

        Plot plot = user.getPlots().get(i);
        if (plot == null) {
          template.set(slot, blockedPlot.getButton(1, null, null, action -> {
          }, 1, TimeUnit.SECONDS, 1));
          continue;
        }

        ItemModel itemModel;
        if (plot.hasEggs()) {
          itemModel = plotWithEgg;
        } else if (plot.notParents()) {
          itemModel = plotWithOutParents;
        } else {
          itemModel = plotWithOutEgg;
        }

        template.set(slot, itemModel.getButton(
          plot.getEggs().size(),
          null,
          replacePlotLore(plot, player),
          action -> UltraDaycare.language.getPlotMenu().open(player, plot, user)
          , 1, TimeUnit.SECONDS, 1));
      }


      List<String> loreInfo = new ArrayList<>(info.getLore());
      long cooldown = System.currentTimeMillis() + PlayerUtils.getCooldown(UltraDaycare.config.getCooldowns(), UltraDaycare.config.getCooldown()
        , player);
      loreInfo.replaceAll(s -> {
        for (Mechanics mechanic : UltraDaycare.mechanics) {
          s = mechanic.replace(s, player);
        }
        s = s.replace("%cooldown%", PlayerUtils.getCooldown(cooldown));
        return s;
      });

      info.applyTemplate(template, info.getButton(1, null, loreInfo, action -> {
      }, 1, TimeUnit.SECONDS, 1));

      close.applyTemplate(template, close.getButton(action -> UIManager.closeUI(player), 1, TimeUnit.SECONDS, 1));


      RateLimitedButton profileButton = profileOptions.getButton(action -> UltraDaycare.language.getProfileMenu().open(player, user), 1, TimeUnit.SECONDS, 1);

      if (profileOptions.getItem().contains("minecraft:player_head")) {
        ItemStack headItem = PlayerUtils.getHeadItem(player);
        headItem.set(DataComponentTypes.CUSTOM_NAME, AdventureTranslator.toNative(profileOptions.getDisplayname()));
        headItem.set(DataComponentTypes.LORE,
          new LoreComponent(AdventureTranslator.toNativeL(profileOptions.getLore())));
        profileButton.setDisplay(headItem);
      }

      profileOptions.applyTemplate(template, profileButton);

      if (UltraDaycare.config.getIncubators().isEnabled()) {
        incubatorsButton.applyTemplate(template, incubatorsButton.getButton(
            action -> UltraDaycare.language.getIncubatorsMenu().open(player, user),
            1, TimeUnit.SECONDS, 1
        ));
      }

      GooeyPage page = GooeyPage.builder()
        .template(template)
        .title(AdventureTranslator.toNative(title))
        .build();

      CobbleUtils.server.execute(() -> UIManager.openUIForcefully(player, page));
    });
  }

  private List<String> replacePlotLore(Plot plot, ServerPlayerEntity player) {
    List<String> newLore = new ArrayList<>(lore);

    String cooldown = PlayerUtils.getCooldown(plot.getTimeToHatch());
    Pokemon male = plot.getMale();
    Pokemon female = plot.getFemale();
    List<Pokemon> parents = new ArrayList<>();
    parents.add(male);
    parents.add(female);

    newLore.replaceAll(s -> {
      s = s
        .replace("%eggs%", String.valueOf(plot.getEggs().size()))
        .replace("%limiteggs%", String.valueOf(plot.limitEggs(player)))
        .replace("%time%", cooldown)
        .replace("%cooldown%", cooldown);
      return PokemonUtils.replace(s, parents);
    });

    return newLore;
  }


}
