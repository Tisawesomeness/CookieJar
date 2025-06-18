package com.tisawesomeness.cookiejar;

import com.tisawesomeness.cookiejar.mixin.StoreCookieS2CPacketAccessor;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.Identifier;

import java.util.HashMap;
import java.util.Map;

public final class CookieUtil {
    private CookieUtil() {}

    public static final long ONE_GIGABYTE = 1024 * 1024 * 1024;
    public static final int MAX_COOKIE_SIZE = StoreCookieS2CPacketAccessor.getMaxCookieLength();

    /**
     * Serializes a cookie map to NBT.
     * @param cookies map of cookie keys to payloads
     * @return NBT compound containing cookies, each cookie is a byte array tag named by namespace:path
     */
    public static NbtCompound toNbt(Map<Identifier, byte[]> cookies) {
        NbtCompound compoundNbt = new NbtCompound();
        cookies.forEach((key, data) -> compoundNbt.putByteArray(key.toString(), data));
        return compoundNbt;
    }

    /**
     * Deserializes a cookie map from NBT.
     * @param nbt cookies in NBT format
     * @return map of cookie keys to payloads
     */
    public static Map<Identifier, byte[]> fromNbt(NbtCompound nbt) {
        Map<Identifier, byte[]> cookies = new HashMap<>();
        nbt.getKeys().forEach(key -> {
            Identifier id = Identifier.tryParse(key);
            if (id != null) {
                nbt.getByteArray(key).ifPresent(bytes -> cookies.put(id, bytes));
            }
        });
        return cookies;
    }

}
