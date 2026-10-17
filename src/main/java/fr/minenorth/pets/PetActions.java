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

    public static final int FLAG_SITTING = 1, FLAG_SLEEPING = 2, FLAG_STAYING = 4, FLAG_OWNER = 8, FLAG_VET = 16, FLAG_COLLAR = 32, FLAG_LOST = 64, FLAG_CHALLENGED = 128, FLAG_FIGHTING = 256, FLAG_UNCONSCIOUS = 512, FLAG_CARRIED = 1024, FLAG_PARROT = 2048, FLAG_MTS = 4096, FLAG_MOUNTED = 8192;

    /** Les 5 premières sont réservées au maître, BOOK au maître / vétérinaire / police, les 3 dernières au vétérinaire, LOST au maître. */
    public enum Action { PET, PLAY, SLEEP, SIT, STAY, BOOK, VACCINE, HEAL, CHIP, LOST, CHALLENGE, ACCEPT, SEPARATE, CARRY, DROP, REVIVE, TALK, BOARD, ALIGHT, RECOVER }

    public static boolean isSleeping(Entity e) { return e.getPersistentData().getBoolean(SLEEP_TAG); }
    public static boolean isStaying(Entity e) { return e.getPersistentData().getBoolean(STAY_TAG); }

    public static int flags(TamableAnimal pet) {
        return (pet.isOrderedToSit() ? FLAG_SITTING : 0) | (isSleeping(pet) ? FLAG_SLEEPING : 0) | (isStaying(pet) ? FLAG_STAYING : 0)
                | (PetBook.hasCollar(pet) ? FLAG_COLLAR : 0) | (PetBook.isLost(pet) ? FLAG_LOST : 0)
                | (PetFight.isFighting(pet) ? FLAG_FIGHTING : 0) | (PetKo.isUnconscious(pet) ? FLAG_UNCONSCIOUS : 0) | (PetCarry.isCarried(pet) ? FLAG_CARRIED : 0) | (PetEvents.isParrot(pet) ? FLAG_PARROT : 0)
                | (MtsRide.loaded() ? FLAG_MTS : 0) | (MtsRide.loaded() && MtsRide.seatOf(pet) != null ? FLAG_MOUNTED : 0)
                | (pet.level().getServer() != null && PetFight.isChallenged(pet, pet.level().getServer()) ? FLAG_CHALLENGED : 0);
    }

    /** Flags du menu pour ce joueur : états de l'animal + droits (maître, vétérinaire). 0 s'il n'a aucun droit de lecture. */
    public static int menuFlags(ServerPlayer p, TamableAnimal pet) {
        boolean owner = pet.isOwnedBy(p), vet = Compat.isVet(p);
        if (!owner && !vet && !Compat.isPolice(p)) return 0;
        return flags(pet) | (owner ? FLAG_OWNER : 0) | (vet ? FLAG_VET : 0);
    }

    /** N'importe quel animal apprivoisé de Polly Pets à portée (les droits sont vérifiés action par action). */
    public static TamableAnimal targetAny(ServerPlayer p, int entityId) {
        Entity e = p.level().getEntity(entityId);
        if (!(e instanceof TamableAnimal pet) || !PetEvents.isPet(pet) || !pet.isTame()) return null;
        return p.distanceToSqr(pet) <= REACH * REACH ? pet : null;
    }

    /** Exécute une action du menu après avoir revérifié les droits du joueur. */
    public static void performChecked(ServerPlayer p, int entityId, Action action) {
        TamableAnimal pet = targetAny(p, entityId);
        if (pet == null) return;
        boolean owner = pet.isOwnedBy(p);
        // Le perroquet n'a ni jeu, ni sommeil couché, ni « rester ici », ni combat ; « Faire parler » n'existe que pour lui.
        boolean parrot = PetEvents.isParrot(pet);
        if (parrot && switch (action) { case PLAY, SLEEP, STAY, CHALLENGE, ACCEPT, SEPARATE, BOARD, ALIGHT -> true; default -> false; }) return;
        if (!parrot && action == Action.TALK) return;
        switch (action) {
            case PET, PLAY, SLEEP, SIT, STAY, LOST, CHALLENGE, ACCEPT, SEPARATE, TALK -> {
                if (owner && PetKo.isUnconscious(pet)) {
                    p.displayClientMessage(Component.literal("§cIl est inconscient : portez-le chez le vétérinaire."), true);
                } else if (owner) {
                    perform(p, pet, action);
                }
            }
            case CARRY -> {
                if (owner) PetCarry.carry(p, pet);
            }
            case BOARD, ALIGHT, RECOVER -> {
                if (owner) perform(p, pet, action);
            }
            case DROP -> {
                if (PetCarry.carriedBy(p) == pet) PetCarry.drop(p, "§7Vous posez votre animal.");
            }
            case REVIVE -> {
                if (Compat.isVet(p)) PetKo.revive(p, pet);
            }
            case BOOK -> {
                if (owner || Compat.isVet(p) || Compat.isPolice(p)) {
                    PetBook.ensure(pet);
                    K9Net.CHANNEL.send(net.minecraftforge.network.PacketDistributor.PLAYER.with(() -> p), new K9Net.Book(PetBook.lines(p.server, pet)));
                }
            }
            case VACCINE, HEAL, CHIP -> {
                if (Compat.isVet(p)) perform(p, pet, action);
            }
        }
    }

    /** L'animal visé si le joueur en est le maître et qu'il est à portée, sinon null. */
    public static TamableAnimal target(ServerPlayer p, int entityId) {
        Entity e = p.level().getEntity(entityId);
        if (!(e instanceof TamableAnimal pet) || !PetEvents.isPet(pet) || !pet.isTame() || !pet.isOwnedBy(p)) return null;
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
            case BOOK, CARRY, DROP, REVIVE -> { }
            case BOARD -> board(p, pet);
            case ALIGHT -> alight(p, pet);
            case RECOVER -> PetCarnet.recover(p, pet);
            case TALK -> talk(p, pet);
            case VACCINE -> {
                PetBook.vaccinate(pet);
                heartsAndSound(pet, 3);
                msg(p, "§aVaccin enregistré dans le carnet de santé.");
            }
            case HEAL -> {
                pet.setHealth(pet.getMaxHealth());
                PetBook.care(pet);
                heartsAndSound(pet, 8);
                msg(p, "§aSoins terminés : l'animal est en pleine forme.");
            }
            case CHIP -> {
                String number = PetBook.chip(pet);
                msg(p, number == null ? "§7Cet animal est déjà pucé." : "§aPuce posée : §f" + number);
            }
            case LOST -> lost(p, pet);
            case CHALLENGE -> PetFight.challenge(p, pet);
            case ACCEPT -> PetFight.acceptFor(p, pet);
            case SEPARATE -> PetFight.separate(p, pet);
            case STAY -> {
                boolean stay = !isStaying(pet);
                if (stay) pet.getPersistentData().putBoolean(STAY_TAG, true); else pet.getPersistentData().remove(STAY_TAG);
                pet.getNavigation().stop();
                msg(p, stay ? "message.minenorthpets.stay" : "message.minenorthpets.follow");
            }
        }
    }

    private static void heartsAndSound(TamableAnimal pet, int hearts) {
        if (pet.level() instanceof ServerLevel sl) {
            sl.sendParticles(ParticleTypes.HEART, pet.getX(), pet.getY() + pet.getBbHeight() + 0.2, pet.getZ(), hearts, 0.3, 0.2, 0.3, 0.02);
            sl.sendParticles(ParticleTypes.HAPPY_VILLAGER, pet.getX(), pet.getY() + pet.getBbHeight() * 0.5, pet.getZ(), hearts, 0.3, 0.3, 0.3, 0.0);
        }
        sound(pet, happySound(pet), 0.7f);
    }

    /** « Signaler perdu / retrouvé » : drapeau lisible sur le collier + message à la police en service. */
    private static void lost(ServerPlayer p, TamableAnimal pet) {
        if (!PetBook.hasCollar(pet)) {
            msg(p, "§cPosez d'abord un collier d'identification sur votre animal.");
            return;
        }
        boolean lost = !PetCarnet.isLost(pet);
        PetCarnet.setLost(p, pet.getUUID(), pet, lost);
        msg(p, lost ? "§cAnimal signalé perdu. La police est prévenue." : "§aAnimal signalé retrouvé.");
    }

    /** « Monter en voiture » : véhicule MTS du maître, sinon le plus proche (6 blocs) ; place passager libre, jamais le poste de conduite. */
    private static void board(ServerPlayer p, TamableAnimal pet) {
        if (!PetsConfig.vehicleMenuEnabled()) { msg(p, "§7Cette action est désactivée."); return; }
        if (!MtsRide.loaded()) { msg(p, "§7Aucun véhicule compatible n'est installé."); return; }
        if (PetKo.isUnconscious(pet) || isSleeping(pet) || pet.isOrderedToSit()) { msg(p, "§7Faites d'abord lever votre animal."); return; }
        if (PetFight.isFighting(pet)) { msg(p, "§7Impossible : votre animal est en plein combat."); return; }
        if (PetCarry.isCarried(pet)) { msg(p, "§7Posez d'abord votre animal."); return; }
        if (pet.isPassenger()) { msg(p, "§7Votre animal est déjà dans un véhicule."); return; }
        double max = PetsConfig.k9BoardDistance();
        if (pet.distanceToSqr(p) > max * max) { msg(p, "§7Votre animal est trop loin du véhicule."); return; }
        Object seat = MtsRide.seatOf(p);
        Object vehicle = seat != null ? MtsRide.vehicleOf(seat) : MtsRide.nearestVehicle(p, 6.0);
        if (vehicle == null) { msg(p, "§7Aucun véhicule à proximité (6 blocs) : montez dedans ou approchez-vous."); return; }
        if (!MtsRide.board(pet, vehicle)) { msg(p, "§7Aucune place libre dans ce véhicule."); return; }
        msg(p, "§aVotre animal monte dans le véhicule.");
    }

    private static void alight(ServerPlayer p, TamableAnimal pet) {
        Object seat = MtsRide.loaded() ? MtsRide.seatOf(pet) : null;
        if (seat == null) { msg(p, "§7Votre animal n'est pas dans un véhicule."); return; }
        MtsRide.eject(seat);
        msg(p, "§7Votre animal descend du véhicule.");
    }

    /** « Faire parler » (perroquet) : son d'ambiance et notes de musique, pause de 3 s. */
    private static void talk(ServerPlayer p, TamableAnimal pet) {
        long now = pet.level().getGameTime();
        if (pet.getPersistentData().getLong("minenorthpets_talk") > now) {
            msg(p, "message.minenorthpets.rest");
            return;
        }
        pet.getPersistentData().putLong("minenorthpets_talk", now + 60);
        sound(pet, SoundEvents.PARROT_AMBIENT, 1.0f);
        if (pet.level() instanceof ServerLevel sl) {
            sl.sendParticles(ParticleTypes.NOTE, pet.getX(), pet.getY() + pet.getBbHeight() + 0.3, pet.getZ(), 4, 0.3, 0.2, 0.3, 1.0);
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

    /** Clé de traduction (message.minenorthpets.*) ou texte français direct (commence par un code de couleur). */
    private static void msg(ServerPlayer p, String keyOrText) {
        p.displayClientMessage(keyOrText.startsWith("§") ? Component.literal(keyOrText) : Component.translatable(keyOrText), true);
    }
}
