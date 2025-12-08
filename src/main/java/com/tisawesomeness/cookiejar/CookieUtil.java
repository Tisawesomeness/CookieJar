package com.tisawesomeness.cookiejar;

import com.tisawesomeness.cookiejar.mixin.ClientboundStoreCookiePacketAccessor;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;

public final class CookieUtil {
    private CookieUtil() {}

    public static final long ONE_GIGABYTE = 1024 * 1024 * 1024;
    public static final int MAX_COOKIE_SIZE = ClientboundStoreCookiePacketAccessor.getMaxCookieLength();

    /**
     * Serializes a cookie map to NBT.
     * @param cookies map of cookie keys to payloads
     * @return NBT compound containing cookies, each cookie is a byte array tag named by namespace:path
     */
    public static CompoundTag toNbt(Map<Identifier, byte[]> cookies) {
        CompoundTag compoundNbt = new CompoundTag();
        cookies.forEach((key, data) -> compoundNbt.putByteArray(key.toString(), data));
        return compoundNbt;
    }

    /**
     * Deserializes a cookie map from NBT.
     * @param nbt cookies in NBT format
     * @return map of cookie keys to payloads
     */
    public static Map<Identifier, byte[]> fromNbt(CompoundTag nbt) {
        Map<Identifier, byte[]> cookies = new HashMap<>();
        nbt.keySet().forEach(key -> {
            Identifier id = Identifier.tryParse(key);
            if (id != null) {
                nbt.getByteArray(key).ifPresent(bytes -> cookies.put(id, bytes));
            }
        });
        return cookies;
    }

}
