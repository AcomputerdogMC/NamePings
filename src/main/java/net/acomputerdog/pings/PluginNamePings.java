package net.acomputerdog.pings;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.*;
import java.util.*;
import java.util.logging.Level;

/**
 * Plugin main class
 */
public class PluginNamePings extends JavaPlugin implements Listener {

    private String soundName;
    private int pingTimeout;
    private boolean allowNamePings;

    private File blockedPingsFile;

    // TODO database
    private List<UUID> blockedPings;

    private Map<Player, Long> timeouts;
    private long currentTick = 0;

    private boolean loaded = false;

    /*
    Called when plugin is enabled
     */
    @Override
    public void onEnable() {
        if (!getDataFolder().isDirectory() && !getDataFolder().mkdirs()) {
            getLogger().warning("Unable to create data directory.");
        }

        blockedPings = new ArrayList<>();
        timeouts = new HashMap<>();

        saveDefaultConfig(); //only saves if it doesn't exist
        reloadConfig();
        soundName = getConfig().getString("sound_name");
        pingTimeout = getConfig().getInt("ping_delay");
        allowNamePings = getConfig().getBoolean("enable_name_pings");
        getLogger().info("Configuration loaded.");

        blockedPingsFile = new File(getDataFolder(), "blocked_pings.lst");
        if (blockedPingsFile.exists()) {
            loadBlockedPings();
        } else {
            getLogger().warning("Blocked pings file does not exist, it will be created.");
            saveBlockedPings();
        }

        if (!loaded) {
            getServer().getPluginManager().registerEvents(this, this);
        }

        getServer().getScheduler().cancelTasks(this);

        // counts ticks, because there is no bukkit API to get the current tick number...
        // TODO set a minimum precision for performance
        getServer().getScheduler().scheduleSyncRepeatingTask(this, () -> currentTick++, 0, 1);

        loaded = true;
    }

    /*
    Called when plugin is disabled
     */
    @Override
    public void onDisable() {
        getLogger().info("Shutting down.");
        saveBlockedPings();
        soundName = null;
        blockedPingsFile = null;
        blockedPings = null;
        timeouts = null;
    }

    /*
    Receives and dispatches all plugin commands
     */
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        switch (command.getName()) {
            case "sendping":
                onSendPing(sender, args);
                break;
            case "togglepings":
                onTogglePings(sender);
                break;
            case "forceping":
                onForcePing(sender, args);
                break;
            case "reloadnamepings":
                onReload(sender);
                break;
            default:
                sender.sendMessage(ChatColor.RED + "Unknown command!  Please report this error!");
                break;
        }
        return true;
    }

    /*
    Listens for an async chat event
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onAsyncChat(AsyncPlayerChatEvent e) {
        if (allowNamePings) {
            sendPings(e.getMessage(), e.getRecipients());
        }
    }

    @EventHandler
    public void onPlayerLogout(PlayerQuitEvent e) {
        timeouts.remove(e.getPlayer());
    }

    /*
    Loads list of blocked pings
     */
    private void loadBlockedPings() {
        try (BufferedReader reader = new BufferedReader(new FileReader(blockedPingsFile))){
            while (reader.ready()) {
                String line = reader.readLine();
                blockedPings.add(UUID.fromString(line));
            }
        } catch (IOException e) {
            getLogger().log(Level.SEVERE, "Exception loading blocked pings", e);
        }
    }

    /*
    Saves list of blocked pings
     */
    private void saveBlockedPings() {
        try (Writer writer = new FileWriter(blockedPingsFile)) {
            for (UUID uuid : blockedPings) {
                writer.write(uuid.toString());
                writer.write("\n");
            }
        } catch (IOException e) {
            getLogger().log(Level.SEVERE, "Exception saving blocked pings", e);
        }
    }

    /*
    Handles /sendping command
     */
    private void onSendPing(CommandSender sender, String[] args) {
        if (!sender.hasPermission("namepings.command.sendping")) {
            sender.sendMessage(ChatColor.RED + "You do not have permission.");
            return;
        }
        if (args.length != 1) {
            sender.sendMessage(ChatColor.RED + "Invalid usage, you must specify exactly one target!");
            return;
        }
        Player player = getServer().getPlayer(args[0]);
        if (player == null) {
            sender.sendMessage(ChatColor.RED + "That player could not be found!");
            return;
        }
        if (blockedPings.contains(player.getUniqueId())) {
            sender.sendMessage(ChatColor.RED + "That player does not allow pings!");
            return;
        }
        sendPing(player);
        player.sendMessage(ChatColor.AQUA + "You were pinged by " + sender.getName());
        sender.sendMessage(ChatColor.AQUA + "Pinged " + player.getName() + ".");
    }

    /*
    Handles /togglepings command
     */
    private void onTogglePings(CommandSender sender) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "That command can only be used by a player!");
            return;
        }
        Player player = (Player) sender;
        if (!player.hasPermission("namepings.command.togglepings")) {
            sender.sendMessage(ChatColor.RED + "You do not have permission!");
            return;
        }
        UUID uuid = player.getUniqueId();

        //if the list contains the uuid then remove it
        //if not then add it
        if (!blockedPings.remove(uuid)) {
            blockedPings.add(uuid);
            player.sendMessage(ChatColor.AQUA + "Pings are now disabled.");
        } else {
            player.sendMessage(ChatColor.AQUA + "Pings are now enabled.");
        }
    }

    /*
    Handles /forceping
     */
    private void onForcePing(CommandSender sender, String[] args) {
        if (!sender.hasPermission("namepings.command.forceping")) {
            sender.sendMessage(ChatColor.RED + "You do not have permission.");
            return;
        }
        if (args.length != 1) {
            sender.sendMessage(ChatColor.RED + "Invalid usage, you must specify exactly one target!");
            return;
        }
        Player player = getServer().getPlayer(args[0]);
        if (player == null) {
            sender.sendMessage(ChatColor.RED + "That player could not be found!");
            return;
        }
        sendPing(player);
        player.sendMessage(ChatColor.AQUA + "You were pinged by " + sender.getName());
        sender.sendMessage(ChatColor.AQUA + "Pinged " + player.getName() + ".");
    }

    /*
    Handles /reload
     */
    private void onReload(CommandSender sender) {
        if (!sender.hasPermission("namepings.command.reload")) {
            sender.sendMessage(ChatColor.RED + "You do not have permission!");
            return;
        }
        onDisable();
        onEnable();
        sender.sendMessage(ChatColor.AQUA + "Reload complete.");
    }

    /*
    Sends pings to all players who's names are mentioned in a string
     */
    private void sendPings(String message, Set<Player> targets) {

        //don't bother if no targets
        if (targets.isEmpty()) {
            return;
        }

        //calculate the shortest name and longest name
        int shortest = Integer.MAX_VALUE;
        int longest = 0;
        for (Player player : targets) {
            String name = player.getName();
            if (name.length() > longest) {
                longest = name.length();
            }
            if (name.length() < shortest) {
                shortest = name.length();
            }
        }

        String[] words = message.split(" ");
        for (String word : words) {
            int length = word.length();
            if (length >= shortest && length <= longest) { //make sure word is not too small or too long
                targets.stream().filter(player -> player.getName().equals(word)).forEach(this::sendPing);
            }
        }
    }

    /*
    Sends a ping to a player, and updates the timeout if necessary
     */
    private void sendPing(Player player) {
        // make sure that player has not blocked pings
        if (blockedPings.contains(player.getUniqueId())) {
            return;
        }

        // if player has a timeout, check if it has expired
        Long timeout = timeouts.get(player);
        if (timeout != null && currentTick - timeout <= pingTimeout) { //if timeout has time remaining
            return; //timeout in progress, don't ping
        }

        timeouts.put(player, currentTick); //reset timeout
        player.playSound(player.getLocation(), soundName, 1.0f, 1.0f); //send the actual ping
    }
}
