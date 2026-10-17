package fr.minenorth.pets;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Combats consentis entre deux animaux apprivoisés de Polly Pets, sans mort possible.
 * Déroulement : le maître A choisit « Défier » (l'animal apprivoisé d'un AUTRE maître le plus proche, à portée) ; le maître B a quelques secondes
 * pour « Accepter le combat » (menu de son animal ou /pets accepter). Pendant le combat, FightGoal rapproche les deux animaux et ce gestionnaire
 * leur inflige de petits dégâts directs ; sous le seuil de KO le combat s'arrête, le perdant s'assoit un instant. Rien n'est écrit sur disque :
 * un redémarrage du serveur met fin à tout combat. Aucun autre être vivant n'est touché par ces dégâts.
 */
@Mod.EventBusSubscriber(modid = MineNorthPets.MOD_ID)
public final class PetFight {
    private PetFight() {}

    private static final int HIT_INTERVAL = 15, KO_SIT_TICKS = 200;
    private static final double REACH_SQ = 2.5 * 2.5, MAX_START_DISTANCE_SQ = 16.0 * 16.0;

    private record Challenge(UUID challengerPet, UUID challengerPlayer, String challengerName, long expires) {}

    private static final class Fight {
        final UUID a, b;
        int age;
        Fight(UUID a, UUID b) { this.a = a; this.b = b; }
    }

    private static final Map<UUID, Challenge> PENDING = new HashMap<>();
    private static final Map<UUID, Fight> BY_PET = new HashMap<>();
    private static final List<Fight> FIGHTS = new ArrayList<>();
    private static final Map<UUID, Long> KOS = new HashMap<>();

    // ------------------------------------------------------------------ états

    public static boolean isFighting(Entity pet) { return BY_PET.containsKey(pet.getUUID()); }

    public static boolean isChallenged(Entity pet, MinecraftServer server) {
        Challenge c = PENDING.get(pet.getUUID());
        return c != null && c.expires > now(server);
    }

    /** L'adversaire de cet animal dans le combat en cours, ou null. */
    public static TamableAnimal opponentOf(TamableAnimal pet) {
        Fight f = BY_PET.get(pet.getUUID());
        if (f == null || !(pet.level() instanceof ServerLevel level)) return null;
        Entity e = level.getEntity(pet.getUUID().equals(f.a) ? f.b : f.a);
        return e instanceof TamableAnimal t && t.isAlive() ? t : null;
    }

    private static long now(MinecraftServer s) { return s.overworld().getGameTime(); }

    private static TamableAnimal find(MinecraftServer server, UUID id) {
        for (ServerLevel l : server.getAllLevels()) {
            Entity e = l.getEntity(id);
            if (e instanceof TamableAnimal t && t.isAlive()) return t;
        }
        return null;
    }

    private static String name(TamableAnimal pet) {
        return pet.hasCustomName() ? pet.getCustomName().getString() : pet.getType().getDescription().getString();
    }

    private static void bar(ServerPlayer p, String text) { p.displayClientMessage(Component.literal(text), true); }

    private static ServerPlayer ownerOnline(MinecraftServer s, TamableAnimal pet) {
        return pet.getOwnerUUID() == null ? null : s.getPlayerList().getPlayer(pet.getOwnerUUID());
    }

    // ------------------------------------------------------------------ défi

    public static void challenge(ServerPlayer p, TamableAnimal pet) {
        if (!PetsConfig.combatEnabled()) { bar(p, "§7Les combats d'animaux sont désactivés."); return; }
        if (isFighting(pet)) { bar(p, "§7Votre animal est déjà en combat."); return; }
        if (!(pet.level() instanceof ServerLevel level)) return;
        double range = PetsConfig.combatRange();
        TamableAnimal best = null;
        for (TamableAnimal t : level.getEntitiesOfClass(TamableAnimal.class, pet.getBoundingBox().inflate(range))) {
            if (t == pet || !PetEvents.isPollyPet(t) || !t.isTame() || t.getOwnerUUID() == null || t.getOwnerUUID().equals(p.getUUID())) continue;
            if (isFighting(t) || ownerOnline(p.server, t) == null || pet.distanceToSqr(t) > range * range) continue;
            if (best == null || pet.distanceToSqr(t) < pet.distanceToSqr(best)) best = t;
        }
        if (best == null) { bar(p, "§7Aucun animal d'un autre maître à proximité."); return; }
        int delay = PetsConfig.combatAnswerSeconds();
        String challenger = p.getGameProfile().getName();
        PENDING.put(best.getUUID(), new Challenge(pet.getUUID(), p.getUUID(), challenger, now(p.server) + delay * 20L));
        ServerPlayer other = ownerOnline(p.server, best);
        Component accept = Component.literal("§a§l[ACCEPTER LE COMBAT]").withStyle(s -> s
                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/pets accepter"))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal("Lancer le combat (/pets accepter)"))));
        other.sendSystemMessage(Component.literal("§6[Combat] §e" + challenger + " §fdéfie votre §b" + name(best) + " §f! ").append(accept)
                .append(Component.literal(" §7(" + delay + " s)")));
        bar(other, "§6Défi reçu pour votre " + name(best) + " : ouvrez son menu (Ctrl + clic droit) ou cliquez dans le chat.");
        bar(p, "§eDéfi lancé à §f" + other.getGameProfile().getName() + "§e : réponse attendue (" + delay + " s).");
    }

    /** Depuis le menu de l'animal défié. */
    public static void acceptFor(ServerPlayer p, TamableAnimal pet) {
        Challenge c = PENDING.get(pet.getUUID());
        if (c == null || c.expires <= now(p.server)) { PENDING.remove(pet.getUUID()); bar(p, "§7Aucun défi en attente pour cet animal."); return; }
        start(p.server, p, pet, c);
    }

    /** /pets accepter : le premier défi en attente visant un animal du joueur. */
    public static void acceptAny(ServerPlayer p) {
        long now = now(p.server);
        for (Map.Entry<UUID, Challenge> en : new ArrayList<>(PENDING.entrySet())) {
            if (en.getValue().expires <= now) continue;
            TamableAnimal pet = find(p.server, en.getKey());
            if (pet != null && pet.isOwnedBy(p)) { start(p.server, p, pet, en.getValue()); return; }
        }
        bar(p, "§7Aucun défi en attente.");
    }

    private static void start(MinecraftServer server, ServerPlayer accepter, TamableAnimal target, Challenge c) {
        PENDING.remove(target.getUUID());
        TamableAnimal challenger = find(server, c.challengerPet);
        if (!PetsConfig.combatEnabled() || challenger == null || !challenger.isTame() || isFighting(challenger) || isFighting(target)
                || challenger.level() != target.level() || challenger.distanceToSqr(target) > MAX_START_DISTANCE_SQ) {
            bar(accepter, "§7Le combat n'est plus possible (adversaire trop loin ou indisponible).");
            return;
        }
        for (TamableAnimal t : List.of(challenger, target)) {
            if (PetActions.isSleeping(t)) PetActions.wake(t); else PetActions.setSitting(t, false);
        }
        Fight f = new Fight(challenger.getUUID(), target.getUUID());
        FIGHTS.add(f);
        BY_PET.put(f.a, f);
        BY_PET.put(f.b, f);
        String msg = "§6[Combat] §f" + name(challenger) + " §6contre §f" + name(target) + " §6: que le meilleur gagne !";
        for (TamableAnimal t : List.of(challenger, target)) {
            ServerPlayer o = ownerOnline(server, t);
            if (o != null) bar(o, msg);
        }
    }

    /** L'animal sort de tout combat ou défi (abandon). */
    public static void forget(TamableAnimal pet) {
        PENDING.remove(pet.getUUID());
        Fight f = BY_PET.get(pet.getUUID());
        if (f != null) end(pet.level().getServer(), f);
        pet.getNavigation().stop();
    }

    public static void separate(ServerPlayer p, TamableAnimal pet) {
        Fight f = BY_PET.get(pet.getUUID());
        if (f == null) { bar(p, "§7Votre animal n'est pas en combat."); return; }
        end(p.server, f);
        for (UUID id : List.of(f.a, f.b)) {
            TamableAnimal t = find(p.server, id);
            if (t != null) {
                t.getNavigation().stop();
                ServerPlayer o = ownerOnline(p.server, t);
                if (o != null) bar(o, "§7Le combat est arrêté : les animaux sont séparés.");
            }
        }
    }

    // ------------------------------------------------------------------ déroulement

    private static void end(MinecraftServer server, Fight f) {
        FIGHTS.remove(f);
        BY_PET.remove(f.a);
        BY_PET.remove(f.b);
    }

    private static boolean wouldKo(TamableAnimal target, float damage) {
        return target.getHealth() - damage < target.getMaxHealth() * PetsConfig.combatKoRatio();
    }

    private static void ko(MinecraftServer server, Fight f, TamableAnimal loser, TamableAnimal winner) {
        end(server, f);
        loser.getNavigation().stop();
        winner.getNavigation().stop();
        PetActions.setSitting(loser, true);
        KOS.put(loser.getUUID(), now(server) + KO_SIT_TICKS);
        if (loser.level() instanceof ServerLevel sl) {
            sl.sendParticles(ParticleTypes.HEART, winner.getX(), winner.getY() + winner.getBbHeight() + 0.2, winner.getZ(), 8, 0.3, 0.2, 0.3, 0.02);
            sl.sendParticles(ParticleTypes.FIREWORK, loser.getX(), loser.getY() + loser.getBbHeight() + 0.2, loser.getZ(), 8, 0.3, 0.2, 0.3, 0.02);
        }
        ServerPlayer wo = ownerOnline(server, winner), lo = ownerOnline(server, loser);
        if (wo != null) bar(wo, "§a§lVictoire ! §r§aVotre " + name(winner) + " a gagné le combat.");
        if (lo != null) bar(lo, "§cVotre " + name(loser) + " est KO : " + name(winner) + " remporte le combat.");
    }

    private static void hit(TamableAnimal attacker, TamableAnimal target, float damage) {
        if (!(attacker.level() instanceof ServerLevel level)) return;
        attacker.swing(InteractionHand.MAIN_HAND);
        target.hurt(level.damageSources().mobAttack(attacker), damage);
        level.playSound(null, attacker.getX(), attacker.getY(), attacker.getZ(), SoundEvents.WOLF_GROWL, SoundSource.NEUTRAL, 0.8f, 1.0f + level.random.nextFloat() * 0.3f);
        level.sendParticles(ParticleTypes.CRIT, target.getX(), target.getY() + target.getBbHeight() * 0.6, target.getZ(), 5, 0.2, 0.2, 0.2, 0.1);
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent ev) {
        if (ev.phase != TickEvent.Phase.END) return;
        MinecraftServer server = ev.getServer();
        if (PENDING.isEmpty() && FIGHTS.isEmpty() && KOS.isEmpty()) return;
        long now = now(server);
        PENDING.values().removeIf(c -> c.expires <= now);
        for (Iterator<Map.Entry<UUID, Long>> it = KOS.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<UUID, Long> en = it.next();
            if (en.getValue() > now) continue;
            it.remove();
            TamableAnimal t = find(server, en.getKey());
            if (t != null && !PetActions.isSleeping(t)) PetActions.setSitting(t, false);
        }
        for (Fight f : new ArrayList<>(FIGHTS)) {
            TamableAnimal a = find(server, f.a), b = find(server, f.b);
            if (a == null || b == null || !a.isTame() || !b.isTame() || a.level() != b.level()) { end(server, f); continue; }
            f.age++;
            if (f.age >= PetsConfig.combatSeconds() * 20) {
                end(server, f);
                for (TamableAnimal t : List.of(a, b)) {
                    t.getNavigation().stop();
                    ServerPlayer o = ownerOnline(server, t);
                    if (o != null) bar(o, "§6Temps écoulé : match nul !");
                }
                continue;
            }
            if (f.age % HIT_INTERVAL != 0 || a.distanceToSqr(b) > REACH_SQ) continue;
            float dmg = (float) PetsConfig.combatDamage();
            if (wouldKo(b, dmg)) { ko(server, f, b, a); continue; }
            hit(a, b, dmg);
            if (wouldKo(a, dmg)) { ko(server, f, a, b); continue; }
            hit(b, a, dmg);
        }
    }

    /** Filet de sécurité : un animal en combat ne peut pas mourir, quelle que soit la source du dégât (le combat s'arrête alors par un KO). */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onHurt(LivingHurtEvent ev) {
        if (!(ev.getEntity() instanceof TamableAnimal pet) || pet.level().isClientSide()) return;
        Fight f = BY_PET.get(pet.getUUID());
        if (f == null || !wouldKo(pet, ev.getAmount())) return;
        ev.setCanceled(true);
        TamableAnimal winner = opponentOf(pet);
        if (winner != null && pet.level().getServer() != null) ko(pet.level().getServer(), f, pet, winner);
        else end(pet.level().getServer(), f);
    }
}
