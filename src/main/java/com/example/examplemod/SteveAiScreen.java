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
        TRADE("Trade"),
        CHAT("Chat"),
        MAP("Map"),
        INVENTORY("Inventory");

        private final String label;

        Tab(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    private Tab activeTab = Tab.TRADE;

    private int tabY;
    private int tradeTabX;
    private int chatTabX;
    private int mapTabX;
    private int inventoryTabX;

    private static final int TAB_HEIGHT = 20;
    private static final int TRADE_TAB_WIDTH = 52;
    private static final int CHAT_TAB_WIDTH = 52;
    private static final int MAP_TAB_WIDTH = 48;
    private static final int INVENTORY_TAB_WIDTH = 72;
    private static final int TAB_GAP = 4;

    private EditBox chatInput;
    private String lastPrompt = "";
    private String lastResponse = "Future chat functionality";

    public SteveAiScreen(SteveAiMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        LOGGER.info("### SteveAiScreen CONSTRUCTOR ###");
    }

    @Override
    protected void init() {
        super.init();

        tabY = this.topPos - 18;

        tradeTabX = this.leftPos + 18;
        chatTabX = tradeTabX + TRADE_TAB_WIDTH + TAB_GAP;
        mapTabX = chatTabX + CHAT_TAB_WIDTH + TAB_GAP;
        inventoryTabX = mapTabX + MAP_TAB_WIDTH + TAB_GAP;

        chatInput = new EditBox(
            this.font,
            this.leftPos + 24,
            this.topPos + this.imageHeight - 34,
            this.imageWidth - 48,
            18,
            Component.literal("Ask SteveAI")
        );
        chatInput.setMaxLength(200);
        chatInput.setVisible(activeTab == Tab.CHAT);
        chatInput.setFocused(activeTab == Tab.CHAT);
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
        guiGraphics.drawString(this.font, this.title, this.leftPos + 106, this.topPos + 6, 0x000000, false);
    }

    private void drawTabs(GuiGraphics guiGraphics) {
        drawTab(guiGraphics, tradeTabX, tabY, TRADE_TAB_WIDTH, Tab.TRADE);
        drawTab(guiGraphics, chatTabX, tabY, CHAT_TAB_WIDTH, Tab.CHAT);
        drawTab(guiGraphics, mapTabX, tabY, MAP_TAB_WIDTH, Tab.MAP);
        drawTab(guiGraphics, inventoryTabX, tabY, INVENTORY_TAB_WIDTH, Tab.INVENTORY);
    }

    private void drawTab(GuiGraphics guiGraphics, int x, int y, int width, Tab tab) {
        boolean selected = activeTab == tab;

        int outer = 0xFF3F3F3F;
        int light = 0xFFFFFFFF;
        int fill = selected ? 0xFFC6C6C6 : 0xFFB5B5B5;
        int shadow = 0xFF8B8B8B;
        int textColor = 0x000000;

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
        guiGraphics.drawString(this.font, line2, centerX - line2Width / 2, centerY + 14, 0x000000, false);
    }

    private void drawChatTab(GuiGraphics guiGraphics) {
        guiGraphics.drawString(this.font, "SteveAI Chat", this.leftPos + 24, this.topPos + 24, 0x000000, false);
        guiGraphics.drawString(this.font, "You:", this.leftPos + 24, this.topPos + 46, 0x000000, false);
        guiGraphics.drawString(this.font, lastPrompt, this.leftPos + 60, this.topPos + 46, 0x000000, false);

        guiGraphics.drawString(this.font, "SteveAI:", this.leftPos + 24, this.topPos + 68, 0x000000, false);
        drawWrappedText(
            guiGraphics,
            lastResponse,
            this.leftPos + 24,
            this.topPos + 84,
            this.imageWidth - 48,
            0x000000
        );
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

        if (activeTab == Tab.CHAT && chatInput != null) {
            if (chatInput.mouseClicked(event, consumed)) {
                return true;
            }
        }

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

        if (activeTab != Tab.TRADE) {
            return true;
        }

        return super.mouseClicked(event, consumed);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (activeTab == Tab.CHAT && chatInput != null && chatInput.isVisible()) {
            if (chatInput.keyPressed(event)) {
                return true;
            }

            int keyCode = event.key();
            if (keyCode == 257 || keyCode == 335) {
                String message = chatInput.getValue().trim();

                if (!message.isEmpty()) {
                    lastPrompt = message;
                    lastResponse = "Thinking...";
                    chatInput.setValue("");

                    String prompt = "You are SteveAI, a Minecraft villager. Keep replies short. The player asks: " + message;
                    lastResponse = OpenAiService.ask(prompt);
                }

                return true;
            }
        }

        return super.keyPressed(event);
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