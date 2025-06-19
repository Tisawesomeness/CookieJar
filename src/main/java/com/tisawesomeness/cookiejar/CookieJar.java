package com.tisawesomeness.cookiejar;

import com.tisawesomeness.cookiejar.ui.CookieScreen;
import com.tisawesomeness.cookiejar.mixin.ClientCommonNetworkHandlerAccessor;
import com.tisawesomeness.cookiejar.mixin.ClientConnectionAccessor;
import com.tisawesomeness.cookiejar.ui.TransferScreen;
import eu.midnightdust.lib.config.MidnightConfig;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientCommonNetworkHandler;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.listener.PacketListener;
import net.minecraft.network.packet.s2c.common.ServerTransferS2CPacket;
import net.minecraft.network.packet.s2c.common.StoreCookieS2CPacket;
import net.minecraft.util.Identifier;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.Map;

public class CookieJar implements ClientModInitializer {

    public static final Logger LOGGER = LogManager.getLogger("CookieJar");

    /** Dummy ServerInfo only used to prevent crash */
    public static final ServerInfo SINGLEPLAYER_INFO = new ServerInfo("singleplayer", "cookiejar.singleplayer", ServerInfo.ServerType.OTHER);

    public static final int COLOR_INVALID = 0xFFFF0000;
    public static final int COLOR_VALID = 0xFFE0E0E0;
    public static final int COLOR_SUGGESTION = 0xFF555555;

    private static @Nullable ClientConnection lastKnownConnection;
    public static void updateConnection(ClientConnection connection) {
        lastKnownConnection = connection;
    }

    @Override
    public void onInitializeClient() {
        // Also inits ModMenu integration
        MidnightConfig.init("cookiejar", CookieJarConfig.class);
        KeyBinding openCookiesKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.cookiejar.open",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_UNKNOWN,
                "category.cookiejar.title"
        ));
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (openCookiesKey.wasPressed()) {
                tryOpenCookieScreen(client);
            }
        });
    }

    private static void tryOpenCookieScreen(MinecraftClient client) {
        ClientCommonNetworkHandler handler = getNetworkHandler();
        // If client somehow opens cookie editor without an active connection, fail silently
        if (handler == null) {
            return;
        }
        Map<Identifier, byte[]> cookies = ((ClientCommonNetworkHandlerAccessor) handler).getServerCookies();
        client.setScreen(new CookieScreen(client.currentScreen, cookies));
    }

    public static @Nullable ClientCommonNetworkHandler getNetworkHandler() {
        ClientConnection connection = getConnectionIfAlive();
        if (connection == null) {
            return null;
        }
        PacketListener listener = connection.getPacketListener();
        if (listener instanceof ClientCommonNetworkHandler handler) {
            return handler;
        }
        return null;
    }

    private static @Nullable ClientConnection getConnectionIfAlive() {
        if (lastKnownConnection == null) {
            return null;
        }
        if (((ClientConnectionAccessor) lastKnownConnection).isDisconnected()) {
            return null;
        }
        return lastKnownConnection;
    }

    public static void onStoreCookie(StoreCookieS2CPacket packet) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.currentScreen instanceof CookieScreen cookieScreen) {
            cookieScreen.onStoreCookie(packet.key());
        }
    }

    public static boolean shouldIgnoreCookieStore() {
        return CookieJarConfig.ignoreCookieStores == CookieJarConfig.IgnoreCondition.ALWAYS ||
                (CookieJarConfig.ignoreCookieStores == CookieJarConfig.IgnoreCondition.WHILE_SCREEN_OPEN &&
                        MinecraftClient.getInstance().currentScreen instanceof CookieScreen);
    }

    public static boolean shouldIgnoreTransfer(ServerTransferS2CPacket packet) {
        if (!CookieJarConfig.ignoreTransfers) {
            return false;
        }
        // Even if ignore transfers enabled, must let through packets created from transfer screen
        if (MinecraftClient.getInstance().currentScreen instanceof TransferScreen transferScreen) {
            return !transferScreen.isSamePacket(packet);
        }
        return true;
    }

}
