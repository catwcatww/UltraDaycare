package com.kingpixel.ultradaycare.commands.base;

import com.kingpixel.cobbleutils.api.PermissionApi;
import com.kingpixel.ultradaycare.UltraDaycare;
import com.kingpixel.ultradaycare.models.User;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.List;

/**
 * Command to directly open the Incubators GUI.
 *
 * @author Carlos Varas Alonso
 */
public class CommandIncubators {

  public static void register(CommandDispatcher<ServerCommandSource> dispatcher,
                              LiteralArgumentBuilder<ServerCommandSource> base) {
    dispatcher.register(
        base
            .requires(source -> PermissionApi.hasPermission(source,
                List.of("ultradaycare.incubators", "ultradaycare.user", "cobbledaycare.user"), 2))
            .executes(context -> {
              if (context.getSource().isExecutedByPlayer()) {
                ServerPlayerEntity player = context.getSource().getPlayer();
                User user = UltraDaycare.database.getUser(player);
                if (user != null) {
                  UltraDaycare.language.getIncubatorsMenu().open(player, user);
                }
              }
              return 1;
            })
            .then(
                CommandManager.literal("other")
                    .requires(source -> PermissionApi.hasPermission(source,
                        List.of("ultradaycare.admin", "cobbledaycare.admin"), 4))
                    .then(
                        CommandManager.argument("player", EntityArgumentType.player())
                            .executes(context -> {
                              ServerPlayerEntity target = EntityArgumentType.getPlayer(context, "player");
                              User user = UltraDaycare.database.getUser(target);
                              if (user != null && context.getSource().isExecutedByPlayer()) {
                                ServerPlayerEntity admin = context.getSource().getPlayer();
                                UltraDaycare.language.getIncubatorsMenu().open(admin, user);
                              }
                              return 1;
                            })
                    )
            )
    );
  }
}
