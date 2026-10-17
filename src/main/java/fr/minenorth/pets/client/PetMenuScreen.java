package fr.minenorth.pets.client;

import fr.minenorth.pets.K9Net;
import fr.minenorth.pets.PetEvents;
import fr.minenorth.pets.PetActions;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;

import java.util.ArrayList;
import java.util.List;

/**
 * Menu d'actions d'un animal apprivoisé (Ctrl + clic droit). Panneau et boutons très arrondis, fond sombre translucide.
 * Les boutons dépendent des droits envoyés par le serveur : le maître a les actions de compagnie (et « Signaler perdu » s'il y a un collier),
 * le vétérinaire a les soins, tout le monde peut lire le carnet de santé. Plus de 6 boutons : deux colonnes.
 */
public class PetMenuScreen extends Screen {
    private static final int BUTTON_H = 24, GAP = 6, PAD = 14, TITLE_H = 22, MAX_ROWS = 6;

    /** Un bouton : libellé, action serveur (ou null pour ouvrir l'écran de remarque). */
    private record Entry(String labelKey, PetActions.Action action) {}

    private final int entityId;
    private final int flags;
    private final List<Entry> entries = new ArrayList<>();

    private PetMenuScreen(int entityId, int flags) {
        super(Component.translatable("gui.minenorthpets.menu.title"));
        this.entityId = entityId;
        this.flags = flags;
        build();
    }

    public static void open(int entityId, int flags) {
        Minecraft.getInstance().setScreen(new PetMenuScreen(entityId, flags));
    }

    private boolean has(int flag) { return (flags & flag) != 0; }

    private void build() {
        boolean unconscious = has(PetActions.FLAG_UNCONSCIOUS);
        if (has(PetActions.FLAG_OWNER) && has(PetActions.FLAG_PARROT)) {
            // Perroquet : caresser, assis / debout, parler, signalement ; pas de jeu, sommeil, combat ni portage.
            if (!unconscious) {
                entries.add(new Entry("gui.minenorthpets.menu.pet", PetActions.Action.PET));
                entries.add(new Entry(has(PetActions.FLAG_SITTING) ? "gui.minenorthpets.menu.stand" : "gui.minenorthpets.menu.sit", PetActions.Action.SIT));
                entries.add(new Entry("gui.minenorthpets.menu.talk", PetActions.Action.TALK));
                if (has(PetActions.FLAG_COLLAR)) {
                    entries.add(new Entry(has(PetActions.FLAG_LOST) ? "gui.minenorthpets.menu.found" : "gui.minenorthpets.menu.lost", PetActions.Action.LOST));
                }
            }
        } else if (has(PetActions.FLAG_OWNER) && unconscious) {
            // Inconscient : le maître ne peut que le porter (ou le poser) jusqu'au vétérinaire.
            entries.add(carryEntry());
        } else if (has(PetActions.FLAG_OWNER)) {
            boolean sitting = has(PetActions.FLAG_SITTING) && !has(PetActions.FLAG_SLEEPING);
            entries.add(new Entry("gui.minenorthpets.menu.pet", PetActions.Action.PET));
            entries.add(new Entry("gui.minenorthpets.menu.play", PetActions.Action.PLAY));
            entries.add(new Entry(has(PetActions.FLAG_SLEEPING) ? "gui.minenorthpets.menu.wake" : "gui.minenorthpets.menu.sleep", PetActions.Action.SLEEP));
            entries.add(new Entry(sitting ? "gui.minenorthpets.menu.stand" : "gui.minenorthpets.menu.sit", PetActions.Action.SIT));
            entries.add(new Entry(has(PetActions.FLAG_STAYING) ? "gui.minenorthpets.menu.follow" : "gui.minenorthpets.menu.stay", PetActions.Action.STAY));
            if (has(PetActions.FLAG_COLLAR)) {
                entries.add(new Entry(has(PetActions.FLAG_LOST) ? "gui.minenorthpets.menu.found" : "gui.minenorthpets.menu.lost", PetActions.Action.LOST));
            }
            if (has(PetActions.FLAG_FIGHTING)) entries.add(new Entry("gui.minenorthpets.menu.separate", PetActions.Action.SEPARATE));
            else if (has(PetActions.FLAG_CHALLENGED)) entries.add(new Entry("gui.minenorthpets.menu.accept", PetActions.Action.ACCEPT));
            else entries.add(new Entry("gui.minenorthpets.menu.challenge", PetActions.Action.CHALLENGE));
            entries.add(carryEntry());
        }
        entries.add(new Entry("gui.minenorthpets.menu.book", PetActions.Action.BOOK));
        if (has(PetActions.FLAG_VET)) {
            entries.add(new Entry("gui.minenorthpets.menu.vaccine", PetActions.Action.VACCINE));
            entries.add(new Entry("gui.minenorthpets.menu.heal", PetActions.Action.HEAL));
            entries.add(new Entry("gui.minenorthpets.menu.remark", null));
            entries.add(new Entry("gui.minenorthpets.menu.chip", PetActions.Action.CHIP));
            if (unconscious) entries.add(new Entry("gui.minenorthpets.menu.revive", PetActions.Action.REVIVE));
        }
    }

    private Entry carryEntry() {
        return has(PetActions.FLAG_CARRIED) ? new Entry("gui.minenorthpets.menu.drop", PetActions.Action.DROP)
                : new Entry("gui.minenorthpets.menu.carry", PetActions.Action.CARRY);
    }

    private int buttonWidth() { return cols() == 3 ? 130 : 150; }

    private int cols() { return entries.size() > MAX_ROWS * 2 ? 3 : entries.size() > MAX_ROWS ? 2 : 1; }
    private int rows() { return (entries.size() + cols() - 1) / cols(); }
    private int panelWidth() { return cols() * buttonWidth() + (cols() - 1) * GAP + PAD * 2; }
    private int panelHeight() { return TITLE_H + PAD * 2 + rows() * BUTTON_H + (rows() - 1) * GAP; }
    private int panelTop() { return (height - panelHeight()) / 2; }

    private Entity pet() { return Minecraft.getInstance().level == null ? null : Minecraft.getInstance().level.getEntity(entityId); }

    @Override
    protected void init() {
        int left = (width - panelWidth()) / 2 + PAD;
        int top = panelTop() + TITLE_H + PAD;
        for (int i = 0; i < entries.size(); i++) {
            Entry e = entries.get(i);
            int col = i / rows(), row = i % rows();
            addRenderableWidget(new RoundButton(left + col * (buttonWidth() + GAP), top + row * (BUTTON_H + GAP), buttonWidth(), BUTTON_H,
                    Component.translatable(e.labelKey()), b -> {
                if (e.action() == null) {
                    Minecraft.getInstance().setScreen(new PetRemarkScreen(entityId));
                    return;
                }
                K9Net.CHANNEL.sendToServer(new K9Net.Action(entityId, e.action().ordinal()));
                onClose();
            }));
        }
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        int w = panelWidth(), x = (width - w) / 2, y = panelTop();
        RoundButton.roundedRect(g, x - 1, y - 1, w + 2, panelHeight() + 2, 14, 0x66FFFFFF);
        RoundButton.roundedRect(g, x, y, w, panelHeight(), 13, 0xE0181B26);
        Entity pet = pet();
        Component name = pet != null && pet.hasCustomName() ? pet.getCustomName() : pet != null ? Component.literal(PetEvents.speciesName(pet)) : title;
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
