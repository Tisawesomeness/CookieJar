package com.tisawesomeness.cookiejar.mixin;

import net.minecraft.network.protocol.common.ClientboundStoreCookiePacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ClientboundStoreCookiePacket.class)
public interface ClientboundStoreCookiePacketAccessor {
    @Accessor("MAX_PAYLOAD_SIZE")
    static int getMaxCookieLength() {
        throw new AssertionError("mixin died lol");
    }
}
