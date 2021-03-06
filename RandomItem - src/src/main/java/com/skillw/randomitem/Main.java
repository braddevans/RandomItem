package com.skillw.randomitem;

import com.skillw.randomitem.api.RandomItemApi;
import com.skillw.randomitem.api.manager.ItemManager;
import com.skillw.randomitem.api.randomitem.RandomItem;
import com.skillw.randomitem.manager.ItemManagerImpl;
import com.skillw.randomitem.manager.RandomItemApiImpl;
import com.skillw.randomitem.section.type.CalculationType;
import com.skillw.randomitem.section.type.NumberType;
import com.skillw.randomitem.section.type.ScriptType;
import com.skillw.randomitem.section.type.StringType;
import com.skillw.randomitem.util.ConfigUtils;
import com.skillw.randomitem.util.StringUtils;
import io.izzel.taboolib.internal.apache.lang3.concurrent.BasicThreadFactory;
import io.izzel.taboolib.internal.gson.Gson;
import io.izzel.taboolib.internal.gson.GsonBuilder;
import io.izzel.taboolib.loader.Plugin;
import io.izzel.taboolib.metrics.BStats;
import io.izzel.taboolib.module.command.lite.CommandBuilder;
import io.izzel.taboolib.module.config.TConfig;
import io.izzel.taboolib.module.inject.TSchedule;
import io.izzel.taboolib.module.tellraw.TellrawJson;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.stream.Collectors;

import static com.skillw.randomitem.util.ConfigUtils.*;
import static com.skillw.randomitem.util.FileUtils.saveResource;
import static com.skillw.randomitem.util.Utils.getCheckVersionMessage;
import static io.izzel.taboolib.module.locale.TLocaleLoader.getLocalPriorityFirst;
import static org.bukkit.Material.AIR;

/**
 * @author Glom_
 */
public final class Main extends Plugin {
    private static final ScheduledExecutorService scheduledExecutorService = new ScheduledThreadPoolExecutor(10,
            new BasicThreadFactory.Builder().namingPattern("random-item-schedule-pool-%d").daemon(true).build());
    public static int gameVersion;
    private static Gson gson;
    private static Main instance;
    private static RandomItemApi randomItemAPI;
    private static ItemManager itemManager;
    public boolean papi;
    public boolean mm;
    private File configFile;
    private TConfig config;
    private File langFile;
    private File itemsFile;
    private File globalSectionsFile;

    private static void sendList(CommandSender sender, int page) {
        TellrawJson tellrawJson = TellrawJson.create();
        List<RandomItem> randomItems = new ArrayList<>(Main.getItemManager().getRandomItemHashMap().values());
        int total = randomItems.size();
        int number = ConfigUtils.getListNumber();
        int lastPage = total / number + (total % number != 0 ? 1 : 0);
        tellrawJson.append(ConfigUtils.getListUpMessage() + "\n");
        int lastI;
        if (lastPage == 1) {
            lastI = total;
        } else if (page != lastPage) {
            lastI = number * page;
        } else {
            lastI = total;
        }
        for (int i = (page - 1) * number + 1; i <= lastI; i++) {
            int index = i - 1;
            RandomItem randomItem = randomItems.get(index);
            tellrawJson.append(ConfigUtils.getListFormat(i, randomItem));
            tellrawJson.hoverItem(randomItem.getItemStack(), true);
            if (sender instanceof Player) {
                Player player = (Player) sender;
                tellrawJson.clickCommand("/ri give " + player.getDisplayName() + " " + randomItem.getId());
            }
            tellrawJson.append("\n");
        }
        int previousPage = page - 1;
        TellrawJson left = TellrawJson.create();
        left.append(ConfigUtils.getListLeftMessage());
        if (previousPage > 0) {
            left.clickCommand("/ri list " + previousPage);
        }
        int nextPage = page + 1;
        TellrawJson right = TellrawJson.create();
        right.append(ConfigUtils.getListRightMessage());
        if (nextPage <= lastPage) {
            right.clickCommand("/ri list " + nextPage);
        }
        tellrawJson.append(left);
        tellrawJson.append(ConfigUtils.getListPage(page, lastPage));
        tellrawJson.append(right);
        tellrawJson.append("\n");
        tellrawJson.append(ConfigUtils.getListDownMessage());
        tellrawJson.send(sender);
    }

    public static RandomItemApi getRandomItemAPI() {
        return randomItemAPI;
    }

    public static ScheduledExecutorService getScheduledExecutorService() {
        return scheduledExecutorService;
    }

    public static Main getInstance() {
        return instance;
    }

    public static void sendMessage(String text) {
        Bukkit.getConsoleSender().sendMessage(StringUtils.getMessage(text));
    }

    public static void sendWrong(String text) {
        Main.sendMessage(getPrefix() + "&c" + text);
    }

    public static boolean isDebug() {
        return ConfigUtils.getDebug();
    }

    public static void sendDebug(String message) {
        if (ConfigUtils.getDebug()) {
            message = message.replace("\\n", "\n").replace("\\", "");
            String[] strings = message.split("\n");
            for (int i = 0; i < strings.length; i++) {
                String text = strings[i];
                sendMessage(getPrefix() + "&e" + (i == 0 ? "" : "   ") + text);
            }
        }
    }

    public static ItemManager getItemManager() {
        return itemManager;
    }

    @TSchedule(delay = 15000, period = 15000)
    static void run() {
        Main.getInstance().checkVersion();
    }

    public static Gson getGson() {
        return gson;
    }

    private void createCommand() {
        CommandBuilder.create()
                .command(ConfigUtils.getCommandName())
                .aliases(ConfigUtils.getCommandAliases().toArray(new String[0]))
                .permission(ConfigUtils.getCommandPermission())
                .permissionMessage(getNoPermissionMessage())
                .execute(((sender, args) -> {
                    if (args.length == 0) {
                        for (String text : getCommandMessages()) {
                            sender.sendMessage(text);
                        }
                    }
                    if (args.length > 0) {
                        switch (args[0]) {
                            case "drop":
                                if (args.length == 7) {
                                    String name = args[1];
                                    Player p = Bukkit.getServer().getPlayer(name);
                                    String itemID = args[2];
                                    RandomItem randomItem = Main.getItemManager().getRandomItemHashMap().get(itemID);
                                    String worldName = args[3];
                                    String xS = args[4];
                                    String yS = args[5];
                                    String zS = args[6];
                                    double x = 0;
                                    double y = 0;
                                    double z = 0;
                                    if (p != null) {
                                        if (randomItem != null) {
                                            World world = Bukkit.getWorld(worldName);
                                            if (world != null) {
                                                try {
                                                    x = Double.parseDouble(xS);
                                                    y = Double.parseDouble(yS);
                                                    z = Double.parseDouble(zS);
                                                } catch (Exception e) {
                                                    sendValidXyzMessage(sender);
                                                }
                                                if (x != 0 && y != 0 && z != 0) {
                                                    Location location = new Location(world, x, y, z);
                                                    world.dropItem(location, randomItem.getItemStack(p));
                                                }
                                            } else {
                                                sendValidWorldMessage(sender, worldName);
                                                break;
                                            }
                                        } else {
                                            sendValidItemMessage(sender, itemID);
                                        }
                                        break;
                                    } else {
                                        sendValidPlayerMessage(sender, name);
                                    }
                                }
                                break;
                            case "save":
                                if (args.length == 3) {
                                    if (sender instanceof Player) {
                                        String itemID = args[1];
                                        String path = args[2];
                                        Player p = (Player) sender;
                                        ItemStack itemStack = p.getInventory().getItemInMainHand();
                                        if (itemStack.getType() == AIR) {
                                            sendValidSaveMessage(sender, "UNKNOWN");
                                            break;
                                        }
                                        ItemMeta itemMeta = itemStack.getItemMeta();
                                        String name = (itemMeta.hasDisplayName()) ? itemMeta.getDisplayName() : itemStack.getType().name();
                                        if (Main.getItemManager().createItemStackConfig(itemStack, itemID, path)) {
                                            sendSaveItemMessage(sender, name);
                                        } else {
                                            sendValidSaveMessage(sender, name);
                                        }
                                        break;
                                    }
                                    sendOnlyPlayerMessage(sender);
                                }
                                break;
                            case "give":
                                if (args.length >= 3) {
                                    String name = args[1];
                                    String itemID = args[2];
                                    String pointData = null;
                                    if (args.length == 4) {
                                        pointData = args[3];
                                    }
                                    Player player = Bukkit.getServer().getPlayer(name);
                                    if (player != null) {
                                        itemManager.giveRandomItem(player, itemID, player, pointData);
                                        break;
                                    } else {
                                        sendValidPlayerMessage(sender, player.getDisplayName());
                                    }
                                    break;
                                }
                                break;
                            case "get":
                                if (args.length >= 2) {
                                    if (sender instanceof Player) {
                                        String itemID = args[1];
                                        Player player = (Player) sender;
                                        String pointData = null;
                                        if (args.length == 3) {
                                            pointData = args[2];
                                        }
                                        itemManager.giveRandomItem(player, itemID, player, pointData);
                                    } else {
                                        sendOnlyPlayerMessage(sender);
                                    }
                                }
                                break;
                            case "list": {
                                int page = 1;
                                if (args.length == 1) {
                                    page = 1;
                                } else {
                                    try {
                                        page = Integer.parseInt(args[1]);
                                    } catch (Exception exception) {
                                        sendValidNumberMessage(sender);
                                    }
                                }
                                sendList(sender, page);
                            }
                            break;
                            case "reload":
                                Main.getInstance().loadConfig();
                                sendReloadMessage(sender);
                                break;
                            default:
                                break;
                        }
                    }
                }))
                .tab((sender, args) -> {
                    final String[] itemSub = {"get", "give", "list", "save", "drop", "reload"};
                    if (args.length == 1) {
                        return Arrays.stream(itemSub).filter(s -> s.startsWith(args[0])).collect(Collectors.toList());
                    }
                    if (args.length > 1) {
                        if ("get".equalsIgnoreCase(args[0])) {
                            if (args.length == 3) {
                                return null;
                            }
                            return this.getRandomItemIDByCommand(args);
                        } else if ("give".equalsIgnoreCase(args[0])) {
                            if (args.length == 2) {
                                return null;
                            }
                            if (args.length == 4) {
                                return null;
                            }
                            return this.getRandomItemIDByCommand(args);
                        }
                    }
                    return null;
                }).build();
    }

    private void initialize() {
        instance = this;
        gson = new GsonBuilder().disableHtmlEscaping().setPrettyPrinting().create();
        randomItemAPI = new RandomItemApiImpl();
        itemManager = new ItemManagerImpl();
        this.firstLoad();
        this.createCommand();
        new StringType().register();
        new NumberType().register();
        new CalculationType().register();
        new ScriptType().register();
    }

    private List<String> getRandomItemIDByCommand(String... args) {
        ArrayList<String> stringArrayList = new ArrayList<>();
        for (RandomItem main : Main.getItemManager().getRandomItemHashMap().values()) {
            stringArrayList.add(main.getId());
        }
        int index = args.length - 1;
        if (index == 1 || index == 2) {
            return Arrays.stream(stringArrayList.toArray(new String[0])).filter(s -> s.startsWith(args[index])).collect(Collectors.toList());
        }
        return null;
    }

    public File getItemsFile() {
        return this.itemsFile;
    }

    private void firstLoad() {
        gameVersion = Integer.parseInt(Bukkit.getServer().getClass().getPackage().getName().replace(".", ",").split(",")[3].replace("v", "").replace("_", "").replace("R", ""));
        this.configFile = new File(this.getPlugin().getDataFolder(), "config.yml");
        this.langFile = new File(this.getPlugin().getDataFolder() + "/lang");
        this.itemsFile = new File(this.getPlugin().getDataFolder() + "/Items");
        this.globalSectionsFile = new File(this.getPlugin().getDataFolder() + "/GlobalSections");
        if (!this.configFile.exists()) {
            saveResource("config.yml", true);
        }
        if (!this.langFile.exists()) {
            saveResource("lang/en_US.yml", true);
            saveResource("lang/zh_CN.yml", true);
        }
        this.config = TConfig.create(this.configFile, this.getPlugin());
        if (!this.itemsFile.exists()) {
            saveResource(ConfigUtils.getLanguage() + "Items/ExampleItem.yml", true);
        }
        if (!this.globalSectionsFile.exists()) {
            saveResource(ConfigUtils.getLanguage() + "GlobalSections/Basic.yml", true);
            saveResource(ConfigUtils.getLanguage() + "GlobalSections/Script.yml", true);
        }
        {
            this.config.listener(() -> {
                this.loadConfig();
                if (Bukkit.getPluginManager().isPluginEnabled(this.getPlugin())) {
                    sendConfigReloadMessage(Bukkit.getConsoleSender());
                }
            });
        }
    }

    public File getConfigFile() {
        return this.configFile;
    }

    public YamlConfiguration getConfig() {
        return this.config;
    }

    public File getLangFile() {
        return this.langFile;
    }

    public void loadConfig() {
        if (!this.configFile.exists()) {
            saveResource("config.yml", true);
        }
        if (!this.langFile.exists()) {
            saveResource("lang/en_US.yml", true);
            saveResource("lang/zh_CN.yml", true);
        }
        if (!this.itemsFile.exists()) {
            saveResource(ConfigUtils.getLanguage() + "Items/ExampleItem.yml", true);
        }
        if (!this.globalSectionsFile.exists()) {
            saveResource(ConfigUtils.getLanguage() + "GlobalSections/Basic.yml", true);
            saveResource(ConfigUtils.getLanguage() + "GlobalSections/Script.yml", true);
        }
        sendDebug("&aReloading:");
        try {
            this.config.load(this.configFile);
        } catch (final IOException | InvalidConfigurationException e) {
            e.printStackTrace();
        }

        ConfigUtils.loadGlobalSection();
        randomItemAPI.reloadRandomItems();
    }

    @Override
    public void onLoad() {
        this.initialize();
        sendMessage("&bRandomItem loaded successfully! &9Author: Glom_ &6QQ: 88595433");
    }

    @Override
    public void onEnable() {
        this.loadConfig();
        this.papi = Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI");
        this.mm = Bukkit.getPluginManager().isPluginEnabled("MythicMobs");
        BStats stats = new BStats(this.getPlugin());
        stats.addCustomChart(new BStats.MultiLineChart("players_and_servers", () -> {
            Map<String, Integer> valueMap = new HashMap<>();
            valueMap.put("servers", 1);
            valueMap.put("players", Bukkit.getOnlinePlayers().size());
            return valueMap;
        }));
        sendMessage("&eRandomItem is starting...");
        sendMessage("&6Chosen language: &b" + getLocalPriorityFirst(Main.getInstance().getPlugin()));
        sendMessage("&5Depends Info: ");
        sendMessage("  &b- &6PlaceholderAPI " + ((this.papi) ? "&2&l√" : "&4&l×"));
        sendMessage((this.papi) ? "   &aFound &6PlaceholderAPI&a, HOOK!" : "   &cNot found &6PlaceholderAPI&c, Skip!");
        sendMessage("  &b- &6MythicMobs " + ((this.mm) ? "&2&l√" : "&4&l×"));
        sendMessage((this.mm) ? "   &aFound &6MythicMobs&a, HOOK!" : "   &cNot found &6MythicMobs&c, Skip!");
        this.checkVersion();
        sendMessage("&2RandomItem is enable! &dAuthor: Glom_ &6QQ: 88595433");
    }

    private void checkVersion() {
        String string = getCheckVersionMessage();
        if (string != null) {
            sendMessage(string);
        }
    }

    @Override
    public void onDisable() {
        // Plugin shutdown logic
        this.config.release();
        sendMessage("&cRandomItem is disable! &5Author: Glom_ &6QQ: 88595433");
        scheduledExecutorService.shutdown();
        instance = null;
        randomItemAPI = null;
        itemManager = null;
    }

    public File getGlobalSectionsFile() {
        return this.globalSectionsFile;
    }
}
