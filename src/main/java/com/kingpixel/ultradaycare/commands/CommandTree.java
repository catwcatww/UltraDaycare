package com.kingpixel.ultradaycare.commands;

import com.kingpixel.cobbleutils.api.PermissionApi;
import com.kingpixel.ultradaycare.UltraDaycare;
import com.kingpixel.ultradaycare.commands.admin.*;
import com.kingpixel.ultradaycare.commands.base.CommandEggInfo;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.List;

/**
 * @author Carlos Varas Alonso - 31/01/2025 0:12
 */
public class CommandTree {
  public static void register(CommandDispatcher<ServerCommandSource> dispatcher, CommandRegistryAccess registry) {
    try {
      for (String command : UltraDaycare.config.getCommands()) {
        var base = CommandManager.literal(command)
          .requires(source -> PermissionApi.hasPermission(source, List.of("cobbledaycare.user",
              "cobbledaycare.admin"),
            4));
        if (!UltraDaycare.config.getCommandEggInfo().isEmpty()) {
          CommandEggInfo.register(dispatcher, CommandManager.literal(UltraDaycare.config.getCommandEggInfo()));
        }

        CommandHatch.register(dispatcher, CommandManager.literal("hatch"));
        CommandBreed.register(dispatcher, CommandManager.literal("breed"));

        if (UltraDaycare.config.getIncubators().isEnabled()) {
          for (String incCmd : UltraDaycare.config.getIncubators().getCommands()) {
            com.kingpixel.ultradaycare.commands.base.CommandIncubators.register(dispatcher, CommandManager.literal(incCmd));
          }
          com.kingpixel.ultradaycare.commands.base.CommandIncubators.register(dispatcher, CommandManager.literal("incubators"));
        }

        CommandReload.register(dispatcher, base);
        CommandIncense.register(dispatcher, base);
        CommandBoosterSteps.register(dispatcher, base);
        CommandBreedable.register(dispatcher, base);
        CommandEgg.register(dispatcher, base);
        CommandSteps.register(dispatcher, base);

        dispatcher.register(base
          .executes(context -> {
            if (context.getSource().isExecutedByPlayer()) {
              ServerPlayerEntity player = context.getSource().getPlayer();
              UltraDaycare.language.getPrincipalMenu().open(player);
            }
            return 1;
          }).then(
            CommandManager.literal("other")
              .requires(source -> PermissionApi.hasPermission(source, List.of("cobbledaycare.admin", "cobbledaycare" + ".other"), 2))
              .then(
                CommandManager.argument("player", EntityArgumentType.player())
                  .executes(context -> {
                    ServerPlayerEntity player = EntityArgumentType.getPlayer(context, "player");
                    UltraDaycare.language.getPrincipalMenu().open(player);
                    return 1;
                  })
              )
          )
        );
      }

    } catch (Exception e) {
      UltraDaycare.LOGGER.error("Error registering commands: ", e);
    }
  }

}
