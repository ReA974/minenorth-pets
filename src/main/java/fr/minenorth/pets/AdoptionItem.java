package fr.minenorth.pets;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraftforge.registries.ForgeRegistries;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Certificat d'adoption : porte l'espèce dans son NBT (« pet »). Clic droit sur un bloc = l'animal apparaît déjà apprivoisé, à son acheteur.
 * Les boutiques (minenorth_shops) conservent le NBT : on vend un animal en y mettant ce certificat.
 */
public class AdoptionItem extends Item {
    public static final String TAG = "pet";
    /** Espèce virtuelle : un perroquet vanilla de variante grise. */
    public static final ResourceLocation GABON = new ResourceLocation(MineNorthPets.MOD_ID, "gris_du_gabon");

    public AdoptionItem() { super(new Item.Properties().stacksTo(1)); }

    public static ItemStack of(ResourceLocation entityId) {
        ItemStack st = new ItemStack(ModItems.ADOPTION.get());
        st.getOrCreateTag().putString(TAG, entityId.toString());
        return st;
    }

    @Nullable
    private static ResourceLocation speciesId(ItemStack st) {
        if (!st.hasTag() || !st.getTag().contains(TAG)) return null;
        return ResourceLocation.tryParse(st.getTag().getString(TAG));
    }

    /** Type d'entité à faire apparaître : le Gris du Gabon est un perroquet (variante grise appliquée à l'apparition). */
    @Nullable
    private static EntityType<?> species(ItemStack st) {
        ResourceLocation id = speciesId(st);
        if (id == null) return null;
        if (id.equals(GABON)) return EntityType.PARROT;
        // Sécurité : le NBT d'un certificat forgé ne doit pas pouvoir faire apparaître un wither, un dragon, un golem...
        if (!"polly_pets".equals(id.getNamespace()) && !EntityType.getKey(EntityType.PARROT).equals(id)) return null;
        return ForgeRegistries.ENTITY_TYPES.containsKey(id) ? ForgeRegistries.ENTITY_TYPES.getValue(id) : null;
    }

    @Override
    public Component getName(ItemStack st) {
        Component base = Component.translatable(getDescriptionId());
        ResourceLocation id = speciesId(st);
        if (id != null && id.equals(GABON)) return Component.empty().append(base).append(" : ").append(Component.translatable("species.minenorthpets.gris_du_gabon"));
        EntityType<?> t = species(st);
        return t == null ? base : Component.empty().append(base).append(" : ").append(t.getDescription());
    }

    @Override
    public void appendHoverText(ItemStack st, @Nullable Level level, List<Component> tip, TooltipFlag flag) {
        tip.add(Component.literal("§8Clic droit sur un bloc : l'animal apparaît, déjà apprivoisé"));
    }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        Level level = ctx.getLevel();
        if (level.isClientSide) return InteractionResult.SUCCESS;
        ItemStack st = ctx.getItemInHand();
        EntityType<?> type = species(st);
        if (type == null || !(level instanceof ServerLevel sl) || !(ctx.getPlayer() instanceof ServerPlayer p)) return InteractionResult.FAIL;
        BlockPos pos = ctx.getClickedPos().relative(ctx.getClickedFace());
        var e = type.spawn(sl, ItemStack.EMPTY, p, pos, MobSpawnType.SPAWN_EGG, true, false);
        if (e == null) return InteractionResult.FAIL;
        if (!(e instanceof TamableAnimal)) {   // jamais autre chose qu'un animal apprivoisable ; on ne consomme pas le certificat
            e.discard();
            return InteractionResult.FAIL;
        }
        if (e instanceof net.minecraft.world.entity.animal.Parrot parrot && GABON.equals(speciesId(st))) parrot.setVariant(net.minecraft.world.entity.animal.Parrot.Variant.GRAY);
        if (e instanceof TamableAnimal pet) {
            pet.tame(p);
            PetBook.ensure(pet);
            pet.setOrderedToSit(false);
            pet.setInSittingPose(false);
            level.broadcastEntityEvent(pet, (byte) 7);
        }
        p.displayClientMessage(Component.literal("§aVotre animal est à vous !"), true);
        if (!p.getAbilities().instabuild) st.shrink(1);
        return InteractionResult.CONSUME;
    }
}
