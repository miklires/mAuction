package io.github.miklires.mauction;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public final class ConfirmMenu implements InventoryHolder {
    static final int CONFIRM=11,CANCEL=15;
    private static final LegacyComponentSerializer LEGACY=LegacyComponentSerializer.legacySection();
    private final Inventory inventory;
    private final Listing listing;

    ConfirmMenu(Listing listing,Messages messages){
        this.listing=listing;
        inventory=Bukkit.createInventory(this,27,messages.text("confirm-title"));
        ItemStack preview;
        try{preview=ItemCodec.decode(listing.item());}catch(IllegalArgumentException error){preview=button(Material.BARRIER,messages.text("corrupt-title"));}
        inventory.setItem(13,preview);
        inventory.setItem(CONFIRM,button(Material.LIME_CONCRETE,messages.text("confirm-buy","price",listing.price())));
        inventory.setItem(CANCEL,button(Material.RED_CONCRETE,messages.text("confirm-cancel")));
    }

    Listing listing(){return listing;}
    @Override public Inventory getInventory(){return inventory;}
    private static ItemStack button(Material material,String name){ItemStack item=new ItemStack(material);ItemMeta meta=item.getItemMeta();meta.displayName(LEGACY.deserialize(name));item.setItemMeta(meta);return item;}
}
