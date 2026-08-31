package com.tisawesomeness.cookiejar.ui;

import com.tisawesomeness.cookiejar.CookieJar;
import com.tisawesomeness.cookiejar.CookieUtil;
import eu.midnightdust.lib.config.MidnightConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ContainerObjectSelectionList;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.ImageButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.WidgetSprites;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ClientCommonPacketListenerImpl;
import net.minecraft.locale.Language;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.cookie.ServerboundCookieResponsePacket;
import net.minecraft.resources.Identifier;
import org.apache.commons.codec.DecoderException;
import org.apache.commons.codec.binary.Hex;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.lwjgl.util.tinyfd.TinyFileDialogs;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Function;

public class CookieScreen extends Screen {

    private static final int SCROLLER_WIDTH = 6;
    private static final int TEXTURE_SIZE = 16;
    private static final int PADDING = 4;
    private static final int ROW_HEIGHT = TEXTURE_SIZE + PADDING;
    private static final int KEY_WIDTH = 180;
    private static final int IMPORT_METHOD_WIDTH = 80;

    private static final Identifier CROSS_BUTTON = Identifier.withDefaultNamespace("widget/cross_button");
    private static final Identifier CROSS_BUTTON_HIGHLIGHTED = Identifier.withDefaultNamespace("widget/cross_button_highlighted");

    private final Screen parent;
    // reference to the same cookie map used in the network handler
    private final Map<Identifier, byte[]> cookies;

    // Row 1
    private Button addButton;
    private EditBox keyWidget;
    private EditBox payloadWidget;
    private Button transferButton;
    private Button settingsButton;

    // Row 2
    private EditBox filterWidget;
    private Button dataTypeButton;
    private Button importButton;
    private Button importMethodButton;
    private Button exportButton;
    private ImageButton clearButton;

    // Cookie list
    private CookieListWidget cookieWidget;
    // Minecraft's entry lists do not offer filtering, so keep a master list of all entries here
    // and update the widget with a filtered subset when the filter changes
    private final List<CookieListWidget.Entry> cookieEntries = new ArrayList<>();

    // Backing data with validation
    private @Nullable Identifier keyToAdd; // null if invalid or empty
    private byte[] payloadToAdd = new byte[0]; // last known good value
    private @Nullable String filter = null; // null if empty
    private DataType dataType = DataType.STRING;
    private ImportMethod importMethod = ImportMethod.MERGE;

    public CookieScreen(Screen parent, Map<Identifier, byte[]> cookies) {
        super(Component.translatable("gui.cookiejar.cookie_editor.title"));
        this.parent = parent;
        this.cookies = cookies;
    }

    @Override
    protected void init() {
        // init() is not a constructor, it may be called multiple times, so clearing is necessary
        cookieEntries.clear();
        // If client somehow opens screen without an active connection,
        // some buttons won't work and should be disabled
        boolean allCookieActionsSupported = CookieJar.getNetworkListener() != null;

        // Row 1
        addButton = Button.builder(Component.literal("+"), button -> addCookie())
                .bounds(PADDING, PADDING, TEXTURE_SIZE, TEXTURE_SIZE)
                .tooltip(Tooltip.create(Component.translatable("gui.cookiejar.cookie_editor.add_cookie")))
                .build();
        addButton.active = false;

        keyWidget = new EditBox(
                font,
                PADDING + TEXTURE_SIZE + PADDING,
                PADDING,
                KEY_WIDTH,
                TEXTURE_SIZE,
                Component.translatable("gui.cookiejar.cookie_editor.key")
        );
        keyWidget.setMaxLength(Integer.MAX_VALUE);
        keyWidget.setHint(Component.translatable("gui.cookiejar.cookie_editor.key_placeholder"));
        keyWidget.setTextColor(CookieJar.COLOR_SUGGESTION);
        keyWidget.setResponder(this::editKey);

        payloadWidget = new EditBox(
                font,
                PADDING + (TEXTURE_SIZE + PADDING + KEY_WIDTH) + PADDING,
                PADDING,
                width - (SCROLLER_WIDTH + PADDING + (TEXTURE_SIZE + PADDING) * 3 + PADDING * 2 + KEY_WIDTH),
                TEXTURE_SIZE,
                Component.translatable("gui.cookiejar.cookie_editor.payload")
        );
        // Payload max size and placeholder set by setDataType()
        payloadWidget.setTextColor(CookieJar.COLOR_SUGGESTION);
        payloadWidget.setResponder(payloadStr -> {
            setPayloadWidget(payloadWidget, payloadStr, payload -> payloadToAdd = payload);
        });

        transferButton = Button.builder(Component.literal("➡"), button -> {
                    minecraft.gui.setScreen(new TransferScreen(this));
                })
                .bounds(
                        width - (SCROLLER_WIDTH + (TEXTURE_SIZE + PADDING) * 2),
                        PADDING,
                        TEXTURE_SIZE,
                        TEXTURE_SIZE
                )
                .tooltip(Tooltip.create(Component.translatable("gui.cookiejar.cookie_editor.transfer")))
                .build();
        transferButton.active = allCookieActionsSupported;

        settingsButton = Button.builder(Component.literal("\uD83D\uDD27"), button -> {
                    minecraft.gui.setScreen(MidnightConfig.getScreen(this, "cookiejar"));
                })
                .bounds(
                        width - (SCROLLER_WIDTH + TEXTURE_SIZE + PADDING),
                        PADDING,
                        TEXTURE_SIZE,
                        TEXTURE_SIZE
                )
                .tooltip(Tooltip.create(Component.translatable("gui.cookiejar.cookie_editor.settings")))
                .build();

        addRenderableWidget(addButton);
        addRenderableWidget(keyWidget);
        addRenderableWidget(payloadWidget);
        addRenderableWidget(transferButton);
        addRenderableWidget(settingsButton);

        // Row 2
        filterWidget = new EditBox(
                font,
                PADDING,
                PADDING + TEXTURE_SIZE + PADDING,
                KEY_WIDTH,
                TEXTURE_SIZE,
                Component.translatable("gui.cookiejar.cookie_editor.filter")
        );
        filterWidget.setHint(Component.translatable("gui.cookiejar.cookie_editor.filter_placeholder"));
        filterWidget.setTextColor(CookieJar.COLOR_SUGGESTION);
        filterWidget.setResponder(this::editFilter);

        dataTypeButton = Button.builder(Component.literal("S"), button -> cycleDataType())
                .bounds(
                        PADDING + KEY_WIDTH + PADDING,
                        PADDING + TEXTURE_SIZE + PADDING,
                        TEXTURE_SIZE,
                        TEXTURE_SIZE
                )
                .build();
        // Data type set at end, after cookie widget initialized

        importButton = Button.builder(Component.literal("\uD83D\uDCE4"), button -> importCookies())
                .bounds(
                        width - (SCROLLER_WIDTH + (TEXTURE_SIZE + PADDING) * 3 + IMPORT_METHOD_WIDTH + PADDING),
                        PADDING + TEXTURE_SIZE + PADDING,
                        TEXTURE_SIZE,
                        TEXTURE_SIZE
                )
                .tooltip(Tooltip.create(Component.translatable("gui.cookiejar.cookie_editor.import")))
                .build();

        importMethodButton = Button.builder(Component.literal(""), button -> cycleImportMethod())
                .bounds(
                        width - (SCROLLER_WIDTH + (TEXTURE_SIZE + PADDING) * 2 + IMPORT_METHOD_WIDTH + PADDING),
                        PADDING + TEXTURE_SIZE + PADDING,
                        IMPORT_METHOD_WIDTH,
                        TEXTURE_SIZE
                )
                .build();
        setImportMethod(importMethod);

        exportButton = Button.builder(Component.literal("\uD83D\uDCE5"), button -> exportCookies())
                .bounds(
                        width - (SCROLLER_WIDTH + (TEXTURE_SIZE + PADDING) * 2),
                        PADDING + TEXTURE_SIZE + PADDING,
                        TEXTURE_SIZE,
                        TEXTURE_SIZE
                )
                .tooltip(Tooltip.create(Component.translatable("gui.cookiejar.cookie_editor.export")))
                .build();

        clearButton = new ImageButton(
                width - (SCROLLER_WIDTH + TEXTURE_SIZE + PADDING),
                PADDING + TEXTURE_SIZE + PADDING,
                TEXTURE_SIZE,
                TEXTURE_SIZE,
                new WidgetSprites(CROSS_BUTTON, CROSS_BUTTON_HIGHLIGHTED),
                button -> clear(),
                Component.translatable("gui.cookiejar.cookie_editor.clear")
        );
        clearButton.setTooltip(Tooltip.create(Component.translatable("gui.cookiejar.cookie_editor.clear")));

        addRenderableWidget(filterWidget);
        addRenderableWidget(dataTypeButton);
        addRenderableWidget(importButton);
        addRenderableWidget(importMethodButton);
        addRenderableWidget(exportButton);
        addRenderableWidget(clearButton);

        // Cookie list
        cookieWidget = new CookieListWidget(minecraft,
                width,
                height - (PADDING + (TEXTURE_SIZE + PADDING) * 2),
                PADDING + (TEXTURE_SIZE + PADDING) * 2,
                ROW_HEIGHT
        );
        populateEntriesFromCookies(allCookieActionsSupported);
        setDataType(dataType);
        cookieWidget.populateFilteredFromMasterEntries();

        addRenderableWidget(cookieWidget);
    }

    private void populateEntriesFromCookies(boolean allCookieActionsSupported) {
        cookies.forEach((key, payload) -> cookieEntries.add(cookieWidget.newEntry(key, payload, allCookieActionsSupported)));
    }

    private void addCookie() {
        if (keyToAdd == null) {
            return;
        }
        cookies.put(keyToAdd, payloadToAdd);
        updateListWidgetWithCookie(keyToAdd);
    }

    private void editKey(String keyStr) {
        if (keyStr.isEmpty()) {
            keyWidget.setTextColor(CookieJar.COLOR_SUGGESTION);
            keyToAdd = null;
            addButton.active = false;
            return;
        }
        Identifier newKey = Identifier.tryParse(keyStr);
        if (newKey == null) {
            keyWidget.setTextColor(CookieJar.COLOR_INVALID);
            keyToAdd = null;
            addButton.active = false;
        } else {
            keyWidget.setTextColor(CookieJar.COLOR_VALID);
            keyToAdd = newKey;
            addButton.active = true;
        }
    }

    private void setPayloadWidget(EditBox widget, String payloadStr, Consumer<byte[]> validPayloadConsumer) {
        widget.setMaxLength(dataType.getMaxLength(payloadStr));
        Optional<byte[]> payloadOpt = dataType.toPayload(payloadStr);
        if (payloadOpt.isPresent()) {
            validPayloadConsumer.accept(payloadOpt.get());
            if (payloadStr.isEmpty()) {
                widget.setTextColor(CookieJar.COLOR_SUGGESTION);
            } else {
                widget.setTextColor(CookieJar.COLOR_VALID);
            }
        } else {
            widget.setTextColor(CookieJar.COLOR_INVALID);
        }
    }

    private void editFilter(String filterStr) {
        if (filterStr.isEmpty()) {
            filterWidget.setTextColor(CookieJar.COLOR_SUGGESTION);
            filter = null;
        } else {
            filterWidget.setTextColor(CookieJar.COLOR_VALID);
            filter = filterStr;
        }
        cookieWidget.populateFilteredFromMasterEntries();
    }

    private void cycleDataType() {
        DataType newType = switch (dataType) {
            case STRING -> DataType.BYTE_ARRAY;
            case BYTE_ARRAY -> DataType.STRING;
        };
        setDataType(newType);
    }
    private void setDataType(DataType type) {
        dataType = type;
        dataTypeButton.setMessage(type.label);
        dataTypeButton.setTooltip(type.tooltip);
        String input = type.toStringInput(payloadToAdd);
        payloadWidget.setMaxLength(type.getMaxLength(input));
        payloadWidget.setResponder(null);
        payloadWidget.setValue(input);
        payloadWidget.setResponder(payloadStr ->
                setPayloadWidget(payloadWidget, payloadStr, payload -> payloadToAdd = payload));
        payloadWidget.setHint(type.getPayloadPlaceholder());
        cookieWidget.children().forEach(CookieListWidget.Entry::updatePayloadFromDataType);
    }

    private void cycleImportMethod() {
        ImportMethod newMethod = switch (importMethod) {
            case ADD -> ImportMethod.MERGE;
            case MERGE -> ImportMethod.ADD;
        };
        setImportMethod(newMethod);
    }
    private void setImportMethod(ImportMethod method) {
        importMethod = method;
        importMethodButton.setMessage(method.label);
        importMethodButton.setTooltip(method.tooltip);
    }

    private void importCookies() {
        // Reminder: Do NOT pass user input into tinyfd
        String pathStr = TinyFileDialogs.tinyfd_openFileDialog((CharSequence) null, null, null, null, false);
        if (pathStr == null) {
            return;
        }
        CompoundTag nbt;
        try {
            // No cookie file should get anywhere close to 1G, but just in case...
            nbt = NbtIo.readCompressed(Paths.get(pathStr), NbtAccounter.create(CookieUtil.ONE_GIGABYTE));
        } catch (IOException e) {
            CookieJar.LOGGER.error("Failed to import cookies", e);
            return;
        }
        Map<Identifier, byte[]> imported = CookieUtil.fromNbt(nbt);
        switch (importMethod) {
            case ADD -> imported.forEach(cookies::putIfAbsent);
            case MERGE -> cookies.putAll(imported);
        }
        // Connection status could have changed since menu opened
        boolean allCookieActionsSupported = CookieJar.getNetworkListener() != null;
        // Completely re-create list of entries
        cookieEntries.clear();
        populateEntriesFromCookies(allCookieActionsSupported);
        cookieWidget.populateFilteredFromMasterEntries();
    }

    private void exportCookies() {
        // Reminder: Do NOT pass user input into tinyfd
        String pathStr = TinyFileDialogs.tinyfd_saveFileDialog((CharSequence) null, null, null, null);
        if (pathStr == null) {
            return;
        }
        try {
            NbtIo.writeCompressed(CookieUtil.toNbt(cookies), Paths.get(pathStr));
        } catch (IOException e) {
            CookieJar.LOGGER.error("Failed to export cookies", e);
        }
    }

    private void clear() {
        cookies.clear();
        cookieEntries.clear();
        cookieWidget.clear();
    }

    /**
     * Called when cookie is set on client while screen is open.
     * Used to update the screen live.
     * @param key the key of the cookie
     */
    public void onStoreCookie(Identifier key) {
        CookieListWidget.Entry entry = cookieWidget.getEntry(key);
        // If user was editing the cookie that just got set, unselect it to prevent misinput
        if (entry != null && key.equals(entry.key)) {
            cookieWidget.setSelected(null);
        }
        updateListWidgetWithCookie(key);
    }

    // When a cookie is added to the cookie map using put(),
    // it could be a completely new cookie or modifying the payload of an existing cookie.
    private void updateListWidgetWithCookie(Identifier key) {
        byte[] payload = cookies.get(key);
        CookieListWidget.Entry existingEntry = cookieWidget.getEntry(key);
        // If the cookie is new, add it to the list
        if (existingEntry == null) {
            // Update master entry list
            boolean allCookieActionsSupported = CookieJar.getNetworkListener() != null;
            CookieListWidget.Entry newEntry = cookieWidget.newEntry(key, payload, allCookieActionsSupported);
            cookieEntries.add(newEntry);
            // Only add to the viewable list if the cookie passes the filter
            if (newEntry.passesFilter()) {
                cookieWidget.populateFilteredFromMasterEntries();
            }
        } else {
            // Cookie already exists, only need to update payload
            existingEntry.payload = payload;
            existingEntry.updatePayloadFromDataType();
        }
    }

    @Override
    public void tick() {
        super.tick();
        // Connection status could have changed since menu opened
        boolean allCookieActionsSupported = CookieJar.getNetworkListener() != null;
        cookieWidget.children().forEach(c -> c.setSendButtonActive(allCookieActionsSupported));
    }

    @Override
    public void onClose() {
        minecraft.gui.setScreen(parent);
    }

    private class CookieListWidget extends ContainerObjectSelectionList<CookieListWidget.@NonNull Entry> {

        public CookieListWidget(Minecraft client, int width, int height, int y, int itemHeight) {
            super(client, width, height, y, itemHeight);
        }

        @Override
        public int getRowWidth() {
            return width; // Fixes scrollbar appearing in the middle of the screen
        }

        public Entry newEntry(Identifier key, byte[] payload, boolean allCookieActionsSupported) {
            return new Entry(key, payload, allCookieActionsSupported);
        }

        public @Nullable Entry getEntry(Identifier key) {
            return children().stream()
                    .filter(e -> e.key.equals(key))
                    .findFirst()
                    .orElse(null);
        }

        public void populateFilteredFromMasterEntries() {
            clear(); // Prevent duplicate or glitched entries
            cookieEntries.stream()
                    .filter(Entry::passesFilter)
                    .sorted(Comparator.comparing(en -> en.key)) // Map isn't sorted by default
                    .forEach(this::addEntry);
        }

        // Widens access
        public void clear() {
            clearEntries();
        }

        @Override
        protected int scrollBarX() {
            return width - SCROLLER_WIDTH;
        }

        private class Entry extends ContainerObjectSelectionList.Entry<@NonNull Entry> {

            // Data updated when user types, last known good value
            private Identifier key;
            private byte[] payload;

            private final List<AbstractWidget> children;
            private final ImageButton deleteButton;
            private final Button sendButton;
            private final EditBox keyWidget;
            private final Button copyButton;
            private final EditBox payloadWidget;

            public Entry(Identifier key, byte[] payload, boolean allCookieActionsSupported) {
                this.key = key;
                this.payload = payload;

                deleteButton = new ImageButton(
                        CONTENT_PADDING, 0, TEXTURE_SIZE, TEXTURE_SIZE,
                        new WidgetSprites(CROSS_BUTTON, CROSS_BUTTON_HIGHLIGHTED),
                        button -> deleteCookie(),
                        Component.translatable("gui.cookiejar.cookie_editor.delete")
                );
                deleteButton.setTooltip(Tooltip.create(Component.translatable("gui.cookiejar.cookie_editor.delete")));

                sendButton = Button.builder(Component.literal("\uD83D\uDCE8"), button -> sendCookie())
                        .bounds(CONTENT_PADDING + TEXTURE_SIZE + CONTENT_PADDING, 0, TEXTURE_SIZE, TEXTURE_SIZE)
                        .tooltip(Tooltip.create(Component.translatable("gui.cookiejar.cookie_editor.send")))
                        .build();
                sendButton.active = allCookieActionsSupported;

                keyWidget = new EditBox(
                        font,
                        (CONTENT_PADDING + TEXTURE_SIZE) * 2 + CONTENT_PADDING,
                        0,
                        KEY_WIDTH,
                        TEXTURE_SIZE,
                        Component.translatable("gui.cookiejar.cookie_editor.key")
                );
                keyWidget.setMaxLength(Integer.MAX_VALUE);
                keyWidget.setValue(key.toString());
                keyWidget.setResponder(this::editKey);

                copyButton = Button.builder(Component.literal("\uD83D\uDCCB"), button -> copyPayload())
                        .bounds((CONTENT_PADDING + TEXTURE_SIZE) * 2 + CONTENT_PADDING + KEY_WIDTH + CONTENT_PADDING, 0, TEXTURE_SIZE, TEXTURE_SIZE)
                        .tooltip(Tooltip.create(Component.translatable("gui.cookiejar.cookie_editor.copy_payload")))
                        .build();

                payloadWidget = new EditBox(
                        font,
                        (CONTENT_PADDING + TEXTURE_SIZE) * 3 + CONTENT_PADDING + KEY_WIDTH + CONTENT_PADDING,
                        0,
                        width - ((SCROLLER_WIDTH + (CONTENT_PADDING + TEXTURE_SIZE) * 3 + CONTENT_PADDING + KEY_WIDTH + CONTENT_PADDING) + CONTENT_PADDING),
                        TEXTURE_SIZE,
                        Component.translatable("gui.cookiejar.cookie_editor.payload")
                );
                updatePayloadFromDataType();
                payloadWidget.setResponder(this::editPayload);

                children = List.of(deleteButton, sendButton, keyWidget, copyButton, payloadWidget);
            }

            public void setSendButtonActive(boolean active) {
                sendButton.active = active;
                if (active) {
                    sendButton.setTooltip(Tooltip.create(Component.translatable("gui.cookiejar.cookie_editor.send")));
                } else {
                    sendButton.setTooltip(Tooltip.create(Component.translatable("gui.cookiejar.cookie_editor.send_disabled")));
                }
            }

            public void updatePayloadFromDataType() {
                String input = dataType.toStringInput(payload);
                payloadWidget.moveCursorTo(0, false); // Prevent crash due to OOB selection
                payloadWidget.setMaxLength(dataType.getMaxLength(input));
                payloadWidget.setResponder(null); // Prevent UTF-8 round trip from corrupting binary payloads
                payloadWidget.setValue(input);
                payloadWidget.setResponder(this::editPayload);
            }

            private void deleteCookie() {
                cookies.remove(key);
                cookieEntries.remove(this);
                CookieListWidget.this.removeEntry(this);
            }

            private void editKey(String keyStr) {
                Identifier newKey = Identifier.tryParse(keyStr);
                // If key is unchanged, do nothing to prevent it being marked as invalid
                // due to the cookies.containsKey() check
                if (key.equals(newKey)) {
                    return;
                }
                // Show key as invalid if it already exists, two identical keys cannot exist
                if (newKey == null || cookies.containsKey(newKey)) {
                    keyWidget.setTextColor(CookieJar.COLOR_INVALID);
                } else {
                    keyWidget.setTextColor(CookieJar.COLOR_VALID);
                    cookies.remove(key);
                    cookies.put(newKey, payload);
                    key = newKey;
                }
            }

            private void sendCookie() {
                ClientCommonPacketListenerImpl listener = CookieJar.getNetworkListener();
                if (listener == null) {
                    return;
                }
                ServerboundCookieResponsePacket packet = new ServerboundCookieResponsePacket(key, payload);
                listener.send(packet);
            }

            private void editPayload(String payloadStr) {
                setPayloadWidget(payloadWidget, payloadStr, payload -> {
                    this.payload = payload;
                    cookies.put(key, payload);
                });
            }

            private void copyPayload() {
                minecraft.keyboardHandler.setClipboard(dataType.toStringInput(payload));
            }

            public boolean passesFilter() {
                return filter == null || key.toString().toLowerCase(Locale.ROOT).contains(filter.toLowerCase(Locale.ROOT));
            }

            @Override
            public @NonNull List<? extends NarratableEntry> narratables() {
                return children;
            }

            /**
             * <strong>Unmodifiable!</strong>
             */
            @Override
            public @NonNull List<? extends GuiEventListener> children() {
                return children;
            }

            @Override
            public void extractContent(@NonNull GuiGraphicsExtractor graphics, int mouseX, int mouseY, boolean hovered, float deltaTicks) {
                children.forEach(child -> {
                    child.setY(getY());
                    child.extractRenderState(graphics, mouseX, mouseY, deltaTicks);
                });
            }
        }

    }

    private enum DataType {
        /** UTF-8 string, always valid */
        STRING(
                Component.literal("S").withColor(0xFFFFFF),
                Tooltip.create(Component.translatable("gui.cookiejar.cookie_editor.string_data")),
                payload -> new String(payload, StandardCharsets.UTF_8),
                input -> Optional.of(input.getBytes(StandardCharsets.UTF_8))
        ),
        /** Raw bytes, edited in hex form */
        BYTE_ARRAY(
                Component.literal("B").withColor(0xBB833A),
                Tooltip.create(Component.translatable("gui.cookiejar.cookie_editor.byte_data")),
                Hex::encodeHexString,
                input -> {
                    try {
                        return Optional.of(Hex.decodeHex(input));
                    } catch (DecoderException e) {
                        return Optional.empty();
                    }
                }
        );

        public final Component label;
        public final Tooltip tooltip;
        // Convert from raw payload to string displayed in text box
        private final Function<byte[], String> toDisplayFunc;
        // Convert from string displayed in text box to raw payload, or empty if invalid
        private final Function<String, Optional<byte[]>> toPayloadFunc;

        DataType(Component label, Tooltip tooltip, Function<byte[], String> toDisplayFunc, Function<String, Optional<byte[]>> toPayloadFunc) {
            this.label = label;
            this.tooltip = tooltip;
            this.toDisplayFunc = toDisplayFunc;
            this.toPayloadFunc = toPayloadFunc;
        }

        /**
         * Converts a cookie payload to a user-editable string.
         * @param payload payload
         * @return string
         */
        public String toStringInput(byte[] payload) {
            return toDisplayFunc.apply(payload);
        }
        /**
         * Tries to convert user input to a cookie payload.
         * @param input string
         * @return the parsed cookie payload, or empty if invalid or too long
         */
        public Optional<byte[]> toPayload(String input) {
            return toPayloadFunc.apply(input).filter(payload -> payload.length <= CookieUtil.MAX_COOKIE_SIZE);
        }

        /**
         * Calculates what the max length of a text box should be to not go over the cookie size limit.
         * If typing any character would go above the cookie size limit, the max length will be the current length to
         * prevent any more typing.
         * Note that since UTF-8 strings use variable-length encoding, if the user has one byte remaining but enters a
         * 2-byte character, the text box will be over the byte length limit but not the character length limit.
         * @param input string user input, may or may not be valid
         * @return the text box max length
         */
        public int getMaxLength(String input) {
            if (this == BYTE_ARRAY) {
                return Math.max(input.length(), CookieUtil.MAX_COOKIE_SIZE * 2);
            }
            if (input.getBytes(StandardCharsets.UTF_8).length >= CookieUtil.MAX_COOKIE_SIZE) {
                return input.length();
            }
            return CookieUtil.MAX_COOKIE_SIZE;
        }

        /**
         * Converts the payload string from the lang file (defined in STRING data type) to the
         * string in the current data type that produces the same payload.
         * @return placeholder to put in the data text box
         */
        public Component getPayloadPlaceholder() {
            if (this == DataType.STRING) {
                return Component.translatable("gui.cookiejar.cookie_editor.payload_placeholder");
            }
            String localized = Language.getInstance().getOrDefault("gui.cookiejar.cookie_editor.payload_placeholder");
            byte[] intermediatePayload = DataType.STRING.toPayload(localized).orElseThrow();
            String placeholder = toStringInput(intermediatePayload);
            return Component.literal(placeholder);
        }
    }

    private enum ImportMethod {
        /** Strictly adds cookies, no updating */
        ADD(
                Component.translatable("gui.cookiejar.cookie_editor.import_add"),
                Tooltip.create(Component.translatable("gui.cookiejar.cookie_editor.import_add_description"))
        ),
        /** Merges all cookies, overwriting existing ones */
        MERGE(
                Component.translatable("gui.cookiejar.cookie_editor.import_merge"),
                Tooltip.create(Component.translatable("gui.cookiejar.cookie_editor.import_merge_description"))
        );

        public final Component label;
        public final Tooltip tooltip;

        ImportMethod(Component label, Tooltip tooltip) {
            this.label = label;
            this.tooltip = tooltip;
        }
    }

}
