package io.github.miklires.mauction;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import net.milkbowl.vault.economy.Economy;
import org.bstats.bukkit.Metrics;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

public final class MAuctionPlugin extends JavaPlugin implements CommandExecutor, TabCompleter, Listener {
    private AuctionRepository repository;
    private Economy economy;
    private Messages messages;
    private volatile boolean ready;

    @Override public void onEnable() {
        saveDefaultConfig();
        messages = new Messages(this);
        RegisteredServiceProvider<Economy> provider = getServer().getServicesManager().getRegistration(Economy.class);
        if (provider == null) { getLogger().severe("Vault economy provider is required"); getServer().getPluginManager().disablePlugin(this); return; }
        economy = provider.getProvider();
        repository = new AuctionRepository(getDataFolder().toPath().resolve("auction"));
        repository.initialize().thenRun(() -> global(() -> { ready=true; getServer().getOnlinePlayers().forEach(this::recover); getLogger().info("mAuction "+getPluginMeta().getVersion()+" is ready"); }))
                .exceptionally(error -> { getLogger().severe("Storage failed: "+error.getMessage()); return null; });
        PluginCommand command = Objects.requireNonNull(getCommand("auction"));
        command.setExecutor(this); command.setTabCompleter(this);
        getServer().getPluginManager().registerEvents(this, this);
        if (getConfig().getBoolean("metrics.enabled", true)) { int id=Math.max(0,getConfig().getInt("metrics.bstats-id",27943)); if(id>0)new Metrics(this,id); }
    }

    @Override public void onDisable() { if (repository != null) repository.close(); }

    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) { sender.sendMessage(messages.text("player-only")); return true; }
        if (!player.hasPermission("mauction.use")) { player.sendMessage(messages.text("no-permission")); return true; }
        if (!ready) { player.sendMessage(messages.text("loading")); return true; }
        if (args.length == 0) { open(player); return true; }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "sell" -> { if(args.length<2){player.sendMessage(messages.text("usage-sell"));return true;} sell(player,args[1]); }
            case "cancel" -> { if(args.length<2){player.sendMessage(messages.text("usage-cancel"));return true;} cancel(player,args[1]); }
            case "reload" -> { if(!player.hasPermission("mauction.admin")){player.sendMessage(messages.text("no-permission"));return true;} reloadConfig();messages.reload();player.sendMessage(messages.text("reloaded")); }
            default -> player.sendMessage(messages.text("usage"));
        }
        return true;
    }

    private void cancel(Player player, String raw) {
        try {
            UUID id=UUID.fromString(raw);
            repository.cancel(id,player.getUniqueId(),player.hasPermission("mauction.admin")).thenAccept(value -> player.getScheduler().execute(this,()->{
                if(value.isEmpty()){player.sendMessage(messages.text("not-found"));return;}
                Listing listing=value.get(); Player owner=Bukkit.getPlayer(listing.seller());
                if(owner!=null)owner.getScheduler().execute(this,()->deliverReturn(owner,listing),null,1);
                player.sendMessage(messages.text(owner==player?"cancelled-returned":"cancelled-queued"));
            },null,1));
        } catch (IllegalArgumentException error) { player.sendMessage(messages.text("invalid-id")); }
    }

    private void open(Player player) { repository.active(45).thenAccept(list -> player.getScheduler().execute(this,()->player.openInventory(new AuctionMenu(messages.text("gui-title"),list,messages).getInventory()),null,1)); }

    private void sell(Player player,String raw) {
        double price;
        try { price=Double.parseDouble(raw);double min=Math.max(.01,getConfig().getDouble("price.minimum",1));double max=Math.max(min,getConfig().getDouble("price.maximum",1_000_000_000));if(!Double.isFinite(price)||price<min||price>max)throw new NumberFormatException(); }
        catch(NumberFormatException error){player.sendMessage(messages.text("invalid-price"));return;}
        repository.activeCount(player.getUniqueId()).thenAccept(count -> player.getScheduler().execute(this,()->{
            int limit=Math.clamp(getConfig().getInt("listing.limit-per-player",10),1,100);
            if(count>=limit){player.sendMessage(messages.text("listing-limit","limit",limit));return;}
            ItemStack held=player.getInventory().getItemInMainHand();if(held.getType().isAir()){player.sendMessage(messages.text("hold-item"));return;}
            ItemStack item=held.clone();player.getInventory().setItemInMainHand(null);
            long hours=Math.clamp(getConfig().getLong("listing.duration-hours",48),1,720);Instant expires=Instant.now().plus(Duration.ofHours(hours));
            repository.create(player.getUniqueId(),player.getName(),ItemCodec.encode(item),price,expires).whenComplete((listing,error)->player.getScheduler().execute(this,()->{if(error!=null){give(player,item);player.sendMessage(messages.text("create-failed"));}else player.sendMessage(messages.text("created","id",listing.id()));},null,1));
        },null,1));
    }

    @EventHandler(ignoreCancelled=true) public void click(InventoryClickEvent event){if(!(event.getInventory().getHolder() instanceof AuctionMenu menu))return;event.setCancelled(true);if(!(event.getWhoClicked() instanceof Player player)||event.getClickedInventory()!=event.getInventory())return;menu.listing(event.getSlot()).ifPresent(listing->{player.closeInventory();buy(player,listing.id());});}

    private void buy(Player buyer,UUID id){repository.reserve(id,buyer.getUniqueId(),buyer.getName()).thenAccept(result->global(()->{if(result.status()!=PurchaseResult.Status.RESERVED){buyer.getScheduler().execute(this,()->buyer.sendMessage(messages.text("unavailable","status",result.status())),null,1);return;}Listing listing=result.listing();if(!economy.has(buyer,listing.price())){repository.release(result.transactionId(),listing.id());buyer.getScheduler().execute(this,()->buyer.sendMessage(messages.text("insufficient-funds")),null,1);return;}var withdrawal=economy.withdrawPlayer(buyer,listing.price());if(!withdrawal.transactionSuccess()){repository.release(result.transactionId(),listing.id());buyer.getScheduler().execute(this,()->buyer.sendMessage(messages.text("payment-rejected")),null,1);return;}repository.stage(result.transactionId(),"MONEY_WITHDRAWN");double tax=Math.clamp(getConfig().getDouble("economy.tax-percent",5),0,100);double net=listing.price()*(1-tax/100);var deposit=economy.depositPlayer(Bukkit.getOfflinePlayer(listing.seller()),net);if(!deposit.transactionSuccess()){economy.depositPlayer(buyer,listing.price());repository.release(result.transactionId(),listing.id());buyer.getScheduler().execute(this,()->buyer.sendMessage(messages.text("seller-payment-failed")),null,1);return;}repository.stage(result.transactionId(),"SELLER_PAID").thenCompose(v->repository.sold(result.transactionId(),listing.id())).thenRun(()->buyer.getScheduler().execute(this,()->deliverPurchase(buyer,result.transactionId(),listing),null,1));}));}

    @EventHandler public void join(PlayerJoinEvent event){if(ready)recover(event.getPlayer());}
    private void recover(Player player){repository.returns(player.getUniqueId()).thenAccept(list->player.getScheduler().execute(this,()->list.forEach(value->deliverReturn(player,value)),null,1));repository.pending(player.getUniqueId()).thenAccept(list->player.getScheduler().execute(this,()->list.forEach(value->deliverPurchase(player,value.transactionId(),value.listing())),null,1));}
    private void deliverReturn(Player player,Listing listing){if(!player.isOnline())return;give(player,ItemCodec.decode(listing.item()));repository.returned(listing.id(),listing.seller());player.sendMessage(messages.text("returned","id",listing.id()));}
    private void deliverPurchase(Player buyer,UUID tx,Listing listing){if(!buyer.isOnline())return;give(buyer,ItemCodec.decode(listing.item()));repository.stage(tx,"DELIVERED");buyer.sendMessage(messages.text("purchased","price",listing.price()));}
    private void give(Player player,ItemStack item){Map<Integer,ItemStack>left=player.getInventory().addItem(item);left.values().forEach(value->player.getWorld().dropItemNaturally(player.getLocation(),value));}
    private void global(Runnable task){getServer().getGlobalRegionScheduler().execute(this,task);}
    @Override public List<String> onTabComplete(CommandSender sender,Command command,String alias,String[]args){return args.length==1?List.of("sell","cancel","reload").stream().filter(v->v.startsWith(args[0].toLowerCase(Locale.ROOT))).toList():List.of();}
}
