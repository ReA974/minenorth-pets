package fr.minenorth.pets;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Chien K9 : toutes les quelques secondes il flaire les joueurs proches ; s'il en trouve un qui porte de la drogue (liste de la config),
 * il va vers lui, grogne, et prévient son maître.
 */
public class K9SniffGoal extends Goal {
    private final TamableAnimal pet;
    /** Joueur -> tick de jeu avant lequel on ne le signale plus (évite le spam). */
    private final Map<UUID, Long> quiet = new HashMap<>();
    private ServerPlayer suspect;
    private int nextScan;
    private int endTick;

    public K9SniffGoal(TamableAnimal pet) {
        this.pet = pet;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (!(pet.level() instanceof ServerLevel level) || !PetEvents.isK9(pet) || !pet.isTame() || pet.isOrderedToSit() || pet.isPassenger()) return false;
        if (pet.tickCount < nextScan) return false;
        nextScan = pet.tickCount + PetsConfig.k9Interval();
        long now = level.getGameTime();
        double r = PetsConfig.k9Radius();
        ServerPlayer best = null;
        double bestDist = Double.MAX_VALUE;
        for (ServerPlayer p : level.getEntitiesOfClass(ServerPlayer.class, pet.getBoundingBox().inflate(r))) {
            if (p.isSpectator() || pet.isOwnedBy(p) || quiet.getOrDefault(p.getUUID(), 0L) > now) continue;
            double d = pet.distanceToSqr(p);
            if (d > r * r || d >= bestDist) continue;
            if (PetsConfig.k9IgnorePolice() && Compat.isPolice(p)) continue;
            if (countDrugs(p) == 0) continue;
            best = p;
            bestDist = d;
        }
        suspect = best;
        return best != null;
    }

    @Override
    public boolean canContinueToUse() {
        double r = PetsConfig.k9Radius() + 4;
        return suspect != null && suspect.isAlive() && !suspect.isRemoved() && !pet.isOrderedToSit()
                && pet.tickCount < endTick && pet.distanceToSqr(suspect) < r * r;
    }

    @Override
    public void start() {
        endTick = pet.tickCount + 100;
        ServerLevel level = (ServerLevel) pet.level();
        quiet.put(suspect.getUUID(), level.getGameTime() + PetsConfig.k9CooldownSeconds() * 20L);
        int n = countDrugs(suspect);
        growl();
        level.sendParticles(ParticleTypes.ANGRY_VILLAGER, suspect.getX(), suspect.getY() + 2.2, suspect.getZ(), 3, 0.2, 0.1, 0.2, 0);
        LivingEntity owner = pet.getOwner();
        if (owner instanceof ServerPlayer op) {
            op.displayClientMessage(Component.literal("§9§lK9 §r§c: drogue flairée sur §f" + suspect.getGameProfile().getName() + "§c !"), true);
            op.sendSystemMessage(Component.literal("§9[K9] §fVotre chien signale §c" + n + " objet(s) suspect(s) §fsur §e"
                    + suspect.getGameProfile().getName() + "§f."));
        }
        suspect.sendSystemMessage(Component.literal("§7Le chien policier aboie dans votre direction."));
    }

    @Override
    public void tick() {
        pet.getLookControl().setLookAt(suspect, 30f, 30f);
        if (pet.distanceToSqr(suspect) > 2.5 * 2.5) {
            if (pet.tickCount % 10 == 0) pet.getNavigation().moveTo(suspect, 1.3);
        } else {
            pet.getNavigation().stop();
            if (pet.tickCount % 25 == 0) growl();
        }
    }

    @Override
    public void stop() {
        suspect = null;
        pet.getNavigation().stop();
    }

    private void growl() {
        pet.level().playSound(null, pet.getX(), pet.getY(), pet.getZ(), SoundEvents.WOLF_GROWL, SoundSource.NEUTRAL, 1.0f, 1.0f);
    }

    /** Nombre d'objets de la liste flairée dans tout l'inventaire du joueur (sac, armure, main gauche). */
    private static int countDrugs(ServerPlayer p) {
        List<String> ids = PetsConfig.k9Items();
        int n = 0;
        for (List<ItemStack> part : List.of(p.getInventory().items, p.getInventory().armor, p.getInventory().offhand)) {
            for (ItemStack st : part) {
                if (st.isEmpty()) continue;
                var key = ForgeRegistries.ITEMS.getKey(st.getItem());
                if (key != null && ids.contains(key.toString())) n += st.getCount();
            }
        }
        return n;
    }
}
