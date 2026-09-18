package com.kingpixel.ultradaycare;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.api.Priority;
import com.cobblemon.mod.common.api.battles.model.actor.BattleActor;
import com.cobblemon.mod.common.api.events.CobblemonEvents;
import com.cobblemon.mod.common.api.pokemon.PokemonProperties;
import com.cobblemon.mod.common.api.properties.CustomPokemonProperty;
import com.cobblemon.mod.common.battles.actor.PlayerBattleActor;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.kingpixel.cobbleutils.CobbleUtils;
import com.kingpixel.cobbleutils.Model.CobbleUtilsTags;
import com.kingpixel.cobbleutils.api.PermissionApi;
import com.kingpixel.cobbleutils.util.PokemonUtils;
import com.kingpixel.cobbleutils.util.Utils;
import com.kingpixel.cobbleutils.util.UtilsLogger;
import com.kingpixel.cobbleutils.util.async.AsyncContext;
import com.kingpixel.cobbleutils.util.async.UtilsAsync;
import com.kingpixel.ultradaycare.api.DaycareMode;
import com.kingpixel.ultradaycare.api.DaycareRegistry;
import com.kingpixel.ultradaycare.api.PokeMMODaycareMode;
import com.kingpixel.ultradaycare.api.PokemonDaycareMode;
import com.kingpixel.ultradaycare.commands.CommandTree;
import com.kingpixel.ultradaycare.config.Config;
import com.kingpixel.ultradaycare.config.Language;
import com.kingpixel.ultradaycare.database.DatabaseClient;
import com.kingpixel.ultradaycare.database.DatabaseClientFactory;
import com.kingpixel.ultradaycare.mechanics.Mechanics;
import com.kingpixel.ultradaycare.mechanics.pokemon.*;
import com.kingpixel.ultradaycare.models.Plot;
import com.kingpixel.ultradaycare.models.User;
import com.kingpixel.ultradaycare.placeholders.DaycarePlaceholders;
import com.kingpixel.ultradaycare.properties.BreedablePropertyType;
import com.kingpixel.ultradaycare.tasks.TaskDayCare;
import com.kingpixel.ultradaycare.util.MigrationService;
import dev.architectury.event.events.common.CommandRegistrationEvent;
import dev.architectury.event.events.common.LifecycleEvent;
import dev.architectury.event.events.common.PlayerEvent;
import kotlin.Unit;
import net.fabricmc.api.ModInitializer;
import net.minecraft.server.network.ServerPlayerEntity;
import org.apache.logging.log4j.Logger;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * @author Carlos Varas Alonso - 23/07/2024 9:24
 */
public class UltraDaycare implements ModInitializer {
  public static final String MOD_ID = "ultradaycare";
  public static final String MOD_NAME = "UltraDaycare";
  public static final String TAG_SPAWNED = "spawned";
  public static final List<Mechanics> mechanics = new ArrayList<>();
  public static final Map<String, List<Mechanics>> modeMechanics = new HashMap<>();
  public static final Map<String, List<Mechanics>> registeredModeMechanics = new HashMap<>();
  public static final Logger LOGGER = UtilsLogger.getLogger(MOD_NAME);
  public static final String PATH = "/config/ultradaycare/";
  public static final String PATH_MODULES = PATH + "modules/";
  private static final String API_URL_IP = "http://ip-api.com/json/";
  private static final Map<UUID, UserInfo> playerCountry = new HashMap<>();
  private static final TaskDayCare TASK_DAY_CARE = new TaskDayCare();
  public static DatabaseClient database;
  public static Config config = new Config();
  public static Language language = new Language();
  private static Path path;

  public static void registerMechanic(String modeId, Mechanics mechanic) {
    if (mechanic != null) {
      List<Mechanics> list = registeredModeMechanics.computeIfAbsent(modeId.toLowerCase(), k -> new ArrayList<>());
      if (!list.contains(mechanic)) {
        list.add(mechanic);
      }
    }
  }

  public static void registerMechanic(Mechanics mechanic) {
    registerMechanic("pokemon", mechanic);
    registerMechanic("pokemmo", mechanic);
  }

  public static <T extends Mechanics> T getActiveMechanic(Class<T> clazz) {
    for (Mechanics mechanic : mechanics) {
      if (clazz.isInstance(mechanic)) {
        return clazz.cast(mechanic);
      }
    }
    return null;
  }

  public static Path getPath() {
    if (path == null) {
      path = CobbleUtils.getPath().resolve(MOD_ID);
    }
    return path;
  }

  public static AsyncContext getAsyncContext() {
    return UtilsAsync.createContext(MOD_ID, MOD_NAME);
  }

  /**
   * Check if a player has a permission, with legacy support for "cobbledaycare".
   *
   * @param player     The player to check.
   * @param permission The permission to check.
   * @param level      The default permission level.
   *
   * @return True if the player has the permission.
   */
  public static boolean hasPermission(net.minecraft.server.command.ServerCommandSource player, String permission, int level) {
    if (PermissionApi.hasPermission(player, permission, level)) return true;
    if (permission.startsWith(MOD_ID)) {
      return PermissionApi.hasPermission(player, permission.replace(MOD_ID, "cobbledaycare"), level);
    }
    return false;
  }

  /**
   * Check if a player has a permission, with legacy support for "cobbledaycare".
   *
   * @param player     The player to check.
   * @param permission The permission to check.
   * @param level      The default permission level.
   *
   * @return True if the player has the permission.
   */
  public static boolean hasPermission(ServerPlayerEntity player, String permission, int level) {
    if (PermissionApi.hasPermission(player, permission, level)) return true;
    if (permission.startsWith(MOD_ID)) {
      return PermissionApi.hasPermission(player, permission.replace(MOD_ID, "cobbledaycare"), level);
    }
    return false;
  }

  public static void load() {
    MigrationService.migrate();
    DaycareRegistry.registerMode(new PokemonDaycareMode());
    DaycareRegistry.registerMode(new PokeMMODaycareMode());
    registerDefaultMechanics();
    CobbleUtils.info(MOD_ID, "1.2.7", "https://github.com/zonary123/UltraDaycare");
    files();
    DatabaseClientFactory.createDatabaseClient(config.getDataBase());
  }

  public static DaycareMode getActiveMode() {
    String modeId = config.getDaycareMode();
    DaycareMode mode = DaycareRegistry.getMode(modeId);
    if (mode == null) {
      mode = DaycareRegistry.getMode("pokemon");
    }
    return mode;
  }

  private static void tasks() {
    getAsyncContext().scheduleAtFixedRate(() -> {
      if (database == null)
        return;
      database.saveAll();
    }, 0, 30, TimeUnit.SECONDS);
  }

  private static void registerDefaultMechanics() {
    if (!registeredModeMechanics.isEmpty()) return;

    // Register Pokémon Mode Mechanics
    registerMechanic("pokemon", new DayCarePokemon());
    registerMechanic("pokemon", new DayCareForm());
    registerMechanic("pokemon", new DayCareAbility());
    registerMechanic("pokemon", new DayCareEggMoves());
    registerMechanic("pokemon", new DayCareMirrorHerb());
    registerMechanic("pokemon", new DayCareNature());
    registerMechanic("pokemon", new DayCareCountry());
    registerMechanic("pokemon", new DayCareShiny());
    registerMechanic("pokemon", new DayCarePokeBall());
    registerMechanic("pokemon", new DaycareIvs());
    registerMechanic("pokemon", new DayCareInciense());

    // Register PokeMMO Mode Mechanics
    // Reusing the identical ones from the pokemon package, and registering custom PokeMMO ones where needed
    registerMechanic("pokemmo", new DayCarePokemon());
    registerMechanic("pokemmo", new DayCareForm());
    registerMechanic("pokemmo", new DayCareAbility());
    registerMechanic("pokemmo", new DayCareEggMoves());
    registerMechanic("pokemmo", new DayCareMirrorHerb());
    registerMechanic("pokemmo", new DayCareNature());
    registerMechanic("pokemmo", new DayCareCountry());
    registerMechanic("pokemmo", new DayCareShiny());
    registerMechanic("pokemmo", new DayCarePokeBall());
    registerMechanic("pokemmo", new com.kingpixel.ultradaycare.mechanics.pokemmo.DaycareIvs()); // Custom PokeMMO IVs
    registerMechanic("pokemmo", new DayCareInciense());
  }

  private static void files() {
    config.init();
    language.init();
    modeMechanics.clear();

    registeredModeMechanics.forEach((modeId, templates) -> {
      List<Mechanics> activeList = new ArrayList<>();
      for (Mechanics mechanic : templates) {
        Mechanics instance = mechanic.getInstance(modeId);
        if (instance != null) {
          activeList.add(instance);
        }
      }
      activeList.removeIf(Objects::isNull);
      activeList.removeIf(mechanic -> {
        if (mechanic instanceof com.kingpixel.ultradaycare.mechanics.pokemon.DayCarePokemon || mechanic instanceof com.kingpixel.ultradaycare.mechanics.pokemon.DayCareForm) {
          return false;
        }
        return !mechanic.isActive();
      });
      modeMechanics.put(modeId, activeList);
    });

    mechanics.clear();
    mechanics.addAll(getActiveMechanics());

    List<String> activeMechanics = new ArrayList<>();
    for (Mechanics mechanic : mechanics) {
      activeMechanics.add(mechanic.fileName());
    }
    UltraDaycare.LOGGER.info(MOD_ID, "Active mechanics:\n- " + String.join("\n- ", activeMechanics));
  }

  public static List<Mechanics> getActiveMechanics() {
    String activeModeId = getActiveMode().getId().toLowerCase();
    return modeMechanics.getOrDefault(activeModeId, Collections.emptyList());
  }

  private static void events() {
    files();

    CobblemonEvents.BATTLE_STARTED_PRE.subscribe(Priority.HIGHEST, evt -> {
      var actors = evt.getBattle().getActors();
      for (BattleActor actor : actors) {
        if (actor instanceof PlayerBattleActor playerBattleActor) {
          var pokemons = playerBattleActor.getPokemonList();
          pokemons.removeIf(pokemon -> pokemon.getOriginalPokemon().getSpecies().showdownId().equals("egg"));
          if (pokemons.isEmpty())
            evt.cancel();
        }
      }
      return Unit.INSTANCE;
    });

    CommandRegistrationEvent.EVENT
      .register((dispatcher, registry, selection) -> CommandTree.register(dispatcher, registry));

    LifecycleEvent.SERVER_STARTED.register(server -> {
      DataPackDaycare.installDatapack(server, "ultradaycare_datapack");
      load();
      int size = config.getSlotPlots().size();
      for (int i = 0; i < size; i++) {
        hasPermission(server.getCommandSource(), Plot.plotPermission(i), 4);
      }
      CustomPokemonProperty.Companion.register(BreedablePropertyType.getInstance());
    });

    LifecycleEvent.SERVER_STOPPING.register(server -> database.disconnect());

    PlayerEvent.PLAYER_JOIN.register(player -> database.find(player)
      .whenComplete((user, throwable) -> {
        if (throwable != null) {
          LOGGER.error("Error loading user " + player.getName().getString(), throwable);
          return;
        }
        if (user == null)
          user = new User(player);
        DatabaseClient.USERS.put(player.getUuid(), user);
        user.setConnectedTime(System.currentTimeMillis());
        boolean update = false;
        if (user.getCountry() == null) {
          UserInfo info = playerCountry.computeIfAbsent(player.getUuid(), uuid -> fetchCountryInfo(player));
          if (info != null) {
            user.setCountry(info.country());
            update = true;
          }
        }

        fixPlayer(player);

        int numPlots = 1;
        int size = UltraDaycare.config.getSlotPlots().size();
        for (int i = 0; i < size; i++) {
          if (hasPermission(player, Plot.plotPermission(i), 4)) {
            numPlots = i + 1;
          }
        }

        if (user.check(numPlots, player))
          update = true;
        if (user.fix(player))
          update = true;

        // Offline Breeding Simulation
        long now = System.currentTimeMillis();
        long lastActive = user.getLastActiveTime();
        if (lastActive > 0) {
          long elapsed = now - lastActive;
          for (Plot plot : user.getPlots()) {
            plot.checkOfflineBreeding(player, user, elapsed);
          }
        }
        user.setLastActiveTime(now);

        if (update)
          user.markDirty();

        user.save();
      }));

    PlayerEvent.PLAYER_QUIT.register(player -> {
      User user = database.getUser(player);
      if (user != null) {
        user.setConnectedTime(0);
        user.setLastActiveTime(System.currentTimeMillis());
        user.save().thenRun(() -> DatabaseClient.USERS.invalidate(player.getUuid()));
      }
    });

    CobblemonEvents.POKEMON_CAPTURED.subscribe(Priority.HIGHEST, evt -> {
      fixBreedable(evt.getPokemon());
      return Unit.INSTANCE;
    });

    CobblemonEvents.EVOLUTION_COMPLETE.subscribe(Priority.NORMAL, evt -> {
      fixBreedable(evt.getPokemon());
      return Unit.INSTANCE;
    });

    CobblemonEvents.POKEMON_SENT_PRE.subscribe(Priority.HIGHEST, evt -> {
      Pokemon pokemon = evt.getPokemon();
      fixBreedable(pokemon);
      if (pokemon.getSpecies().showdownId().equals("egg")) {
        evt.cancel();
        return Unit.INSTANCE;
      }
      return Unit.INSTANCE;
    });

    CobblemonEvents.POKEMON_ENTITY_SPAWN.subscribe(Priority.LOWEST, evt -> {
      if (!evt.getEntity().getPokemon().isWild())
        return Unit.INSTANCE;
      if (!config.isSpawnEggWorld())
        return Unit.INSTANCE;
      var pokemonEntity = evt.getEntity();
      var pokemon = pokemonEntity.getPokemon();
      if (pokemon.getSpecies().showdownId().equals("egg"))
        return Unit.INSTANCE;
      boolean rarity = Utils.getRandom().nextInt(config.getRaritySpawnEgg()) == 0;
      if (rarity) {
        var egg = PokemonProperties.Companion.parse("egg").create();
        for (Mechanics mechanic : mechanics) {
          mechanic.createEgg(null, pokemon, egg);
        }
        egg.getPersistentData().putBoolean(TAG_SPAWNED, true);
        pokemonEntity.speed = 0;
        pokemonEntity.setAiDisabled(true);
        pokemonEntity.setPokemon(egg);
      }
      return Unit.INSTANCE;
    });
  }

  private static UserInfo fetchCountryInfo(ServerPlayerEntity player) {
    try {
      URL url = new URL(API_URL_IP + player.getIp());
      HttpURLConnection conn = (HttpURLConnection) url.openConnection();
      conn.setRequestMethod("GET");

      try (BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()))) {
        JsonObject json = JsonParser.parseReader(in).getAsJsonObject();
        if (json.has("country")) {
          String country = json.get("country").getAsString();
          String countryCode = json.get("countryCode").getAsString();
          String language = switch (countryCode) {
            case "AR", "ES" -> "es";
            case "US", "GB", "AU" -> "en";
            default -> "en";
          };
          return new UserInfo(country, countryCode, language);
        }
      }
    } catch (Exception e) {
      if (UltraDaycare.config.isDebug()) {
        UltraDaycare.LOGGER.error("Error fetching country for player " + player.getName().getString() + ": ", e);
      }
    }
    return null;
  }

  private static void fixPlayer(ServerPlayerEntity player) {
    var user = database.getUser(player);
    var party = Cobblemon.INSTANCE.getStorage().getParty(player);
    for (Pokemon pokemon : party) {
      fixBreedable(pokemon);
      fixCountryInfo(pokemon, user.getCountry());
      if (config.isFixIlegalAbilities()) {
        PokemonUtils.isLegalAbility(pokemon);
      }
    }

    var pc = Cobblemon.INSTANCE.getStorage().getPC(player);
    for (Pokemon pokemon : pc) {
      fixBreedable(pokemon);
      fixCountryInfo(pokemon, user.getCountry());
      if (config.isFixIlegalAbilities()) {
        PokemonUtils.isLegalAbility(pokemon);
      }
    }
  }

  private static void fixCountryInfo(Pokemon pokemon, String country) {
    if (country == null)
      return;
    if (!pokemon.getPersistentData().contains(com.kingpixel.ultradaycare.mechanics.pokemon.DayCareCountry.TAG)) {
      pokemon.getPersistentData().putString(com.kingpixel.ultradaycare.mechanics.pokemon.DayCareCountry.TAG, country);
    }
  }

  public static void fixBreedable(Pokemon pokemon) {
    boolean isNotBreedable = Plot.isNotBreedable(pokemon);

    var nbt = pokemon.getPersistentData();
    boolean builderOverride = nbt.getBoolean(CobbleUtilsTags.BREEDABLE_BUILDER_TAG);

    if (isNotBreedable) {
      setBreedable(pokemon, false);
      return;
    }

    if (!builderOverride) {
      setBreedable(pokemon, true);
    }
  }

  public static synchronized void setBreedable(Pokemon pokemon, boolean value) {
    pokemon.getPersistentData().putBoolean(CobbleUtilsTags.BREEDABLE_TAG, value);
  }

  public static synchronized boolean isBreedable(Pokemon pokemon) {
    return pokemon.getPersistentData().getBoolean(CobbleUtilsTags.BREEDABLE_TAG);
  }

  @Override
  public void onInitialize() {
    load();
    events();
    tasks();
    DaycarePlaceholders.register();
  }

  public record UserInfo(String country, String countryCode, String language) {
  }
}
