package fr.minenorth.pets;

import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Porter son animal. L'animal n'est pas passager du joueur (un passager de joueur serait posé à hauteur de la tête et boucherait la vue) :
 * à chaque tick il est replacé dans les bras du joueur (devant, à droite, à hauteur de poitrine), IA et gravité coupées, assis.
 * Le porteur est ralenti (config). On le pose avec le menu (Ctrl + clic droit, même dans le vide) ou /pets poser.
 * Pose automatique : déconnexion, mort du porteur, changement de dimension, entrée dans un véhicule MTS.
 * Rien n'est gardé sur disque, mais l'étiquette « porté » l'est : à son chargement, un animal marqué porté sans porteur est libéré (PetEvents.onJoin).
 */
@Mod.EventBusSubscriber(modid = MineNorthPets.MOD_ID)
public final class PetCarry {
    private PetCarry() {}

    private static final String TAG = "minenorthpets_carried";
    private static final Map<UUID, UUID> BY_PLAYER = new HashMap<>();

    public static boolean isCarried(Entity pet) { return pet.getPersistentData().getBoolean(TAG) && BY_PLAYER.containsValue(pet.getUUID()); }

    /** L'animal porté par ce joueur, ou null. */
    public static TamableAnimal carriedBy(ServerPlayer p) {
        UUID id = BY_PLAYER.get(p.getUUID());
        return id == null ? null : find(p.server, id);
    }

    private static TamableAnimal find(MinecraftServer server, UUID id) {
        for (ServerLevel l : server.getAllLevels()) {
            if (l.getEntity(id) instanceof TamableAnimal t && t.isAlive()) return t;
        }
        return null;
    }

    private static void bar(ServerPlayer p, String text) { p.displayClientMessage(Component.literal(text), true); }

    public static void carry(ServerPlayer p, TamableAnimal pet) {
        if (!PetsConfig.carryEnabled()) { bar(p, "§7Le portage d'animaux est désactivé."); return; }
        if (BY_PLAYER.containsKey(p.getUUID())) { bar(p, "§7Vous portez déjà un animal."); return; }
        if (PetFight.isFighting(pet)) { bar(p, "§7Impossible : votre animal est en plein combat."); return; }
        if (pet.isPassenger() || isCarried(pet)) { bar(p, "§7Impossible de porter cet animal pour le moment."); return; }
        if (MtsRide.loaded() && MtsRide.seatOf(p) != null) { bar(p, "§7Impossible de porter un animal dans un véhicule."); return; }
        if (PetActions.isSleeping(pet) && !PetKo.isUnconscious(pet)) PetActions.wake(pet);
        BY_PLAYER.put(p.getUUID(), pet.getUUID());
        pet.getPersistentData().putBoolean(TAG, true);
        pet.getNavigation().stop();
        pet.setNoAi(true);
        pet.setNoGravity(true);
        pet.setInSittingPose(true);
        K9Net.sendCarry(p, true);
        bar(p, "§aVous portez votre animal. Ctrl + clic droit ou /pets poser pour le poser.");
        place(p, pet);
    }

    /** L'animal n'est plus à porter (abandon, perte du maître) : on oublie le portage sans le replacer. */
    public static void forget(TamableAnimal pet) {
        for (Map.Entry<UUID, UUID> en : new ArrayList<>(BY_PLAYER.entrySet())) {
            if (!en.getValue().equals(pet.getUUID())) continue;
            BY_PLAYER.remove(en.getKey());
            if (pet.level().getServer() != null) {
                ServerPlayer p = pet.level().getServer().getPlayerList().getPlayer(en.getKey());
                if (p != null) K9Net.sendCarry(p, false);
            }
        }
        if (pet.getPersistentData().getBoolean(TAG)) release(pet);
    }

    /** Pose l'animal devant le joueur. Renvoie faux si le joueur ne portait rien. */
    public static boolean drop(ServerPlayer p, String message) {
        UUID id = BY_PLAYER.remove(p.getUUID());
        if (id == null) return false;
        K9Net.sendCarry(p, false);
        TamableAnimal pet = find(p.server, id);
        if (pet != null) {
            release(pet);
            Vec3 look = p.getLookAngle().multiply(1, 0, 1);
            Vec3 ahead = look.lengthSqr() < 1.0e-4 ? Vec3.ZERO : look.normalize().scale(0.8);
            if (pet.level() == p.level()) pet.moveTo(p.getX() + ahead.x, p.getY(), p.getZ() + ahead.z, p.getYRot(), 0);
        }
        if (message != null) bar(p, message);
        return true;
    }

    /** Remet l'animal en état normal (un animal inconscient reste sans IA, couché et invulnérable). */
    public static void release(TamableAnimal pet) {
        pet.getPersistentData().remove(TAG);
        pet.setNoGravity(false);
        pet.resetFallDistance();
        if (!PetKo.isUnconscious(pet)) {
            pet.setNoAi(false);
            PetActions.setSitting(pet, false);
        }
    }

    private static void place(ServerPlayer p, TamableAnimal pet) {
        Vec3 look = p.getLookAngle().multiply(1, 0, 1);
        look = look.lengthSqr() < 1.0e-4 ? Vec3.ZERO : look.normalize();
        Vec3 right = new Vec3(-look.z, 0, look.x);
        double x = p.getX() + look.x * 0.55 + right.x * 0.35;
        double z = p.getZ() + look.z * 0.55 + right.z * 0.35;
        pet.moveTo(x, p.getY() + 0.65, z, p.getYRot(), 0);
        pet.yBodyRot = p.getYRot();
        pet.yHeadRot = p.getYRot();
        pet.setDeltaMovement(Vec3.ZERO);
        pet.resetFallDistance();
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent ev) {
        if (ev.phase != TickEvent.Phase.END || BY_PLAYER.isEmpty()) return;
        MinecraftServer server = ev.getServer();
        boolean slow = server.getTickCount() % 20 == 0;
        for (Map.Entry<UUID, UUID> en : new ArrayList<>(BY_PLAYER.entrySet())) {
            ServerPlayer p = server.getPlayerList().getPlayer(en.getKey());
            TamableAnimal pet = find(server, en.getValue());
            if (p == null || pet == null || pet.level() != p.level() || !p.isAlive()) {
                BY_PLAYER.remove(en.getKey());
                if (pet != null) release(pet);
                if (p != null) K9Net.sendCarry(p, false);
                continue;
            }
            if (MtsRide.loaded() && MtsRide.seatOf(p) != null) {
                drop(p, "§7Vous posez votre animal avant de monter dans le véhicule.");
                continue;
            }
            place(p, pet);
            int level = PetsConfig.carrySlowness();
            if (slow && level > 0) p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, level - 1, true, false, true));
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent ev) {
        if (ev.getEntity() instanceof ServerPlayer p) drop(p, null);
    }

    @SubscribeEvent
    public static void onPlayerDeath(LivingDeathEvent ev) {
        if (ev.getEntity() instanceof ServerPlayer p) drop(p, null);
    }
}
