package fr.minenorth.pets.client;

import fr.minenorth.pets.K9Net;
import fr.minenorth.pets.MineNorthPets;
import fr.minenorth.pets.ModParticles;
import fr.minenorth.pets.PetEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.RegisterParticleProvidersEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Ctrl + clic droit sur son animal apprivoisé : demande l'ouverture du menu (le serveur répond avec les états et les droits : maître, vétérinaire, police).
 * Annuler PlayerInteractEvent.EntityInteract côté client ne suffit pas : MultiPlayerGameMode.interact envoie déjà le paquet d'interaction
 * au serveur avant de lancer cet événement (le serveur nourrissait / asseyait donc l'animal en plus du menu). On coupe donc plus tôt, au niveau de la
 * touche « utiliser » (InputEvent.InteractionKeyMappingTriggered) : rien n'est envoyé, l'objet tenu n'est pas utilisé non plus.
 */
public final class ClientPetEvents {
    private ClientPetEvents() {}

    @Mod.EventBusSubscriber(modid = MineNorthPets.MOD_ID, value = Dist.CLIENT)
    public static final class Forge {
        @SubscribeEvent(priority = EventPriority.HIGHEST)
        public static void onUseKey(InputEvent.InteractionKeyMappingTriggered ev) {
            if (!ev.isUseItem() || !Screen.hasControlDown()) return;
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null || mc.screen != null || !(mc.hitResult instanceof EntityHitResult hit)) return;
            if (!(hit.getEntity() instanceof TamableAnimal pet) || !PetEvents.isPollyPet(pet) || !pet.isTame()) return;
            ev.setCanceled(true);
            ev.setSwingHand(false);
            K9Net.CHANNEL.sendToServer(new K9Net.OpenRequest(pet.getId()));
        }
    }

    @Mod.EventBusSubscriber(modid = MineNorthPets.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class ModBus {
        @SubscribeEvent
        public static void particles(RegisterParticleProvidersEvent ev) {
            ev.registerSpriteSet(ModParticles.SLEEP_Z.get(), SleepZParticle.Provider::new);
        }
    }
}
