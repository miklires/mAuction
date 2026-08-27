package io.github.miklires.mauction;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.Comparator;
import java.util.ArrayList;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
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
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;
import java.util.concurrent.ConcurrentHashMap;

public final class MAuctionPlugin extends JavaPlugin implements CommandExecutor, TabCompleter, Listener {
    private AuctionRepository repository;
    private Economy economy;
    private Messages messages;
    private volatile boolean ready;
    private final Map<UUID,Long> sellCooldowns=new ConcurrentHashMap<>();

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
        long refresh=Math.clamp(getConfig().getLong("gui.live-refresh-ticks",20),10,200);
        getServer().getGlobalRegionScheduler().runAtFixedRate(this,task->refreshOpenMenus(),refresh,refresh);
        if (getConfig().getBoolean("metrics.enabled", true)) { int id=Math.max(0,getConfig().getInt("metrics.bstats-id",27943)); if(id>0)new Metrics(this,id); }
    }

    @Override public void onDisable() { if (repository != null) repository.close(); }

    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) { sender.sendMessage(messages.text("player-only")); return true; }
        if (!player.hasPermission("mauction.use")) { player.sendMessage(messages.text("no-permission")); return true; }
        if (!ready) { player.sendMessage(messages.text("loading")); return true; }
        if (args.length == 0) { open(player,"",0,AuctionRepository.SortOrder.NEWEST,false); return true; }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "sell" -> { if(args.length<2){player.sendMessage(messages.text("usage-sell"));return true;} sell(player,args[1]); }
            case "search" -> {if(args.length<2){player.sendMessage(messages.text("usage-search"));return true;}open(player,String.join(" ",java.util.Arrays.copyOfRange(args,1,args.length)),0,AuctionRepository.SortOrder.NEWEST,false);}
            case "selling" -> open(player,"",0,AuctionRepository.SortOrder.NEWEST,true);
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

    private void open(Player player,String query,int page,AuctionRepository.SortOrder sort,boolean mine) {
        repository.active(Math.clamp(getConfig().getInt("gui.maximum-loaded-listings",1000),45,10000)).thenAccept(found -> player.getScheduler().execute(this,()->{
            List<Listing> list=new ArrayList<>(found);if(mine)list.removeIf(value->!value.seller().equals(player.getUniqueId()));
            String needle=query.toLowerCase(Locale.ROOT).strip();if(!needle.isEmpty())list.removeIf(value->!matches(value,needle));
            Comparator<Listing> comparator=switch(sort){case PRICE_LOW->Comparator.comparingDouble(Listing::price);case PRICE_HIGH->Comparator.comparingDouble(Listing::price).reversed();case EXPIRING->Comparator.comparing(Listing::expiresAt);default->Comparator.comparing(Listing::createdAt).reversed();};list.sort(comparator);
            player.openInventory(new AuctionMenu(messages.text(mine?"gui-title-mine":"gui-title"),list,query,page,sort,mine,messages).getInventory());
        },null,1));
    }
    private boolean matches(Listing listing,String needle){ItemStack item=ItemCodec.decode(listing.item());String material=item.getType().getKey().getKey().replace('_',' ');String display=item.hasItemMeta()&&item.getItemMeta().hasDisplayName()?PlainTextComponentSerializer.plainText().serialize(item.getItemMeta().displayName()):"";return material.contains(needle)||display.toLowerCase(Locale.ROOT).contains(needle)||listing.sellerName().toLowerCase(Locale.ROOT).contains(needle);}

    private void sell(Player player,String raw) {
        double price;
        try { price=PriceParser.parse(raw);double min=Math.max(.01,getConfig().getDouble("price.minimum",1));double max=Math.max(min,getConfig().getDouble("price.maximum",1_000_000_000));if(price<min||price>max)throw new NumberFormatException(); }
        catch(NumberFormatException error){player.sendMessage(messages.text("invalid-price"));return;}
        long now=System.currentTimeMillis(),cooldown=Math.clamp(getConfig().getLong("listing.sell-cooldown-seconds",2),0,60)*1000;Long last=sellCooldowns.get(player.getUniqueId());if(last!=null&&now-last<cooldown){player.sendMessage(messages.text("sell-cooldown","seconds",Math.max(1,(cooldown-(now-last)+999)/1000)));return;}
        repository.activeCount(player.getUniqueId()).thenAccept(count -> player.getScheduler().execute(this,()->{
            int limit=Math.clamp(getConfig().getInt("listing.limit-per-player",10),1,100);
            if(count>=limit){player.sendMessage(messages.text("listing-limit","limit",limit));return;}
            ItemStack held=player.getInventory().getItemInMainHand();if(held.getType().isAir()){player.sendMessage(messages.text("hold-item"));return;}
            if(getConfig().getStringList("listing.blocked-materials").stream().anyMatch(value->value.equalsIgnoreCase(held.getType().name()))){player.sendMessage(messages.text("blocked-item"));return;}
            ItemStack item=held.clone();byte[] encoded=ItemCodec.encode(item);int maxBytes=Math.clamp(getConfig().getInt("listing.maximum-serialized-bytes",1048576),65536,4194304);if(encoded.length>maxBytes){player.sendMessage(messages.text("item-too-large"));return;}player.getInventory().setItemInMainHand(null);sellCooldowns.put(player.getUniqueId(),System.currentTimeMillis());
            long hours=Math.clamp(getConfig().getLong("listing.duration-hours",48),1,720);Instant expires=Instant.now().plus(Duration.ofHours(hours));
            repository.create(player.getUniqueId(),player.getName(),encoded,price,expires).whenComplete((listing,error)->player.getScheduler().execute(this,()->{if(error!=null){give(player,item);player.sendMessage(messages.text("create-failed"));}else player.sendMessage(messages.text("created","id",listing.id()));},null,1));
        },null,1));
    }

    @EventHandler(ignoreCancelled=true) public void click(InventoryClickEvent event){if(!(event.getInventory().getHolder() instanceof AuctionMenu menu))return;event.setCancelled(true);if(!(event.getWhoClicked() instanceof Player player)||event.getClickedInventory()!=event.getInventory())return;int slot=event.getSlot();if(slot==AuctionMenu.PREVIOUS&&menu.page()>0){open(player,menu.query(),menu.page()-1,menu.sort(),menu.mine());return;}if(slot==AuctionMenu.NEXT&&menu.page()+1<menu.pages()){open(player,menu.query(),menu.page()+1,menu.sort(),menu.mine());return;}if(slot==AuctionMenu.REFRESH){open(player,menu.query(),menu.page(),menu.sort(),menu.mine());return;}if(slot==AuctionMenu.SORT){menu.nextSort();open(player,menu.query(),0,menu.sort(),menu.mine());return;}menu.listing(slot).ifPresent(listing->{if(listing.seller().equals(player.getUniqueId()))cancel(player,listing.id().toString());else buy(player,listing.id());});}

    private void buy(Player buyer,UUID id){repository.reserve(id,buyer.getUniqueId(),buyer.getName()).thenAccept(result->global(()->{if(result.status()!=PurchaseResult.Status.RESERVED){markStale(id,ListingState.SOLD);buyer.getScheduler().execute(this,()->buyer.sendMessage(messages.text("unavailable","status",result.status())),null,1);return;}Listing listing=result.listing();markStale(id,ListingState.RESERVED);if(!economy.has(buyer,listing.price())){repository.release(result.transactionId(),listing.id());buyer.getScheduler().execute(this,()->buyer.sendMessage(messages.text("insufficient-funds")),null,1);return;}var withdrawal=economy.withdrawPlayer(buyer,listing.price());if(!withdrawal.transactionSuccess()){repository.release(result.transactionId(),listing.id());buyer.getScheduler().execute(this,()->buyer.sendMessage(messages.text("payment-rejected")),null,1);return;}repository.stage(result.transactionId(),"MONEY_WITHDRAWN");double tax=Math.clamp(getConfig().getDouble("economy.tax-percent",5),0,100);double net=listing.price()*(1-tax/100);var deposit=economy.depositPlayer(Bukkit.getOfflinePlayer(listing.seller()),net);if(!deposit.transactionSuccess()){economy.depositPlayer(buyer,listing.price());repository.release(result.transactionId(),listing.id());buyer.getScheduler().execute(this,()->buyer.sendMessage(messages.text("seller-payment-failed")),null,1);return;}repository.stage(result.transactionId(),"SELLER_PAID").thenCompose(v->repository.sold(result.transactionId(),listing.id())).thenRun(()->buyer.getScheduler().execute(this,()->deliverPurchase(buyer,result.transactionId(),listing),null,1));}));}

    @EventHandler(ignoreCancelled=true)public void drag(InventoryDragEvent event){if(event.getInventory().getHolder()instanceof AuctionMenu&&event.getRawSlots().stream().anyMatch(slot->slot<event.getInventory().getSize()))event.setCancelled(true);}
    @EventHandler public void join(PlayerJoinEvent event){if(ready)recover(event.getPlayer());}
    private void recover(Player player){repository.returns(player.getUniqueId()).thenAccept(list->player.getScheduler().execute(this,()->list.forEach(value->deliverReturn(player,value)),null,1));repository.pending(player.getUniqueId()).thenAccept(list->player.getScheduler().execute(this,()->list.forEach(value->deliverPurchase(player,value.transactionId(),value.listing())),null,1));}
    private void deliverReturn(Player player,Listing listing){if(!player.isOnline())return;give(player,ItemCodec.decode(listing.item()));repository.returned(listing.id(),listing.seller());player.sendMessage(messages.text("returned","id",listing.id()));}
    private void deliverPurchase(Player buyer,UUID tx,Listing listing){if(!buyer.isOnline())return;give(buyer,ItemCodec.decode(listing.item()));repository.stage(tx,"DELIVERED");buyer.sendMessage(messages.text("purchased","price",listing.price()));}
    private void give(Player player,ItemStack item){Map<Integer,ItemStack>left=player.getInventory().addItem(item);left.values().forEach(value->player.getWorld().dropItemNaturally(player.getLocation(),value));}
    private void global(Runnable task){getServer().getGlobalRegionScheduler().execute(this,task);}
    private void refreshOpenMenus(){for(Player player:Bukkit.getOnlinePlayers())if(player.getOpenInventory().getTopInventory().getHolder()instanceof AuctionMenu menu)repository.states(menu.ids()).thenAccept(states->player.getScheduler().execute(this,()->states.forEach((id,state)->{if(state!=ListingState.ACTIVE)menu.stale(id,state);}),null,1));}
    private void markStale(UUID id,ListingState state){for(Player player:Bukkit.getOnlinePlayers())if(player.getOpenInventory().getTopInventory().getHolder()instanceof AuctionMenu menu)player.getScheduler().execute(this,()->menu.stale(id,state),null,1);}
    @Override public List<String> onTabComplete(CommandSender sender,Command command,String alias,String[]args){return args.length==1?List.of("sell","search","selling","cancel","reload").stream().filter(v->v.startsWith(args[0].toLowerCase(Locale.ROOT))).toList():List.of();}
}
