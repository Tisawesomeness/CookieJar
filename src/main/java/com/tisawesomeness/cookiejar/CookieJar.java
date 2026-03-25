package com.tisawesomeness.cookiejar;

import com.tisawesomeness.cookiejar.ui.CookieScreen;
import com.mojang.blaze3d.platform.InputConstants;
import com.tisawesomeness.cookiejar.mixin.ClientCommonPacketListenerImplAccessor;
import com.tisawesomeness.cookiejar.mixin.ConnectionAccessor;
import com.tisawesomeness.cookiejar.ui.TransferScreen;
import eu.midnightdust.lib.config.MidnightConfig;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientCommonPacketListenerImpl;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.network.Connection;
import net.minecraft.network.PacketListener;
import net.minecraft.network.protocol.common.ClientboundStoreCookiePacket;
import net.minecraft.network.protocol.common.ClientboundTransferPacket;
import net.minecraft.resources.Identifier;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jspecify.annotations.Nullable;

import java.util.Map;

public class CookieJar implements ClientModInitializer {

    public static final Logger LOGGER = LogManager.getLogger("CookieJar");

    /** Dummy ServerData only used to prevent crash */
    public static final ServerData SINGLEPLAYER_INFO = new ServerData("singleplayer", "cookiejar.singleplayer", ServerData.Type.OTHER);

    public static final int COLOR_INVALID = 0xFFFF0000;
    public static final int COLOR_VALID = 0xFFE0E0E0;
    public static final int COLOR_SUGGESTION = 0xFF555555;

    private static @Nullable Connection lastKnownConnection;
    public static void updateConnection(Connection connection) {
        lastKnownConnection = connection;
    }

    @Override
    public void onInitializeClient() {
        // Also inits ModMenu integration
        MidnightConfig.init("cookiejar", CookieJarConfig.class);
        KeyMapping openCookiesKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.cookiejar.open",
                InputConstants.Type.KEYSYM,
                InputConstants.UNKNOWN.getValue(),
                KeyMapping.Category.register(Identifier.fromNamespaceAndPath("cookiejar", "general"))
        ));
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (openCookiesKey.consumeClick()) {
                tryOpenCookieScreen(client);
            }
        });
    }

    private static void tryOpenCookieScreen(Minecraft client) {
        ClientCommonPacketListenerImpl listener = getNetworkListener();
        // If client somehow opens cookie editor without an active connection, fail silently
        if (listener == null) {
            return;
        }
        Map<Identifier, byte[]> cookies = ((ClientCommonPacketListenerImplAccessor) listener).getServerCookies();
        client.setScreen(new CookieScreen(client.screen, cookies));
    }

    public static @Nullable ClientCommonPacketListenerImpl getNetworkListener() {
        Connection connection = getConnectionIfAlive();
        if (connection == null) {
            return null;
        }
        PacketListener packetListener = connection.getPacketListener();
        if (packetListener instanceof ClientCommonPacketListenerImpl networkListener) {
            return networkListener;
        }
        return null;
    }

    private static @Nullable Connection getConnectionIfAlive() {
        if (lastKnownConnection == null) {
            return null;
        }
        if (((ConnectionAccessor) lastKnownConnection).isDisconnected()) {
            return null;
        }
        return lastKnownConnection;
    }

    public static void onStoreCookie(ClientboundStoreCookiePacket packet) {
        Minecraft client = Minecraft.getInstance();
        if (client.screen instanceof CookieScreen cookieScreen) {
            cookieScreen.onStoreCookie(packet.key());
        }
    }

    public static boolean shouldIgnoreCookieStore() {
        return CookieJarConfig.ignoreCookieStores == CookieJarConfig.IgnoreCondition.ALWAYS ||
                (CookieJarConfig.ignoreCookieStores == CookieJarConfig.IgnoreCondition.WHILE_SCREEN_OPEN &&
                        Minecraft.getInstance().screen instanceof CookieScreen);
    }

    public static boolean shouldIgnoreTransfer(ClientboundTransferPacket packet) {
        if (!CookieJarConfig.ignoreTransfers) {
            return false;
        }
        // Even if ignore transfers enabled, must let through packets created from transfer screen
        if (Minecraft.getInstance().screen instanceof TransferScreen transferScreen) {
            return !transferScreen.isSamePacket(packet);
        }
        return true;
    }

}
