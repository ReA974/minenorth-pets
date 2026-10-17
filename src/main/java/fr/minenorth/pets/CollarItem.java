package fr.minenorth.pets;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.List;

/** Collier d'identification : clic droit sur son animal apprivoisé pour le poser (le collier est consommé, l'animal l'emporte). */
public class CollarItem extends Item {
    public CollarItem() { super(new Item.Properties().stacksTo(16)); }

    @Override
    public void appendHoverText(ItemStack st, @Nullable Level level, List<Component> tip, TooltipFlag flag) {
        tip.add(Component.literal("§8Clic droit sur son animal : enregistre le nom du maître"));
        tip.add(Component.literal("§8Accroupi, main nue, sur un animal à collier : lire son maître"));
    }
}
