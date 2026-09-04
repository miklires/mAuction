package io.github.miklires.mauction;

import org.bukkit.inventory.ItemStack;
import org.bukkit.util.io.BukkitObjectInputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.ObjectInputFilter;

public final class ItemCodec {
    static final int HARD_MAX_BYTES = 4_194_304;
    private ItemCodec() { }
    public static byte[] encode(ItemStack item) {
        if (item == null || item.getType().isAir() || item.getAmount() < 1) throw new IllegalArgumentException("Item cannot be empty");
        byte[] encoded = item.serializeAsBytes();
        if (encoded.length == 0 || encoded.length > HARD_MAX_BYTES) throw new IllegalArgumentException("Serialized item exceeds the hard limit");
        return encoded;
    }
    public static ItemStack decode(byte[] data) {
        if (data == null || data.length == 0 || data.length > HARD_MAX_BYTES) throw new IllegalArgumentException("Invalid item payload size");
        try { ItemStack item = ItemStack.deserializeBytes(data); if (item.getType().isAir() || item.getAmount() < 1) throw new IllegalArgumentException("Decoded item is empty"); return item; }
        catch (RuntimeException modernFailure) { return decodeLegacy(data, modernFailure); }
    }
    private static ItemStack decodeLegacy(byte[] data, RuntimeException modernFailure) {
        try (BukkitObjectInputStream input = new BukkitObjectInputStream(new ByteArrayInputStream(data))) {
            input.setObjectInputFilter(info -> info.depth() > 64 || info.references() > 20_000 || info.arrayLength() > HARD_MAX_BYTES ? ObjectInputFilter.Status.REJECTED : ObjectInputFilter.Status.UNDECIDED);
            Object decoded = input.readObject();
            if (!(decoded instanceof ItemStack item) || item.getType().isAir() || item.getAmount() < 1) throw new IllegalArgumentException("Legacy item is invalid");
            return item;
        } catch (IOException | ClassNotFoundException | RuntimeException legacyFailure) { IllegalArgumentException failure = new IllegalArgumentException("Cannot deserialize item", legacyFailure); failure.addSuppressed(modernFailure); throw failure; }
    }
}
