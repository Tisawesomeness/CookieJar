package com.tisawesomeness.cookiejar.mixin;

import com.tisawesomeness.cookiejar.CookieJar;
import com.tisawesomeness.cookiejar.CookieJarConfig;
import net.minecraft.client.multiplayer.ClientCommonPacketListenerImpl;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.network.protocol.common.ClientboundStoreCookiePacket;
import net.minecraft.network.protocol.common.ClientboundTransferPacket;
import net.minecraft.network.protocol.cookie.ClientboundCookieRequestPacket;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientCommonPacketListenerImpl.class)
public class ClientCommonPacketListenerImplMixin {

    @Inject(method = "handleStoreCookie", at = @At("HEAD"), cancellable = true)
    private void onStoreCookie(ClientboundStoreCookiePacket packet, CallbackInfo ci) {
        if (CookieJar.shouldIgnoreCookieStore()) {
            CookieJar.LOGGER.trace("Ignored cookie store for {}", packet.key());
            ci.cancel();
        } else {
            CookieJar.onStoreCookie(packet);
        }
    }

    @Inject(method = "handleRequestCookie", at = @At("HEAD"), cancellable = true)
    private void onCookieRequest(ClientboundCookieRequestPacket packet, CallbackInfo ci) {
        if (CookieJarConfig.ignoreCookieRequests) {
            CookieJar.LOGGER.trace("Ignored cookie request for {}", packet.key());
            ci.cancel();
        }
    }

    @Inject(method = "handleTransfer", at = @At("HEAD"), cancellable = true)
    private void onServerTransfer(ClientboundTransferPacket packet, CallbackInfo ci) {
        if (CookieJar.shouldIgnoreTransfer(packet)) {
            CookieJar.LOGGER.info("Ignored transfer to {}:{}", packet.host(), packet.port());
            ci.cancel();
        }
    }

    // Prevent crash when transferring to another server from singleplayer
    // by supplying a ServerInfo for a "singleplayer" server
    @Redirect(method = "handleTransfer", at = @At(value = "FIELD", target = "Lnet/minecraft/client/multiplayer/ClientCommonPacketListenerImpl;serverData:Lnet/minecraft/client/multiplayer/ServerData;", opcode = Opcodes.GETFIELD))
    private ServerData onServerTransfer$serverInfo(ClientCommonPacketListenerImpl instance) {
        ServerData current = ((ClientCommonPacketListenerImplAccessor) instance).getServerInfo();
        if (current == null && CookieJarConfig.enableSingleplayerFix) {
            return CookieJar.SINGLEPLAYER_INFO;
        }
        return current;
    }

}
