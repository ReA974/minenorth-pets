package fr.minenorth.pets.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;

/** Carnet de santé en lecture seule : les lignes viennent du serveur (texte français déjà mis en forme). */
public class PetBookScreen extends Screen {
    private static final int PAD = 14, LINE_H = 12;

    private final List<String> lines;

    private PetBookScreen(List<String> lines) {
        super(Component.translatable("gui.minenorthpets.book.title"));
        this.lines = lines;
    }

    public static void open(List<String> lines) {
        Minecraft.getInstance().setScreen(new PetBookScreen(lines));
    }

    private int panelWidth() {
        int w = 180;
        for (String l : lines) w = Math.max(w, font.width(l));
        return Math.min(w, width - 40) + PAD * 2;
    }

    private int panelHeight() { return lines.size() * LINE_H + PAD * 2 + 34; }

    @Override
    protected void init() {
        int w = panelWidth(), top = (height - panelHeight()) / 2;
        addRenderableWidget(new PetMenuScreen.RoundButton((width - 100) / 2, top + panelHeight() - PAD - 22, 100, 22,
                Component.translatable("gui.minenorthpets.book.close"), b -> onClose()));
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        int w = panelWidth(), x = (width - w) / 2, y = (height - panelHeight()) / 2;
        PetMenuScreen.RoundButton.roundedRect(g, x - 1, y - 1, w + 2, panelHeight() + 2, 14, 0x66FFFFFF);
        PetMenuScreen.RoundButton.roundedRect(g, x, y, w, panelHeight(), 13, 0xF0181B26);
        for (int i = 0; i < lines.size(); i++) {
            g.drawString(font, lines.get(i), x + PAD, y + PAD + i * LINE_H, 0xFFFFFFFF, false);
        }
        super.render(g, mouseX, mouseY, partialTick);
    }

    @Override
    public void renderBackground(GuiGraphics g) {
        g.fill(0, 0, width, height, 0x55000000);
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
