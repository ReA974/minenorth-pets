package fr.minenorth.pets;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Carnet de santé de poche, remis au maître quand il pose le collier. Il porte l'UUID de l'animal et un instantané lisible ; sa validité est
 * toujours vérifiée côté serveur (LostPets : maître + numéro de carnet), jamais d'après son seul NBT. Clic droit : fiche, « Signaler perdu / retrouvé »,
 * « Abandonner l'animal ».
 */
public class HealthBookItem extends Item {
    public HealthBookItem() { super(new Item.Properties().stacksTo(1)); }

    @Override
    public Component getName(ItemStack st) {
        Component base = Component.translatable(getDescriptionId());
        String species = st.hasTag() ? st.getTag().getString(PetCarnet.SPECIES) : "";
        return species.isEmpty() ? base : Component.empty().append(base).append(" : " + species);
    }

    @Override
    public void appendHoverText(ItemStack st, @Nullable Level level, List<Component> tip, TooltipFlag flag) {
        tip.add(Component.literal("§8Clic droit : fiche de l'animal, signaler perdu, abandonner"));
        if (st.hasTag() && st.getTag().getBoolean(PetCarnet.LOST)) tip.add(Component.literal("§cAnimal signalé perdu"));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack st = player.getItemInHand(hand);
        if (!level.isClientSide && player instanceof ServerPlayer p) PetCarnet.open(p, st);
        return InteractionResultHolder.sidedSuccess(st, level.isClientSide());
    }
}
