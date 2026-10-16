package fr.minenorth.pets;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * Actions du menu d'un animal apprivoisé (Ctrl + clic droit). Tout est vérifié côté serveur : le joueur doit être le maître et être à portée.
 * États gardés dans les données persistantes de l'animal : « minenorthpets_sleep » (couché), « minenorthpets_stay » (reste sur place).
 */
public final class PetActions {
    private PetActions() {}

    public static final String SLEEP_TAG = "minenorthpets_sleep";
    public static final String STAY_TAG = "minenorthpets_stay";
    private static final String COOLDOWN_TAG = "minenorthpets_cd";
    private static final int COOLDOWN_TICKS = 100;
    private static final double REACH = 8.0;

    public static final int FLAG_SITTING = 1, FLAG_SLEEPING = 2, FLAG_STAYING = 4;

    public enum Action { PET, PLAY, SLEEP, SIT, STAY }

    public static boolean isSleeping(Entity e) { return e.getPersistentData().getBoolean(SLEEP_TAG); }
    public static boolean isStaying(Entity e) { return e.getPersistentData().getBoolean(STAY_TAG); }

    public static int flags(TamableAnimal pet) {
        return (pet.isOrderedToSit() ? FLAG_SITTING : 0) | (isSleeping(pet) ? FLAG_SLEEPING : 0) | (isStaying(pet) ? FLAG_STAYING : 0);
    }

    /** L'animal visé si le joueur en est le maître et qu'il est à portée, sinon null. */
    public static TamableAnimal target(ServerPlayer p, int entityId) {
        Entity e = p.level().getEntity(entityId);
        if (!(e instanceof TamableAnimal pet) || !PetEvents.isPollyPet(pet) || !pet.isTame() || !pet.isOwnedBy(p)) return null;
        return p.distanceToSqr(pet) <= REACH * REACH ? pet : null;
    }

    public static void setSitting(TamableAnimal pet, boolean sit) {
        pet.setOrderedToSit(sit);
        pet.setInSittingPose(sit);
        pet.setJumping(false);
        pet.getNavigation().stop();
        pet.setTarget(null);
    }

    public static void sleep(TamableAnimal pet) {
        pet.getPersistentData().putBoolean(SLEEP_TAG, true);
        setSitting(pet, true);
        K9Net.broadcastSleep(pet, true);
    }

    /** Réveille et remet debout. */
    public static void wake(TamableAnimal pet) {
        pet.getPersistentData().remove(SLEEP_TAG);
        setSitting(pet, false);
        K9Net.broadcastSleep(pet, false);
    }

    public static void sleepParticles(TamableAnimal pet) {
        if (pet.level() instanceof ServerLevel sl) {
            sl.sendParticles(ModParticles.SLEEP_Z.get(), pet.getX(), pet.getY() + pet.getBbHeight() + 0.2, pet.getZ(), 1, 0.15, 0.05, 0.15, 0.0);
        }
    }

    public static void perform(ServerPlayer p, TamableAnimal pet, Action action) {
        switch (action) {
            case PET -> pet(p, pet);
            case PLAY -> play(p, pet);
            case SLEEP -> {
                if (isSleeping(pet)) {
                    wake(pet);
                    msg(p, "message.minenorthpets.wake");
                } else {
                    sleep(pet);
                    msg(p, "message.minenorthpets.sleep");
                }
            }
            case SIT -> PetEvents.toggleSit(p, pet);
            case STAY -> {
                boolean stay = !isStaying(pet);
                if (stay) pet.getPersistentData().putBoolean(STAY_TAG, true); else pet.getPersistentData().remove(STAY_TAG);
                pet.getNavigation().stop();
                msg(p, stay ? "message.minenorthpets.stay" : "message.minenorthpets.follow");
            }
        }
    }

    private static boolean cooldownReady(ServerPlayer p, TamableAnimal pet) {
        long now = pet.level().getGameTime();
        if (pet.getPersistentData().getLong(COOLDOWN_TAG) > now) {
            msg(p, "message.minenorthpets.rest");
            return false;
        }
        pet.getPersistentData().putLong(COOLDOWN_TAG, now + COOLDOWN_TICKS);
        return true;
    }

    private static void pet(ServerPlayer p, TamableAnimal pet) {
        if (!cooldownReady(p, pet)) return;
        pet.heal(2.0f);
        if (pet.level() instanceof ServerLevel sl) {
            sl.sendParticles(ParticleTypes.HEART, pet.getX(), pet.getY() + pet.getBbHeight() + 0.2, pet.getZ(), 6, 0.3, 0.2, 0.3, 0.02);
        }
        sound(pet, happySound(pet), 0.7f);
        msg(p, "message.minenorthpets.pet");
    }

    private static void play(ServerPlayer p, TamableAnimal pet) {
        if (!cooldownReady(p, pet)) return;
        if (isSleeping(pet)) wake(pet); else if (pet.isOrderedToSit()) setSitting(pet, false);
        Vec3 v = pet.getDeltaMovement();
        pet.setDeltaMovement(v.x, 0.42, v.z);
        pet.hasImpulse = true;
        if (pet.level() instanceof ServerLevel sl) {
            sl.sendParticles(ParticleTypes.NOTE, pet.getX(), pet.getY() + pet.getBbHeight() + 0.3, pet.getZ(), 4, 0.3, 0.2, 0.3, 1.0);
        }
        sound(pet, happySound(pet), 1.0f);
        msg(p, "message.minenorthpets.play");
    }

    private static final java.util.List<String> CATS = java.util.List.of("kitty", "kitten", "calico", "bombay", "british", "khao");

    private static SoundEvent happySound(TamableAnimal pet) {
        ResourceLocation k = ForgeRegistries.ENTITY_TYPES.getKey(pet.getType());
        String path = k == null ? "" : k.getPath();
        for (String c : CATS) if (path.contains(c)) return SoundEvents.CAT_PURR;
        return SoundEvents.WOLF_PANT;
    }

    private static void sound(TamableAnimal pet, SoundEvent s, float volume) {
        pet.level().playSound(null, pet.getX(), pet.getY(), pet.getZ(), s, SoundSource.NEUTRAL, volume, 1.0f);
    }

    private static void msg(ServerPlayer p, String key) {
        p.displayClientMessage(Component.translatable(key), true);
    }
}
