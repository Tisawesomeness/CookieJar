package com.tisawesomeness.cookiejar.mixin;

import net.minecraft.client.network.ClientCommonNetworkHandler;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Map;

@Mixin(ClientCommonNetworkHandler.class)
public interface ClientCommonNetworkHandlerAccessor {
    @Accessor("serverCookies")
    Map<Identifier, byte[]> getServerCookies();
    @Accessor("serverInfo")
    ServerInfo getServerInfo();
}
