package fr.minenorth.pets.client;

import fr.minenorth.pets.K9Net;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;

/** Carnet de santé en lecture seule : les lignes viennent du serveur (texte français déjà mis en forme). */
public class PetBookScreen extends Screen {
    private static final int PAD = 14, LINE_H = 12;

    private final List<String> lines;
    private final boolean withActions;
    private final boolean lost;
    private long armedUntil;

    private PetBookScreen(List<String> lines, boolean withActions, boolean lost) {
        super(Component.translatable("gui.minenorthpets.book.title"));
        this.lines = lines;
        this.withActions = withActions;
        this.lost = lost;
    }

    public static void open(List<String> lines) {
        Minecraft.getInstance().setScreen(new PetBookScreen(lines, false, false));
    }

    /** Fiche du carnet de poche : boutons « perdu / retrouvé » et « abandonner » (confirmation par un second clic dans les 5 s). */
    public static void openWithActions(List<String> lines, boolean lost) {
        Minecraft.getInstance().setScreen(new PetBookScreen(lines, true, lost));
    }

    private int panelWidth() {
        int w = withActions ? 420 - PAD * 2 : 180;
        for (String l : lines) w = Math.max(w, font.width(l));
        return Math.min(w, width - 40) + PAD * 2;
    }

    private int panelHeight() { return lines.size() * LINE_H + PAD * 2 + 34; }

    @Override
    protected void init() {
        int w = panelWidth(), top = (height - panelHeight()) / 2;
        int y = top + panelHeight() - PAD - 22;
        if (!withActions) {
            addRenderableWidget(new PetMenuScreen.RoundButton((width - 100) / 2, y, 100, 22, Component.translatable("gui.minenorthpets.book.close"), b -> onClose()));
            return;
        }
        int x = (width - 396) / 2;
        addRenderableWidget(new PetMenuScreen.RoundButton(x, y, 96, 22,
                Component.translatable("gui.minenorthpets.book.rename"), b -> Minecraft.getInstance().setScreen(new PetNameScreen())));
        x += 100;
        addRenderableWidget(new PetMenuScreen.RoundButton(x, y, 96, 22,
                Component.translatable(lost ? "gui.minenorthpets.book.found" : "gui.minenorthpets.book.lost"), b -> {
            K9Net.CHANNEL.sendToServer(new K9Net.BookAction(0));
            onClose();
        }));
        PetMenuScreen.RoundButton abandon = new PetMenuScreen.RoundButton(x + 100, y, 96, 22, Component.translatable("gui.minenorthpets.book.abandon"), b -> {});
        abandon.setOnPress(b -> {
            boolean armed = System.currentTimeMillis() < armedUntil;
            K9Net.CHANNEL.sendToServer(new K9Net.BookAction(1));
            if (armed) {
                onClose();
            } else {
                armedUntil = System.currentTimeMillis() + 5000;
                abandon.setMessage(Component.translatable("gui.minenorthpets.book.confirm"));
            }
        });
        addRenderableWidget(abandon);
        addRenderableWidget(new PetMenuScreen.RoundButton(x + 200, y, 96, 22, Component.translatable("gui.minenorthpets.book.close"), b -> onClose()));
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
