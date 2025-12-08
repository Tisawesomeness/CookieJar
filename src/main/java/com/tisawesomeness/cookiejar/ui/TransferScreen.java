package com.tisawesomeness.cookiejar.ui;

import com.tisawesomeness.cookiejar.CookieJar;
import com.tisawesomeness.cookiejar.mixin.ServerAddressAccessor;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ClientCommonPacketListenerImpl;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.ClientboundTransferPacket;

@Environment(EnvType.CLIENT)
public class TransferScreen extends Screen {

    private static final int SERVER_WIDTH = 250;
    private static final int CONNECT_WIDTH = 80;
    private static final int HEIGHT = 16;
    private static final int PADDING = 4;

    private final Screen parent;
    private ServerAddress serverAddress;
    private ClientboundTransferPacket outgoingPacket;

    private EditBox serverField;
    private Button connectButton;

    protected TransferScreen(Screen parent) {
        super(Component.translatable("gui.cookiejar.transfer_screen.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        serverField = new EditBox(
                font,
                width / 2 - SERVER_WIDTH / 2,
                height / 2 - HEIGHT - PADDING / 2,
                SERVER_WIDTH,
                HEIGHT,
                Component.translatable("gui.cookiejar.transfer_screen.server_address")
        );
        serverField.setMaxLength(Integer.MAX_VALUE);
        serverField.setHint(Component.translatable("gui.cookiejar.transfer_screen.server_address_placeholder"));
        serverField.setTextColor(CookieJar.COLOR_SUGGESTION);
        serverField.setResponder(this::editServer);

        connectButton = Button.builder(Component.translatable("gui.cookiejar.transfer_screen.connect"), button -> connect())
                .bounds(
                        width / 2 - CONNECT_WIDTH / 2,
                        height / 2 + HEIGHT + PADDING / 2,
                        CONNECT_WIDTH,
                        HEIGHT)
                .build();

        addRenderableWidget(serverField);
        addRenderableWidget(connectButton);
    }

    private void editServer(String serverStr) {
        if (serverStr.isEmpty()) {
            serverField.setTextColor(CookieJar.COLOR_SUGGESTION);
            serverAddress = null;
            connectButton.active = false;
            return;
        }
        ServerAddress newAddress = ServerAddress.parseString(serverStr);
        if (ServerAddressAccessor.getInvalid().equals(newAddress)) {
            serverField.setTextColor(CookieJar.COLOR_INVALID);
            serverAddress = null;
            connectButton.active = false;
        } else {
            serverField.setTextColor(CookieJar.COLOR_VALID);
            serverAddress = newAddress;
            connectButton.active = true;
        }
    }

    private void connect() {
        if (serverField.getValue().isEmpty()) {
            return;
        }
        ClientCommonPacketListenerImpl handler = minecraft.getConnection();
        if (handler == null) {
            return;
        }
        // save packet so the mixin can detect whether a received packet was from this screen
        // and let the packet through even when configured to ignore transfers
        outgoingPacket = new ClientboundTransferPacket(serverAddress.getHost(), serverAddress.getPort());
        handler.handleTransfer(outgoingPacket);
        // check happens in `onServerTransfer` mixin so packet isn't needed anymore
        this.outgoingPacket = null;
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }

    public boolean isSamePacket(ClientboundTransferPacket packet) {
        return outgoingPacket == packet;
    }

}
