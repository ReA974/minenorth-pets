package fr.minenorth.pets;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraftforge.event.entity.living.LivingChangeTargetEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Mort d'un animal apprivoisé de Polly Pets : il ne meurt pas tout de suite, il tombe INCONSCIENT (1 PV, IA coupée, invulnérable, couché).
 * Le maître a `mort.delaiMinutes` minutes pour le porter chez un vétérinaire (ou un opérateur) qui le réanime ; sinon il meurt pour de bon.
 * L'état est dans le NBT de l'animal (échéance en temps de jeu) : il survit aux redémarrages. /kill et le vide tuent toujours vraiment.
 * Les animaux sauvages ou sans maître meurent normalement.
 */
@Mod.EventBusSubscriber(modid = MineNorthPets.MOD_ID)
public final class PetKo {
    private PetKo() {}

    private static final String DEADLINE_TAG = "minenorthpets_ko";
    private static final String WARNED_TAG = "minenorthpets_ko_warned";

    public static boolean isUnconscious(Entity pet) { return pet.getPersistentData().contains(DEADLINE_TAG); }

    private static long deadline(Entity pet) { return pet.getPersistentData().getLong(DEADLINE_TAG); }

    public static int minutesLeft(Entity pet) {
        long left = deadline(pet) - pet.level().getGameTime();
        return (int) Math.max(0, (left + 1199) / 1200);
    }

    private static String name(TamableAnimal pet) { return PetEvents.speciesName(pet); }

    private static ServerPlayer owner(TamableAnimal pet) {
        return pet.getOwnerUUID() == null || pet.level().getServer() == null ? null : pet.level().getServer().getPlayerList().getPlayer(pet.getOwnerUUID());
    }

    /** Un animal qui devrait mourir : on annule la mort (voir onDeath), il passe à 1 PV et s'endort. */
    private static void knockOut(TamableAnimal pet) {
        pet.getPersistentData().putLong(DEADLINE_TAG, pet.level().getGameTime() + PetsConfig.koMinutes() * 1200L);
        pet.getPersistentData().remove(WARNED_TAG);
        pet.setHealth(1.0f);
        pet.setTarget(null);
        pet.setInvulnerable(true);
        pet.setNoAi(true);
        pet.setSecondsOnFire(0);
        PetActions.sleep(pet);
        ServerPlayer o = owner(pet);
        if (o != null) {
            o.sendSystemMessage(Component.literal("§c[Animaux] Votre " + name(pet) + " est inconscient ! Portez-le chez le vétérinaire dans "
                    + PetsConfig.koMinutes() + " min."));
            o.displayClientMessage(Component.literal("§cVotre " + name(pet) + " est inconscient !"), true);
        }
    }

    /** Réanimation par un vétérinaire (ou un opérateur) : au moins 50 % de vie, IA rétablie, ligne dans le carnet. */
    public static void revive(ServerPlayer vet, TamableAnimal pet) {
        if (!isUnconscious(pet)) {
            vet.displayClientMessage(Component.literal("§7Cet animal n'est pas inconscient."), true);
            return;
        }
        pet.getPersistentData().remove(DEADLINE_TAG);
        pet.getPersistentData().remove(WARNED_TAG);
        pet.setInvulnerable(false);
        pet.setNoAi(false);
        PetActions.wake(pet);
        pet.setHealth(Math.max(pet.getHealth(), pet.getMaxHealth() * 0.5f));
        if (pet.level() instanceof ServerLevel sl) {
            sl.sendParticles(ParticleTypes.HEART, pet.getX(), pet.getY() + pet.getBbHeight() + 0.2, pet.getZ(), 8, 0.3, 0.2, 0.3, 0.02);
        }
        String who = Compat.displayName(vet.server, vet.getUUID(), vet.getGameProfile().getName());
        PetBook.addLine(pet, "Réanimé par " + who + " le " + PetBook.todayText());
        vet.displayClientMessage(Component.literal("§aAnimal réanimé."), true);
        ServerPlayer o = owner(pet);
        if (o != null && o != vet) o.sendSystemMessage(Component.literal("§a[Animaux] Votre " + name(pet) + " a été réanimé par " + who + " !"));
    }

    private static void dieForGood(TamableAnimal pet) {
        ServerPlayer o = owner(pet);
        pet.getPersistentData().remove(DEADLINE_TAG);
        pet.setInvulnerable(false);
        if (o != null) o.sendSystemMessage(Component.literal("§c[Animaux] Votre " + name(pet) + " n'a pas survécu : le délai est écoulé."));
        pet.hurt(pet.damageSources().genericKill(), Float.MAX_VALUE);
    }

    /** Annule la mort d'un animal apprivoisé ; /kill, le vide (sources qui contournent l'invulnérabilité) et le délai expiré tuent vraiment. */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onDeath(LivingDeathEvent ev) {
        if (!(ev.getEntity() instanceof TamableAnimal pet) || pet.level().isClientSide() || !PetEvents.isPet(pet)) return;
        if (!PetsConfig.koEnabled() || !pet.isTame() || pet.getOwnerUUID() == null) return;
        if (ev.getSource().is(DamageTypeTags.BYPASSES_INVULNERABILITY)) return;
        ev.setCanceled(true);
        pet.setHealth(1.0f);
        if (!isUnconscious(pet)) knockOut(pet);
    }

    /** Les mobs ne ciblent plus un animal inconscient. */
    @SubscribeEvent
    public static void onTarget(LivingChangeTargetEvent ev) {
        if (ev.getNewTarget() != null && isUnconscious(ev.getNewTarget())) ev.setCanceled(true);
    }

    /** Compte à rebours : un rappel à la dernière minute, la mort quand le délai est écoulé. */
    @SubscribeEvent
    public static void onTick(LivingEvent.LivingTickEvent ev) {
        if (!(ev.getEntity() instanceof TamableAnimal pet) || pet.level().isClientSide() || pet.tickCount % 20 != 0 || !isUnconscious(pet)) return;
        long left = deadline(pet) - pet.level().getGameTime();
        if (left <= 0) {
            dieForGood(pet);
        } else if (left <= 1200 && !pet.getPersistentData().getBoolean(WARNED_TAG)) {
            pet.getPersistentData().putBoolean(WARNED_TAG, true);
            ServerPlayer o = owner(pet);
            if (o != null) o.sendSystemMessage(Component.literal("§c[Animaux] Dernière minute pour sauver votre " + name(pet) + " !"));
        }
    }
}
