package fr.minenorth.pets.client;

import fr.minenorth.pets.MineNorthPets;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.entity.EntityLeaveLevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashSet;
import java.util.Set;

/** États d'animaux connus du client (remplis par K9Net) : ids d'entités K9, et ids d'entités couchées. */
@Mod.EventBusSubscriber(modid = MineNorthPets.MOD_ID, value = Dist.CLIENT)
public final class ClientK9 {
    private ClientK9() {}

    private static final Set<Integer> IDS = new HashSet<>();
    private static final Set<Integer> SLEEPING = new HashSet<>();
    private static boolean carrying;

    public static void set(int entityId, boolean k9) {
        PetRenderHooks.LOGGER.info("[MineNorthPets] statut K9 reçu : entité {} -> {}", entityId, k9);
        if (k9) IDS.add(entityId); else IDS.remove(entityId);
    }

    public static void setSleeping(int entityId, boolean sleeping) {
        if (sleeping) SLEEPING.add(entityId); else SLEEPING.remove(entityId);
    }

    public static void setCarrying(boolean value) { carrying = value; }

    public static boolean isCarrying() { return carrying; }

    public static boolean has(int entityId) { return IDS.contains(entityId); }

    public static boolean isSleeping(int entityId) { return SLEEPING.contains(entityId); }

    /** Un id d'entité peut être réutilisé : on l'oublie quand l'entité quitte le monde. */
    @SubscribeEvent
    public static void onLeave(EntityLeaveLevelEvent ev) {
        if (ev.getLevel().isClientSide()) {
            IDS.remove(ev.getEntity().getId());
            SLEEPING.remove(ev.getEntity().getId());
        }
    }
}
