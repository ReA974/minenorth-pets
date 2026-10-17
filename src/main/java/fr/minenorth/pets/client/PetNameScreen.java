package fr.minenorth.pets.client;

import fr.minenorth.pets.K9Net;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Saisie du nom de l'animal depuis son carnet (24 caractères, le serveur nettoie et revérifie). */
public class PetNameScreen extends Screen {
    private static final int W = 220, H = 100;

    private EditBox box;

    public PetNameScreen() {
        super(Component.translatable("gui.minenorthpets.name.title"));
    }

    @Override
    protected void init() {
        int x = (width - W) / 2, y = (height - H) / 2;
        box = new EditBox(font, x + 14, y + 34, W - 28, 20, Component.translatable("gui.minenorthpets.name.title"));
        box.setMaxLength(24);
        box.setHint(Component.translatable("gui.minenorthpets.name.hint"));
        addRenderableWidget(box);
        setInitialFocus(box);
        addRenderableWidget(new PetMenuScreen.RoundButton(x + 14, y + H - 14 - 22, 96, 22, Component.translatable("gui.minenorthpets.name.ok"), b -> send()));
        addRenderableWidget(new PetMenuScreen.RoundButton(x + W - 14 - 96, y + H - 14 - 22, 96, 22, Component.translatable("gui.minenorthpets.name.cancel"), b -> onClose()));
    }

    private void send() {
        K9Net.CHANNEL.sendToServer(new K9Net.BookRename(box.getValue().trim()));
        onClose();
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (key == 257 || key == 335) { // Entrée
            send();
            return true;
        }
        return super.keyPressed(key, scan, mods);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        int x = (width - W) / 2, y = (height - H) / 2;
        PetMenuScreen.RoundButton.roundedRect(g, x - 1, y - 1, W + 2, H + 2, 14, 0x66FFFFFF);
        PetMenuScreen.RoundButton.roundedRect(g, x, y, W, H, 13, 0xF0181B26);
        g.drawCenteredString(font, title, width / 2, y + 14, 0xFFFFFFFF);
        super.render(g, mouseX, mouseY, partialTick);
    }

    @Override
    public void renderBackground(GuiGraphics g) {
        g.fill(0, 0, width, height, 0x55000000);
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
