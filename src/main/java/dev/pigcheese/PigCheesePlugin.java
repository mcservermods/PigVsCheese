package dev.pigcheese;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.OfflinePlayer;
import org.bukkit.Registry;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.block.Block;
import org.bukkit.entity.AbstractWindCharge;
import org.bukkit.entity.EnderPearl;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Firework;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.WindCharge;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDispenseEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.NotePlayEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.BlockRedstoneEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.EntityToggleGlideEvent;
import org.bukkit.event.entity.ExplosionPrimeEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.inventory.PrepareSmithingEvent;
import org.bukkit.event.player.PlayerArmorStandManipulateEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemDamageEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.potion.PotionType;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.io.File;
import java.time.Duration;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

public class PigCheesePlugin extends JavaPlugin implements Listener {

    // ---- Tier ladders (index 0 = starting tier) ----
    private static final String[] ARMOR = {"LEATHER", "COPPER", "CHAINMAIL", "IRON", "DIAMOND", "NETHERITE"};
    private static final String[] WEAPON = {"WOODEN", "STONE", "COPPER", "IRON", "DIAMOND", "NETHERITE"};
    private static final String[] PIECES = {"HELMET", "CHESTPLATE", "LEGGINGS", "BOOTS"};
    private static final String[] WEAPON_TYPES = {"SWORD", "SPEAR", "AXE"};
    private static final int MAX_TIER = ARMOR.length - 1;
    /** Kills needed to leave each tier (index = current tier, 0 = tier 1). Later tiers need more kills. */
    private static final int[] KILLS_NEEDED = {1, 2, 2, 5, 6};
    /** Deaths needed before you actually drop a tier (index = current tier). Lower tiers are harder to lose. */
    private static final int[] DEATHS_TO_DROP = {0, 4, 3, 2, 2, 1};

    /** e.g. "1, 2, 2, 5, then 6" - used in the guide texts so they always match the real numbers. */
    private static String killsScaleText() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < KILLS_NEEDED.length; i++) {
            if (i > 0) sb.append(i == KILLS_NEEDED.length - 1 ? ", then " : ", ");
            sb.append(KILLS_NEEDED[i]);
        }
        return sb.toString();
    }

    private static int killsNeeded(int tier) {
        return KILLS_NEEDED[Math.max(0, Math.min(KILLS_NEEDED.length - 1, tier))];
    }

    private static int deathsToDrop(int tier) {
        return DEATHS_TO_DROP[Math.max(1, Math.min(DEATHS_TO_DROP.length - 1, tier))];
    }

    /** Kill points earned for killing someone, indexed by THEIR tier at the moment they died. */
    private static final int[] KILL_POINTS = {6, 9, 13, 18, 25, 35};
    private static final int WIND_CHARGES = 16;
    private static final int MAX_LISTINGS_PER_PLAYER = 5;
    private static final long SPAWN_PROTECTION_MS = 30_000L;
    private static final int SHOP_PAGE_SIZE = 45;

    // ---- pause-menu "Duel a player" button (installed as a data pack) ----
    private static final String DATAPACK_VERSION = "1";
    private static final String DP_MCMETA = """
            {"pack": {"description": "Pig vs Cheese - pause menu Duel button", "min_format": 94, "max_format": 200}}
            """;
    private static final String DP_DIALOG = """
            {
              "type": "minecraft:notice",
              "title": "Duel",
              "external_title": "Duel a player",
              "can_close_with_escape": true,
              "pause": false,
              "body": [
                {
                  "type": "minecraft:plain_message",
                  "contents": "PvP needs a duel. Choose a player to challenge - nobody else can hurt you while you fight."
                }
              ],
              "action": {
                "label": "Choose a player",
                "width": 220,
                "action": {
                  "type": "minecraft:run_command",
                  "command": "duelmenu"
                }
              }
            }
            """;
    private static final String DP_TAG = """
            {"values": ["pigcheese:duel"]}
            """;

    /** Banker block: this many deposited items = 1 kill point. */
    private static final int BANKER_ITEMS_PER_POINT = 32;

    // ---- combat / waypoints / tpa ----
    private static final long COMBAT_MS = 20_000L;
    private static final int COMBAT_TIER_LOSS = 3;
    private static final int COMBAT_POINT_LOSS = 10;
    private static final int WAYPOINT_COST = 100;
    private static final int MAX_WAYPOINTS = 10;
    private static final long TPA_EXPIRE_MS = 60_000L;
    private static final long DUEL_REQUEST_MS = 60_000L;

    // ---- Banned things ----
    private static final Set<String> EXPLOSIVES = Set.of(
            "TNT", "TNT_MINECART", "END_CRYSTAL", "RESPAWN_ANCHOR", "FIREWORK_ROCKET", "FIREWORK_STAR");
    private static final Set<String> MOVEMENT_ITEMS = Set.of(
            "ENDER_PEARL", "CHORUS_FRUIT", "ELYTRA", "WIND_CHARGE");
    private static final Set<String> OP_ONLY_ITEMS = Set.of(
            "BARRIER", "BEDROCK", "LIGHT", "STRUCTURE_BLOCK", "STRUCTURE_VOID", "JIGSAW", "DEBUG_STICK",
            "KNOWLEDGE_BOOK", "END_PORTAL_FRAME", "REINFORCED_DEEPSLATE", "VAULT", "TRIAL_KEY", "OMINOUS_TRIAL_KEY",
            "ENCHANTED_BOOK", "POTION", "SPLASH_POTION", "LINGERING_POTION", "TIPPED_ARROW", "FILLED_MAP",
            "WRITTEN_BOOK", "PLAYER_HEAD", "BUDDING_AMETHYST", "PETRIFIED_OAK_SLAB", "AIR", "NOTE_BLOCK");

    /** Every sword / spear / axe / armour piece on the ladders, plus the mace. These can never be lost. */
    private static final Set<Material> GEAR = new HashSet<>();

    static {
        for (String t : ARMOR) for (String p : PIECES) addGear(t + "_" + p);
        for (String t : WEAPON) for (String w : WEAPON_TYPES) addGear(t + "_" + w);
        addGear("MACE");
    }

    private static void addGear(String name) {
        Material m = Material.matchMaterial(name);
        if (m != null) GEAR.add(m);
    }

    /** Tier gear + mace (durability-free). */
    private boolean isGear(ItemStack i) {
        return i != null && GEAR.contains(i.getType());
    }

    /** Things that can never be dropped / stored / sold: tier gear, mace and wind charges. */
    private boolean isProtected(ItemStack i) {
        return i != null && (GEAR.contains(i.getType()) || i.getType() == Material.WIND_CHARGE);
    }

    /** Any armour or melee weapon at all (these cannot be crafted, bought, traded for or picked up). */
    private boolean isCombatGear(Material m) {
        String n = m.name();
        return n.endsWith("_HELMET") || n.endsWith("_CHESTPLATE") || n.endsWith("_LEGGINGS") || n.endsWith("_BOOTS")
                || n.endsWith("_SWORD") || n.endsWith("_SPEAR") || n.endsWith("_AXE") || n.equals("MACE");
    }

    private boolean isCombatGear(ItemStack i) {
        return i != null && isCombatGear(i.getType());
    }

    // ---- saved data ----
    private static class PlayerData {
        String team;
        String lockedTeam; // set by operators: balancing can never move this player
        int tier;
        int kills;
        int points;
        boolean seenGuide;
        String notice; // message shown on next join (e.g. combat-log punishment)
        long mutedUntil;
        // lifetime stats
        int totalKills;
        int deaths;
        int streak;
        int bestStreak;
        int highestTier;
        int duelWins;
        int duelLosses;
        long pointsEarned;
        long playSeconds;
        int strikes; // deaths counted toward the next tier drop
        int appliedTierVersion; // which /settierall has already been applied to this player
        long itemsBanked; // lifetime items put into banker blocks
    }

    private static class Listing {
        String id;
        UUID seller;
        String sellerName;
        int price;
        long time;
        ItemStack item;
    }

    private static class Waypoint {
        String id;
        String name;
        String world;
        double x, y, z;
        float yaw, pitch;
        boolean unlocked;

        Location loc() {
            World w = Bukkit.getWorld(world);
            return w == null ? null : new Location(w, x, y, z, yaw, pitch);
        }

        void set(Location l) {
            world = l.getWorld().getName();
            x = l.getX();
            y = l.getY();
            z = l.getZ();
            yaw = l.getYaw();
            pitch = l.getPitch();
        }
    }

    private record ShopEntry(ItemStack stack, int unit, String key) {}

    private record TpaRequest(UUID from, long expires) {}

    private record DuelRequest(UUID from, long expires) {}

    private static class ShopHolder implements InventoryHolder {
        Inventory inv;
        List<ShopEntry> results = new ArrayList<>();
        int page;
        String query = "";

        @Override
        public Inventory getInventory() {
            return inv;
        }
    }

    private static class AuctionHolder implements InventoryHolder {
        Inventory inv;
        final String team;
        final int page;
        final List<String> ids = new ArrayList<>();

        AuctionHolder(String team, int page) {
            this.team = team;
            this.page = page;
        }

        @Override
        public Inventory getInventory() {
            return inv;
        }
    }

    private static class GuideHolder implements InventoryHolder {
        Inventory inv;

        @Override
        public Inventory getInventory() {
            return inv;
        }
    }

    private static class EventWaypointHolder implements InventoryHolder {
        Inventory inv;
        final List<Waypoint> list;

        EventWaypointHolder(List<Waypoint> list) {
            this.list = list;
        }

        @Override
        public Inventory getInventory() {
            return inv;
        }
    }

    private static class BankerHolder implements InventoryHolder {
        Inventory inv;

        @Override
        public Inventory getInventory() {
            return inv;
        }
    }

    private static class DuelMenuHolder implements InventoryHolder {
        Inventory inv;
        final List<UUID> ids;
        final int page;

        DuelMenuHolder(List<UUID> ids, int page) {
            this.ids = ids;
            this.page = page;
        }

        @Override
        public Inventory getInventory() {
            return inv;
        }
    }

    private static class StatsHolder implements InventoryHolder {
        Inventory inv;

        @Override
        public Inventory getInventory() {
            return inv;
        }
    }

    private static class WaypointHolder implements InventoryHolder {
        Inventory inv;
        final boolean manage;
        final List<Waypoint> list;

        WaypointHolder(boolean manage, List<Waypoint> list) {
            this.manage = manage;
            this.list = list;
        }

        @Override
        public Inventory getInventory() {
            return inv;
        }
    }

    private final Map<UUID, PlayerData> data = new ConcurrentHashMap<>();
    private final Map<String, List<Listing>> auctions = new HashMap<>();
    private final Map<UUID, List<Waypoint>> waypoints = new HashMap<>();
    private final List<ShopEntry> shop = new ArrayList<>();
    private final Map<UUID, Long> protectedUntil = new HashMap<>();
    private final Map<UUID, Long> combatUntil = new HashMap<>();
    private final Map<UUID, TpaRequest> tpaRequests = new HashMap<>();
    private final Map<UUID, UUID> duels = new HashMap<>(); // both directions: A->B and B->A
    private final Map<UUID, DuelRequest> duelRequests = new HashMap<>();
    private final Set<UUID> searching = ConcurrentHashMap.newKeySet();
    private final Set<UUID> guidePending = ConcurrentHashMap.newKeySet();
    private final Set<UUID> frozen = ConcurrentHashMap.newKeySet();
    private final Set<UUID> intruders = ConcurrentHashMap.newKeySet(); // enemies currently inside the other team's base
    private boolean datapackJustInstalled;
    private final Map<UUID, Long> sessionStart = new ConcurrentHashMap<>();
    private int globalTier = -1;        // pending /settierall target (0-5), -1 = none
    private int globalTierVersion = 0;  // bumps every time /settierall is used
    private File dataFile, auctionFile, waypointFile, pricesFile, eventFile, eventWaypointFile, bankerFile;
    private final Set<String> bankerBlocks = ConcurrentHashMap.newKeySet(); // placed banker block locations
    private NamespacedKey bankerKey, bankerRecipeKey;
    private final List<Waypoint> eventWaypoints = new ArrayList<>(); // set by operators, free to use during events

    // ---- timed events (/event) ----
    private String eventType; // null = nothing running
    private long eventEndsAt, eventTotalMs, lastEventWarn = -1;
    private boolean evRandomTiers, evRandomEnchants, evDoublePoints, evFfa; // evFfa = free-for-all (teams ignored)
    private final Map<UUID, int[]> eventSnap = new HashMap<>(); // tier, kills, strikes from BEFORE the event
    private BossBar eventBar;
    private final Map<String, Integer> customPrices = new HashMap<>(); // operator price overrides

    private NamespacedKey oldTeamKey, oldTierKey, oldKillsKey; // only for importing data from v1.0/1.1
    private Team pigTeam, cheeseTeam;

    @Override
    public void onEnable() {
        oldTeamKey = new NamespacedKey(this, "team");
        oldTierKey = new NamespacedKey(this, "tier");
        oldKillsKey = new NamespacedKey(this, "kills");

        getConfig().addDefault("pack.url", "https://github.com/mcservermods/PigVsCheese/raw/main/BankerPack.zip");
        getConfig().addDefault("pack.sha1", "aa9b02da985327480ee438e6ecdc203e61152341");
        getConfig().addDefault("pack.required", false);
        getConfig().options().copyDefaults(true);
        saveConfig();

        getDataFolder().mkdirs();
        dataFile = new File(getDataFolder(), "data.yml");
        auctionFile = new File(getDataFolder(), "auction.yml");
        waypointFile = new File(getDataFolder(), "waypoints.yml");
        pricesFile = new File(getDataFolder(), "prices.yml");
        loadData();
        loadAuction();
        loadWaypoints();
        loadPrices();
        eventFile = new File(getDataFolder(), "event.yml");
        eventWaypointFile = new File(getDataFolder(), "eventwaypoints.yml");
        loadEventWaypoints();
        bankerKey = new NamespacedKey(this, "banker");
        bankerRecipeKey = new NamespacedKey(this, "banker_block");
        bankerFile = new File(getDataFolder(), "bankers.yml");
        loadBankers();
        datapackJustInstalled = installDatapack();
        loadEvent();
        buildShop();

        Scoreboard sb = Bukkit.getScoreboardManager().getMainScoreboard();
        pigTeam = makeTeam(sb, "pig", "Pig", NamedTextColor.LIGHT_PURPLE);
        cheeseTeam = makeTeam(sb, "cheese", "Cheese", NamedTextColor.YELLOW);

        Bukkit.getPluginManager().registerEvents(this, this);
        registerBankerRecipe();

        // autosave every 5 minutes
        Bukkit.getScheduler().runTaskTimer(this, () -> {
            for (Player pl : Bukkit.getOnlinePlayers()) flushPlaytime(pl.getUniqueId(), true);
            saveData();
            saveAuction();
            saveWaypoints();
        }, 6000L, 6000L);

        // combat + spawn protection countdown (every second)
        Bukkit.getScheduler().runTaskTimer(this, () -> {
            tickEvent();
            tickBases();
            long now = System.currentTimeMillis();
            for (Player p : Bukkit.getOnlinePlayers()) {
                UUID id = p.getUniqueId();
                Long c = combatUntil.get(id);
                if (c != null) {
                    if (c <= now) {
                        combatUntil.remove(id);
                        p.sendMessage(Component.text("You are no longer in combat.", NamedTextColor.GREEN));
                    } else {
                        p.sendActionBar(Component.text("IN COMBAT: " + ((c - now + 999) / 1000)
                                + "s - don't log out or teleport!", NamedTextColor.RED));
                        continue;
                    }
                }
                Long pr = protectedUntil.get(id);
                if (pr != null) {
                    if (pr <= now) {
                        protectedUntil.remove(id);
                        p.sendActionBar(Component.text("Spawn protection ended!", NamedTextColor.RED));
                    } else {
                        p.sendActionBar(Component.text("Spawn protection: " + ((pr - now + 999) / 1000) + "s", NamedTextColor.AQUA));
                    }
                }
            }
        }, 20L, 20L);
    }

    @Override
    public void onDisable() {
        if (bankerRecipeKey != null) Bukkit.removeRecipe(bankerRecipeKey);
        for (Player pl : Bukkit.getOnlinePlayers()) flushPlaytime(pl.getUniqueId(), false);
        saveData();
        saveEvent();
        saveAuction();
        saveWaypoints();
    }

    /** Puts the player in the right scoreboard team (name colour/prefix) - or in none during a free-for-all event. */
    private void refreshTeamEntry(Player p) {
        pigTeam.removeEntry(p.getName());
        cheeseTeam.removeEntry(p.getName());
        String t = teamOf(p);
        if (evFfa || t == null) return;
        (t.equals("pig") ? pigTeam : cheeseTeam).addEntry(p.getName());
    }

    /** When someone changes team, their team-auction listings follow them so they can still cancel them. */
    private void moveListings(UUID seller, String from, String to) {
        List<Listing> src = auctions.get(from);
        if (src == null) return;
        List<Listing> mine = new ArrayList<>();
        for (Listing l : src) if (l.seller.equals(seller)) mine.add(l);
        if (mine.isEmpty()) return;
        src.removeAll(mine);
        List<Listing> dst = auctions.computeIfAbsent(to, k -> new ArrayList<>());
        dst.addAll(mine);
        dst.sort(Comparator.comparingLong(l -> l.time));
        saveAuction();
    }

    private String eventLabel() {
        return eventTitle(eventType) + (evFfa ? " (FFA)" : "");
    }

    private Team makeTeam(Scoreboard sb, String id, String display, NamedTextColor color) {
        Team t = sb.getTeam(id);
        if (t == null) t = sb.registerNewTeam(id);
        t.displayName(Component.text(display, color));
        t.color(color);
        t.prefix(Component.text("[" + display + "] ", color));
        t.setAllowFriendlyFire(true); // our own damage handler blocks teammate damage (except in duels)
        return t;
    }

    // ---------------------------------------------------------------- save / load
    private void loadData() {
        data.clear();
        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(dataFile);
        globalTier = cfg.getInt("global.tier", -1);
        globalTierVersion = cfg.getInt("global.tierVersion", 0);
        ConfigurationSection sec = cfg.getConfigurationSection("players");
        if (sec == null) return;
        for (String key : sec.getKeys(false)) {
            try {
                UUID id = UUID.fromString(key);
                PlayerData d = new PlayerData();
                d.team = sec.getString(key + ".team");
                d.lockedTeam = sec.getString(key + ".locked");
                d.tier = sec.getInt(key + ".tier", 0);
                d.kills = sec.getInt(key + ".kills", 0);
                d.points = sec.getInt(key + ".points", 0);
                d.seenGuide = sec.getBoolean(key + ".seenGuide", false);
                d.notice = sec.getString(key + ".notice");
                d.mutedUntil = sec.getLong(key + ".muted", 0L);
                d.totalKills = sec.getInt(key + ".totalKills", 0);
                d.deaths = sec.getInt(key + ".deaths", 0);
                d.streak = sec.getInt(key + ".streak", 0);
                d.bestStreak = sec.getInt(key + ".bestStreak", 0);
                d.highestTier = sec.getInt(key + ".highestTier", 0);
                d.duelWins = sec.getInt(key + ".duelWins", 0);
                d.duelLosses = sec.getInt(key + ".duelLosses", 0);
                d.pointsEarned = sec.getLong(key + ".pointsEarned", 0L);
                d.playSeconds = sec.getLong(key + ".playSeconds", 0L);
                d.strikes = sec.getInt(key + ".strikes", 0);
                d.appliedTierVersion = sec.getInt(key + ".appliedTierVersion", 0);
                d.itemsBanked = sec.getLong(key + ".itemsBanked", 0L);
                data.put(id, d);
            } catch (IllegalArgumentException ignored) {
            }
        }
    }

    private void saveData() {
        YamlConfiguration cfg = new YamlConfiguration();
        cfg.set("global.tier", globalTier);
        cfg.set("global.tierVersion", globalTierVersion);
        for (Map.Entry<UUID, PlayerData> en : data.entrySet()) {
            String base = "players." + en.getKey();
            PlayerData d = en.getValue();
            cfg.set(base + ".team", d.team);
            cfg.set(base + ".locked", d.lockedTeam);
            cfg.set(base + ".tier", d.tier);
            cfg.set(base + ".kills", d.kills);
            cfg.set(base + ".points", d.points);
            cfg.set(base + ".seenGuide", d.seenGuide);
            cfg.set(base + ".notice", d.notice);
            cfg.set(base + ".muted", d.mutedUntil);
            cfg.set(base + ".totalKills", d.totalKills);
            cfg.set(base + ".deaths", d.deaths);
            cfg.set(base + ".streak", d.streak);
            cfg.set(base + ".bestStreak", d.bestStreak);
            cfg.set(base + ".highestTier", d.highestTier);
            cfg.set(base + ".duelWins", d.duelWins);
            cfg.set(base + ".duelLosses", d.duelLosses);
            cfg.set(base + ".pointsEarned", d.pointsEarned);
            cfg.set(base + ".playSeconds", d.playSeconds);
            cfg.set(base + ".strikes", d.strikes);
            cfg.set(base + ".appliedTierVersion", d.appliedTierVersion);
            cfg.set(base + ".itemsBanked", d.itemsBanked);
        }
        try {
            cfg.save(dataFile);
        } catch (IOException ex) {
            getLogger().warning("Could not save data.yml: " + ex.getMessage());
        }
    }

    private void loadAuction() {
        auctions.clear();
        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(auctionFile);
        for (String team : new String[]{"pig", "cheese", "global"}) {
            List<Listing> list = new ArrayList<>();
            ConfigurationSection sec = cfg.getConfigurationSection("listings." + team);
            if (sec != null) {
                for (String id : sec.getKeys(false)) {
                    ItemStack item = sec.getItemStack(id + ".item");
                    String seller = sec.getString(id + ".seller");
                    if (item == null || seller == null) continue;
                    Listing l = new Listing();
                    l.id = id;
                    l.seller = UUID.fromString(seller);
                    l.sellerName = sec.getString(id + ".name", "?");
                    l.price = sec.getInt(id + ".price", 1);
                    l.time = sec.getLong(id + ".time", 0L);
                    l.item = item;
                    list.add(l);
                }
            }
            list.sort(Comparator.comparingLong(l -> l.time));
            auctions.put(team, list);
        }
    }

    private void saveAuction() {
        YamlConfiguration cfg = new YamlConfiguration();
        for (Map.Entry<String, List<Listing>> en : auctions.entrySet()) {
            for (Listing l : en.getValue()) {
                String base = "listings." + en.getKey() + "." + l.id;
                cfg.set(base + ".seller", l.seller.toString());
                cfg.set(base + ".name", l.sellerName);
                cfg.set(base + ".price", l.price);
                cfg.set(base + ".time", l.time);
                cfg.set(base + ".item", l.item);
            }
        }
        try {
            cfg.save(auctionFile);
        } catch (IOException ex) {
            getLogger().warning("Could not save auction.yml: " + ex.getMessage());
        }
    }

    private void loadWaypoints() {
        waypoints.clear();
        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(waypointFile);
        ConfigurationSection sec = cfg.getConfigurationSection("players");
        if (sec == null) return;
        for (String pid : sec.getKeys(false)) {
            try {
                UUID id = UUID.fromString(pid);
                List<Waypoint> list = new ArrayList<>();
                ConfigurationSection ps = sec.getConfigurationSection(pid);
                if (ps == null) continue;
                for (String wid : ps.getKeys(false)) {
                    Waypoint w = new Waypoint();
                    w.id = wid;
                    w.name = ps.getString(wid + ".name", wid);
                    w.world = ps.getString(wid + ".world", "world");
                    w.x = ps.getDouble(wid + ".x");
                    w.y = ps.getDouble(wid + ".y");
                    w.z = ps.getDouble(wid + ".z");
                    w.yaw = (float) ps.getDouble(wid + ".yaw");
                    w.pitch = (float) ps.getDouble(wid + ".pitch");
                    w.unlocked = ps.getBoolean(wid + ".unlocked", false);
                    list.add(w);
                }
                waypoints.put(id, list);
            } catch (IllegalArgumentException ignored) {
            }
        }
    }

    private void saveWaypoints() {
        YamlConfiguration cfg = new YamlConfiguration();
        for (Map.Entry<UUID, List<Waypoint>> en : waypoints.entrySet()) {
            for (Waypoint w : en.getValue()) {
                String base = "players." + en.getKey() + "." + w.id;
                cfg.set(base + ".name", w.name);
                cfg.set(base + ".world", w.world);
                cfg.set(base + ".x", w.x);
                cfg.set(base + ".y", w.y);
                cfg.set(base + ".z", w.z);
                cfg.set(base + ".yaw", w.yaw);
                cfg.set(base + ".pitch", w.pitch);
                cfg.set(base + ".unlocked", w.unlocked);
            }
        }
        try {
            cfg.save(waypointFile);
        } catch (IOException ex) {
            getLogger().warning("Could not save waypoints.yml: " + ex.getMessage());
        }
    }

    // ---------------------------------------------------------------- data helpers
    private PlayerData dataOf(UUID id) {
        return data.computeIfAbsent(id, k -> new PlayerData());
    }

    private String teamOf(Player p) {
        PlayerData d = data.get(p.getUniqueId());
        return d == null ? null : d.team;
    }

    private int tierOf(Player p) {
        PlayerData d = data.get(p.getUniqueId());
        return d == null ? 0 : d.tier;
    }

    private void setTier(Player p, int t) {
        PlayerData d = dataOf(p.getUniqueId());
        int nt = Math.max(0, Math.min(MAX_TIER, t));
        if (nt != d.tier) d.strikes = 0;
        d.tier = nt;
    }

    private int killsOf(Player p) {
        PlayerData d = data.get(p.getUniqueId());
        return d == null ? 0 : d.kills;
    }

    private void setKills(Player p, int k) {
        dataOf(p.getUniqueId()).kills = k;
    }

    private int pointsOf(Player p) {
        PlayerData d = data.get(p.getUniqueId());
        return d == null ? 0 : d.points;
    }

    private Location spawnOf(String team) {
        return team == null ? null : getConfig().getLocation("spawn." + team);
    }

    private static String cap(String s) {
        return s.substring(0, 1).toUpperCase() + s.substring(1);
    }

    private static Component line(String text, NamedTextColor color) {
        return Component.text(text, color).decoration(TextDecoration.ITALIC, false);
    }

    private static void msg(CommandSender s, String text, NamedTextColor color) {
        s.sendMessage(Component.text(text, color));
    }

    private static Color teamColor(String team) {
        return "pig".equals(team) ? Color.fromRGB(0xFF8FB8) : Color.fromRGB(0xFFD83A);
    }

    private UUID resolveId(String name) {
        Player on = Bukkit.getPlayerExact(name);
        if (on != null) return on.getUniqueId();
        OfflinePlayer off = Bukkit.getOfflinePlayerIfCached(name);
        return off == null ? null : off.getUniqueId();
    }

    private String nameOf(UUID id) {
        String n = Bukkit.getOfflinePlayer(id).getName();
        return n == null ? id.toString().substring(0, 8) : n;
    }

    // ---------------------------------------------------------------- teams (balanced on every join)
    private String pickBalancedTeam(Player joining) {
        if (Bukkit.getOnlinePlayers().size() <= 1) { // first one in: pure random
            return ThreadLocalRandom.current().nextBoolean() ? "pig" : "cheese";
        }
        int pig = 0, cheese = 0;
        for (Player o : Bukkit.getOnlinePlayers()) {
            if (o.equals(joining)) continue;
            String t = teamOf(o);
            if ("pig".equals(t)) pig++;
            else if ("cheese".equals(t)) cheese++;
        }
        if (pig != cheese) return pig < cheese ? "pig" : "cheese";
        String prev = teamOf(joining); // tie: stay on your old team if you had one
        if (prev != null) return prev;
        return ThreadLocalRandom.current().nextBoolean() ? "pig" : "cheese";
    }

    private void assignTeam(Player p, String team, boolean teleport) {
        boolean fresh = !data.containsKey(p.getUniqueId());
        PlayerData d = dataOf(p.getUniqueId());
        String oldTeam = d.team;
        d.team = team;
        if (oldTeam != null && !oldTeam.equals(team)) moveListings(p.getUniqueId(), oldTeam, team);
        if (fresh) {
            d.tier = 0;
            d.kills = 0;
            d.appliedTierVersion = globalTierVersion;
        }
        refreshTeamEntry(p);
        applyTier(p); // gives missing gear and recolours leather armour
        Location spawn = spawnOf(team);
        if (teleport && spawn != null) p.teleport(spawn);
        p.sendMessage(Component.text("You are on Team " + cap(team) + "!",
                team.equals("pig") ? NamedTextColor.LIGHT_PURPLE : NamedTextColor.YELLOW));
        saveData();
    }

    // ---------------------------------------------------------------- spawn protection
    private boolean isSpawnProtected(Player p) {
        Long until = protectedUntil.get(p.getUniqueId());
        return until != null && until > System.currentTimeMillis();
    }

    private void giveProtection(Player p) {
        protectedUntil.put(p.getUniqueId(), System.currentTimeMillis() + SPAWN_PROTECTION_MS);
        p.sendMessage(Component.text("You have 30 seconds of spawn protection (it ends early if you attack).", NamedTextColor.AQUA));
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onProtectedDamage(EntityDamageEvent e) {
        if (!(e.getEntity() instanceof Player p)) return;
        EntityDamageEvent.DamageCause c = e.getCause();
        if (c == EntityDamageEvent.DamageCause.VOID || c == EntityDamageEvent.DamageCause.KILL
                || c == EntityDamageEvent.DamageCause.SUICIDE) return;
        if (isSpawnProtected(p) || frozen.contains(p.getUniqueId()) || guidePending.contains(p.getUniqueId())) {
            e.setCancelled(true);
        }
    }

    // ---------------------------------------------------------------- combat mode
    /** True when this player is standing inside the OTHER team's base (and base PvP is switched on). */
    private boolean isIntruder(Player p) {
        if (!getConfig().getBoolean("base.pvp", true)) return false;
        String t = teamOf(p);
        if (t == null) return false;
        String enemy = t.equals("pig") ? "cheese" : "pig";
        Location c = getConfig().getLocation("base." + enemy + ".center");
        double r = getConfig().getDouble("base." + enemy + ".radius", 0);
        if (c == null || c.getWorld() == null || r <= 0 || !c.getWorld().equals(p.getWorld())) return false;
        Location l = p.getLocation();
        double dx = l.getX() - c.getX(), dz = l.getZ() - c.getZ();
        return dx * dx + dz * dz <= r * r;
    }

    private void tickBases() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            boolean now = isIntruder(p);
            boolean was = intruders.contains(p.getUniqueId());
            if (now && !was) {
                intruders.add(p.getUniqueId());
                String enemy = "pig".equals(teamOf(p)) ? "cheese" : "pig";
                msg(p, "You entered Team " + cap(enemy) + "'s base - PvP is open here, no duel needed!", NamedTextColor.RED);
                for (Player o : Bukkit.getOnlinePlayers()) {
                    if (enemy.equals(teamOf(o))) msg(o, p.getName() + " has entered your base! PvP is open against them.", NamedTextColor.RED);
                }
            } else if (!now && was) {
                intruders.remove(p.getUniqueId());
                msg(p, "You left the enemy base - PvP needs a duel again.", NamedTextColor.GREEN);
            }
        }
    }

    private void adminBase(CommandSender sender, String name, String[] args) {
        switch (name) {
            case "setbase" -> {
                if (!(sender instanceof Player p)) {
                    msg(sender, "Run this in-game, standing in the middle of the base.", NamedTextColor.RED);
                    return;
                }
                if (args.length != 2 || !(args[0].equalsIgnoreCase("pig") || args[0].equalsIgnoreCase("cheese"))) {
                    msg(p, "Usage: /setbase <pig|cheese> <radius in blocks>", NamedTextColor.RED);
                    return;
                }
                int r;
                try {
                    r = Integer.parseInt(args[1]);
                } catch (NumberFormatException ex) {
                    r = 0;
                }
                if (r < 5 || r > 500) {
                    msg(p, "The radius must be between 5 and 500 blocks.", NamedTextColor.RED);
                    return;
                }
                String team = args[0].toLowerCase();
                getConfig().set("base." + team + ".center", p.getLocation());
                getConfig().set("base." + team + ".radius", r);
                saveConfig();
                msg(p, "Team " + team + "'s base set here with a radius of " + r + " blocks. Enemies inside it can fight without a duel"
                        + (getConfig().getBoolean("base.pvp", true) ? "." : " (but /basepvp is currently OFF)."), NamedTextColor.GREEN);
            }
            case "removebase" -> {
                if (args.length != 1 || !(args[0].equalsIgnoreCase("pig") || args[0].equalsIgnoreCase("cheese"))) {
                    msg(sender, "Usage: /removebase <pig|cheese>", NamedTextColor.RED);
                    return;
                }
                getConfig().set("base." + args[0].toLowerCase(), null);
                saveConfig();
                msg(sender, "Team " + args[0].toLowerCase() + "'s base removed.", NamedTextColor.YELLOW);
            }
            case "basepvp" -> {
                if (args.length == 1 && (args[0].equalsIgnoreCase("on") || args[0].equalsIgnoreCase("off"))) {
                    getConfig().set("base.pvp", args[0].equalsIgnoreCase("on"));
                    saveConfig();
                }
                msg(sender, "Base PvP (enemies raiding a base can fight without a duel) is "
                        + (getConfig().getBoolean("base.pvp", true) ? "ON" : "OFF") + ". Use /basepvp on|off to change it.", NamedTextColor.AQUA);
            }
            default -> {
            }
        }
    }

    private boolean isInCombat(Player p) {
        Long u = combatUntil.get(p.getUniqueId());
        return u != null && u > System.currentTimeMillis();
    }

    private void tagCombat(Player p) {
        boolean was = isInCombat(p);
        combatUntil.put(p.getUniqueId(), System.currentTimeMillis() + COMBAT_MS);
        if (!was) {
            p.sendMessage(Component.text("You are in combat for 20 seconds! Logging out or teleporting now will cost you "
                    + COMBAT_TIER_LOSS + " tiers and " + COMBAT_POINT_LOSS + " points.", NamedTextColor.RED));
        }
    }

    /** Applies the combat-escape punishment to saved data and returns a short summary. */
    private String punish(PlayerData d) {
        int oldTier = d.tier;
        int oldPoints = d.points;
        d.tier = Math.max(0, d.tier - COMBAT_TIER_LOSS); // never below tier 1
        d.kills = 0;
        d.streak = 0;
        d.strikes = 0;
        d.points = Math.max(0, d.points - COMBAT_POINT_LOSS);
        return "tier " + (oldTier + 1) + " -> " + (d.tier + 1) + " and -" + (oldPoints - d.points) + " points";
    }

    private void punishCombat(Player p, String reason) {
        if (teamOf(p) == null) return;
        combatUntil.remove(p.getUniqueId());
        String summary = punish(dataOf(p.getUniqueId()));
        applyTier(p);
        saveData();
        p.sendMessage(Component.text("PUNISHED for " + reason + " while in combat: " + summary + ".", NamedTextColor.RED));
    }

    private Player attackerOf(EntityDamageByEntityEvent e) {
        Entity d = e.getDamager();
        if (d instanceof Player p) return p;
        if (d instanceof Projectile pr && pr.getShooter() instanceof Player p) return p;
        return null;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCombatTag(EntityDamageByEntityEvent e) {
        if (!(e.getEntity() instanceof Player victim)) return;
        Player attacker = attackerOf(e);
        if (attacker == null || attacker.equals(victim)) return;
        if (areDueling(attacker, victim)) {
            String ta = teamOf(attacker), tv = teamOf(victim);
            if (ta != null && ta.equals(tv)) return; // friendly duel: no combat tag
        }
        tagCombat(victim);
        tagCombat(attacker);
    }

    /** Teleports a player, applying the combat punishment first if they are in combat. */
    private boolean teleportPlayer(Player p, Location dest, String reason) {
        if (frozen.contains(p.getUniqueId())) {
            msg(p, "You are frozen and can't teleport.", NamedTextColor.RED);
            return false;
        }
        if (dest == null || dest.getWorld() == null) {
            msg(p, "That location isn't available.", NamedTextColor.RED);
            return false;
        }
        if (isInCombat(p)) punishCombat(p, "teleporting (" + reason + ")");
        p.teleport(dest);
        return true;
    }

    // ---------------------------------------------------------------- kit
    private ItemStack makeGear(Material m, Color color) {
        ItemStack item = new ItemStack(m);
        item.editMeta(meta -> {
            meta.setUnbreakable(true);
            if (meta instanceof LeatherArmorMeta lm) lm.setColor(color);
        });
        return item;
    }

    private ItemStack getArmor(PlayerInventory inv, String piece) {
        return switch (piece) {
            case "HELMET" -> inv.getHelmet();
            case "CHESTPLATE" -> inv.getChestplate();
            case "LEGGINGS" -> inv.getLeggings();
            default -> inv.getBoots();
        };
    }

    private void setArmor(PlayerInventory inv, String piece, ItemStack item) {
        switch (piece) {
            case "HELMET" -> inv.setHelmet(item);
            case "CHESTPLATE" -> inv.setChestplate(item);
            case "LEGGINGS" -> inv.setLeggings(item);
            default -> inv.setBoots(item);
        }
    }

    private boolean hasGear(PlayerInventory inv, String suffix) {
        for (ItemStack i : inv.getContents()) {
            if (isGear(i) && i.getType().name().endsWith(suffix)) return true;
        }
        return false;
    }

    /** Full set for the player's tier: 4 armour pieces, sword, spear, axe. Netherite also gets a mace + wind charges. */
    private void ensureKit(Player p) {
        String team = teamOf(p);
        if (team == null) return;
        int tier = tierOf(p);
        Color color = teamColor(team);
        PlayerInventory inv = p.getInventory();

        for (String piece : PIECES) {
            ItemStack cur = getArmor(inv, piece);
            if (cur == null || cur.getType().isAir()) {
                Material m = Material.matchMaterial(ARMOR[tier] + "_" + piece);
                if (m != null) setArmor(inv, piece, makeGear(m, color));
            }
        }
        for (String type : WEAPON_TYPES) {
            if (!hasGear(inv, "_" + type)) {
                Material m = Material.matchMaterial(WEAPON[tier] + "_" + type);
                if (m != null) inv.addItem(makeGear(m, color));
            }
        }

        if (tier == MAX_TIER) {
            if (!hasGear(inv, "MACE")) inv.addItem(makeGear(Material.MACE, color));
            int have = 0;
            for (ItemStack i : inv.getContents()) {
                if (i != null && i.getType() == Material.WIND_CHARGE) have += i.getAmount();
            }
            if (have < WIND_CHARGES) inv.addItem(new ItemStack(Material.WIND_CHARGE, WIND_CHARGES - have));
        } else {
            inv.remove(Material.MACE);
            inv.remove(Material.WIND_CHARGE);
            // Inventory.remove() skips the off-hand slot, so clear that one by hand
            Material off = inv.getItemInOffHand().getType();
            if (off == Material.MACE || off == Material.WIND_CHARGE) inv.setItemInOffHand(null);
        }
    }

    private void applyTier(Player p) {
        int tier = tierOf(p);
        Color color = teamColor(teamOf(p));
        ensureKit(p);
        PlayerInventory inv = p.getInventory();
        ItemStack[] contents = inv.getContents();
        for (int i = 0; i < contents.length; i++) {
            contents[i] = convert(contents[i], tier);
            ItemStack it = contents[i];
            if (it != null && isGear(it) && it.getType().name().startsWith("LEATHER_")) {
                it.editMeta(LeatherArmorMeta.class, m -> m.setColor(color));
            }
            if (it != null && isGear(it)) {
                if (evRandomEnchants) {
                    if (it.getEnchantments().isEmpty()) addRandomEnchants(it); // event: random enchantments
                } else if (!it.getEnchantments().isEmpty()) {
                    stripEnchants(it); // normal play: tier gear carries no enchantments
                }
            }
        }
        inv.setContents(contents);
    }

    private void stripEnchants(ItemStack it) {
        for (Enchantment e : new ArrayList<>(it.getEnchantments().keySet())) it.removeEnchantment(e);
    }

    private void stripGearEnchants(Player p) {
        PlayerInventory inv = p.getInventory();
        ItemStack[] contents = inv.getContents();
        for (ItemStack it : contents) {
            if (it != null && isGear(it)) stripEnchants(it);
        }
        inv.setContents(contents);
    }

    /** Event enchants: ANY enchantment in the game, even ones that don't belong on this item. */
    private void addRandomEnchants(ItemStack it) {
        List<Enchantment> pool = new ArrayList<>();
        for (Enchantment e : Registry.ENCHANTMENT) pool.add(e);
        Collections.shuffle(pool);
        int want = 3 + ThreadLocalRandom.current().nextInt(3); // 3-5 enchantments
        List<Enchantment> chosen = new ArrayList<>(pool.subList(0, Math.min(want, pool.size())));
        it.editMeta(m -> {
            for (Enchantment e : chosen) {
                m.addEnchant(e, 1 + ThreadLocalRandom.current().nextInt(Math.max(1, e.getMaxLevel())), true);
            }
        });
    }

    private ItemStack convert(ItemStack item, int tier) {
        if (item == null || item.getType().isAir()) return item;
        if (isGear(item)) item.editMeta(m -> m.setUnbreakable(true));
        String n = item.getType().name();
        for (String piece : PIECES) {
            for (String t : ARMOR) {
                if (n.equals(t + "_" + piece)) return swap(item, ARMOR[tier] + "_" + piece);
            }
        }
        for (String type : WEAPON_TYPES) {
            for (String t : WEAPON) {
                if (n.equals(t + "_" + type)) return swap(item, WEAPON[tier] + "_" + type);
            }
        }
        return item;
    }

    private ItemStack swap(ItemStack item, String newMaterial) {
        Material m = Material.matchMaterial(newMaterial);
        if (m == null || m == item.getType()) return item;
        ItemStack result = item.withType(m); // keeps enchants / name / components
        result.editMeta(meta -> meta.setUnbreakable(true));
        return result;
    }

    // ---------------------------------------------------------------- guide (/help, /guide) + first-join screen
    private void sendGuide(Player p) {
        p.sendMessage(Component.text("========= Pig vs Cheese: How to play =========", NamedTextColor.GOLD));
        guide(p, "Teams", "You are put on Team Pig or Team Cheese automatically, and re-balanced every time you join so teams stay even. PvP is only allowed in duels (see Duels), during events, and when an enemy raids your base.");
        guide(p, "Tiers", "Kills on the enemy team upgrade your sword, spear, axe and armour one tier (leather -> copper -> chainmail -> iron -> diamond -> netherite). Kills needed per tier: " + killsScaleText() + ". Only deaths caused by other players count at all - lava, falls and mobs never cost a tier, add to your death count or break your streak. Dying doesn't wipe your kill progress, and lower tiers are harder to lose: it takes 4 deaths to drop from tier 2, 3 from tier 3, 2 from tiers 4 and 5, and 1 from netherite. Your tier is saved when you leave.");
        guide(p, "Your gear", "It never breaks and you can't lose, drop, store or sell it. Armour, swords, spears and axes can't be crafted, bought or looted - the only way up is kills.");
        guide(p, "Netherite perks", "Netherite tier also gets a mace and wind charges that never run out.");
        guide(p, "Combat", "Hitting or being hit by a player puts you in combat for 20 seconds. Logging out or teleporting (/spawn, /tpa, waypoints, portals) in combat drops you 3 tiers (never below tier 1) and costs 10 points.");
        guide(p, "Kill points", "Each enemy kill earns points - killing a higher-tier player pays more. Check them with /points.");
        guide(p, "/shop", "Spend points on almost any item. Left-click buys 1, shift-click buys 16, right-click buys a full stack. Click the compass to search (then type in chat), or use /shop <words>.");
        guide(p, "/ah", "Two auctions: /ah is your team's private one and /ah global is open to every player (a button inside switches between them). /ah sell <price> lists the item in your hand for your team - add 'global' on the end to sell to everyone. Click your own listing to take it back.");
        guide(p, "Duels", "PvP needs a duel. Press Escape and click 'Duel a player' (or type /duel) for a menu of players, or /duel <name> to challenge someone directly - they accept with /duelaccept or /dueldeny. While you duel, nobody else can hurt you or join in. Duels against teammates cost nothing; duels against the enemy team use the normal death rules. /duel end stops it. Exceptions: PvP is open during events, and when a player walks into the other team's base (operators mark bases with /setbase).");
        guide(p, "Stats", "/stats shows your tier, playtime, kills, deaths, K/D, streaks, points and duels (/stats <player> to look at someone else). /playtime shows just your playtime.");
        guide(p, "Events", "Operators can run timed events (for example Chaos: a random tier and random enchantments after every player kill). Some events can be free-for-all, where teams don't matter and everyone can fight everyone. Kill points work the same, and when an event ends your tier and gear go back to how they were. /eventwaypoints lists free teleports that only work while an event is running.");
        guide(p, "Banker block", "Craft it with 2 diamonds and 6 iron blocks (iron blocks on the top and bottom rows, a diamond on each side of the middle row). Place it, right-click it, put items in and press Deposit: every " + BANKER_ITEMS_PER_POINT + " items you put in gives 1 kill point. Handy when you're playing alone. If it looks like a plain note block, accept the server resource pack (or type /pack).");
        guide(p, "Teleports", "/spawn goes to your team's home. /tpa <teammate> asks a teammate to let you teleport to them (/tpaccept or /tpdeny).");
        guide(p, "Waypoints", "/createwaypoint <name> saves your location. /waypoints lists them: you can always see where they are, and pay 100 points to unlock teleporting to one forever. /managewaypoint to move, rename or delete.");
        guide(p, "Disabled", "Redstone, explosives (TNT, crystals, anchors, fireworks), ender pearls, chorus fruit, elytra, riptide and minecarts. Boats, saddles and spears are allowed.");
        guide(p, "Spawn protection", "30 seconds after joining or respawning (it ends early if you attack someone).");
        guide(p, "Commands", "/help  /stats  /shop  /ah  /points  /duel  /spawn  /tpa  /waypoints  /createwaypoint  /managewaypoint");
        if (p.hasPermission("pigcheese.admin")) {
            guide(p, "Operator commands", "/teampig /teamcheese <player>  /lockteam <player> <team>  /unlockteam <player>  /setteamspawn <team>  /tierup /tierdown <player>  /settier <player> <1-6>  /givepoints /setpoints <player> <n>  /resetplayer /clearcombat /freeze /unfreeze /invsee /playerinfo <player>  /mute <player> [minutes]  /unmute <player>  /setprice <item|hand> <price>  /resetprice <item|hand>  /settierall <1-6|cancel>  /event start|stop|status|list  /seteventwaypoint <name>  /removeeventwaypoint <name>  /givebanker [player]  /setpack <url> <sha1>  /setbase <team> <radius>  /removebase <team>  /basepvp on|off  TEST: /testnewplayer /testguide /testcombat /testpunish /testprotect /testkill /testgear /testbalance");
        }
    }

    private void guide(Player p, String head, String body) {
        p.sendMessage(Component.text(head + ": ", NamedTextColor.YELLOW).append(Component.text(body, NamedTextColor.WHITE)));
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onHelpCommand(PlayerCommandPreprocessEvent e) {
        String m = e.getMessage().trim().toLowerCase();
        if (m.equals("/help") || m.startsWith("/help ") || m.equals("/?") || m.equals("/minecraft:help") || m.equals("/bukkit:help")) {
            e.setCancelled(true);
            sendGuide(e.getPlayer());
        }
    }

    private ItemStack guideItem(Material mat, String title, String... lines) {
        ItemStack it = new ItemStack(mat);
        it.editMeta(m -> {
            m.displayName(line(title, NamedTextColor.GOLD));
            List<Component> lore = new ArrayList<>();
            for (String l : lines) lore.add(line(l, NamedTextColor.WHITE));
            m.lore(lore);
        });
        return it;
    }

    /** Full-screen style welcome menu: the world is blacked out and it can't be closed until "I understand" is clicked. */
    private void openGuideMenu(Player p) {
        GuideHolder h = new GuideHolder();
        Inventory inv = Bukkit.createInventory(h, 54, Component.text("Welcome! Read this, then click I UNDERSTAND"));
        h.inv = inv;

        ItemStack filler = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        filler.editMeta(m -> m.displayName(Component.text(" ")));
        for (int i = 0; i < 54; i++) inv.setItem(i, filler);

        inv.setItem(10, guideItem(Material.WHITE_BANNER, "Teams",
                "You're put on Team Pig or Team Cheese", "automatically and re-balanced every join.", "PvP only happens in duels (see Duels)."));
        inv.setItem(11, guideItem(Material.IRON_SWORD, "Tiers",
                "Enemy kills upgrade your sword, spear, axe", "and armour. Kills needed per tier:", killsScaleText() + ". Low tiers take several", "deaths to lose (4, 3, 2, 2, then 1).", "Only deaths from other players count -", "lava, falls and mobs are ignored completely.",
                "Leather > copper > chainmail > iron", "> diamond > netherite. Tier is saved."));
        inv.setItem(12, guideItem(Material.SHIELD, "Your gear",
                "It never breaks and can't be lost,", "dropped, stored or sold.", "Armour/weapons can't be crafted or bought."));
        inv.setItem(13, guideItem(Material.WIND_CHARGE, "Netherite perks",
                "Netherite tier also gets a mace and", "wind charges that never run out."));
        inv.setItem(14, guideItem(Material.GOLD_INGOT, "Kill points",
                "Enemy kills earn points. Higher-tier", "victims pay more. Check with /points."));
        inv.setItem(15, guideItem(Material.EMERALD, "/shop",
                "Buy almost any item with points.", "Click the compass to search.", "Left 1, shift 16, right = a stack."));
        inv.setItem(16, guideItem(Material.CHEST, "/ah",
                "Team auction: /ah. Everyone: /ah global.", "/ah sell <price> [global] lists the", "item in your hand for points."));
        inv.setItem(28, guideItem(Material.DIAMOND_SWORD, "Combat mode",
                "Hit or get hit by a player = combat", "for 20 seconds.", "Logging out or teleporting in combat:",
                "-3 tiers (min tier 1) and -10 points."));
        inv.setItem(29, guideItem(Material.COMPASS, "Teleports",
                "/spawn = your team's home.", "/tpa <teammate> to join a teammate", "(they use /tpaccept)."));
        inv.setItem(30, guideItem(Material.ENDER_EYE, "Waypoints",
                "/createwaypoint <name> saves a spot.", "/waypoints shows them. Pay 100 points", "to unlock teleporting to one forever."));
        inv.setItem(31, guideItem(Material.BARRIER, "Disabled",
                "Redstone, explosives, ender pearls,", "chorus fruit, elytra, riptide, minecarts.", "Boats, saddles and spears are fine."));
        inv.setItem(32, guideItem(Material.CLOCK, "Spawn protection",
                "30 seconds after joining or respawning.", "It ends early if you attack someone."));
        inv.setItem(33, guideItem(Material.BOOK, "Need this again?",
                "Type /help any time."));
        inv.setItem(34, guideItem(Material.GOLDEN_SWORD, "Duels",
                "PvP needs a duel! Press Escape >", "Duel a player, or type /duel <name>.", "Nobody else can hurt you. Teammate duels",
                "are free; enemy duels use normal death", "rules. PvP is open in events and when", "an enemy enters your base."));

        ItemStack ok = new ItemStack(Material.LIME_CONCRETE);
        ok.editMeta(m -> {
            m.displayName(line("I UNDERSTAND - CLICK TO START PLAYING", NamedTextColor.GREEN).decorate(TextDecoration.BOLD));
            m.lore(List.of(line("You can't play until you click this.", NamedTextColor.GRAY)));
        });
        inv.setItem(49, ok);

        p.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 5_000_000, 0, false, false, false));
        p.openInventory(inv);
    }

    private void acceptGuide(Player p) {
        guidePending.remove(p.getUniqueId());
        p.closeInventory();
        p.removePotionEffect(PotionEffectType.BLINDNESS);
        p.setInvulnerable(false);
        dataOf(p.getUniqueId()).seenGuide = true;
        saveData();
        giveProtection(p);
        p.sendMessage(Component.text("Have fun! Type /help any time to see the guide again.", NamedTextColor.GREEN));
    }

    @EventHandler
    public void onGuideClose(InventoryCloseEvent e) {
        if (!(e.getInventory().getHolder() instanceof GuideHolder)) return;
        if (!(e.getPlayer() instanceof Player p)) return;
        if (!guidePending.contains(p.getUniqueId())) {
            p.removePotionEffect(PotionEffectType.BLINDNESS); // closed a preview / already accepted
            return;
        }
        Bukkit.getScheduler().runTaskLater(this, () -> {
            if (p.isOnline() && guidePending.contains(p.getUniqueId())) openGuideMenu(p);
        }, 2L);
    }

    // ---------------------------------------------------------------- commands
    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        String name = cmd.getName().toLowerCase();

        switch (name) {
            case "guide" -> {
                if (sender instanceof Player p) sendGuide(p);
                return true;
            }
            case "shop" -> {
                if (sender instanceof Player p) openShop(p, String.join(" ", args).trim(), 0);
                return true;
            }
            case "points" -> {
                if (sender instanceof Player p) {
                    p.sendMessage(Component.text("You have " + pointsOf(p) + " kill points.", NamedTextColor.GOLD));
                }
                return true;
            }
            case "ah" -> {
                if (sender instanceof Player p) handleAh(p, args);
                return true;
            }
            case "spawn" -> {
                if (sender instanceof Player p) cmdSpawn(p);
                return true;
            }
            case "tpa" -> {
                if (sender instanceof Player p) cmdTpa(p, args);
                return true;
            }
            case "tpaccept" -> {
                if (sender instanceof Player p) cmdTpAccept(p);
                return true;
            }
            case "tpdeny" -> {
                if (sender instanceof Player p) cmdTpDeny(p);
                return true;
            }
            case "duelmenu" -> {
                if (sender instanceof Player p) openDuelMenu(p, 0);
                return true;
            }
            case "pack" -> {
                if (sender instanceof Player p) {
                    sendPack(p);
                    msg(p, "Sending the server resource pack... accept it to see the Banker Block texture.", NamedTextColor.AQUA);
                }
                return true;
            }
            case "eventwaypoints" -> {
                if (sender instanceof Player p) openEventWaypoints(p);
                return true;
            }
            case "stats" -> {
                if (sender instanceof Player p) cmdStats(p, args);
                return true;
            }
            case "playtime" -> {
                if (sender instanceof Player p) cmdPlaytime(p, args);
                return true;
            }
            case "duel" -> {
                if (sender instanceof Player p) cmdDuel(p, args);
                return true;
            }
            case "duelaccept" -> {
                if (sender instanceof Player p) cmdDuelAccept(p);
                return true;
            }
            case "dueldeny" -> {
                if (sender instanceof Player p) cmdDuelDeny(p);
                return true;
            }
            case "createwaypoint" -> {
                if (sender instanceof Player p) cmdCreateWaypoint(p, args);
                return true;
            }
            case "waypoints" -> {
                if (sender instanceof Player p) openWaypoints(p, false);
                return true;
            }
            case "managewaypoint" -> {
                if (sender instanceof Player p) cmdManageWaypoint(p, args);
                return true;
            }
            default -> {
            }
        }

        // ----- operator commands -----
        if (!sender.hasPermission("pigcheese.admin")) {
            msg(sender, "Operators only.", NamedTextColor.RED);
            return true;
        }
        handleAdmin(sender, name, args);
        return true;
    }

    // ---------------------------------------------------------------- /spawn and /tpa
    // ---------------------------------------------------------------- stats + playtime
    private long playSecondsOf(UUID id) {
        PlayerData d = data.get(id);
        long base = d == null ? 0 : d.playSeconds;
        Long st = sessionStart.get(id);
        return base + (st == null ? 0 : Math.max(0, (System.currentTimeMillis() - st) / 1000));
    }

    private void flushPlaytime(UUID id, boolean keepRunning) {
        Long st = keepRunning ? sessionStart.get(id) : sessionStart.remove(id);
        if (st == null) return;
        long secs = (System.currentTimeMillis() - st) / 1000;
        if (secs > 0) dataOf(id).playSeconds += secs;
        if (keepRunning) sessionStart.put(id, st + secs * 1000);
    }

    private static String formatDuration(long secs) {
        long d = secs / 86400, h = (secs % 86400) / 3600, m = (secs % 3600) / 60;
        if (d > 0) return d + "d " + h + "h " + m + "m";
        if (h > 0) return h + "h " + m + "m";
        return m + "m " + (secs % 60) + "s";
    }

    private ItemStack statItem(Material mat, String title, NamedTextColor color, String... lines) {
        ItemStack it = new ItemStack(mat);
        it.editMeta(m -> {
            m.displayName(line(title, color));
            List<Component> lore = new ArrayList<>();
            for (String l : lines) lore.add(line(l, NamedTextColor.WHITE));
            m.lore(lore);
        });
        return it;
    }

    private void cmdStats(Player p, String[] args) {
        UUID id = args.length == 0 ? p.getUniqueId() : resolveId(args[0]);
        if (id == null) {
            msg(p, "I don't know that player.", NamedTextColor.RED);
            return;
        }
        openStats(p, id);
    }

    private void cmdPlaytime(Player p, String[] args) {
        UUID id = args.length == 0 ? p.getUniqueId() : resolveId(args[0]);
        if (id == null || data.get(id) == null) {
            msg(p, "No playtime found for that player.", NamedTextColor.RED);
            return;
        }
        String who = id.equals(p.getUniqueId()) ? "Your" : nameOf(id) + "'s";
        msg(p, who + " playtime: " + formatDuration(playSecondsOf(id)), NamedTextColor.AQUA);
    }

    private void openStats(Player viewer, UUID id) {
        PlayerData d = data.get(id);
        if (d == null) {
            msg(viewer, "No stats found for that player yet.", NamedTextColor.RED);
            return;
        }
        String name = nameOf(id);
        int tier = Math.max(0, Math.min(MAX_TIER, d.tier));
        String tierName = cap(ARMOR[tier].toLowerCase());
        NamedTextColor tc = "pig".equals(d.team) ? NamedTextColor.LIGHT_PURPLE
                : ("cheese".equals(d.team) ? NamedTextColor.YELLOW : NamedTextColor.WHITE);

        StatsHolder h = new StatsHolder();
        Inventory inv = Bukkit.createInventory(h, 36, Component.text(name + "'s Stats"));
        h.inv = inv;

        ItemStack filler = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        filler.editMeta(m -> m.displayName(Component.text(" ")));
        for (int i = 0; i < 36; i++) inv.setItem(i, filler);

        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        String teamLine = "Team: " + (d.team == null ? "none" : cap(d.team)) + (d.lockedTeam != null ? " (locked)" : "");
        head.editMeta(SkullMeta.class, m -> {
            m.setOwningPlayer(Bukkit.getOfflinePlayer(id));
            m.displayName(line(name, tc));
            m.lore(List.of(line(teamLine, tc)));
        });
        inv.setItem(4, head);

        Material tierIcon = Material.matchMaterial(ARMOR[tier] + "_CHESTPLATE");
        if (tierIcon == null) tierIcon = Material.IRON_CHESTPLATE;
        String progress = tier >= MAX_TIER ? "Max tier reached!" : "Kills toward next tier: " + d.kills + "/" + killsNeeded(tier);
        String dropLine = tier <= 0 ? "Can't drop any lower"
                : "Deaths toward losing a tier: " + d.strikes + "/" + deathsToDrop(tier);
        inv.setItem(10, statItem(tierIcon, "Tier " + (tier + 1) + " of " + (MAX_TIER + 1), NamedTextColor.GOLD,
                tierName + " gear", progress, dropLine));

        inv.setItem(11, statItem(Material.CLOCK, "Playtime", NamedTextColor.AQUA,
                formatDuration(playSecondsOf(id)), "(counted since stats were added)"));

        inv.setItem(12, statItem(Material.GOLD_INGOT, "Kill points", NamedTextColor.GOLD,
                "Current: " + d.points, "Earned all-time: " + d.pointsEarned, "Items banked: " + d.itemsBanked));

        String kdr = d.deaths == 0 ? String.valueOf(d.totalKills)
                : String.format(java.util.Locale.US, "%.2f", d.totalKills / (double) d.deaths);
        inv.setItem(13, statItem(Material.DIAMOND_SWORD, "Kills and deaths", NamedTextColor.RED,
                "Kills: " + d.totalKills, "Deaths: " + d.deaths, "K/D: " + kdr));

        inv.setItem(14, statItem(Material.BLAZE_POWDER, "Kill streak", NamedTextColor.GOLD,
                "Current: " + d.streak, "Best: " + d.bestStreak));

        int best = Math.max(d.highestTier, tier);
        inv.setItem(15, statItem(Material.NETHER_STAR, "Highest tier reached", NamedTextColor.LIGHT_PURPLE,
                "Tier " + (best + 1) + " (" + cap(ARMOR[best].toLowerCase()) + ")"));

        inv.setItem(16, statItem(Material.GOLDEN_SWORD, "Duels", NamedTextColor.YELLOW,
                "Won: " + d.duelWins, "Lost: " + d.duelLosses));

        List<Waypoint> wl = waypoints.get(id);
        int wpTotal = wl == null ? 0 : wl.size();
        int wpUnlocked = 0;
        if (wl != null) for (Waypoint w : wl) if (w.unlocked) wpUnlocked++;
        inv.setItem(21, statItem(Material.ENDER_EYE, "Waypoints", NamedTextColor.GREEN,
                "Saved: " + wpTotal + "/" + MAX_WAYPOINTS, "Teleport unlocked: " + wpUnlocked));

        int listings = 0;
        for (List<Listing> ls : auctions.values()) for (Listing l : ls) if (l.seller.equals(id)) listings++;
        inv.setItem(23, statItem(Material.CHEST, "Auction listings", NamedTextColor.GREEN,
                "Active: " + listings + "/" + MAX_LISTINGS_PER_PLAYER));

        viewer.openInventory(inv);
    }

    private void cmdSpawn(Player p) {
        String team = teamOf(p);
        Location spawn = spawnOf(team);
        if (spawn == null) {
            msg(p, "Your team's spawn hasn't been set yet (an operator uses /setteamspawn).", NamedTextColor.RED);
            return;
        }
        if (teleportPlayer(p, spawn, "/spawn")) msg(p, "Teleported to your team's spawn.", NamedTextColor.GREEN);
    }

    private void cmdTpa(Player p, String[] args) {
        if (args.length != 1) {
            msg(p, "Usage: /tpa <teammate>", NamedTextColor.RED);
            return;
        }
        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null || target.equals(p)) {
            msg(p, "Pick an online teammate (not yourself).", NamedTextColor.RED);
            return;
        }
        String a = teamOf(p), b = teamOf(target);
        if (a == null || !a.equals(b)) {
            msg(p, "You can only /tpa to players on your own team.", NamedTextColor.RED);
            return;
        }
        tpaRequests.put(target.getUniqueId(), new TpaRequest(p.getUniqueId(), System.currentTimeMillis() + TPA_EXPIRE_MS));
        msg(p, "Teleport request sent to " + target.getName() + " (expires in 60s).", NamedTextColor.GREEN);
        if (isInCombat(p)) {
            msg(p, "Warning: you're in combat - teleporting while in combat is punished!", NamedTextColor.RED);
        }
        Component accept = Component.text("[ACCEPT]", NamedTextColor.GREEN).clickEvent(ClickEvent.runCommand("/tpaccept"));
        Component deny = Component.text("[DENY]", NamedTextColor.RED).clickEvent(ClickEvent.runCommand("/tpdeny"));
        target.sendMessage(Component.text(p.getName() + " wants to teleport to you. ", NamedTextColor.YELLOW)
                .append(accept).append(Component.text(" ")).append(deny));
    }

    private void cmdTpAccept(Player p) {
        TpaRequest r = tpaRequests.remove(p.getUniqueId());
        if (r == null || r.expires() < System.currentTimeMillis()) {
            msg(p, "You have no pending teleport request.", NamedTextColor.RED);
            return;
        }
        Player from = Bukkit.getPlayer(r.from());
        if (from == null) {
            msg(p, "That player went offline.", NamedTextColor.RED);
            return;
        }
        String a = teamOf(from), b = teamOf(p);
        if (a == null || !a.equals(b)) {
            msg(p, "That player is no longer on your team.", NamedTextColor.RED);
            return;
        }
        msg(p, "Accepted. " + from.getName() + " is teleporting to you.", NamedTextColor.GREEN);
        if (teleportPlayer(from, p.getLocation(), "/tpa")) {
            msg(from, "Teleported to " + p.getName() + ".", NamedTextColor.GREEN);
        }
    }

    private void cmdTpDeny(Player p) {
        TpaRequest r = tpaRequests.remove(p.getUniqueId());
        if (r == null) {
            msg(p, "You have no pending teleport request.", NamedTextColor.RED);
            return;
        }
        msg(p, "Request denied.", NamedTextColor.YELLOW);
        Player from = Bukkit.getPlayer(r.from());
        if (from != null) msg(from, p.getName() + " denied your teleport request.", NamedTextColor.RED);
    }

    // ---------------------------------------------------------------- waypoints
    // ---------------------------------------------------------------- duels
    private boolean areDueling(Player a, Player b) {
        UUID x = duels.get(a.getUniqueId());
        return x != null && x.equals(b.getUniqueId());
    }

    private void endDuel(UUID id) {
        UUID other = duels.remove(id);
        if (other != null) duels.remove(other);
    }

    private void cmdDuel(Player p, String[] args) {
        if (args.length == 0) {
            openDuelMenu(p, 0); // same menu as the pause-screen button
            return;
        }
        if (args.length != 1) {
            msg(p, "Usage: /duel <player>  |  /duel end  |  /duel (opens the player menu)", NamedTextColor.RED);
            return;
        }
        if (args[0].equalsIgnoreCase("end")) {
            UUID other = duels.get(p.getUniqueId());
            if (other == null) {
                msg(p, "You aren't in a duel.", NamedTextColor.RED);
                return;
            }
            endDuel(p.getUniqueId());
            msg(p, "You ended the duel.", NamedTextColor.YELLOW);
            Player o = Bukkit.getPlayer(other);
            if (o != null) msg(o, p.getName() + " ended the duel.", NamedTextColor.YELLOW);
            return;
        }
        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null || target.equals(p)) {
            msg(p, "Pick another online player to duel.", NamedTextColor.RED);
            return;
        }
        if (duels.containsKey(p.getUniqueId())) {
            msg(p, "You're already in a duel (/duel end to stop it).", NamedTextColor.RED);
            return;
        }
        if (duels.containsKey(target.getUniqueId())) {
            msg(p, target.getName() + " is already in a duel.", NamedTextColor.RED);
            return;
        }
        if (teamOf(p) == null || teamOf(target) == null || guidePending.contains(target.getUniqueId())) {
            msg(p, "That player can't duel right now.", NamedTextColor.RED);
            return;
        }
        boolean same = teamOf(p).equals(teamOf(target));
        duelRequests.put(target.getUniqueId(), new DuelRequest(p.getUniqueId(), System.currentTimeMillis() + DUEL_REQUEST_MS));
        msg(p, "Duel request sent to " + target.getName() + " (expires in 60s).", NamedTextColor.GREEN);
        Component accept = Component.text("[ACCEPT]", NamedTextColor.GREEN).clickEvent(ClickEvent.runCommand("/duelaccept"));
        Component deny = Component.text("[DENY]", NamedTextColor.RED).clickEvent(ClickEvent.runCommand("/dueldeny"));
        String rule = same ? "(friendly duel: nobody loses a tier) " : "(ENEMY team: normal death rules, the loser drops a tier) ";
        target.sendMessage(Component.text(p.getName() + " challenges you to a duel! " + rule, NamedTextColor.YELLOW)
                .append(accept).append(Component.text(" ")).append(deny));
    }

    private void openDuelMenu(Player p, int page) {
        List<UUID> ids = new ArrayList<>();
        for (Player o : Bukkit.getOnlinePlayers()) {
            if (!o.equals(p) && teamOf(o) != null) ids.add(o.getUniqueId());
        }
        int pages = Math.max(1, (ids.size() + 44) / 45);
        int pg = Math.max(0, Math.min(page, pages - 1));
        DuelMenuHolder h = new DuelMenuHolder(ids, pg);
        Inventory inv = Bukkit.createInventory(h, 54, Component.text("Duel - pick a player (" + (pg + 1) + "/" + pages + ")"));
        h.inv = inv;
        for (int i = 0; i < 45; i++) {
            int idx = pg * 45 + i;
            if (idx >= ids.size()) break;
            Player o = Bukkit.getPlayer(ids.get(idx));
            if (o == null) continue;
            String t = teamOf(o);
            NamedTextColor tc = "pig".equals(t) ? NamedTextColor.LIGHT_PURPLE : NamedTextColor.YELLOW;
            boolean busy = duels.containsKey(o.getUniqueId());
            boolean same = t.equals(teamOf(p));
            ItemStack head = new ItemStack(Material.PLAYER_HEAD);
            head.editMeta(SkullMeta.class, m -> {
                m.setOwningPlayer(o);
                m.displayName(line(o.getName(), tc));
                List<Component> lore = new ArrayList<>();
                lore.add(line("Team " + cap(t) + " - tier " + (tierOf(o) + 1), NamedTextColor.GRAY));
                if (busy) {
                    lore.add(line("Already in a duel", NamedTextColor.RED));
                } else {
                    lore.add(line(same ? "Friendly duel: nobody loses a tier" : "Enemy duel: normal death rules", NamedTextColor.WHITE));
                    lore.add(line("Click to send a duel request", NamedTextColor.GREEN));
                }
                m.lore(lore);
            });
            inv.setItem(i, head);
        }
        inv.setItem(45, navItem(Material.ARROW, "Previous page", null));
        inv.setItem(49, navItem(Material.GOLDEN_SWORD, "Click a player to challenge them", "PvP only happens in duels"));
        inv.setItem(53, navItem(Material.ARROW, "Next page", null));
        p.openInventory(inv);
    }

    private void duelMenuClick(Player p, DuelMenuHolder h, InventoryClickEvent e) {
        int slot = e.getSlot();
        if (slot == 45) {
            Bukkit.getScheduler().runTask(this, () -> openDuelMenu(p, h.page - 1));
            return;
        }
        if (slot == 53) {
            Bukkit.getScheduler().runTask(this, () -> openDuelMenu(p, h.page + 1));
            return;
        }
        if (slot < 0 || slot >= 45) return;
        int idx = h.page * 45 + slot;
        if (idx >= h.ids.size()) return;
        Player target = Bukkit.getPlayer(h.ids.get(idx));
        Bukkit.getScheduler().runTask(this, () -> {
            p.closeInventory();
            if (target == null) {
                msg(p, "That player went offline.", NamedTextColor.RED);
                return;
            }
            cmdDuel(p, new String[]{target.getName()});
        });
    }

    private void cmdDuelAccept(Player p) {
        DuelRequest r = duelRequests.remove(p.getUniqueId());
        if (r == null || r.expires() < System.currentTimeMillis()) {
            msg(p, "You have no pending duel request.", NamedTextColor.RED);
            return;
        }
        Player from = Bukkit.getPlayer(r.from());
        if (from == null) {
            msg(p, "That player went offline.", NamedTextColor.RED);
            return;
        }
        if (duels.containsKey(from.getUniqueId()) || duels.containsKey(p.getUniqueId())) {
            msg(p, "One of you is already in a duel.", NamedTextColor.RED);
            return;
        }
        duels.put(from.getUniqueId(), p.getUniqueId());
        duels.put(p.getUniqueId(), from.getUniqueId());
        protectedUntil.remove(from.getUniqueId()); // spawn protection ends when a duel starts
        protectedUntil.remove(p.getUniqueId());
        boolean same = teamOf(from) != null && teamOf(from).equals(teamOf(p));
        String info = same ? "Friendly duel - nobody loses a tier." : "Enemy duel - normal death rules apply.";
        Component start = Component.text("DUEL STARTED: " + from.getName() + " vs " + p.getName()
                + "! Nobody else can hurt you. " + info + " (/duel end to stop)", NamedTextColor.GOLD);
        from.sendMessage(start);
        p.sendMessage(start);
    }

    private void cmdDuelDeny(Player p) {
        DuelRequest r = duelRequests.remove(p.getUniqueId());
        if (r == null) {
            msg(p, "You have no pending duel request.", NamedTextColor.RED);
            return;
        }
        msg(p, "Duel declined.", NamedTextColor.YELLOW);
        Player from = Bukkit.getPlayer(r.from());
        if (from != null) msg(from, p.getName() + " declined your duel.", NamedTextColor.RED);
    }

    private List<Waypoint> waypointsOf(Player p) {
        return waypoints.computeIfAbsent(p.getUniqueId(), k -> new ArrayList<>());
    }

    private Waypoint findWaypoint(Player p, String name) {
        for (Waypoint w : waypointsOf(p)) {
            if (w.name.equalsIgnoreCase(name)) return w;
        }
        return null;
    }

    private void cmdCreateWaypoint(Player p, String[] args) {
        if (args.length != 1 || !args[0].matches("[A-Za-z0-9_\\-]{1,20}")) {
            msg(p, "Usage: /createwaypoint <name>  (1-20 letters, numbers, - or _, no spaces)", NamedTextColor.RED);
            return;
        }
        if (findWaypoint(p, args[0]) != null) {
            msg(p, "You already have a waypoint with that name.", NamedTextColor.RED);
            return;
        }
        List<Waypoint> list = waypointsOf(p);
        if (list.size() >= MAX_WAYPOINTS) {
            msg(p, "You can only have " + MAX_WAYPOINTS + " waypoints. Delete one with /managewaypoint.", NamedTextColor.RED);
            return;
        }
        Waypoint w = new Waypoint();
        w.id = UUID.randomUUID().toString().substring(0, 8);
        w.name = args[0];
        w.set(p.getLocation());
        list.add(w);
        saveWaypoints();
        msg(p, "Waypoint '" + w.name + "' created. Open /waypoints to see it (unlock teleporting for " + WAYPOINT_COST + " points).", NamedTextColor.GREEN);
    }

    private void cmdManageWaypoint(Player p, String[] args) {
        if (args.length == 0) {
            openWaypoints(p, true);
            return;
        }
        if (args[0].equalsIgnoreCase("delete") && args.length == 2) {
            Waypoint w = findWaypoint(p, args[1]);
            if (w == null) {
                msg(p, "No waypoint with that name.", NamedTextColor.RED);
                return;
            }
            waypointsOf(p).remove(w);
            saveWaypoints();
            msg(p, "Deleted waypoint '" + w.name + "'.", NamedTextColor.YELLOW);
            return;
        }
        if (args[0].equalsIgnoreCase("rename") && args.length == 3) {
            Waypoint w = findWaypoint(p, args[1]);
            if (w == null) {
                msg(p, "No waypoint with that name.", NamedTextColor.RED);
                return;
            }
            if (!args[2].matches("[A-Za-z0-9_\\-]{1,20}") || findWaypoint(p, args[2]) != null) {
                msg(p, "That new name is invalid or already used.", NamedTextColor.RED);
                return;
            }
            w.name = args[2];
            saveWaypoints();
            msg(p, "Renamed to '" + w.name + "'.", NamedTextColor.GREEN);
            return;
        }
        msg(p, "Usage: /managewaypoint  |  /managewaypoint rename <old> <new>  |  /managewaypoint delete <name>", NamedTextColor.RED);
    }

    private static String direction(Location from, Location to) {
        double dx = to.getX() - from.getX();
        double dz = to.getZ() - from.getZ();
        double bearing = Math.toDegrees(Math.atan2(dx, -dz)); // 0 = north, 90 = east
        if (bearing < 0) bearing += 360;
        String[] names = {"north", "north-east", "east", "south-east", "south", "south-west", "west", "north-west"};
        return names[(int) Math.round(bearing / 45.0) % 8];
    }

    private void showWaypoint(Player p, Waypoint w) {
        Location l = w.loc();
        String where = w.world + " " + (int) w.x + ", " + (int) w.y + ", " + (int) w.z;
        if (l != null && l.getWorld().equals(p.getWorld())) {
            int dist = (int) l.distance(p.getLocation());
            p.setCompassTarget(l);
            msg(p, "Waypoint '" + w.name + "': " + where + " - " + dist + " blocks " + direction(p.getLocation(), l) + " (compass points to it).", NamedTextColor.AQUA);
        } else {
            msg(p, "Waypoint '" + w.name + "': " + where + " (different world).", NamedTextColor.AQUA);
        }
    }

    private void openWaypoints(Player p, boolean manage) {
        List<Waypoint> list = waypointsOf(p);
        WaypointHolder h = new WaypointHolder(manage, list);
        Inventory inv = Bukkit.createInventory(h, 27,
                Component.text(manage ? "Manage Waypoints" : "Waypoints (" + WAYPOINT_COST + " points to unlock teleport)"));
        h.inv = inv;
        for (int i = 0; i < list.size() && i < 27; i++) {
            Waypoint w = list.get(i);
            ItemStack it = new ItemStack(w.unlocked ? Material.ENDER_EYE : Material.COMPASS);
            it.editMeta(m -> {
                m.displayName(line(w.name, w.unlocked ? NamedTextColor.GREEN : NamedTextColor.YELLOW));
                List<Component> lore = new ArrayList<>();
                lore.add(line(w.world + ": " + (int) w.x + ", " + (int) w.y + ", " + (int) w.z, NamedTextColor.GRAY));
                if (manage) {
                    lore.add(line("Left-click: move it to where you stand", NamedTextColor.WHITE));
                    lore.add(line("Right-click: delete it", NamedTextColor.RED));
                    if (w.unlocked) lore.add(line("(Deleting does NOT refund the " + WAYPOINT_COST + " points)", NamedTextColor.DARK_RED));
                } else if (w.unlocked) {
                    lore.add(line("UNLOCKED", NamedTextColor.GREEN));
                    lore.add(line("Left-click: teleport", NamedTextColor.WHITE));
                    lore.add(line("Right-click: show where it is", NamedTextColor.WHITE));
                } else {
                    lore.add(line("Locked - you can see where it is", NamedTextColor.RED));
                    lore.add(line("Right-click: show where it is", NamedTextColor.WHITE));
                    lore.add(line("Shift-click: unlock teleporting for " + WAYPOINT_COST + " points", NamedTextColor.GOLD));
                }
                m.lore(lore);
            });
            inv.setItem(i, it);
        }
        p.openInventory(inv);
    }

    private void waypointClick(Player p, WaypointHolder h, InventoryClickEvent e) {
        int slot = e.getSlot();
        if (slot < 0 || slot >= h.list.size()) return;
        Waypoint w = h.list.get(slot);

        if (h.manage) {
            if (e.isRightClick()) {
                h.list.remove(w);
                msg(p, "Deleted waypoint '" + w.name + "'.", NamedTextColor.YELLOW);
            } else {
                w.set(p.getLocation());
                msg(p, "Moved '" + w.name + "' to your location.", NamedTextColor.GREEN);
            }
            saveWaypoints();
            Bukkit.getScheduler().runTask(this, () -> openWaypoints(p, true));
            return;
        }

        if (e.isShiftClick()) { // unlock
            if (w.unlocked) return;
            PlayerData d = dataOf(p.getUniqueId());
            if (d.points < WAYPOINT_COST) {
                msg(p, "Not enough points (" + d.points + "/" + WAYPOINT_COST + ").", NamedTextColor.RED);
                return;
            }
            d.points -= WAYPOINT_COST;
            w.unlocked = true;
            saveData();
            saveWaypoints();
            msg(p, "Unlocked teleporting to '" + w.name + "' forever! Points left: " + d.points, NamedTextColor.GREEN);
            Bukkit.getScheduler().runTask(this, () -> openWaypoints(p, false));
            return;
        }
        if (e.isRightClick()) {
            showWaypoint(p, w);
            return;
        }
        if (!w.unlocked) {
            msg(p, "Shift-click it to unlock teleporting for " + WAYPOINT_COST + " points.", NamedTextColor.GOLD);
            return;
        }
        Location dest = w.loc();
        Bukkit.getScheduler().runTask(this, () -> {
            p.closeInventory();
            if (teleportPlayer(p, dest, "waypoint")) msg(p, "Teleported to '" + w.name + "'.", NamedTextColor.GREEN);
        });
    }

    // ---------------------------------------------------------------- operator commands
    private void handleAdmin(CommandSender sender, String name, String[] args) {
        switch (name) {
            case "setteamspawn" -> {
                if (!(sender instanceof Player p)) {
                    msg(sender, "Run this in-game, standing where you want the spawn.", NamedTextColor.RED);
                    return;
                }
                if (args.length != 1 || !(args[0].equalsIgnoreCase("pig") || args[0].equalsIgnoreCase("cheese"))) {
                    msg(p, "Usage: /setteamspawn <pig|cheese>", NamedTextColor.RED);
                    return;
                }
                String team = args[0].toLowerCase();
                getConfig().set("spawn." + team, p.getLocation());
                saveConfig();
                msg(p, "Team " + team + " spawn set to your location.", NamedTextColor.GREEN);
            }
            case "tierup", "tierdown", "settier" -> adminTier(sender, name, args);
            case "teampig", "teamcheese" -> {
                if (args.length != 1) {
                    msg(sender, "Usage: /" + name + " <player>", NamedTextColor.RED);
                    return;
                }
                Player target = Bukkit.getPlayerExact(args[0]);
                if (target == null) {
                    msg(sender, "That player isn't online.", NamedTextColor.RED);
                    return;
                }
                PlayerData td = data.get(target.getUniqueId());
                if (td != null && td.lockedTeam != null) {
                    msg(sender, target.getName() + " is locked to Team " + td.lockedTeam + ". Use /unlockteam first.", NamedTextColor.RED);
                    return;
                }
                String team = name.equals("teampig") ? "pig" : "cheese";
                assignTeam(target, team, true);
                msg(sender, target.getName() + " moved to Team " + team + ".", NamedTextColor.GREEN);
            }
            case "lockteam" -> {
                if (args.length != 2 || !(args[1].equalsIgnoreCase("pig") || args[1].equalsIgnoreCase("cheese"))) {
                    msg(sender, "Usage: /lockteam <player> <pig|cheese>", NamedTextColor.RED);
                    return;
                }
                UUID id = resolveId(args[0]);
                if (id == null) {
                    msg(sender, "I don't know that player (they must have joined before).", NamedTextColor.RED);
                    return;
                }
                String team = args[1].toLowerCase();
                PlayerData d = dataOf(id);
                d.lockedTeam = team;
                Player online = Bukkit.getPlayer(id);
                if (online != null) assignTeam(online, team, true);
                else d.team = team;
                saveData();
                msg(sender, nameOf(id) + " is now permanently on Team " + team + " (undo with /unlockteam).", NamedTextColor.GREEN);
            }
            case "unlockteam" -> {
                if (args.length != 1) {
                    msg(sender, "Usage: /unlockteam <player>", NamedTextColor.RED);
                    return;
                }
                UUID id = resolveId(args[0]);
                PlayerData d = id == null ? null : data.get(id);
                if (d == null || d.lockedTeam == null) {
                    msg(sender, "That player isn't team-locked.", NamedTextColor.RED);
                    return;
                }
                d.lockedTeam = null;
                saveData();
                Player online = Bukkit.getPlayer(id);
                if (online != null) msg(online, "Your team lock was removed. Balancing will apply from your next join.", NamedTextColor.YELLOW);
                msg(sender, nameOf(id) + " is no longer team-locked.", NamedTextColor.GREEN);
            }
            case "givepoints", "setpoints" -> {
                if (args.length != 2) {
                    msg(sender, "Usage: /" + name + " <player> <amount>", NamedTextColor.RED);
                    return;
                }
                UUID id = resolveId(args[0]);
                PlayerData d = id == null ? null : data.get(id);
                if (d == null) {
                    msg(sender, "No saved data for that player.", NamedTextColor.RED);
                    return;
                }
                int amount;
                try {
                    amount = Integer.parseInt(args[1]);
                } catch (NumberFormatException ex) {
                    msg(sender, "Amount must be a whole number.", NamedTextColor.RED);
                    return;
                }
                d.points = Math.max(0, name.equals("setpoints") ? amount : d.points + amount);
                saveData();
                Player online = Bukkit.getPlayer(id);
                if (online != null) msg(online, "An operator changed your kill points. You now have " + d.points + ".", NamedTextColor.GOLD);
                msg(sender, nameOf(id) + " now has " + d.points + " points.", NamedTextColor.GREEN);
            }
            case "resetplayer" -> {
                if (args.length != 1) {
                    msg(sender, "Usage: /resetplayer <player>", NamedTextColor.RED);
                    return;
                }
                UUID id = resolveId(args[0]);
                PlayerData d = id == null ? null : data.get(id);
                if (d == null) {
                    msg(sender, "No saved data for that player.", NamedTextColor.RED);
                    return;
                }
                d.tier = 0;
                d.kills = 0;
                d.strikes = 0;
                saveData();
                Player online = Bukkit.getPlayer(id);
                if (online != null) {
                    applyTier(online);
                    msg(online, "An operator reset your tier to 1.", NamedTextColor.GOLD);
                }
                msg(sender, nameOf(id) + " reset to tier 1 (points kept).", NamedTextColor.GREEN);
            }
            case "clearcombat" -> {
                Player t = args.length == 1 ? Bukkit.getPlayerExact(args[0]) : null;
                if (t == null) {
                    msg(sender, "Usage: /clearcombat <online player>", NamedTextColor.RED);
                    return;
                }
                combatUntil.remove(t.getUniqueId());
                msg(sender, t.getName() + " is no longer in combat.", NamedTextColor.GREEN);
                msg(t, "An operator cleared your combat timer.", NamedTextColor.GREEN);
            }
            case "freeze" -> {
                Player t = args.length == 1 ? Bukkit.getPlayerExact(args[0]) : null;
                if (t == null) {
                    msg(sender, "Usage: /freeze <online player>", NamedTextColor.RED);
                    return;
                }
                frozen.add(t.getUniqueId());
                msg(t, "You have been frozen by an operator.", NamedTextColor.RED);
                msg(sender, t.getName() + " is frozen (and takes no damage).", NamedTextColor.GREEN);
            }
            case "unfreeze" -> {
                Player t = args.length == 1 ? Bukkit.getPlayerExact(args[0]) : null;
                if (t == null) {
                    msg(sender, "Usage: /unfreeze <online player>", NamedTextColor.RED);
                    return;
                }
                frozen.remove(t.getUniqueId());
                msg(t, "You have been unfrozen.", NamedTextColor.GREEN);
                msg(sender, t.getName() + " is unfrozen.", NamedTextColor.GREEN);
            }
            case "mute" -> {
                if (args.length < 1 || args.length > 2) {
                    msg(sender, "Usage: /mute <player> [minutes]  (no minutes = until /unmute)", NamedTextColor.RED);
                    return;
                }
                UUID id = resolveId(args[0]);
                if (id == null) {
                    msg(sender, "I don't know that player.", NamedTextColor.RED);
                    return;
                }
                long until = Long.MAX_VALUE;
                if (args.length == 2) {
                    try {
                        long mins = Long.parseLong(args[1]);
                        if (mins < 1) throw new NumberFormatException();
                        until = System.currentTimeMillis() + mins * 60_000L;
                    } catch (NumberFormatException ex) {
                        msg(sender, "Minutes must be a positive whole number.", NamedTextColor.RED);
                        return;
                    }
                }
                dataOf(id).mutedUntil = until;
                saveData();
                msg(sender, nameOf(id) + " is muted" + (args.length == 2 ? " for " + args[1] + " minute(s)." : "."), NamedTextColor.GREEN);
                Player online = Bukkit.getPlayer(id);
                if (online != null) msg(online, "You have been muted by an operator.", NamedTextColor.RED);
            }
            case "unmute" -> {
                UUID id = args.length == 1 ? resolveId(args[0]) : null;
                PlayerData d = id == null ? null : data.get(id);
                if (d == null || d.mutedUntil == 0L) {
                    msg(sender, "Usage: /unmute <muted player>", NamedTextColor.RED);
                    return;
                }
                d.mutedUntil = 0L;
                saveData();
                msg(sender, nameOf(id) + " is unmuted.", NamedTextColor.GREEN);
                Player online = Bukkit.getPlayer(id);
                if (online != null) msg(online, "You have been unmuted.", NamedTextColor.GREEN);
            }
            case "invsee" -> {
                if (!(sender instanceof Player p)) {
                    msg(sender, "Run this in-game.", NamedTextColor.RED);
                    return;
                }
                Player t = args.length == 1 ? Bukkit.getPlayerExact(args[0]) : null;
                if (t == null) {
                    msg(p, "Usage: /invsee <online player>", NamedTextColor.RED);
                    return;
                }
                p.openInventory(t.getInventory());
            }
            case "playerinfo" -> {
                UUID id = args.length == 1 ? resolveId(args[0]) : null;
                PlayerData d = id == null ? null : data.get(id);
                if (d == null) {
                    msg(sender, "Usage: /playerinfo <player> (they must have joined before)", NamedTextColor.RED);
                    return;
                }
                Player online = Bukkit.getPlayer(id);
                String muted = d.mutedUntil == 0L ? "no" : (d.mutedUntil == Long.MAX_VALUE ? "yes (permanent)"
                        : (d.mutedUntil > System.currentTimeMillis() ? "yes (" + ((d.mutedUntil - System.currentTimeMillis()) / 60000 + 1) + " min left)" : "no"));
                msg(sender, "--- " + nameOf(id) + " (" + (online != null ? "online" : "offline") + ") ---", NamedTextColor.GOLD);
                msg(sender, "Team: " + d.team + (d.lockedTeam != null ? " (LOCKED)" : ""), NamedTextColor.YELLOW);
                msg(sender, "Tier: " + (d.tier + 1) + "   Kills toward next: " + d.kills + "/" + killsNeeded(d.tier) + "   Deaths toward drop: " + d.strikes + "   Points: " + d.points, NamedTextColor.YELLOW);
                msg(sender, "Muted: " + muted + "   Frozen: " + (frozen.contains(id) ? "yes" : "no")
                        + "   In combat: " + (online != null && isInCombat(online) ? "yes" : "no"), NamedTextColor.YELLOW);
                List<Waypoint> wl = waypoints.get(id);
                msg(sender, "Waypoints: " + (wl == null ? 0 : wl.size()), NamedTextColor.YELLOW);
                msg(sender, "Playtime: " + formatDuration(playSecondsOf(id)) + "   Kills: " + d.totalKills + "   Deaths: " + d.deaths, NamedTextColor.YELLOW);
            }
            case "testnewplayer", "testguide", "testcombat", "testpunish", "testprotect", "testkill", "testgear", "testbalance"
                    -> adminTest(sender, name, args);
            case "setprice", "resetprice" -> adminPrice(sender, name, args);
            case "settierall" -> adminTierAll(sender, args);
            case "event" -> adminEvent(sender, args);
            case "givebanker" -> adminGiveBanker(sender, args);
            case "setpack" -> adminSetPack(sender, args);
            case "setbase", "removebase", "basepvp" -> adminBase(sender, name, args);
            case "seteventwaypoint", "removeeventwaypoint" -> adminEventWaypoint(sender, name, args);
            default -> msg(sender, "Unknown command.", NamedTextColor.RED);
        }
    }

    // ---------------------------------------------------------------- operator TEST commands
    private void adminTest(CommandSender sender, String name, String[] args) {
        if (name.equals("testbalance")) {
            int pig = 0, cheese = 0;
            for (Player o : Bukkit.getOnlinePlayers()) {
                String tm = teamOf(o);
                if ("pig".equals(tm)) pig++;
                else if ("cheese".equals(tm)) cheese++;
            }
            String next = pig < cheese ? "Team Pig" : (cheese < pig ? "Team Cheese"
                    : "either team (tied: returning players keep their old team, brand-new players are random)");
            msg(sender, "Online now - Pig: " + pig + ", Cheese: " + cheese + ". Next player to join goes to: " + next + ".", NamedTextColor.AQUA);
            return;
        }

        // optional first argument = target player (default: yourself)
        Player t = null;
        String[] rest = args;
        if (args.length >= 1) {
            Player named = Bukkit.getPlayerExact(args[0]);
            if (named != null) {
                t = named;
                rest = Arrays.copyOfRange(args, 1, args.length);
            } else if (name.equals("testkill") && args[0].matches("\\d+") && sender instanceof Player sp) {
                t = sp;
            } else {
                msg(sender, "That player isn't online.", NamedTextColor.RED);
                return;
            }
        } else if (sender instanceof Player sp) {
            t = sp;
        }
        if (t == null) {
            msg(sender, "Run this in-game, or name an online player: /" + name + " <player>", NamedTextColor.RED);
            return;
        }

        switch (name) {
            case "testnewplayer" -> {
                // Only forces the required first-join screen. Nothing is reset or deleted.
                guidePending.add(t.getUniqueId());
                t.setInvulnerable(true);
                openGuideMenu(t);
                msg(sender, "Showing " + t.getName() + " the required first-join screen. They can't close it until they click I UNDERSTAND (nothing else is changed).", NamedTextColor.GREEN);
            }
            case "testguide" -> {
                if (guidePending.contains(t.getUniqueId())) {
                    msg(sender, t.getName() + " is already seeing the real welcome screen.", NamedTextColor.RED);
                    return;
                }
                openGuideMenu(t);
                msg(sender, "Showing the welcome screen to " + t.getName() + " as a preview (it can be closed normally).", NamedTextColor.GREEN);
            }
            case "testcombat" -> {
                tagCombat(t);
                msg(sender, t.getName() + " is in combat for 20s. Now try logging out or /spawn to test the punishment.", NamedTextColor.GREEN);
            }
            case "testpunish" -> {
                punishCombat(t, "a test");
                msg(sender, "Applied the combat punishment to " + t.getName() + ".", NamedTextColor.GREEN);
            }
            case "testprotect" -> {
                giveProtection(t);
                msg(sender, t.getName() + " has 30s of spawn protection.", NamedTextColor.GREEN);
            }
            case "testkill" -> {
                if (teamOf(t) == null) {
                    msg(sender, "That player has no team yet.", NamedTextColor.RED);
                    return;
                }
                int victimTier = 1;
                String tierArg = rest.length >= 1 ? rest[0] : (args.length == 1 && args[0].matches("\\d+") ? args[0] : null);
                if (tierArg != null) {
                    try {
                        victimTier = Integer.parseInt(tierArg);
                    } catch (NumberFormatException ex) {
                        victimTier = 0;
                    }
                    if (victimTier < 1 || victimTier > MAX_TIER + 1) {
                        msg(sender, "Victim tier must be 1-" + (MAX_TIER + 1) + ".", NamedTextColor.RED);
                        return;
                    }
                }
                awardKill(t, victimTier - 1);
                applyTier(t);
                saveData();
                msg(sender, "Gave " + t.getName() + " a fake kill on a tier " + victimTier + " player.", NamedTextColor.GREEN);
            }
            case "testgear" -> {
                applyTier(t);
                msg(sender, "Refreshed " + t.getName() + "'s gear for their tier.", NamedTextColor.GREEN);
            }
            default -> {
            }
        }
    }

    // ---------------------------------------------------------------- timed events
    private static String eventTitle(String type) {
        return switch (type) {
            case "chaos" -> "Chaos";
            case "randomtiers" -> "Random Tiers";
            case "randomenchants" -> "Random Enchantments";
            case "doublepoints" -> "Double Points";
            default -> type;
        };
    }

    private static String eventDescription(String type) {
        return switch (type) {
            case "chaos" -> "Your tier is random and re-rolls every time another player kills you, and your gear gets random enchantments (any enchantment, even ones that don't belong on the item).";
            case "randomtiers" -> "Your tier is random and re-rolls every time another player kills you. Normal tier progress is paused.";
            case "randomenchants" -> "Your gear gets random enchantments (any enchantment at all) that re-roll every time another player kills you.";
            case "doublepoints" -> "Every kill pays double kill points.";
            default -> "";
        };
    }

    private void setEventFlags(String type) {
        evRandomTiers = "chaos".equals(type) || "randomtiers".equals(type);
        evRandomEnchants = "chaos".equals(type) || "randomenchants".equals(type);
        evDoublePoints = "doublepoints".equals(type);
    }

    private static long parseDurationMs(String in) {
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("(\\d{1,6})([smh]?)").matcher(in.toLowerCase());
        if (!m.matches()) return -1;
        long n = Long.parseLong(m.group(1));
        long unit = switch (m.group(2)) {
            case "s" -> 1000L;
            case "h" -> 3_600_000L;
            default -> 60_000L; // plain number = minutes
        };
        long ms = n * unit;
        return (ms < 10_000L || ms > 24L * 3_600_000L) ? -1 : ms;
    }

    private void saveEvent() {
        YamlConfiguration cfg = new YamlConfiguration();
        if (eventType != null) {
            cfg.set("event.type", eventType);
            cfg.set("event.endsAt", eventEndsAt);
            cfg.set("event.total", eventTotalMs);
            cfg.set("event.ffa", evFfa);
            for (Map.Entry<UUID, int[]> en : eventSnap.entrySet()) {
                String base = "event.snapshots." + en.getKey();
                cfg.set(base + ".tier", en.getValue()[0]);
                cfg.set(base + ".kills", en.getValue()[1]);
                cfg.set(base + ".strikes", en.getValue()[2]);
            }
        }
        try {
            cfg.save(eventFile);
        } catch (IOException ex) {
            getLogger().warning("Could not save event.yml: " + ex.getMessage());
        }
    }

    private void loadEvent() {
        eventSnap.clear();
        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(eventFile);
        String t = cfg.getString("event.type");
        if (t == null) return;
        eventType = t;
        eventEndsAt = cfg.getLong("event.endsAt");
        eventTotalMs = cfg.getLong("event.total", 0L);
        setEventFlags(t);
        evFfa = cfg.getBoolean("event.ffa", false);
        ConfigurationSection sec = cfg.getConfigurationSection("event.snapshots");
        if (sec != null) {
            for (String key : sec.getKeys(false)) {
                try {
                    eventSnap.put(UUID.fromString(key),
                            new int[]{sec.getInt(key + ".tier"), sec.getInt(key + ".kills"), sec.getInt(key + ".strikes")});
                } catch (IllegalArgumentException ignored) {
                }
            }
        }
        if (System.currentTimeMillis() >= eventEndsAt) endEvent(); // it ran out while the server was off
    }

    private void ensureEventBar() {
        if (eventType == null || eventBar != null) return;
        eventBar = BossBar.bossBar(Component.text("EVENT: " + eventTitle(eventType)), 1f, BossBar.Color.PURPLE, BossBar.Overlay.PROGRESS);
        for (Player p : Bukkit.getOnlinePlayers()) p.showBossBar(eventBar);
    }

    private void tickEvent() {
        if (eventType == null) return;
        long remaining = eventEndsAt - System.currentTimeMillis();
        if (remaining <= 0) {
            endEvent();
            return;
        }
        ensureEventBar();
        long secs = (remaining + 999) / 1000;
        eventBar.name(Component.text("EVENT: " + eventLabel() + " - " + formatDuration(secs) + " left", NamedTextColor.LIGHT_PURPLE));
        eventBar.progress((float) Math.max(0.0, Math.min(1.0, remaining / (double) Math.max(1L, eventTotalMs))));
        if ((secs == 300 || secs == 60 || secs == 10) && secs != lastEventWarn) {
            lastEventWarn = secs;
            Bukkit.broadcast(Component.text("The " + eventTitle(eventType) + " event ends in " + formatDuration(secs) + "!", NamedTextColor.LIGHT_PURPLE));
        }
    }

    /** Remembers the player's real tier/progress, then gives them a random event tier. */
    private void snapshotAndRoll(Player p) {
        PlayerData d = dataOf(p.getUniqueId());
        eventSnap.putIfAbsent(p.getUniqueId(), new int[]{d.tier, d.kills, d.strikes});
        d.tier = ThreadLocalRandom.current().nextInt(MAX_TIER + 1);
        d.kills = 0;
        d.strikes = 0;
        applyTier(p);
        msg(p, "EVENT: your tier is now " + (d.tier + 1) + " (it re-rolls every time another player kills you).", NamedTextColor.LIGHT_PURPLE);
    }

    private void onEventJoin(Player p, PlayerData d) {
        ensureEventBar();
        if (eventBar != null) p.showBossBar(eventBar);
        if (evRandomTiers && !eventSnap.containsKey(p.getUniqueId())) {
            snapshotAndRoll(p);
            saveEvent();
        }
        long secs = Math.max(0, (eventEndsAt - System.currentTimeMillis()) / 1000);
        p.sendMessage(Component.text("EVENT RUNNING: " + eventLabel() + " (" + formatDuration(secs) + " left). "
                + eventDescription(eventType), NamedTextColor.LIGHT_PURPLE));
        if (!eventWaypoints.isEmpty()) msg(p, "Free event teleports are open: /eventwaypoints", NamedTextColor.AQUA);
    }

    private void startEvent(String type, long ms, boolean ffa) {
        eventType = type;
        eventTotalMs = ms;
        eventEndsAt = System.currentTimeMillis() + ms;
        lastEventWarn = -1;
        eventSnap.clear();
        setEventFlags(type);
        evFfa = ffa;
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (teamOf(p) == null) continue;
            if (evRandomTiers) snapshotAndRoll(p);
            else if (evRandomEnchants) applyTier(p);
        }
        for (Player p : Bukkit.getOnlinePlayers()) refreshTeamEntry(p); // hides team colours in a free-for-all
        saveData();
        saveEvent();
        ensureEventBar();
        Bukkit.broadcast(Component.text("=== EVENT STARTED: " + eventTitle(type) + " (" + formatDuration(ms / 1000) + ") ===", NamedTextColor.LIGHT_PURPLE));
        Bukkit.broadcast(Component.text(eventDescription(type) + " Kill points work as normal.", NamedTextColor.WHITE));
        if (ffa) {
            Bukkit.broadcast(Component.text("FREE FOR ALL: teams don't matter - everyone can fight everyone, and every kill counts!", NamedTextColor.RED));
        }
        if (!eventWaypoints.isEmpty()) {
            Bukkit.broadcast(Component.text("Free event teleports are open: type /eventwaypoints", NamedTextColor.AQUA));
        }
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.showTitle(Title.title(Component.text("EVENT: " + eventTitle(type), NamedTextColor.LIGHT_PURPLE),
                    Component.text(eventDescription(type), NamedTextColor.GRAY),
                    Title.Times.times(Duration.ofMillis(300), Duration.ofSeconds(4), Duration.ofMillis(600))));
        }
    }

    private void endEvent() {
        if (eventType == null) return;
        String type = eventType;
        eventType = null;
        evRandomTiers = false;
        evRandomEnchants = false;
        evDoublePoints = false;
        evFfa = false;

        // everyone goes back to how they were before the event (kill points are kept)
        Set<UUID> restored = new HashSet<>(eventSnap.keySet());
        for (Map.Entry<UUID, int[]> en : eventSnap.entrySet()) {
            PlayerData d = data.get(en.getKey());
            if (d == null) continue;
            d.tier = en.getValue()[0];
            d.kills = en.getValue()[1];
            d.strikes = en.getValue()[2];
        }
        eventSnap.clear();

        for (Player p : Bukkit.getOnlinePlayers()) {
            if (eventBar != null) p.hideBossBar(eventBar);
            refreshTeamEntry(p); // team colours come back
            if (teamOf(p) == null) continue;
            applyTier(p); // swaps gear back to the real tier and removes event enchantments
            msg(p, "The " + eventTitle(type) + " event is over!" + (restored.contains(p.getUniqueId())
                    ? " You're back to tier " + (tierOf(p) + 1) + " with your normal gear." : "")
                    + " Kill points you earned are yours to keep.", NamedTextColor.LIGHT_PURPLE);
        }
        eventBar = null;
        saveData();
        saveEvent();
        Bukkit.broadcast(Component.text("=== EVENT OVER: " + eventTitle(type) + " ===", NamedTextColor.LIGHT_PURPLE));
    }

    private void adminEvent(CommandSender sender, String[] args) {
        String sub = args.length == 0 ? "" : args[0].toLowerCase();
        switch (sub) {
            case "start" -> {
                if (args.length < 3 || args.length > 4) {
                    msg(sender, "Usage: /event start <chaos|randomtiers|randomenchants|doublepoints> <time> [ffa|teams]   (time like 30, 30m, 90s or 2h; default is teams)", NamedTextColor.RED);
                    return;
                }
                boolean ffa = false;
                if (args.length == 4) {
                    if (args[3].equalsIgnoreCase("ffa")) {
                        ffa = true;
                    } else if (!args[3].equalsIgnoreCase("teams")) {
                        msg(sender, "The last word must be ffa (free for all) or teams.", NamedTextColor.RED);
                        return;
                    }
                }
                if (eventType != null) {
                    msg(sender, "An event is already running (" + eventTitle(eventType) + "). Use /event stop first.", NamedTextColor.RED);
                    return;
                }
                String type = args[1].toLowerCase();
                if (!Set.of("chaos", "randomtiers", "randomenchants", "doublepoints").contains(type)) {
                    msg(sender, "Unknown event. Use /event list.", NamedTextColor.RED);
                    return;
                }
                long ms = parseDurationMs(args[2]);
                if (ms < 0) {
                    msg(sender, "Time must be 10 seconds to 24 hours, like 30 (minutes), 90s, 15m or 2h.", NamedTextColor.RED);
                    return;
                }
                startEvent(type, ms, ffa);
                msg(sender, "Started the " + eventTitle(type) + " event for " + formatDuration(ms / 1000) + (ffa ? " as a FREE FOR ALL." : " with teams."), NamedTextColor.GREEN);
            }
            case "stop" -> {
                if (eventType == null) {
                    msg(sender, "No event is running.", NamedTextColor.RED);
                    return;
                }
                endEvent();
                msg(sender, "Event stopped and everyone restored.", NamedTextColor.GREEN);
            }
            case "status" -> {
                if (eventType == null) {
                    msg(sender, "No event is running.", NamedTextColor.YELLOW);
                    return;
                }
                long secs = Math.max(0, (eventEndsAt - System.currentTimeMillis()) / 1000);
                msg(sender, "Running: " + eventLabel() + " - " + formatDuration(secs) + " left. " + eventDescription(eventType), NamedTextColor.AQUA);
            }
            case "list" -> {
                msg(sender, "Events you can start with /event start <name> <time>:", NamedTextColor.GOLD);
                for (String t : new String[]{"chaos", "randomtiers", "randomenchants", "doublepoints"}) {
                    msg(sender, "  " + t + " - " + eventDescription(t), NamedTextColor.YELLOW);
                }
            }
            default -> msg(sender, "Usage: /event start <type> <time> | /event stop | /event status | /event list", NamedTextColor.RED);
        }
    }

    // ---------------------------------------------------------------- event waypoints (free teleports, only during events)
    private void loadEventWaypoints() {
        eventWaypoints.clear();
        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(eventWaypointFile);
        ConfigurationSection sec = cfg.getConfigurationSection("waypoints");
        if (sec == null) return;
        for (String wid : sec.getKeys(false)) {
            Waypoint w = new Waypoint();
            w.id = wid;
            w.name = sec.getString(wid + ".name", wid);
            w.world = sec.getString(wid + ".world", "world");
            w.x = sec.getDouble(wid + ".x");
            w.y = sec.getDouble(wid + ".y");
            w.z = sec.getDouble(wid + ".z");
            w.yaw = (float) sec.getDouble(wid + ".yaw");
            w.pitch = (float) sec.getDouble(wid + ".pitch");
            eventWaypoints.add(w);
        }
    }

    private void saveEventWaypoints() {
        YamlConfiguration cfg = new YamlConfiguration();
        for (Waypoint w : eventWaypoints) {
            String base = "waypoints." + w.id;
            cfg.set(base + ".name", w.name);
            cfg.set(base + ".world", w.world);
            cfg.set(base + ".x", w.x);
            cfg.set(base + ".y", w.y);
            cfg.set(base + ".z", w.z);
            cfg.set(base + ".yaw", w.yaw);
            cfg.set(base + ".pitch", w.pitch);
        }
        try {
            cfg.save(eventWaypointFile);
        } catch (IOException ex) {
            getLogger().warning("Could not save eventwaypoints.yml: " + ex.getMessage());
        }
    }

    private Waypoint findEventWaypoint(String name) {
        for (Waypoint w : eventWaypoints) {
            if (w.name.equalsIgnoreCase(name)) return w;
        }
        return null;
    }

    private void openEventWaypoints(Player p) {
        if (eventWaypoints.isEmpty()) {
            msg(p, "There are no event waypoints yet.", NamedTextColor.YELLOW);
            return;
        }
        boolean active = eventType != null;
        EventWaypointHolder h = new EventWaypointHolder(new ArrayList<>(eventWaypoints));
        Inventory inv = Bukkit.createInventory(h, 36, Component.text(active ? "Event Waypoints (OPEN)" : "Event Waypoints (closed)"));
        h.inv = inv;
        for (int i = 0; i < h.list.size() && i < 36; i++) {
            Waypoint w = h.list.get(i);
            ItemStack it = new ItemStack(active ? Material.BEACON : Material.COMPASS);
            it.editMeta(m -> {
                m.displayName(line(w.name, active ? NamedTextColor.GREEN : NamedTextColor.GRAY));
                List<Component> lore = new ArrayList<>();
                lore.add(line(w.world + ": " + (int) w.x + ", " + (int) w.y + ", " + (int) w.z, NamedTextColor.GRAY));
                if (active) lore.add(line("Left-click: teleport (free)", NamedTextColor.WHITE));
                else lore.add(line("Closed - only works during an event", NamedTextColor.RED));
                lore.add(line("Right-click: show where it is", NamedTextColor.WHITE));
                m.lore(lore);
            });
            inv.setItem(i, it);
        }
        p.openInventory(inv);
    }

    private void eventWaypointClick(Player p, EventWaypointHolder h, InventoryClickEvent e) {
        int slot = e.getSlot();
        if (slot < 0 || slot >= h.list.size()) return;
        Waypoint w = h.list.get(slot);
        if (e.isRightClick()) {
            showWaypoint(p, w);
            return;
        }
        if (eventType == null) {
            msg(p, "Event waypoints only work while an event is running.", NamedTextColor.RED);
            return;
        }
        Location dest = w.loc();
        Bukkit.getScheduler().runTask(this, () -> {
            p.closeInventory();
            if (teleportPlayer(p, dest, "event waypoint")) msg(p, "Teleported to '" + w.name + "'.", NamedTextColor.GREEN);
        });
    }

    private void adminEventWaypoint(CommandSender sender, String name, String[] args) {
        if (name.equals("seteventwaypoint")) {
            if (!(sender instanceof Player p)) {
                msg(sender, "Run this in-game, standing where you want the waypoint.", NamedTextColor.RED);
                return;
            }
            if (args.length != 1 || !args[0].matches("[A-Za-z0-9_\\-]{1,20}")) {
                msg(p, "Usage: /seteventwaypoint <name>  (1-20 letters, numbers, - or _)", NamedTextColor.RED);
                return;
            }
            Waypoint existing = findEventWaypoint(args[0]);
            if (existing != null) {
                existing.set(p.getLocation());
                saveEventWaypoints();
                msg(p, "Moved event waypoint '" + existing.name + "' to your location.", NamedTextColor.GREEN);
                return;
            }
            if (eventWaypoints.size() >= 36) {
                msg(p, "That's the maximum of 36 event waypoints. Remove one first.", NamedTextColor.RED);
                return;
            }
            Waypoint w = new Waypoint();
            w.id = UUID.randomUUID().toString().substring(0, 8);
            w.name = args[0];
            w.set(p.getLocation());
            eventWaypoints.add(w);
            saveEventWaypoints();
            msg(p, "Event waypoint '" + w.name + "' created. Players can use it for free (via /eventwaypoints) while an event is running.", NamedTextColor.GREEN);
            return;
        }
        // removeeventwaypoint
        if (args.length != 1) {
            msg(sender, "Usage: /removeeventwaypoint <name>", NamedTextColor.RED);
            return;
        }
        Waypoint w = findEventWaypoint(args[0]);
        if (w == null) {
            msg(sender, "No event waypoint with that name.", NamedTextColor.RED);
            return;
        }
        eventWaypoints.remove(w);
        saveEventWaypoints();
        msg(sender, "Removed event waypoint '" + w.name + "'.", NamedTextColor.YELLOW);
    }

    // ---------------------------------------------------------------- banker block
    private ItemStack bankerItem() {
        ItemStack it = new ItemStack(Material.NOTE_BLOCK); // the resource pack gives every note block the banker texture
        it.editMeta(m -> {
            m.displayName(line("Banker Block", NamedTextColor.GOLD));
            m.lore(List.of(
                    line("Place it, right-click it and deposit items.", NamedTextColor.GRAY),
                    line("Every " + BANKER_ITEMS_PER_POINT + " items = 1 kill point.", NamedTextColor.YELLOW)));
            m.getPersistentDataContainer().set(bankerKey, PersistentDataType.BYTE, (byte) 1);
        });
        return it;
    }

    /** Banker blocks are note blocks now; barrels are accepted too so any placed during testing keep working. */
    private static boolean isBankerMaterial(Material m) {
        return m == Material.NOTE_BLOCK || m == Material.BARREL;
    }

    private boolean isBankerItem(ItemStack i) {
        return i != null && isBankerMaterial(i.getType()) && i.hasItemMeta()
                && i.getItemMeta().getPersistentDataContainer().has(bankerKey, PersistentDataType.BYTE);
    }

    private void registerBankerRecipe() {
        Bukkit.removeRecipe(bankerRecipeKey); // avoids duplicates after /reload
        ShapedRecipe r = new ShapedRecipe(bankerRecipeKey, bankerItem());
        r.shape("III", "D D", "III");
        r.setIngredient('I', Material.IRON_BLOCK);
        r.setIngredient('D', Material.DIAMOND);
        Bukkit.addRecipe(r);
    }

    private static String blockKey(Block b) {
        return b.getWorld().getName() + ";" + b.getX() + ";" + b.getY() + ";" + b.getZ();
    }

    private void loadBankers() {
        bankerBlocks.clear();
        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(bankerFile);
        bankerBlocks.addAll(cfg.getStringList("blocks"));
    }

    private void saveBankers() {
        YamlConfiguration cfg = new YamlConfiguration();
        cfg.set("blocks", new ArrayList<>(bankerBlocks));
        try {
            cfg.save(bankerFile);
        } catch (IOException ex) {
            getLogger().warning("Could not save bankers.yml: " + ex.getMessage());
        }
    }

    /** Writes the small data pack that adds a "Duel a player" button to the Escape menu. */
    private boolean installDatapack() {
        try {
            Path root = Path.of(Bukkit.getWorlds().get(0).getWorldFolder().getPath(), "datapacks", "PigVsCheese");
            Path version = root.resolve("version.txt");
            if (Files.exists(root.resolve("pack.mcmeta")) && Files.exists(version)
                    && Files.readString(version).trim().equals(DATAPACK_VERSION)) {
                return false; // already installed
            }
            Files.createDirectories(root.resolve("data/pigcheese/dialog"));
            Files.createDirectories(root.resolve("data/minecraft/tags/dialog"));
            Files.writeString(root.resolve("pack.mcmeta"), DP_MCMETA);
            Files.writeString(root.resolve("data/pigcheese/dialog/duel.json"), DP_DIALOG);
            Files.writeString(root.resolve("data/minecraft/tags/dialog/pause_screen_additions.json"), DP_TAG);
            Files.writeString(version, DATAPACK_VERSION);
            getLogger().warning("Installed the Escape-menu Duel button data pack. RESTART the server once to activate it.");
            return true;
        } catch (IOException | RuntimeException ex) {
            getLogger().warning("Could not install the Escape-menu data pack: " + ex.getMessage());
            return false;
        }
    }

    private static byte[] hexToBytes(String hex) {
        if (hex.length() != 40) return null;
        byte[] out = new byte[20];
        for (int i = 0; i < 20; i++) {
            int hi = Character.digit(hex.charAt(i * 2), 16);
            int lo = Character.digit(hex.charAt(i * 2 + 1), 16);
            if (hi < 0 || lo < 0) return null;
            out[i] = (byte) (hi * 16 + lo);
        }
        return out;
    }

    /** Sends the server resource pack (it gives the banker block its texture). */
    private void sendPack(Player p) {
        String url = getConfig().getString("pack.url", "");
        byte[] hash = hexToBytes(getConfig().getString("pack.sha1", "").trim());
        if (url.isBlank() || hash == null) return;
        p.setResourcePack(url, hash, Component.text("Banker Block texture pack"), getConfig().getBoolean("pack.required", false));
    }

    private void adminSetPack(CommandSender sender, String[] args) {
        if (args.length == 1 && args[0].equalsIgnoreCase("off")) {
            getConfig().set("pack.url", "");
            saveConfig();
            msg(sender, "Resource pack turned off (players keep it until they rejoin).", NamedTextColor.YELLOW);
            return;
        }
        if (args.length != 2 || hexToBytes(args[1].trim()) == null) {
            msg(sender, "Usage: /setpack <direct download url> <40-character sha1>   or   /setpack off", NamedTextColor.RED);
            return;
        }
        getConfig().set("pack.url", args[0]);
        getConfig().set("pack.sha1", args[1].trim().toLowerCase());
        saveConfig();
        for (Player p : Bukkit.getOnlinePlayers()) sendPack(p);
        msg(sender, "Resource pack updated and sent to everyone online.", NamedTextColor.GREEN);
    }

    private void adminGiveBanker(CommandSender sender, String[] args) {
        Player t = args.length >= 1 ? Bukkit.getPlayerExact(args[0]) : (sender instanceof Player sp ? sp : null);
        if (t == null) {
            msg(sender, "Usage: /givebanker [online player]", NamedTextColor.RED);
            return;
        }
        giveItem(t, bankerItem());
        msg(sender, "Gave " + t.getName() + " a banker block.", NamedTextColor.GREEN);
    }

    @EventHandler(ignoreCancelled = true)
    public void onBankerPlace(BlockPlaceEvent e) {
        if (!isBankerItem(e.getItemInHand())) return;
        bankerBlocks.add(blockKey(e.getBlockPlaced()));
        saveBankers();
        msg(e.getPlayer(), "Banker block placed! Right-click it to deposit items - every " + BANKER_ITEMS_PER_POINT + " items = 1 kill point.", NamedTextColor.GOLD);
    }

    @EventHandler(ignoreCancelled = true)
    public void onBankerBreak(BlockBreakEvent e) {
        Block b = e.getBlock();
        if (!isBankerMaterial(b.getType()) || !bankerBlocks.remove(blockKey(b))) return;
        saveBankers();
        e.setDropItems(false);
        b.getWorld().dropItemNaturally(b.getLocation().add(0.5, 0.5, 0.5), bankerItem());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBankerUse(PlayerInteractEvent e) {
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK || e.getHand() != EquipmentSlot.HAND) return;
        Block b = e.getClickedBlock();
        if (b == null || !isBankerMaterial(b.getType()) || !bankerBlocks.contains(blockKey(b))) return;
        Player p = e.getPlayer();
        if (p.isSneaking() && !p.getInventory().getItemInMainHand().getType().isAir()) return; // sneak-click to build against it
        e.setCancelled(true);
        openBanker(p);
    }

    private ItemStack bankerInfo(Player p) {
        PlayerData d = dataOf(p.getUniqueId());
        int toNext = BANKER_ITEMS_PER_POINT - (int) (d.itemsBanked % BANKER_ITEMS_PER_POINT);
        return statItem(Material.GOLD_INGOT, "Your banking", NamedTextColor.GOLD,
                "Every " + BANKER_ITEMS_PER_POINT + " items = 1 kill point",
                "Items banked so far: " + d.itemsBanked,
                "Next point in " + toNext + " items",
                "Your kill points: " + d.points);
    }

    private void openBanker(Player p) {
        BankerHolder h = new BankerHolder();
        Inventory inv = Bukkit.createInventory(h, 27, Component.text("Banker - put items in, then Deposit"));
        h.inv = inv;
        ItemStack filler = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        filler.editMeta(m -> m.displayName(Component.text(" ")));
        for (int i = 18; i < 27; i++) inv.setItem(i, filler);
        inv.setItem(20, bankerInfo(p));
        inv.setItem(22, navItem(Material.LIME_CONCRETE, "DEPOSIT", "Click to bank the items above"));
        p.openInventory(inv);
    }

    private void bankerDeposit(Player p, BankerHolder h) {
        int total = 0;
        for (int i = 0; i < 18; i++) {
            ItemStack it = h.inv.getItem(i);
            if (it == null || it.getType().isAir()) continue;
            if (isProtected(it) || isCombatGear(it)) continue; // can't be banked; handed back on close
            total += it.getAmount();
            h.inv.setItem(i, null);
        }
        if (total == 0) {
            msg(p, "Put some items in the top slots first.", NamedTextColor.RED);
            return;
        }
        PlayerData d = dataOf(p.getUniqueId());
        long before = d.itemsBanked / BANKER_ITEMS_PER_POINT;
        d.itemsBanked += total;
        int earned = (int) (d.itemsBanked / BANKER_ITEMS_PER_POINT - before);
        d.points += earned;
        d.pointsEarned += earned;
        saveData();
        int toNext = BANKER_ITEMS_PER_POINT - (int) (d.itemsBanked % BANKER_ITEMS_PER_POINT);
        msg(p, "Banked " + total + " items for +" + earned + " kill point" + (earned == 1 ? "" : "s")
                + " (total " + d.points + "). " + toNext + " more items until your next point.", NamedTextColor.GOLD);
        h.inv.setItem(20, bankerInfo(p));
    }

    @EventHandler
    public void onBankerClick(InventoryClickEvent e) {
        Inventory top = e.getView().getTopInventory();
        if (!(top.getHolder() instanceof BankerHolder bh)) return;
        if (!(e.getWhoClicked() instanceof Player p)) return;
        if (e.getClick() == ClickType.DOUBLE_CLICK) {
            e.setCancelled(true);
            return;
        }
        if (e.getClickedInventory() != null && e.getClickedInventory().equals(top)) {
            int slot = e.getSlot();
            if (slot >= 18) { // bottom row is the control bar
                e.setCancelled(true);
                if (slot == 22) bankerDeposit(p, bh);
            }
        }
    }

    @EventHandler
    public void onBankerDrag(InventoryDragEvent e) {
        Inventory top = e.getView().getTopInventory();
        if (!(top.getHolder() instanceof BankerHolder)) return;
        for (int raw : e.getRawSlots()) {
            if (raw >= 18 && raw < top.getSize()) {
                e.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler
    public void onBankerClose(InventoryCloseEvent e) {
        if (!(e.getInventory().getHolder() instanceof BankerHolder)) return;
        if (!(e.getPlayer() instanceof Player p)) return;
        for (int i = 0; i < 18; i++) { // anything not deposited goes back to the player
            ItemStack it = e.getInventory().getItem(i);
            if (it != null && !it.getType().isAir()) {
                giveItem(p, it);
                e.getInventory().setItem(i, null);
            }
        }
    }

    private void adminTierAll(CommandSender sender, String[] args) {
        if (args.length != 1) {
            msg(sender, "Usage: /settierall <1-" + (MAX_TIER + 1) + ">  or  /settierall cancel", NamedTextColor.RED);
            return;
        }
        if (args[0].equalsIgnoreCase("cancel")) {
            globalTier = -1;
            saveData();
            msg(sender, "Cancelled. Players who already logged in keep their new tier; nobody else will be changed.", NamedTextColor.YELLOW);
            return;
        }
        int tier;
        try {
            tier = Integer.parseInt(args[0]);
        } catch (NumberFormatException ex) {
            tier = 0;
        }
        if (tier < 1 || tier > MAX_TIER + 1) {
            msg(sender, "Tier must be a number from 1 to " + (MAX_TIER + 1) + ".", NamedTextColor.RED);
            return;
        }
        globalTier = tier - 1;
        globalTierVersion++;
        saveData();
        msg(sender, "Every existing player's tier will be set to " + tier + " (" + cap(ARMOR[globalTier].toLowerCase())
                + ") the next time they log in. People online right now are changed when they next rejoin. "
                + "Use /settierall cancel to undo it before they log in.", NamedTextColor.GREEN);
    }

    private void adminTier(CommandSender sender, String name, String[] args) {
        boolean isSet = name.equals("settier");
        if (args.length != (isSet ? 2 : 1)) {
            msg(sender, isSet ? "Usage: /settier <player> <1-" + (MAX_TIER + 1) + ">" : "Usage: /" + name + " <player>", NamedTextColor.RED);
            return;
        }
        Player tgt = Bukkit.getPlayerExact(args[0]);
        if (tgt == null) {
            msg(sender, "That player isn't online.", NamedTextColor.RED);
            return;
        }
        if (teamOf(tgt) == null) {
            msg(sender, "That player has no team yet.", NamedTextColor.RED);
            return;
        }
        int oldTier = tierOf(tgt);
        int newTier;
        if (isSet) {
            try {
                newTier = Integer.parseInt(args[1]) - 1;
            } catch (NumberFormatException ex) {
                msg(sender, "Tier must be a number from 1 to " + (MAX_TIER + 1) + ".", NamedTextColor.RED);
                return;
            }
            if (newTier < 0 || newTier > MAX_TIER) {
                msg(sender, "Tier must be a number from 1 to " + (MAX_TIER + 1) + ".", NamedTextColor.RED);
                return;
            }
        } else {
            newTier = oldTier + (name.equals("tierup") ? 1 : -1);
            if (newTier < 0 || newTier > MAX_TIER) {
                msg(sender, tgt.getName() + " is already at the " + (newTier < 0 ? "lowest" : "highest") + " tier.", NamedTextColor.RED);
                return;
            }
        }
        setTier(tgt, newTier);
        setKills(tgt, 0);
        saveData();
        applyTier(tgt);
        msg(tgt, "An operator set your tier to " + (newTier + 1) + ".", NamedTextColor.GOLD);
        msg(sender, tgt.getName() + " is now tier " + (newTier + 1) + ".", NamedTextColor.GREEN);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        String n = cmd.getName().toLowerCase();
        if (n.equals("setteamspawn") && args.length == 1) return List.of("pig", "cheese");
        if (n.equals("lockteam") && args.length == 2) return List.of("pig", "cheese");
        if (n.equals("ah") && args.length == 1) return List.of("sell", "global");
        if (n.equals("ah") && args.length == 3 && args[0].equalsIgnoreCase("sell")) return List.of("global", "team");
        if (n.equals("duel") && args.length == 1) {
            List<String> l = new ArrayList<>();
            l.add("end");
            for (Player o : Bukkit.getOnlinePlayers()) l.add(o.getName());
            return l;
        }
        if ((n.equals("setprice") || n.equals("resetprice")) && args.length == 1) {
            String pre = args[0].toLowerCase();
            List<String> out = new ArrayList<>();
            if ("hand".startsWith(pre)) out.add("hand");
            if (n.equals("setprice") && "list".startsWith(pre)) out.add("list");
            if ("healing_potion".startsWith(pre)) out.add("healing_potion");
            for (ShopEntry en : shop) {
                String k = en.key().replace(' ', '_');
                if (k.startsWith(pre)) {
                    out.add(k);
                    if (out.size() >= 40) break;
                }
            }
            return out;
        }
        if ((n.equals("setbase") || n.equals("removebase")) && args.length == 1) return List.of("pig", "cheese");
        if (n.equals("setbase") && args.length == 2) return List.of("30", "50", "100");
        if (n.equals("basepvp") && args.length == 1) return List.of("on", "off");
        if (n.equals("removeeventwaypoint") && args.length == 1) {
            List<String> names = new ArrayList<>();
            for (Waypoint w : eventWaypoints) names.add(w.name);
            return names;
        }
        if (n.equals("event")) {
            if (args.length == 1) return List.of("start", "stop", "status", "list");
            if (args.length == 2 && args[0].equalsIgnoreCase("start")) return List.of("chaos", "randomtiers", "randomenchants", "doublepoints");
            if (args.length == 3 && args[0].equalsIgnoreCase("start")) return List.of("10m", "30m", "1h");
            if (args.length == 4 && args[0].equalsIgnoreCase("start")) return List.of("ffa", "teams");
            return List.of();
        }
        if (n.equals("shop") || n.equals("createwaypoint")) return List.of();
        if (n.equals("managewaypoint") && sender instanceof Player p) {
            if (args.length == 1) return List.of("rename", "delete");
            if (args.length == 2) {
                List<String> names = new ArrayList<>();
                for (Waypoint w : waypointsOf(p)) names.add(w.name);
                return names;
            }
            return List.of();
        }
        return null;
    }

    // ---------------------------------------------------------------- join / quit
    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        sessionStart.put(p.getUniqueId(), System.currentTimeMillis());
        p.discoverRecipe(bankerRecipeKey); // shows the banker block in the recipe book
        sendPack(p);
        if (datapackJustInstalled && p.hasPermission("pigcheese.admin")) {
            msg(p, "The pause-menu Duel button was just installed - restart the server ONCE to activate it.", NamedTextColor.GOLD);
        }
        Bukkit.getScheduler().runTask(this, () -> processJoin(p));
    }

    /** Everything that happens when someone joins: team balancing, kit, welcome screen / protection. */
    private void processJoin(Player p) {
        {
            if (!p.isOnline()) return;
            UUID id = p.getUniqueId();

            // import from older plugin versions if present
            if (!data.containsKey(id)) {
                String old = p.getPersistentDataContainer().get(oldTeamKey, PersistentDataType.STRING);
                if (old != null) {
                    PlayerData od = dataOf(id);
                    od.team = old;
                    od.tier = p.getPersistentDataContainer().getOrDefault(oldTierKey, PersistentDataType.INTEGER, 0);
                    od.kills = p.getPersistentDataContainer().getOrDefault(oldKillsKey, PersistentDataType.INTEGER, 0);
                    od.seenGuide = true;
                }
            }

            p.removePotionEffect(PotionEffectType.BLINDNESS);
            p.setInvulnerable(false);

            String previous = teamOf(p);
            PlayerData existing = data.get(id);
            String forced = existing == null ? null : existing.lockedTeam;
            String team = forced != null ? forced : pickBalancedTeam(p); // re-balanced on EVERY join
            boolean moved = previous == null || !previous.equals(team);
            assignTeam(p, team, moved);

            PlayerData d = dataOf(id);
            if (d.tier < MAX_TIER) d.kills = Math.min(d.kills, killsNeeded(d.tier) - 1);
            if (globalTier >= 0 && globalTierVersion > d.appliedTierVersion) {
                d.appliedTierVersion = globalTierVersion;
                d.tier = globalTier;
                d.kills = 0;
                d.strikes = 0;
                applyTier(p);
                saveData();
                p.sendMessage(Component.text("An operator set everyone's tier - yours is now tier " + (globalTier + 1) + ".", NamedTextColor.GOLD));
            }
            if (eventType != null) onEventJoin(p, d);
            if (d.notice != null) {
                p.sendMessage(Component.text(d.notice, NamedTextColor.RED));
                d.notice = null;
                saveData();
            }
            if (!d.seenGuide) {
                guidePending.add(id);
                p.setInvulnerable(true);
                openGuideMenu(p); // forced welcome screen; spawn protection starts when they click "I understand"
            } else {
                giveProtection(p);
                p.sendMessage(Component.text("Welcome back! Tier " + (tierOf(p) + 1) + ", "
                        + pointsOf(p) + " kill points. Type /help for the guide.", NamedTextColor.GREEN));
            }
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        Player p = e.getPlayer();
        UUID id = p.getUniqueId();
        searching.remove(id);
        intruders.remove(id);
        flushPlaytime(id, false);
        tpaRequests.remove(id);
        duelRequests.remove(id);
        UUID duelOther = duels.get(id);
        if (duelOther != null) {
            endDuel(id);
            Player o = Bukkit.getPlayer(duelOther);
            if (o != null) msg(o, p.getName() + " left the game, so the duel is over.", NamedTextColor.YELLOW);
        }
        protectedUntil.remove(id);
        if (guidePending.remove(id)) {
            p.removePotionEffect(PotionEffectType.BLINDNESS);
            p.setInvulnerable(false);
        }
        if (isInCombat(p) && teamOf(p) != null) {
            PlayerData d = dataOf(id);
            String summary = punish(d);
            d.notice = "You logged out while in combat and were punished: " + summary + ".";
            Bukkit.broadcast(Component.text(p.getName() + " logged out in combat and was punished!", NamedTextColor.RED));
        }
        combatUntil.remove(id);
        saveData();
        saveAuction();
        saveWaypoints();
    }

    @EventHandler
    public void onFrozenMove(PlayerMoveEvent e) {
        if (!frozen.contains(e.getPlayer().getUniqueId()) || e.getTo() == null) return;
        Location f = e.getFrom(), t = e.getTo();
        if (f.getX() != t.getX() || f.getY() != t.getY() || f.getZ() != t.getZ()) e.setCancelled(true);
    }

    // ---------------------------------------------------------------- PvP rules
    @EventHandler
    public void onDamage(EntityDamageByEntityEvent e) {
        if (!(e.getEntity() instanceof Player victim)) return;
        Player attacker = attackerOf(e);
        if (attacker == null || attacker.equals(victim)) return;

        // Duels: duelists can only hurt each other (even teammates), and nobody else can join in
        if (duels.containsKey(attacker.getUniqueId()) || duels.containsKey(victim.getUniqueId())) {
            if (!areDueling(attacker, victim)) e.setCancelled(true);
            return;
        }

        String a = teamOf(attacker), v = teamOf(victim);
        if (a == null || v == null || (a.equals(v) && !evFfa)) { // free-for-all events let teammates fight
            e.setCancelled(true);
            return;
        }
        // PvP is only open in duels (handled above), during events, and when an enemy is raiding a base
        if (eventType == null && !isIntruder(attacker) && !isIntruder(victim)) {
            e.setCancelled(true);
            attacker.sendActionBar(Component.text("PvP needs a duel! Use /duel " + victim.getName()
                    + " (or press Escape > Duel a player).", NamedTextColor.RED));
            return;
        }
        if (protectedUntil.remove(attacker.getUniqueId()) != null) {
            attacker.sendMessage(Component.text("Your spawn protection ended because you attacked.", NamedTextColor.RED));
        }
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent e) {
        Player victim = e.getEntity();
        Player killer = victim.getKiller();

        // Keep gear so tiers can be swapped in place
        e.setKeepInventory(true);
        e.setKeepLevel(true);
        e.getDrops().clear();
        e.setShouldDropExperience(false);

        int victimTier = tierOf(victim); // tier BEFORE the drop, used for kill points

        // Duel result. Losing a duel to a TEAMMATE costs nothing; duels vs the other team use the normal rules.
        boolean friendlyDuelLoss = false;
        boolean pvpDeath = killer != null && !killer.equals(victim); // only deaths caused by another player can cost a tier
        UUID partnerId = duels.get(victim.getUniqueId());
        if (partnerId != null) {
            Player partner = Bukkit.getPlayer(partnerId);
            endDuel(victim.getUniqueId());
            if (partner != null) {
                if (partner.equals(killer)) {
                    dataOf(partner.getUniqueId()).duelWins++;
                    dataOf(victim.getUniqueId()).duelLosses++;
                    Component won = Component.text(partner.getName() + " won the duel against " + victim.getName() + "!", NamedTextColor.GOLD);
                    partner.sendMessage(won);
                    victim.sendMessage(won);
                    String vt0 = teamOf(victim), pt0 = teamOf(partner);
                    if (vt0 != null && vt0.equals(pt0)) {
                        friendlyDuelLoss = true;
                        victim.sendMessage(Component.text("Friendly duel - you keep your tier.", NamedTextColor.GREEN));
                    }
                } else {
                    msg(partner, "The duel ended because " + victim.getName() + " died.", NamedTextColor.YELLOW);
                }
            }
        }

        if (!pvpDeath) {
            // Not killed by a player (lava, falls, mobs, the void...): nothing is counted or changed at all
            victim.sendMessage(Component.text("You died, but not to a player - nothing changes.", NamedTextColor.GREEN));
        } else if (!friendlyDuelLoss) {
            PlayerData vd = dataOf(victim.getUniqueId());
            vd.deaths++;
            vd.streak = 0;
            if (evRandomTiers) {
                int rolled = ThreadLocalRandom.current().nextInt(MAX_TIER + 1);
                setTier(victim, rolled);
                vd.strikes = 0;
                victim.sendMessage(Component.text("EVENT: you respawn as tier " + (rolled + 1) + "!", NamedTextColor.LIGHT_PURPLE));
            } else if (victimTier > 0) {
                vd.strikes++;
                int needed = deathsToDrop(victimTier);
                if (vd.strikes >= needed) {
                    setTier(victim, victimTier - 1); // also clears strikes
                    vd.kills = Math.min(vd.kills, Math.max(0, killsNeeded(victimTier - 1) - 1)); // keep progress, within range
                    victim.sendMessage(Component.text("You died - dropped to tier " + victimTier + ".", NamedTextColor.RED));
                } else {
                    victim.sendMessage(Component.text("You died - " + vd.strikes + "/" + needed
                            + " deaths before you lose a tier.", NamedTextColor.RED));
                }
            }
        }
        protectedUntil.remove(victim.getUniqueId());
        combatUntil.remove(victim.getUniqueId());

        if (killer != null && !killer.equals(victim)) {
            String kt = teamOf(killer), vt = teamOf(victim);
            if (kt != null && vt != null && (evFfa || !kt.equals(vt))) awardKill(killer, victimTier);
        }

        saveData();
        Bukkit.getScheduler().runTask(this, () -> {
            if (evRandomEnchants && pvpDeath) stripGearEnchants(victim); // re-roll enchantments after each player kill
            applyTier(victim);
            if (killer != null) applyTier(killer);
        });
    }

    /** Gives kill points + tier progress for killing a player who was at victimTier (0-5). */
    private void awardKill(Player killer, int victimTier) {
        int earned = KILL_POINTS[Math.max(0, Math.min(MAX_TIER, victimTier))] * (evDoublePoints ? 2 : 1);
        PlayerData kd = dataOf(killer.getUniqueId());
        kd.points += earned;
        kd.pointsEarned += earned;
        kd.totalKills++;
        kd.streak++;
        kd.bestStreak = Math.max(kd.bestStreak, kd.streak);

        if (evRandomTiers) { // tiers are random during this event: points only, no tier progress
            killer.sendMessage(Component.text("+" + earned + " kill points (total " + pointsOf(killer) + "). Spend them with /shop or /ah.", NamedTextColor.GOLD));
            return;
        }
        int kills = killsOf(killer) + 1;
        int cur = tierOf(killer);
        if (cur >= MAX_TIER) {
            kills = 0; // already at the top
        } else if (kills >= killsNeeded(cur)) {
            kills = 0;
            setTier(killer, cur + 1);
            kd.highestTier = Math.max(kd.highestTier, kd.tier);
            killer.sendMessage(Component.text("TIER UP! You are now tier " + (cur + 2)
                    + "! (next tier needs " + (cur + 1 < MAX_TIER ? killsNeeded(cur + 1) + " kills" : "no more kills - you're at the top") + ")", NamedTextColor.GOLD));
        } else {
            killer.sendMessage(Component.text("Kills toward next tier: " + kills + "/" + killsNeeded(cur), NamedTextColor.GREEN));
        }
        setKills(killer, kills);
        killer.sendMessage(Component.text("+" + earned + " kill points (total " + pointsOf(killer) + "). Spend them with /shop or /ah.", NamedTextColor.GOLD));
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent e) {
        Player p = e.getPlayer();
        Location spawn = spawnOf(teamOf(p));
        if (spawn != null) e.setRespawnLocation(spawn);
        Bukkit.getScheduler().runTask(this, () -> {
            applyTier(p);
            giveProtection(p);
        });
    }

    // ---------------------------------------------------------------- explosives disabled
    @EventHandler
    public void onExplosionPrime(ExplosionPrimeEvent e) {
        if (e.getEntity() instanceof AbstractWindCharge) return; // wind charges only push, keep them working
        e.setCancelled(true);
    }

    @EventHandler
    public void onEntityExplode(EntityExplodeEvent e) {
        if (e.getEntity() instanceof AbstractWindCharge) return;
        e.setCancelled(true);
    }

    @EventHandler
    public void onBlockExplode(BlockExplodeEvent e) {
        e.setCancelled(true); // beds in the Nether/End, respawn anchors, etc.
    }

    // ---------------------------------------------------------------- redstone disabled
    private boolean isRedstone(Material m) {
        String n = m.name();
        if (n.endsWith("_ORE")) return false;
        return n.contains("REDSTONE") || n.equals("REPEATER") || n.equals("COMPARATOR")
                || n.equals("LEVER") || n.endsWith("_BUTTON") || n.endsWith("_PRESSURE_PLATE")
                || n.equals("OBSERVER") || n.contains("PISTON") || n.equals("DISPENSER")
                || n.equals("DROPPER") || n.equals("TARGET") || n.equals("TRIPWIRE_HOOK")
                || n.equals("TRIPWIRE") || n.startsWith("DAYLIGHT_DETECTOR") || n.equals("CRAFTER")
                || n.equals("LIGHTNING_ROD") || n.equals("SCULK_SENSOR") || n.equals("CALIBRATED_SCULK_SENSOR");
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent e) {
        Material placed = e.getBlockPlaced().getType();
        Material hand = e.getItemInHand().getType();
        if (isRedstone(placed) || isRedstone(hand) || EXPLOSIVES.contains(placed.name()) || EXPLOSIVES.contains(hand.name())) {
            e.setCancelled(true);
        }
        if (placed == Material.NOTE_BLOCK && !isBankerItem(e.getItemInHand())) {
            e.setCancelled(true); // only banker blocks may be note blocks
        }
    }

    @EventHandler
    public void onNotePlay(NotePlayEvent e) {
        e.setCancelled(true); // no note-block sounds, so the banker block stays silent
    }

    @EventHandler
    public void onRedstoneSignal(BlockRedstoneEvent e) {
        e.setNewCurrent(e.getOldCurrent());
    }

    @EventHandler
    public void onPistonExtend(BlockPistonExtendEvent e) { e.setCancelled(true); }

    @EventHandler
    public void onPistonRetract(BlockPistonRetractEvent e) { e.setCancelled(true); }

    @EventHandler
    public void onDispense(BlockDispenseEvent e) { e.setCancelled(true); }

    // ---------------------------------------------------------------- movement items / explosive items disabled
    @EventHandler
    public void onUse(PlayerInteractEvent e) {
        if (e.useInteractedBlock() == Event.Result.DENY && e.useItemInHand() == Event.Result.DENY) return;

        if (e.getClickedBlock() != null && isRedstone(e.getClickedBlock().getType())
                && (e.getAction() == Action.RIGHT_CLICK_BLOCK || e.getAction() == Action.PHYSICAL)) {
            e.setCancelled(true);
            return;
        }

        if (e.getAction() != Action.RIGHT_CLICK_AIR && e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        ItemStack item = e.getItem();
        if (item == null) return;
        Material m = item.getType();
        String n = m.name();

        boolean riptide = m == Material.TRIDENT && item.containsEnchantment(Enchantment.RIPTIDE);
        boolean minecart = n.endsWith("MINECART");
        boolean banned = n.equals("ENDER_PEARL") || n.equals("CHORUS_FRUIT") || EXPLOSIVES.contains(n);
        boolean windLocked = m == Material.WIND_CHARGE && tierOf(e.getPlayer()) < MAX_TIER;
        if (banned || riptide || minecart || windLocked) {
            e.setCancelled(true);
            e.getPlayer().sendActionBar(Component.text("That item is disabled!", NamedTextColor.RED));
        }
    }

    @EventHandler
    public void onLaunch(ProjectileLaunchEvent e) {
        if (e.getEntity() instanceof EnderPearl || e.getEntity() instanceof Firework) {
            e.setCancelled(true);
        } else if (e.getEntity() instanceof WindCharge wc && wc.getShooter() instanceof Player p) {
            if (tierOf(p) < MAX_TIER) {
                e.setCancelled(true); // only netherite tier may use wind charges
                return;
            }
            // Wind charges never run out: put the stack back after the game uses one up
            PlayerInventory inv = p.getInventory();
            int heldSlot = inv.getHeldItemSlot();
            ItemStack main = inv.getItem(heldSlot) == null ? null : inv.getItem(heldSlot).clone();
            ItemStack off = inv.getItemInOffHand().clone();
            Bukkit.getScheduler().runTask(this, () -> {
                if (!p.isOnline()) return;
                PlayerInventory now = p.getInventory();
                if (main != null && main.getType() == Material.WIND_CHARGE) {
                    ItemStack cur = now.getItem(heldSlot);
                    if (cur == null || cur.getType().isAir()
                            || (cur.getType() == Material.WIND_CHARGE && cur.getAmount() < main.getAmount())) {
                        now.setItem(heldSlot, main);
                    }
                }
                if (off.getType() == Material.WIND_CHARGE) {
                    ItemStack cur = now.getItemInOffHand();
                    if (cur.getType().isAir()
                            || (cur.getType() == Material.WIND_CHARGE && cur.getAmount() < off.getAmount())) {
                        now.setItemInOffHand(off);
                    }
                }
            });
        }
    }

    @EventHandler
    public void onTeleport(PlayerTeleportEvent e) {
        PlayerTeleportEvent.TeleportCause c = e.getCause();
        if (c == PlayerTeleportEvent.TeleportCause.ENDER_PEARL || c == PlayerTeleportEvent.TeleportCause.CHORUS_FRUIT) {
            e.setCancelled(true);
            return;
        }
        // portals count as teleporting while in combat
        if ((c == PlayerTeleportEvent.TeleportCause.NETHER_PORTAL || c == PlayerTeleportEvent.TeleportCause.END_PORTAL
                || c == PlayerTeleportEvent.TeleportCause.END_GATEWAY) && isInCombat(e.getPlayer())) {
            punishCombat(e.getPlayer(), "using a portal");
        }
    }

    @EventHandler
    public void onGlide(EntityToggleGlideEvent e) {
        if (e.isGliding()) e.setCancelled(true); // no elytra flight
    }

    // ---------------------------------------------------------------- no crafting / trading / looting armour & weapons
    @EventHandler
    public void onPrepareCraft(PrepareItemCraftEvent e) {
        ItemStack result = e.getInventory().getResult();
        if (result != null && (isCombatGear(result) || (result.getType() == Material.NOTE_BLOCK && !isBankerItem(result)))) {
            e.getInventory().setResult(null);
        }
    }

    @EventHandler
    public void onCraft(CraftItemEvent e) {
        ItemStack made = e.getRecipe().getResult();
        if (isCombatGear(made) || (made.getType() == Material.NOTE_BLOCK && !isBankerItem(made))) e.setCancelled(true);
    }

    @EventHandler
    public void onPrepareSmithing(PrepareSmithingEvent e) {
        ItemStack result = e.getResult();
        if (result == null || !isCombatGear(result)) return;
        ItemStack base = e.getInventory().getInputEquipment();
        if (base == null || base.getType() != result.getType()) e.setResult(null); // blocks netherite upgrades
    }

    @EventHandler
    public void onPickup(EntityPickupItemEvent e) {
        if (e.getEntity() instanceof Player && isCombatGear(e.getItem().getItemStack())) e.setCancelled(true);
    }

    // ---------------------------------------------------------------- protected items can never be lost
    @EventHandler
    public void onItemDamage(PlayerItemDamageEvent e) {
        if (isGear(e.getItem())) e.setCancelled(true);
    }

    @EventHandler
    public void onDrop(PlayerDropItemEvent e) {
        if (isProtected(e.getItemDrop().getItemStack())) e.setCancelled(true);
    }

    @EventHandler
    public void onGearClick(InventoryClickEvent e) {
        ItemStack cursor = e.getCursor();
        ItemStack current = e.getCurrentItem();

        if ((e.getClick() == ClickType.DROP || e.getClick() == ClickType.CONTROL_DROP) && isProtected(current)) {
            e.setCancelled(true);
            return;
        }
        if (e.getSlotType() == InventoryType.SlotType.OUTSIDE && isProtected(cursor)) {
            e.setCancelled(true);
            return;
        }

        Inventory top = e.getView().getTopInventory();
        if (top.getType() == InventoryType.CRAFTING) return; // just their own inventory

        boolean clickedTop = e.getClickedInventory() != null && e.getClickedInventory().equals(top);
        if (clickedTop && isProtected(cursor)) e.setCancelled(true);
        if (!clickedTop && e.isShiftClick() && isProtected(current)) e.setCancelled(true);
        if (clickedTop && e.getClick() == ClickType.NUMBER_KEY
                && isProtected(e.getWhoClicked().getInventory().getItem(e.getHotbarButton()))) e.setCancelled(true);
        if (clickedTop && e.getClick() == ClickType.SWAP_OFFHAND
                && isProtected(e.getWhoClicked().getInventory().getItemInOffHand())) e.setCancelled(true);
        // can't take armour/weapons out of chests, villager trades, etc.
        if (clickedTop && isCombatGear(current)) e.setCancelled(true);
    }

    @EventHandler
    public void onGearDrag(InventoryDragEvent e) {
        if (!isProtected(e.getOldCursor())) return;
        Inventory top = e.getView().getTopInventory();
        if (top.getType() == InventoryType.CRAFTING) return;
        for (int slot : e.getRawSlots()) {
            if (slot < top.getSize()) {
                e.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler
    public void onFrame(PlayerInteractEntityEvent e) {
        if (e.getRightClicked() instanceof ItemFrame
                && isProtected(e.getPlayer().getInventory().getItem(e.getHand()))) {
            e.setCancelled(true);
        }
    }

    @EventHandler
    public void onStand(PlayerArmorStandManipulateEvent e) {
        if (isProtected(e.getPlayerItem()) || isProtected(e.getArmorStandItem())) e.setCancelled(true);
    }

    // ---------------------------------------------------------------- shop (/shop) - every item that isn't banned
    private boolean isBannedItem(Material m) {
        String n = m.name();
        if (isRedstone(m) || EXPLOSIVES.contains(n) || MOVEMENT_ITEMS.contains(n) || n.endsWith("MINECART")
                || isCombatGear(m) || OP_ONLY_ITEMS.contains(n)) return true;
        return n.contains("SPAWN_EGG") || n.contains("COMMAND_BLOCK") || n.startsWith("INFESTED_")
                || n.startsWith("TEST_") || n.contains("SPAWNER") || n.startsWith("LEGACY_");
    }

    private static final Map<String, Integer> PRICE = new HashMap<>();

    static {
        PRICE.put("GOLDEN_APPLE", 60);
        PRICE.put("ENCHANTED_GOLDEN_APPLE", 500);
        PRICE.put("TOTEM_OF_UNDYING", 250);
        PRICE.put("SADDLE", 80);
        PRICE.put("BOW", 40);
        PRICE.put("CROSSBOW", 50);
        PRICE.put("SHIELD", 35);
        PRICE.put("ARROW", 1);
        PRICE.put("NETHERITE_SCRAP", 60);
        PRICE.put("BEACON", 400);
        PRICE.put("NETHER_STAR", 500);
        PRICE.put("ENDER_CHEST", 60);
        PRICE.put("DRAGON_EGG", 800);
        PRICE.put("DRAGON_HEAD", 150);
        PRICE.put("CONDUIT", 200);
        PRICE.put("HEART_OF_THE_SEA", 150);
        PRICE.put("NAUTILUS_SHELL", 30);
        PRICE.put("ECHO_SHARD", 40);
        PRICE.put("NAME_TAG", 20);
        PRICE.put("BLAZE_ROD", 10);
        PRICE.put("GHAST_TEAR", 30);
        PRICE.put("ENCHANTING_TABLE", 40);
        PRICE.put("ANVIL", 25);
        PRICE.put("EXPERIENCE_BOTTLE", 10);
        PRICE.put("LODESTONE", 60);
        PRICE.put("SHULKER_SHELL", 40);
        PRICE.put("OBSIDIAN", 6);
        PRICE.put("CRYING_OBSIDIAN", 12);
        PRICE.put("OAK_BOAT", 15);
        PRICE.put("TRIDENT", 120);
    }

    private int metalUnit(String n) {
        if (n.contains("NETHERITE")) return 250;
        if (n.contains("DIAMOND")) return 40;
        if (n.contains("EMERALD")) return 15;
        if (n.contains("GOLD")) return 8;
        if (n.contains("IRON")) return 5;
        if (n.contains("COPPER")) return 2;
        if (n.contains("LAPIS")) return 3;
        if (n.contains("AMETHYST")) return 4;
        return 0;
    }

    /** Price in kill points for ONE item. */
    private int priceOf(Material m) {
        String n = m.name();
        Integer custom = customPrices.get(n);
        if (custom != null) return custom;
        Integer o = PRICE.get(n);
        if (o != null) return o;
        if (n.endsWith("_NUGGET")) return 1;
        if (n.contains("MUSIC_DISC")) return 30;
        if (n.contains("SHULKER_BOX")) return 120;
        if (n.contains("SKULL") || n.endsWith("_HEAD")) return 60;
        int unit = metalUnit(n);
        if (unit > 0) {
            if (n.endsWith("_BLOCK")) return unit * 9;
            if (n.endsWith("_PICKAXE") || n.endsWith("_AXE") || n.endsWith("_SHOVEL") || n.endsWith("_HOE")) return unit * 3;
            return unit;
        }
        return m.isBlock() ? 1 : 3;
    }

    private void loadPrices() {
        customPrices.clear();
        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(pricesFile);
        ConfigurationSection sec = cfg.getConfigurationSection("prices");
        if (sec == null) return;
        for (String key : sec.getKeys(false)) customPrices.put(key.toUpperCase(), sec.getInt(key));
    }

    private void savePrices() {
        YamlConfiguration cfg = new YamlConfiguration();
        for (Map.Entry<String, Integer> en : customPrices.entrySet()) cfg.set("prices." + en.getKey(), en.getValue());
        try {
            cfg.save(pricesFile);
        } catch (IOException ex) {
            getLogger().warning("Could not save prices.yml: " + ex.getMessage());
        }
    }

    /** Turns "golden apple" / "GOLDEN_APPLE" / "hand" into a shop price key, or null (with a message) if it isn't sold. */
    private String resolvePriceKey(CommandSender sender, String input) {
        String in = input.trim().toUpperCase().replace(' ', '_').replace('-', '_');
        if (in.equals("HAND")) {
            if (!(sender instanceof Player p)) {
                msg(sender, "Hold the item in-game to use 'hand', or type the item name.", NamedTextColor.RED);
                return null;
            }
            Material held = p.getInventory().getItemInMainHand().getType();
            if (held.isAir()) {
                msg(sender, "Hold an item first.", NamedTextColor.RED);
                return null;
            }
            in = held.name();
        }
        if (in.equals("HEALING_POTION")) return in; // the splash potion of healing entry
        Material m = Material.matchMaterial(in);
        if (m == null || !m.isItem() || m.isAir() || isBannedItem(m)) {
            msg(sender, "That item isn't sold in the shop (unknown or banned).", NamedTextColor.RED);
            return null;
        }
        return m.name();
    }

    private void adminPrice(CommandSender sender, String name, String[] args) {
        boolean reset = name.equals("resetprice");
        if (!reset && args.length == 1 && args[0].equalsIgnoreCase("list")) {
            if (customPrices.isEmpty()) {
                msg(sender, "No custom prices set - everything uses the default prices.", NamedTextColor.YELLOW);
                return;
            }
            msg(sender, "Custom prices (per item):", NamedTextColor.GOLD);
            for (Map.Entry<String, Integer> en : new TreeMap<>(customPrices).entrySet()) {
                msg(sender, "  " + en.getKey().toLowerCase() + " = " + en.getValue(), NamedTextColor.YELLOW);
            }
            return;
        }
        if (args.length != (reset ? 1 : 2)) {
            msg(sender, reset ? "Usage: /resetprice <item|hand>" : "Usage: /setprice <item|hand> <points per item>  (or /setprice list)", NamedTextColor.RED);
            return;
        }
        String key = resolvePriceKey(sender, args[0]);
        if (key == null) return;

        if (reset) {
            if (customPrices.remove(key) == null) {
                msg(sender, key.toLowerCase() + " already uses its default price.", NamedTextColor.YELLOW);
                return;
            }
            savePrices();
            buildShop();
            Material m = Material.matchMaterial(key);
            int def = m != null ? priceOf(m) : 55;
            msg(sender, key.toLowerCase() + " is back to its default price: " + def + " points each.", NamedTextColor.GREEN);
            return;
        }

        int price;
        try {
            price = Integer.parseInt(args[1]);
        } catch (NumberFormatException ex) {
            msg(sender, "The price must be a whole number.", NamedTextColor.RED);
            return;
        }
        if (price < 1 || price > 1_000_000) {
            msg(sender, "The price must be between 1 and 1,000,000.", NamedTextColor.RED);
            return;
        }
        customPrices.put(key, price);
        savePrices();
        buildShop();
        msg(sender, key.toLowerCase() + " now costs " + price + " points each. (Open shop menus update when reopened.)", NamedTextColor.GREEN);
    }

    private void buildShop() {
        shop.clear();
        for (Material m : Material.values()) {
            if (m.isLegacy() || !m.isItem() || m.isAir() || isBannedItem(m)) continue;
            shop.add(new ShopEntry(new ItemStack(m), priceOf(m), m.name().toLowerCase().replace('_', ' ')));
        }
        shop.sort(Comparator.comparing(ShopEntry::key));

        ItemStack heal = new ItemStack(Material.SPLASH_POTION, 1);
        heal.editMeta(PotionMeta.class, pm -> pm.setBasePotionType(PotionType.STRONG_HEALING));
        shop.add(0, new ShopEntry(heal, customPrices.getOrDefault("HEALING_POTION", 55), "splash potion of healing"));
        getLogger().info("Shop loaded with " + shop.size() + " items.");
    }

    private ItemStack pointsIcon(Player p) {
        ItemStack icon = new ItemStack(Material.GOLD_INGOT);
        icon.editMeta(m -> m.displayName(line("Your kill points: " + pointsOf(p), NamedTextColor.GOLD)));
        return icon;
    }

    private ItemStack navItem(Material mat, String name, String sub) {
        ItemStack it = new ItemStack(mat);
        it.editMeta(m -> {
            m.displayName(line(name, NamedTextColor.WHITE));
            if (sub != null) m.lore(List.of(line(sub, NamedTextColor.GRAY)));
        });
        return it;
    }

    private void openShop(Player p, String query, int page) {
        String q = query == null ? "" : query.trim().toLowerCase();
        String[] tokens = q.isEmpty() ? new String[0] : q.split("\\s+");

        List<ShopEntry> results = new ArrayList<>();
        for (ShopEntry en : shop) {
            boolean ok = true;
            for (String t : tokens) {
                if (!en.key().contains(t)) {
                    ok = false;
                    break;
                }
            }
            if (ok) results.add(en);
        }

        int pages = Math.max(1, (results.size() + SHOP_PAGE_SIZE - 1) / SHOP_PAGE_SIZE);
        int pg = Math.max(0, Math.min(page, pages - 1));

        ShopHolder h = new ShopHolder();
        h.results = results;
        h.page = pg;
        h.query = q;
        String title = "Shop" + (q.isEmpty() ? "" : ": " + q) + " (" + (pg + 1) + "/" + pages + ")";
        Inventory inv = Bukkit.createInventory(h, 54, Component.text(title));
        h.inv = inv;

        for (int i = 0; i < SHOP_PAGE_SIZE; i++) {
            int idx = pg * SHOP_PAGE_SIZE + i;
            if (idx >= results.size()) break;
            ShopEntry en = results.get(idx);
            ItemStack show = en.stack().clone();
            show.editMeta(m -> {
                List<Component> lore = new ArrayList<>();
                lore.add(line("Price: " + en.unit() + " points each", NamedTextColor.GOLD));
                lore.add(line("Left-click: buy 1", NamedTextColor.GRAY));
                lore.add(line("Shift-click: buy 16", NamedTextColor.GRAY));
                lore.add(line("Right-click: buy a full stack", NamedTextColor.GRAY));
                m.lore(lore);
            });
            inv.setItem(i, show);
        }

        inv.setItem(45, navItem(Material.ARROW, "Previous page", null));
        inv.setItem(47, navItem(Material.COMPASS, "Search", "Click, then type in chat"));
        inv.setItem(49, pointsIcon(p));
        inv.setItem(51, navItem(Material.BARRIER, "Clear search", null));
        inv.setItem(53, navItem(Material.ARROW, "Next page", null));
        p.openInventory(inv);
    }

    private void giveItem(Player p, ItemStack item) {
        Map<Integer, ItemStack> left = p.getInventory().addItem(item);
        for (ItemStack rest : left.values()) {
            p.getWorld().dropItemNaturally(p.getLocation(), rest);
        }
    }

    private void shopClick(Player p, ShopHolder h, InventoryClickEvent e) {
        int slot = e.getSlot();
        if (slot == 45) {
            Bukkit.getScheduler().runTask(this, () -> openShop(p, h.query, h.page - 1));
            return;
        }
        if (slot == 53) {
            Bukkit.getScheduler().runTask(this, () -> openShop(p, h.query, h.page + 1));
            return;
        }
        if (slot == 51) {
            Bukkit.getScheduler().runTask(this, () -> openShop(p, "", 0));
            return;
        }
        if (slot == 47) {
            Bukkit.getScheduler().runTask(this, () -> {
                p.closeInventory();
                searching.add(p.getUniqueId());
                p.sendMessage(Component.text("Type what you want to search for in chat (for example: golden apple). Type 'cancel' to cancel.", NamedTextColor.AQUA));
            });
            return;
        }
        if (slot < 0 || slot >= SHOP_PAGE_SIZE) return;

        int idx = h.page * SHOP_PAGE_SIZE + slot;
        if (idx >= h.results.size()) return;
        ShopEntry en = h.results.get(idx);

        int max = en.stack().getMaxStackSize();
        int qty = e.isShiftClick() ? Math.min(16, max) : (e.isRightClick() ? max : 1);
        int cost = en.unit() * qty;

        PlayerData d = dataOf(p.getUniqueId());
        if (d.points < cost) {
            p.sendMessage(Component.text("Not enough points (" + d.points + "/" + cost + ").", NamedTextColor.RED));
            return;
        }
        d.points -= cost;
        ItemStack give = en.stack().clone();
        give.setAmount(qty);
        giveItem(p, give);
        h.inv.setItem(49, pointsIcon(p));
        p.sendMessage(Component.text("Bought " + qty + " for " + cost + " points. Left: " + d.points, NamedTextColor.GREEN));
        saveData();
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(AsyncChatEvent e) {
        Player p = e.getPlayer();

        // shop search box
        if (searching.remove(p.getUniqueId())) {
            e.setCancelled(true);
            String q = PlainTextComponentSerializer.plainText().serialize(e.message()).trim();
            Bukkit.getScheduler().runTask(this, () -> {
                if (q.equalsIgnoreCase("cancel") || q.isEmpty()) {
                    p.sendMessage(Component.text("Search cancelled.", NamedTextColor.GRAY));
                    return;
                }
                openShop(p, q, 0);
            });
            return;
        }

        // muted players
        PlayerData d = data.get(p.getUniqueId());
        if (d != null && d.mutedUntil > System.currentTimeMillis()) {
            e.setCancelled(true);
            p.sendMessage(Component.text("You are muted.", NamedTextColor.RED));
        }
    }

    // ---------------------------------------------------------------- team auction (/ah)
    private void handleAh(Player p, String[] args) {
        String team = teamOf(p);
        if (team == null) {
            p.sendMessage(Component.text("You aren't on a team yet.", NamedTextColor.RED));
            return;
        }
        if (args.length == 0) {
            openAuction(p, team, 0);
            return;
        }
        if (args[0].equalsIgnoreCase("global") || args[0].equalsIgnoreCase("all")) {
            openAuction(p, "global", 0);
            return;
        }
        if (args[0].equalsIgnoreCase("sell")) {
            if (args.length < 2 || args.length > 3) {
                p.sendMessage(Component.text("Usage: /ah sell <price in points> [global]  (hold the item)", NamedTextColor.RED));
                return;
            }
            int price;
            try {
                price = Integer.parseInt(args[1]);
            } catch (NumberFormatException ex) {
                p.sendMessage(Component.text("Price must be a whole number.", NamedTextColor.RED));
                return;
            }
            if (price < 1 || price > 1_000_000) {
                p.sendMessage(Component.text("Price must be between 1 and 1,000,000.", NamedTextColor.RED));
                return;
            }
            ItemStack hand = p.getInventory().getItemInMainHand();
            if (hand.getType().isAir()) {
                p.sendMessage(Component.text("Hold the item you want to sell.", NamedTextColor.RED));
                return;
            }
            if (isProtected(hand) || isCombatGear(hand)) {
                p.sendMessage(Component.text("Your tier gear, mace and wind charges can't be sold.", NamedTextColor.RED));
                return;
            }
            String market = team; // your team's auction by default
            if (args.length == 3) {
                if (args[2].equalsIgnoreCase("global") || args[2].equalsIgnoreCase("all")) {
                    market = "global";
                } else if (!args[2].equalsIgnoreCase("team")) {
                    p.sendMessage(Component.text("The last word must be 'global' or 'team'.", NamedTextColor.RED));
                    return;
                }
            }
            List<Listing> list = auctions.computeIfAbsent(market, k -> new ArrayList<>());
            int mine = 0;
            for (List<Listing> all : auctions.values()) {
                for (Listing x : all) if (x.seller.equals(p.getUniqueId())) mine++;
            }
            if (mine >= MAX_LISTINGS_PER_PLAYER) {
                p.sendMessage(Component.text("You can only have " + MAX_LISTINGS_PER_PLAYER + " listings at once.", NamedTextColor.RED));
                return;
            }
            Listing l = new Listing();
            l.id = UUID.randomUUID().toString().substring(0, 8);
            l.seller = p.getUniqueId();
            l.sellerName = p.getName();
            l.price = price;
            l.time = System.currentTimeMillis();
            l.item = hand.clone();
            list.add(l);
            p.getInventory().setItemInMainHand(null);
            saveAuction();
            p.sendMessage(Component.text("Listed for " + price + " points on the "
                    + (market.equals("global") ? "global" : "Team " + cap(team)) + " auction.", NamedTextColor.GREEN));
            return;
        }
        p.sendMessage(Component.text("Usage: /ah  |  /ah global  |  /ah sell <price> [global]", NamedTextColor.RED));
    }

    private void openAuction(Player p, String market, int page) {
        if (market == null || teamOf(p) == null) return;
        List<Listing> list = auctions.computeIfAbsent(market, k -> new ArrayList<>());
        int perPage = 45;
        int pages = Math.max(1, (list.size() + perPage - 1) / perPage);
        int pg = Math.max(0, Math.min(page, pages - 1));

        AuctionHolder h = new AuctionHolder(market, pg);
        Inventory inv = Bukkit.createInventory(h, 54,
                Component.text((market.equals("global") ? "Global" : "Team " + cap(market)) + " Auction (" + (pg + 1) + "/" + pages + ")"));
        h.inv = inv;

        for (int i = 0; i < perPage; i++) {
            int idx = pg * perPage + i;
            if (idx >= list.size()) break;
            Listing l = list.get(idx);
            h.ids.add(l.id);
            ItemStack show = l.item.clone();
            boolean own = l.seller.equals(p.getUniqueId());
            show.editMeta(m -> {
                List<Component> lore = m.lore() == null ? new ArrayList<>() : new ArrayList<>(m.lore());
                lore.add(Component.empty());
                lore.add(line("Price: " + l.price + " points", NamedTextColor.GOLD));
                lore.add(line("Seller: " + l.sellerName, NamedTextColor.GRAY));
                lore.add(line(own ? "Click to cancel and take back" : "Click to buy", NamedTextColor.YELLOW));
                m.lore(lore);
            });
            inv.setItem(i, show);
        }

        inv.setItem(45, navItem(Material.ARROW, "Previous page", null));
        inv.setItem(47, market.equals("global")
                ? navItem(Material.ENDER_CHEST, "Switch to your team auction", "Only your team sees that one")
                : navItem(Material.ENDER_CHEST, "Switch to the global auction", "Open to every player"));
        inv.setItem(49, pointsIcon(p));
        inv.setItem(53, navItem(Material.ARROW, "Next page", null));
        p.openInventory(inv);
    }

    private void auctionClick(Player p, AuctionHolder h, int slot) {
        if (slot == 47) { // switch between the team auction and the global auction
            String other = h.team.equals("global") ? teamOf(p) : "global";
            Bukkit.getScheduler().runTask(this, () -> openAuction(p, other, 0));
            return;
        }
        if (slot == 45) {
            Bukkit.getScheduler().runTask(this, () -> openAuction(p, h.team, h.page - 1));
            return;
        }
        if (slot == 53) {
            Bukkit.getScheduler().runTask(this, () -> openAuction(p, h.team, h.page + 1));
            return;
        }
        if (slot < 0 || slot >= h.ids.size()) return;

        List<Listing> list = auctions.computeIfAbsent(h.team, k -> new ArrayList<>());
        Listing found = null;
        for (Listing l : list) {
            if (l.id.equals(h.ids.get(slot))) {
                found = l;
                break;
            }
        }
        if (found == null) { // already sold / cancelled
            Bukkit.getScheduler().runTask(this, () -> openAuction(p, h.team, h.page));
            return;
        }

        if (found.seller.equals(p.getUniqueId())) {
            list.remove(found);
            giveItem(p, found.item.clone());
            p.sendMessage(Component.text("Listing cancelled, item returned.", NamedTextColor.YELLOW));
        } else {
            PlayerData buyer = dataOf(p.getUniqueId());
            if (buyer.points < found.price) {
                p.sendMessage(Component.text("Not enough points (" + buyer.points + "/" + found.price + ").", NamedTextColor.RED));
                return;
            }
            list.remove(found);
            buyer.points -= found.price;
            dataOf(found.seller).points += found.price; // works even if the seller is offline
            giveItem(p, found.item.clone());
            p.sendMessage(Component.text("Bought for " + found.price + " points. Left: " + buyer.points, NamedTextColor.GREEN));
            Player seller = Bukkit.getPlayer(found.seller);
            if (seller != null) {
                seller.sendMessage(Component.text(p.getName() + " bought your listing! +" + found.price + " points.", NamedTextColor.GOLD));
            }
        }
        saveData();
        saveAuction();
        Bukkit.getScheduler().runTask(this, () -> openAuction(p, h.team, h.page));
    }

    // ---------------------------------------------------------------- menu click handling
    private boolean isMenu(InventoryHolder holder) {
        return holder instanceof ShopHolder || holder instanceof AuctionHolder
                || holder instanceof GuideHolder || holder instanceof WaypointHolder || holder instanceof StatsHolder
                || holder instanceof EventWaypointHolder || holder instanceof DuelMenuHolder;
    }

    @EventHandler
    public void onMenuClick(InventoryClickEvent e) {
        Inventory top = e.getView().getTopInventory();
        InventoryHolder holder = top.getHolder();
        if (!isMenu(holder)) return;

        e.setCancelled(true); // nothing can be taken out of or put into the menus
        if (!(e.getWhoClicked() instanceof Player p)) return;
        if (e.getClickedInventory() == null || !e.getClickedInventory().equals(top)) return;

        if (holder instanceof GuideHolder) {
            if (e.getSlot() == 49) acceptGuide(p);
        } else if (holder instanceof ShopHolder sh) {
            shopClick(p, sh, e);
        } else if (holder instanceof AuctionHolder ah) {
            auctionClick(p, ah, e.getSlot());
        } else if (holder instanceof WaypointHolder wh) {
            waypointClick(p, wh, e);
        } else if (holder instanceof EventWaypointHolder ewh) {
            eventWaypointClick(p, ewh, e);
        } else if (holder instanceof DuelMenuHolder dh) {
            duelMenuClick(p, dh, e);
        }
    }

    @EventHandler
    public void onMenuDrag(InventoryDragEvent e) {
        if (isMenu(e.getView().getTopInventory().getHolder())) e.setCancelled(true);
    }
}
