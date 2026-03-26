package com.example.examplemod;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.MerchantScreen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

public class SteveAiScreen extends MerchantScreen {

    private static final Logger LOGGER = LogUtils.getLogger();

    private enum Tab {
        CHAT("Chat"),
        TRADE("Trade"),
        MAP("Map"),
        INVENTORY("Inv"),
        HIST("Hist");

        private final String label;

        Tab(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    private Tab activeTab = Tab.CHAT;

    private int tabY;
    private int tradeTabX;
    private int chatTabX;
    private int mapTabX;
    private int histTabX;
    private int inventoryTabX;

    private static final int CHAT_TAB_WIDTH = 52;
    private static final int TRADE_TAB_WIDTH = 60;
    private static final int MAP_TAB_WIDTH = 44;
    private static final int HIST_TAB_WIDTH = 48;
    private static final int INVENTORY_TAB_WIDTH = 42;
    private static final int TAB_GAP = 4;
    private static final int TAB_HEIGHT = 18;

    private EditBox chatInput;
    private String lastPrompt = "";
    private String lastResponse = "";

    public SteveAiScreen(SteveAiMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        LOGGER.info("### SteveAiScreen CONSTRUCTOR ###");
    }

    @Override
    protected void init() {
        super.init();

        tabY = this.topPos - 18;

        chatTabX = this.leftPos + 8;
        tradeTabX = chatTabX + CHAT_TAB_WIDTH + TAB_GAP;
        mapTabX = tradeTabX + TRADE_TAB_WIDTH + TAB_GAP;
        histTabX = mapTabX + MAP_TAB_WIDTH + TAB_GAP;
        inventoryTabX = histTabX + HIST_TAB_WIDTH + TAB_GAP;

        chatInput = new EditBox(
            this.font,
            this.leftPos + 24,
            this.topPos + 18,
            this.imageWidth - 48,
            18,
            Component.literal("Ask SteveAI")
        );
        chatInput.setMaxLength(200);
        chatInput.setVisible(activeTab == Tab.CHAT);
        chatInput.setFocused(activeTab == Tab.CHAT);
        chatInput.setTextColor(0xFFFFFFFF);
        chatInput.setTextColorUneditable(0xFFFFFFFF);
        chatInput.setBordered(true);
        this.addRenderableWidget(chatInput);
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
    if (activeTab == Tab.TRADE) {
        super.render(guiGraphics, mouseX, mouseY, partialTick);
    } else {
        this.renderBackground(guiGraphics, mouseX, mouseY, partialTick);
        drawMainPanel(guiGraphics);
        drawCustomLabels(guiGraphics);
        drawPlaceholderContent(guiGraphics);

        if (activeTab == Tab.CHAT && chatInput != null && chatInput.isVisible()) {
            chatInput.render(guiGraphics, mouseX, mouseY, partialTick);
        }
    }

    drawTabs(guiGraphics);
    renderTooltip(guiGraphics, mouseX, mouseY);
    }

    @Override
    protected void renderLabels(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        if (activeTab == Tab.TRADE) {
            super.renderLabels(guiGraphics, mouseX, mouseY);
        }
    }

    private void drawMainPanel(GuiGraphics guiGraphics) {
        int x1 = this.leftPos;
        int y1 = this.topPos;
        int x2 = this.leftPos + this.imageWidth;
        int y2 = this.topPos + this.imageHeight;

        int outer = 0xFF3F3F3F;
        int light = 0xFFFFFFFF;
        int panel = 0xFFC6C6C6;
        int shadow = 0xFF8B8B8B;

        guiGraphics.fill(x1, y1, x2, y2, outer);
        guiGraphics.fill(x1 + 1, y1 + 1, x2 - 1, y1 + 3, light);
        guiGraphics.fill(x1 + 1, y1 + 1, x1 + 3, y2 - 1, light);
        guiGraphics.fill(x1 + 1, y2 - 3, x2 - 1, y2 - 1, shadow);
        guiGraphics.fill(x2 - 3, y1 + 1, x2 - 1, y2 - 1, shadow);
        guiGraphics.fill(x1 + 3, y1 + 3, x2 - 3, y2 - 3, panel);
    }

    private void drawCustomLabels(GuiGraphics guiGraphics) {
        guiGraphics.drawString(this.font, this.title, this.leftPos + 106, this.topPos + 6, 0xFF000000, false);
    }

    private void drawTabs(GuiGraphics guiGraphics) {
        drawTab(guiGraphics, chatTabX, tabY, CHAT_TAB_WIDTH, Tab.CHAT);
        drawTab(guiGraphics, tradeTabX, tabY, TRADE_TAB_WIDTH, Tab.TRADE);
        drawTab(guiGraphics, mapTabX, tabY, MAP_TAB_WIDTH, Tab.MAP);
        drawTab(guiGraphics, histTabX, tabY, HIST_TAB_WIDTH, Tab.HIST);
        drawTab(guiGraphics, inventoryTabX, tabY, INVENTORY_TAB_WIDTH, Tab.INVENTORY);
    }

    private void drawTab(GuiGraphics guiGraphics, int x, int y, int width, Tab tab) {
        boolean selected = activeTab == tab;

        int outer = 0xFF3F3F3F;
        int light = 0xFFFFFFFF;
        int fill = selected ? 0xFFC6C6C6 : 0xFFB5B5B5;
        int shadow = 0xFF8B8B8B;
        int textColor = 0xFF000000;

        int x2 = x + width;
        int y2 = y + TAB_HEIGHT;

        guiGraphics.fill(x, y, x2, y2, outer);
        guiGraphics.fill(x + 1, y + 1, x2 - 1, y + 3, light);
        guiGraphics.fill(x + 1, y + 1, x + 3, y2 - 1, light);
        guiGraphics.fill(x + 1, y2 - 3, x2 - 1, y2 - 1, shadow);
        guiGraphics.fill(x2 - 3, y + 1, x2 - 1, y2 - 1, shadow);
        guiGraphics.fill(x + 3, y + 3, x2 - 3, y2 - 3, fill);

        if (selected) {
            guiGraphics.fill(x + 2, y2 - 2, x2 - 2, y2 + 2, 0xFFC6C6C6);
        }

        int textWidth = this.font.width(tab.label());
        guiGraphics.drawString(
            this.font,
            tab.label(),
            x + (width - textWidth) / 2,
            y + 6,
            textColor,
            false
        );
    }

    private void drawPlaceholderContent(GuiGraphics guiGraphics) {
        if (activeTab == Tab.CHAT) {
            drawChatTab(guiGraphics);
            return;
        }

        String line1 = switch (activeTab) {
            case MAP -> "Future map functionality";
            case INVENTORY -> "Future inventory functionality";
            default -> "";
        };

        String line2 = "Placeholder";

        int centerX = this.leftPos + this.imageWidth / 2;
        int centerY = this.topPos + this.imageHeight / 2 - 10;

        int line1Width = this.font.width(line1);
        int line2Width = this.font.width(line2);

        guiGraphics.drawString(this.font, line1, centerX - line1Width / 2, centerY, 0x000000, false);
        guiGraphics.drawString(this.font, line2, centerX - line2Width / 2, centerY + 14, 0xFF000000, false);
    }

    private void drawChatTab(GuiGraphics guiGraphics) {
        int x = this.leftPos + 24;
        int y = this.topPos + 42;
        int maxWidth = this.imageWidth - 48;
        int lineHeight = 10;
        int labelWidth = 52;

        guiGraphics.drawString(this.font, "You:", x, y, 0xFF000000, false);

        var promptLines = this.font.split(Component.literal(lastPrompt), maxWidth - labelWidth);
        int py = y;
        for (var line : promptLines) {
            guiGraphics.drawString(this.font, line, x + labelWidth, py, 0xFF000000, false);
            py += lineHeight;
        }

        y = py + 8;
        guiGraphics.drawString(this.font, "SteveAI:", x, y, 0xFF000000, false);

        y += 16;
        drawWrappedText(guiGraphics, lastResponse, x, y, maxWidth, 0xFF000000);
    }

    private void drawWrappedText(GuiGraphics guiGraphics, String text, int x, int y, int maxWidth, int color) {
        var lines = this.font.split(Component.literal(text), maxWidth);
        int currentY = y;

        for (var line : lines) {
            guiGraphics.drawString(this.font, line, x, currentY, color, false);
            currentY += 10;
        }
    }

    private void updateTabVisibility() {
        if (chatInput != null) {
            boolean showChatInput = activeTab == Tab.CHAT;
            chatInput.setVisible(showChatInput);
            chatInput.setFocused(showChatInput);
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean consumed) {
        double mouseX = event.x();
        double mouseY = event.y();

        if (isTabClicked(mouseX, mouseY, tradeTabX, tabY, TRADE_TAB_WIDTH)) {
            activeTab = Tab.TRADE;
            updateTabVisibility();
            return true;
        }
        if (isTabClicked(mouseX, mouseY, chatTabX, tabY, CHAT_TAB_WIDTH)) {
            activeTab = Tab.CHAT;
            updateTabVisibility();
            return true;
        }
        if (isTabClicked(mouseX, mouseY, mapTabX, tabY, MAP_TAB_WIDTH)) {
            activeTab = Tab.MAP;
            updateTabVisibility();
            return true;
        }
        if (isTabClicked(mouseX, mouseY, inventoryTabX, tabY, INVENTORY_TAB_WIDTH)) {
            activeTab = Tab.INVENTORY;
            updateTabVisibility();
            return true;
        }

        if (activeTab == Tab.CHAT && chatInput != null && chatInput.mouseClicked(event, consumed)) {
            return true;
        }

        if (activeTab != Tab.TRADE) {
            return true;
        }

        return super.mouseClicked(event, consumed);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (activeTab == Tab.CHAT && chatInput != null && chatInput.isVisible()) {
            LOGGER.info("SteveAiScreen keypressed STARTED ");
            
            int keyCode = event.key();

            // ESC should still close the screen
            if (keyCode == 256) {
                return super.keyPressed(event);
            }

            // swallow inventory key E everywhere in this UI
            if (keyCode == 69) { // E
                return true;
            }

            // Handle Enter FIRST
            if (keyCode == 257 || keyCode == 335) {
                LOGGER.info("SteveAiScreen keypressed started to wait for input");
                String message = chatInput.getValue().trim();
                LOGGER.info("SteveAiScreen keypressed input is done and enter pressed {}", message);

                if (!message.isEmpty()) {
                    lastPrompt = message;
                    lastResponse = "Thinking...";
                    chatInput.setValue("");
                    LOGGER.info("SteveAiScreen calling openai to get reply"); 

                    if (minecraft != null && minecraft.player != null && minecraft.player.connection != null) {
                        ModNetworking.CHANNEL.send(
                            new SteveAiChatRequestPacket(message),
                            net.minecraftforge.network.PacketDistributor.SERVER.noArg()
                        );
                    } else {
                        lastResponse = "Not connected.";
                    }
                    //if (minecraft.player != null && minecraft.player.connection != null) {
                    //    minecraft.player.connection.sendCommand("testmod " + message);
                    //}
                    LOGGER.info("SteveAiScreen finished writing to chat file "); 
                }

                return true;
            }

            // Let the text box handle normal editing keys after that
            if (chatInput.keyPressed(event)) {
                LOGGER.info("SteveAiScreen keypressed leaving 1 ???  ");
                return true;
            }

            // IMPORTANT: swallow all other keys so Minecraft keybinds
            // like inventory ('e') do not close the screen
            return true;
        }
        LOGGER.info("SteveAiScreen keypressed leaving 2 ???  ");
        return super.keyPressed(event);

    }

    @Override
    public void onClose() {
        LOGGER.info("### SteveAiScreen onClose ###");
        super.onClose();
    }

    public void receiveServerReply(String prompt, String reply) {
        this.lastPrompt = prompt;
        this.lastResponse = reply;
    }

    @Override
    public void removed() {
        LOGGER.info("### SteveAiScreen removed ###");
        super.removed();
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        if (activeTab == Tab.CHAT && chatInput != null && chatInput.isVisible()) {
            if (chatInput.charTyped(event)) {
                return true;
            }
        }

        return super.charTyped(event);
    }

    private boolean isTabClicked(double mouseX, double mouseY, int x, int y, int width) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + TAB_HEIGHT;
    }
}