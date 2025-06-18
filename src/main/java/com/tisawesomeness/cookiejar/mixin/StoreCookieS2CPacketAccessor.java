package com.tisawesomeness.cookiejar.mixin;

import net.minecraft.network.packet.s2c.common.StoreCookieS2CPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(StoreCookieS2CPacket.class)
public interface StoreCookieS2CPacketAccessor {
    @Accessor("MAX_COOKIE_LENGTH")
    static int getMaxCookieLength() {
        throw new AssertionError("mixin died lol");
    }
}
