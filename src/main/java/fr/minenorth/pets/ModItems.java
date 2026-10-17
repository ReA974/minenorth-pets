package fr.minenorth.pets;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import javax.annotation.Nullable;
import java.util.List;
import java.util.TreeSet;

public final class ModItems {
    private ModItems() {}

    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, MineNorthPets.MOD_ID);

    /** Clic droit sur son chien apprivoisé : l'équipe (ou le déséquipe) en chien K9. Réutilisable. */
    public static final RegistryObject<Item> K9_HARNESS = ITEMS.register("harnais_k9", () -> new Item(new Item.Properties().stacksTo(1)) {
        @Override
        public void appendHoverText(ItemStack st, @Nullable Level level, List<Component> tip, TooltipFlag flag) {
            tip.add(Component.literal("§8Clic droit sur son chien apprivoisé : chien policier K9"));
            tip.add(Component.literal("§8Le K9 flaire la drogue sur les personnes proches"));
        }
    });

    /** Collier d'identification : enregistre le nom du maître sur l'animal. */
    public static final RegistryObject<Item> COLLAR = ITEMS.register("collier_identification", CollarItem::new);

    /** Carnet de santé de poche, remis au maître à la pose du collier. */
    public static final RegistryObject<Item> CARNET = ITEMS.register("carnet_sante", HealthBookItem::new);

    /** Certificat d'adoption : l'espèce est dans le NBT. */
    public static final RegistryObject<Item> ADOPTION = ITEMS.register("certificat_adoption", AdoptionItem::new);

    /** Espèces de Polly Pets (celles qui ont un oeuf d'apparition), adultes seulement. */
    public static List<ResourceLocation> speciesIds() {
        TreeSet<ResourceLocation> out = new TreeSet<>();
        for (Item it : ForgeRegistries.ITEMS) {
            if (!(it instanceof SpawnEggItem egg)) continue;
            ResourceLocation k = ForgeRegistries.ENTITY_TYPES.getKey(egg.getType(null));
            if (k == null || !k.getNamespace().equals("polly_pets")) continue;
            String path = k.getPath();
            if (path.contains("puppy") || path.contains("pupy") || path.contains("baby") || path.contains("kitten")) continue;
            out.add(k);
        }
        out.add(AdoptionItem.GABON);
        return List.copyOf(out);
    }

    /**
     * Onglet « Animaux & Vêtements » (minenorthpets:animaux_vetements). Le mod minenorth-vetements y ajoute ses objets lui-même, via
     * BuildCreativeModeTabContentsEvent sur cette clé.
     */
    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MineNorthPets.MOD_ID);
    public static final RegistryObject<CreativeModeTab> TAB = TABS.register("animaux_vetements", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.minenorthpets.animaux_vetements"))
            .icon(() -> new ItemStack(ADOPTION.get()))
            .displayItems(ModItems.TAB_ITEMS)
            .build());

    public static final CreativeModeTab.DisplayItemsGenerator TAB_ITEMS = (params, out) -> {
        out.accept(K9_HARNESS.get());
        out.accept(COLLAR.get());
        out.accept(CARNET.get());
        for (ResourceLocation id : speciesIds()) out.accept(AdoptionItem.of(id));
    };

    public static void register(IEventBus modBus) {
        ITEMS.register(modBus);
        TABS.register(modBus);
    }
}
