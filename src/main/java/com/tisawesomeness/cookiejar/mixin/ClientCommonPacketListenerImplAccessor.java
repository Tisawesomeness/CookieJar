package com.tisawesomeness.cookiejar.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Map;
import net.minecraft.client.multiplayer.ClientCommonPacketListenerImpl;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.resources.Identifier;

@Mixin(ClientCommonPacketListenerImpl.class)
public interface ClientCommonPacketListenerImplAccessor {
    @Accessor("serverCookies")
    Map<Identifier, byte[]> getServerCookies();
    @Accessor("serverData")
    ServerData getServerInfo();
}
