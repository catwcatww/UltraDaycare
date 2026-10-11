package com.kingpixel.ultradaycare.config;

import com.cobblemon.mod.common.api.pokemon.egg.EggGroup;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.kingpixel.cobbleutils.Model.*;
import com.kingpixel.cobbleutils.util.PlayerUtils;
import com.kingpixel.cobbleutils.util.TypeMessage;
import com.kingpixel.cobbleutils.util.UtilsFile;
import com.kingpixel.ultradaycare.UltraDaycare;
import com.kingpixel.ultradaycare.models.UserInfoOptions;
import lombok.Getter;
import lombok.Setter;
import net.minecraft.server.network.ServerPlayerEntity;

import java.io.IOException;
import java.nio.file.Path;
import java.util.*;

/**
 * @author Carlos Varas Alonso - 30/01/2025 23:47
 */
@Getter
@Setter
public class Config {
  private static final String VIP_COOLDOWN = "cooldown.vip";
  private static final String LEGENDARY_COOLDOWN = "cooldown.legendary";
  private static final String MASTER_COOLDOWN = "cooldown.master";
  private static Map<UUID, Long> cooldownsOpenMenus = new HashMap<>();
  // ==========================================
  // 1. System & Database Configuration
  // ==========================================
  private boolean debug = false;
  private String lang = "en";
  private List<String> commands = List.of("daycare");
  private String commandEggInfo = "egginfo";
  private String daycareMode = "pokemon";
  private EggHatchMethod pokemmoEggHatchMethod = EggHatchMethod.STEPS;
  private DataBaseConfig dataBase = new DataBaseConfig();
  private UserInfoOptions userInfoOptions = new UserInfoOptions();

  // ==========================================
  // 2. General Daycare & Breeding Settings
  // ==========================================
  private boolean fixIlegalAbilities = true;
  private boolean canUseNativeGUI = false;
  private boolean showIvs = false;
  private boolean spawnEggWorld = true;
  private int raritySpawnEgg = 2048;
  private boolean allowElytra = false;
  private boolean dobbleDitto = false;
  private FilterPokemons dobbleDittoFilter = new FilterPokemons();
  private boolean enableFertility = false;
  private int maxFertility = 5;
  private PokemonBlackList blackList;
  private List<String> whitelist = new ArrayList<>();
  private Map<String, Integer> limitEggs = new HashMap<>(Map.of("", 1, "group.vip", 2));

  // ==========================================
  // 3. Step & Time Hatching Progress
  // ==========================================
  private long ticksToWalking = 20;
  private boolean globalMultiplierSteps = false;
  private float multiplierSteps = 1.0f;
  private Map<String, Float> multiplierStepsPermission = new HashMap<>(Map.of("multipliersteps.vip", 2.0f));
  private double defaultSteps = 128D;
  private Map<EggGroup, Double> steps = new EnumMap<>(EggGroup.class);
  private List<String> abilityAcceleration = List.of("magmaarmor", "flamebody", "steamengine");
  private double multiplierAbilityAcceleration = 2.0;
  private double reduceEggStepsVehicle = 2.0;
  private List<String> permittedVehicles = List.of("minecraft:boat", "minecraft:horse", "cobblemon:pokemon");
  private DurationValue pokemmoEggHatchTime = DurationValue.parse("10m");
  private Map<String, DurationValue> pokemmoEggHatchTimePermissions = new HashMap<>(
      Map.of("ultradaycare.hatch.vip", DurationValue.parse("5m")));

  // ==========================================
  // 4. Cooldowns & Slots
  // ==========================================
  private DurationValue cooldownToOpenMenus = DurationValue.parse("0.5s");
  private DurationValue cooldown = DurationValue.parse("3m");
  private Map<String, DurationValue> cooldowns = Map.of(
      VIP_COOLDOWN, DurationValue.parse("15m"),
      LEGENDARY_COOLDOWN, DurationValue.parse("10m"),
      MASTER_COOLDOWN, DurationValue.parse("5m"));
  private DurationValue defaultCooldownBreed = DurationValue.parse("60s");
  private Map<String, DurationValue> cooldownsBreed = new HashMap<>(Map.of(VIP_COOLDOWN, DurationValue.parse("30s")));
  private DurationValue defaultCooldownHatch = DurationValue.parse("60s");
  private Map<String, DurationValue> cooldownsHatch = new HashMap<>(Map.of(VIP_COOLDOWN, DurationValue.parse("30s")));
  private List<Integer> slotPlots = new ArrayList<>(List.of(10, 12, 14, 16));

  // ==========================================
  // 5. Economy & Paid Features
  // ==========================================
  private EconomyUse economyUse = new EconomyUse("IMPACTOR", "impactor:dollars");
  private boolean enableBreedingFee = false;
  private double breedingFeePrice = 1000.0;
  private boolean enablePaidExperience = false;
  private double payXpPrice = 100.0;
  private int payXpAmount = 1;
  private int maxLevelTraining = 100;
  private double xpPerStep = 1.0;
  private double pricePerXp = 5.0;

  // ==========================================
  // 6. Offline Breeding
  // ==========================================
  private boolean enableOfflineBreeding = true;
  private DurationValue offlineBreedingInterval = DurationValue.parse("30m");
  private int maxOfflineEggsPerPlot = 1;

  // ==========================================
  // 7. Incubators Module
  // ==========================================
  private IncubatorConfig incubators = new IncubatorConfig();

  public Config() {
    this.dataBase.setDatabase("ultradaycare");
    this.blackList = PokemonBlackList.createBlackList();
    this.blackList.getPokemons().add("egg");
    this.blackList.getLabels().add("basculegion");
    this.blackList.getLabels().add("legendary");

    for (EggGroup value : EggGroup.values()) {
      this.steps.put(value, defaultSteps);
    }
  }

  public void check() {
    if (daycareMode == null || daycareMode.isEmpty()) {
      daycareMode = "pokemon";
    }
    if (ticksToWalking < 20) {
      ticksToWalking = 20;
    }
    if (multiplierSteps < 1.0f) {
      multiplierSteps = 1.0f;
    }
    if (pokemmoEggHatchMethod == null) {
      pokemmoEggHatchMethod = EggHatchMethod.TIME;
    }
    if (pokemmoEggHatchTime == null) {
      pokemmoEggHatchTime = DurationValue.parse("10m");
    }
    if (pokemmoEggHatchTimePermissions == null) {
      pokemmoEggHatchTimePermissions = new HashMap<>();
    }
    if (incubators == null) {
      incubators = new IncubatorConfig();
    }
    incubators.check();
  }

  public boolean hasOpenCooldown(ServerPlayerEntity player) {
    long currentCooldown = cooldownsOpenMenus.getOrDefault(player.getUuid(), 0L);
    if (currentCooldown <= System.currentTimeMillis()) {
      cooldownsOpenMenus.put(player.getUuid(), System.currentTimeMillis() + cooldownToOpenMenus.toMillis());
      return false;
    }
    PlayerUtils.sendMessage(
        player,
        UltraDaycare.language.getMessageCooldownOpenMenu()
            .replace("%cooldown%", PlayerUtils.getCooldown(currentCooldown)),
        UltraDaycare.language.getPrefix(),
        TypeMessage.CHAT);
    return true;
  }

  public double getSteps(Pokemon pokemon) {
    double d = this.defaultSteps;
    for (EggGroup eggGroup : pokemon.getForm().getEggGroups()) {
      if (this.steps.containsKey(eggGroup) && this.steps.get(eggGroup) < d) {
        d = this.steps.get(eggGroup);
      }
    }
    return d;
  }

  public void init() {
    Path path = UltraDaycare.getPath().resolve("config.json");
    try {
      Config config = UtilsFile.readOrCreate(path, Config.class, Config::new);
      config.check();
      UltraDaycare.config = config;
      UtilsFile.write(path, config);
    } catch (IOException e) {
      UltraDaycare.LOGGER.error("Error reading mod configuration file: ", e);
    }
  }
}
