package com.kingpixel.ultradaycare.placeholders;

import com.kingpixel.cobbleutils.util.PlayerUtils;
import com.kingpixel.ultradaycare.UltraDaycare;
import eu.pb4.placeholders.api.Placeholders;
import eu.pb4.placeholders.api.PlaceholderResult;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

public class DaycarePlaceholders {

  public static void register() {
    registerBreedCooldown();
  }

  private static void registerBreedCooldown() {
    Placeholders.register(
      Identifier.of("daycare", "breed_cooldown"),
      (ctx, arg) -> {
        ServerPlayerEntity player = ctx.player();
        if (player == null) {
          return PlaceholderResult.value(Text.literal("Ready"));
        }

        var user = UltraDaycare.database.getUser(player);
        if (user == null || !user.hasCooldownBreed(player)) {
          return PlaceholderResult.value(Text.literal("Ready"));
        }

        long remaining = user.getCooldownBreed() - System.currentTimeMillis();
        if (remaining <= 0) {
          return PlaceholderResult.value(Text.literal("Ready"));
        }

        return PlaceholderResult.value(Text.literal(formatCooldown(remaining)));
      }
    );
  }

  private static String formatCooldown(long millis) {
    long totalSeconds = millis / 1000;
    long minutes = totalSeconds / 60;
    long seconds = totalSeconds % 60;
    return minutes + "m " + seconds + "s";
  }
}