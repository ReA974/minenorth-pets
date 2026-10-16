package fr.minenorth.pets.client;

import fr.minenorth.pets.K9Net;
import fr.minenorth.pets.PetActions;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;

/**
 * Menu d'actions d'un animal apprivoisé (Ctrl + clic droit). Panneau et boutons très arrondis, fond sombre translucide.
 * Les libellés dépendent de l'état reçu du serveur (assis, couché, reste sur place) ; chaque bouton envoie son action au serveur.
 */
public class PetMenuScreen extends Screen {
    private static final int BUTTON_W = 170, BUTTON_H = 24, GAP = 6, PAD = 14, TITLE_H = 22;

    private final int entityId;
    private final int flags;

    private PetMenuScreen(int entityId, int flags) {
        super(Component.translatable("gui.minenorthpets.menu.title"));
        this.entityId = entityId;
        this.flags = flags;
    }

    public static void open(int entityId, int flags) {
        Minecraft.getInstance().setScreen(new PetMenuScreen(entityId, flags));
    }

    private Entity pet() { return Minecraft.getInstance().level == null ? null : Minecraft.getInstance().level.getEntity(entityId); }

    @Override
    protected void init() {
        boolean sitting = (flags & PetActions.FLAG_SITTING) != 0 && (flags & PetActions.FLAG_SLEEPING) == 0;
        boolean sleeping = (flags & PetActions.FLAG_SLEEPING) != 0;
        boolean staying = (flags & PetActions.FLAG_STAYING) != 0;
        String[] labels = {
                "gui.minenorthpets.menu.pet",
                "gui.minenorthpets.menu.play",
                sleeping ? "gui.minenorthpets.menu.wake" : "gui.minenorthpets.menu.sleep",
                sitting ? "gui.minenorthpets.menu.stand" : "gui.minenorthpets.menu.sit",
                staying ? "gui.minenorthpets.menu.follow" : "gui.minenorthpets.menu.stay"};
        PetActions.Action[] actions = {PetActions.Action.PET, PetActions.Action.PLAY, PetActions.Action.SLEEP, PetActions.Action.SIT, PetActions.Action.STAY};
        int x = (width - BUTTON_W) / 2;
        int y = panelTop() + TITLE_H + PAD;
        for (int i = 0; i < labels.length; i++) {
            PetActions.Action a = actions[i];
            addRenderableWidget(new RoundButton(x, y + i * (BUTTON_H + GAP), BUTTON_W, BUTTON_H, Component.translatable(labels[i]), b -> {
                K9Net.CHANNEL.sendToServer(new K9Net.Action(entityId, a.ordinal()));
                onClose();
            }));
        }
    }

    private int panelHeight() { return TITLE_H + PAD * 2 + 5 * BUTTON_H + 4 * GAP; }
    private int panelTop() { return (height - panelHeight()) / 2; }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        int w = BUTTON_W + PAD * 2, x = (width - w) / 2, y = panelTop();
        RoundButton.roundedRect(g, x - 1, y - 1, w + 2, panelHeight() + 2, 14, 0x66FFFFFF);
        RoundButton.roundedRect(g, x, y, w, panelHeight(), 13, 0xE0181B26);
        Entity pet = pet();
        Component name = pet != null && pet.hasCustomName() ? pet.getCustomName() : pet != null ? pet.getType().getDescription() : title;
        g.drawCenteredString(font, name, width / 2, y + PAD - 2, 0xFFFFFFFF);
        super.render(g, mouseX, mouseY, partialTick);
    }

    @Override
    public void renderBackground(GuiGraphics g) {
        g.fill(0, 0, width, height, 0x55000000);
    }

    @Override
    public boolean isPauseScreen() { return false; }

    /** Bouton rectangle à coins très arrondis. */
    static final class RoundButton extends Button {
        RoundButton(int x, int y, int w, int h, Component label, OnPress onPress) {
            super(x, y, w, h, label, onPress, DEFAULT_NARRATION);
        }

        @Override
        public void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
            int fill = isHoveredOrFocused() ? 0xFF3D6BE0 : 0xFF2B3042;
            roundedRect(g, getX(), getY(), width, height, height / 2, fill);
            g.drawCenteredString(Minecraft.getInstance().font, getMessage(), getX() + width / 2, getY() + (height - 8) / 2, 0xFFFFFFFF);
        }

        /** Rectangle plein dont les coins sont des quarts de cercle de rayon r. */
        static void roundedRect(GuiGraphics g, int x, int y, int w, int h, int r, int color) {
            r = Math.min(r, Math.min(w, h) / 2);
            for (int row = 0; row < h; row++) {
                int dist = row < r ? r - row : row >= h - r ? row - (h - r) + 1 : 0;
                int inset = 0;
                if (dist > 0) inset = r - (int) Math.round(Math.sqrt((double) r * r - (double) (dist - 0.5) * (dist - 0.5)));
                g.fill(x + inset, y + row, x + w - inset, y + row + 1, color);
            }
        }
    }
}
