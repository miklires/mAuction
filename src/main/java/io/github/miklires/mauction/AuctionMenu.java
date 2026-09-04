package io.github.miklires.mauction;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public final class AuctionMenu implements InventoryHolder {
    public static final int PREVIOUS=45,SORT=47,INFO=49,REFRESH=51,NEXT=53;
    private final Inventory inventory;
    private final Map<Integer,Listing> slots=new LinkedHashMap<>();
    private final Messages messages;
    private final String query;
    private final int page;
    private final int pages;
    private final boolean mine;
    private AuctionRepository.SortOrder sort;

    public AuctionMenu(String title,List<Listing>all,int total,String query,int page,int pages,AuctionRepository.SortOrder sort,boolean mine,Messages messages){
        this.messages=messages;this.query=query;this.sort=sort;this.mine=mine;this.pages=Math.max(1,pages);this.page=Math.clamp(page,0,this.pages-1);
        inventory=Bukkit.createInventory(this,54,title);
        for(int slot=0;slot<Math.min(45,all.size());slot++){Listing listing=all.get(slot);slots.put(slot,listing);inventory.setItem(slot,listingItem(listing));}
        inventory.setItem(PREVIOUS,button(Material.ARROW,messages.text("gui-previous")));
        inventory.setItem(SORT,button(Material.HOPPER,messages.text("gui-sort","sort",messages.text("sort-"+sort.name().toLowerCase()))));
        inventory.setItem(INFO,button(Material.BOOK,messages.text("gui-page","page",this.page+1,"pages",this.pages,"count",total)));
        inventory.setItem(REFRESH,button(Material.CLOCK,messages.text("gui-refresh")));
        inventory.setItem(NEXT,button(Material.ARROW,messages.text("gui-next")));
    }
    public Optional<Listing>listing(int slot){return Optional.ofNullable(slots.get(slot));}
    public Collection<UUID>ids(){return slots.values().stream().map(Listing::id).toList();}
    public String query(){return query;} public int page(){return page;} public int pages(){return pages;}
    public boolean mine(){return mine;}
    public AuctionRepository.SortOrder sort(){return sort;} public void nextSort(){sort=sort.next();}
    public void stale(UUID id,ListingState state){slots.entrySet().stream().filter(e->e.getValue().id().equals(id)).findFirst().ifPresent(e->{slots.remove(e.getKey());inventory.setItem(e.getKey(),staleItem(state));});}
    private ItemStack listingItem(Listing listing){try{ItemStack item=ItemCodec.decode(listing.item());ItemMeta meta=item.getItemMeta();List<Component>lore=new ArrayList<>(Optional.ofNullable(meta.lore()).orElse(List.of()));lore.add(component(messages.text("lore-price","price",listing.price())));lore.add(component(messages.text("lore-seller","seller",listing.sellerName())));long seconds=Math.max(0,Duration.between(Instant.now(),listing.expiresAt()).toSeconds());lore.add(component(messages.text("lore-expires","time",format(seconds))));lore.add(component(messages.text("lore-id","id",listing.id())));meta.lore(lore);item.setItemMeta(meta);return item;}catch(IllegalArgumentException error){return button(Material.BARRIER,messages.text("corrupt-title"),messages.text("corrupt-lore","id",listing.id()));}}
    private ItemStack staleItem(ListingState state){return button(Material.GRAY_DYE,messages.text("stale-title"),messages.text("stale-lore","state",messages.text("state-"+state.name().toLowerCase())));}
    private static final LegacyComponentSerializer LEGACY=LegacyComponentSerializer.legacySection();
    private static Component component(String value){return LEGACY.deserialize(value);}
    private static ItemStack button(Material material,String name,String...lore){ItemStack item=new ItemStack(material);ItemMeta meta=item.getItemMeta();meta.displayName(component(name));if(lore.length>0)meta.lore(java.util.Arrays.stream(lore).map(AuctionMenu::component).toList());item.setItemMeta(meta);return item;}
    private static String format(long seconds){long h=seconds/3600,m=seconds%3600/60;return h>0?h+"h "+m+"m":m+"m";}
    @Override public Inventory getInventory(){return inventory;}
}
