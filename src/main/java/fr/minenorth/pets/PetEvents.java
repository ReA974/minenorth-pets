package fr.minenorth.pets;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.goal.FollowOwnerGoal;
import net.minecraft.world.entity.ai.goal.SitWhenOrderedToGoal;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.List;

/**
 * Branche le comportement de compagnon sur les animaux de Polly Pets (déjà apprivoisables à la nourriture, mais sans suivi ni position assise) :
 * suivi du maître, assis / debout à main nue, et chien K9 via le harnais.
 */
@Mod.EventBusSubscriber(modid = MineNorthPets.MOD_ID)
public final class PetEvents {
    private PetEvents() {}

    public static final String K9_TAG = "minenorthpets_k9";
    private static final String K9_NAMED = "minenorthpets_k9name";

    /** Le maître a choisi un nom : il ne doit plus être effacé quand l'animal cesse d'être K9. */
    public static void keepName(net.minecraft.world.entity.Entity pet) { pet.getPersistentData().remove(K9_NAMED); }
    /** Morceaux de nom d'entité Polly Pets qui ne sont pas des chiens : pas de K9. */
    private static final List<String> NOT_DOGS = List.of("hamster", "kitty", "kitten", "calico", "bombay", "british", "khao", "ferret",
            "snake", "chameleon", "gecko", "tortoise", "egg", "parrot");

    /** Animal de Polly Pets apprivoisable (chien, chat, hamster…) : tout le jeu de fonctions (suivi, K9, combats, portage). */
    public static boolean isPollyPet(Entity e) {
        if (!(e instanceof TamableAnimal)) return false;
        ResourceLocation k = ForgeRegistries.ENTITY_TYPES.getKey(e.getType());
        return k != null && k.getNamespace().equals("polly_pets");
    }

    /** Perroquet vanilla : animal de compagnie « léger » (assis, menu, carnet, collier, inconscient, parler), sans K9 ni combat ni portage. */
    public static boolean isParrot(Entity e) { return e instanceof TamableAnimal && e.getType() == net.minecraft.world.entity.EntityType.PARROT; }

    /** Animal géré par le mod : Polly Pets, plus le perroquet vanilla (config perroquet.actif). */
    public static boolean isPet(Entity e) { return isPollyPet(e) || (isParrot(e) && PetsConfig.parrotEnabled()); }

    /** Nom d'espèce en français (côté serveur les traductions de mods ne sont pas fiables pour le perroquet). */
    public static String speciesName(Entity e) {
        if (e instanceof net.minecraft.world.entity.animal.Parrot parrot && isParrot(e)) {
            return parrot.getVariant() == net.minecraft.world.entity.animal.Parrot.Variant.GRAY ? "Gris du Gabon" : "Perroquet";
        }
        ResourceLocation key = ForgeRegistries.ENTITY_TYPES.getKey(e.getType());
        String fr = key != null && key.getNamespace().equals("polly_pets") ? SpeciesFr.of(key.getPath()) : null;
        return fr != null ? fr : e.getType().getDescription().getString();
    }

    public static boolean isK9(Entity e) { return e.getPersistentData().getBoolean(K9_TAG); }

    private static boolean isDog(Entity e) {
        ResourceLocation k = ForgeRegistries.ENTITY_TYPES.getKey(e.getType());
        if (k == null) return false;
        String path = k.getPath();
        for (String n : NOT_DOGS) if (path.contains(n)) return false;
        return true;
    }

    /** Chargement d'un animal du registre des carnets : abandon en attente appliqué, drapeau « perdu » aligné. */
    @SubscribeEvent
    public static void onJoinRegistry(EntityJoinLevelEvent ev) {
        if (!ev.getLevel().isClientSide() && ev.getEntity() instanceof TamableAnimal pet && isPet(pet)) PetCarnet.applyOnLoad(pet);
    }

    /** Dernier lieu connu : mis à jour quand un animal à collier quitte le monde (déchargement, changement de dimension). */
    @SubscribeEvent
    public static void onLeaveRegistry(net.minecraftforge.event.entity.EntityLeaveLevelEvent ev) {
        if (!ev.getLevel().isClientSide() && ev.getEntity() instanceof TamableAnimal pet && isPet(pet) && PetBook.hasCollar(pet)) PetCarnet.touch(pet);
    }

    @SubscribeEvent
    public static void onJoin(EntityJoinLevelEvent ev) {
        if (ev.getLevel().isClientSide() || !(ev.getEntity() instanceof TamableAnimal pet) || !isPollyPet(pet)) return;
        // Un animal marqué « porté » sans porteur (redémarrage) est libéré.
        if (pet.getPersistentData().getBoolean("minenorthpets_carried") && !PetCarry.isCarried(pet)) PetCarry.release(pet);
        // L'événement peut se répéter pour une même entité : on n'ajoute les comportements qu'une fois.
        if (pet.goalSelector.getAvailableGoals().stream().anyMatch(w -> w.getGoal() instanceof SitWhenOrderedToGoal)) return;
        pet.goalSelector.addGoal(0, new FightGoal(pet));
        pet.goalSelector.addGoal(2, new SitWhenOrderedToGoal(pet));
        pet.goalSelector.addGoal(1, new K9SniffGoal(pet));
        pet.goalSelector.addGoal(2, new StayGoal(pet));
        pet.goalSelector.addGoal(3, new FollowOwnerGoal(pet, PetsConfig.followSpeed(), (float) PetsConfig.followStart(),
                (float) PetsConfig.followStop(), false));
    }

    /** Animaux dont le maître était dans le même véhicule MTS : ils en descendent avec lui (un animal monté par le menu depuis l'extérieur n'est pas éjecté). */
    private static final java.util.Set<java.util.UUID> OWNER_WAS_IN = new java.util.HashSet<>();

    /**
     * Véhicules MTS. Un animal de Polly Pets monté (menu ou K9) descend si on le met assis, ou si son maître descend du véhicule après y avoir été avec lui.
     * Seul le K9 monte tout seul dans le véhicule de son maître (place passager libre).
     */
    @SubscribeEvent
    public static void onTick(LivingEvent.LivingTickEvent ev) {
        if (!(ev.getEntity() instanceof TamableAnimal pet) || pet.level().isClientSide() || pet.tickCount % 10 != 0) return;
        if (!isPollyPet(pet) || !pet.isTame() || !MtsRide.loaded()) return;
        Object ownerSeat = MtsRide.seatOf(pet.getOwner());
        Object petSeat = MtsRide.seatOf(pet);
        if (petSeat != null) {
            boolean together = ownerSeat != null && MtsRide.vehicleOf(ownerSeat) == MtsRide.vehicleOf(petSeat);
            if (together) OWNER_WAS_IN.add(pet.getUUID());
            if (pet.isOrderedToSit() || (!together && OWNER_WAS_IN.contains(pet.getUUID()))) {
                MtsRide.eject(petSeat);
                OWNER_WAS_IN.remove(pet.getUUID());
            }
            return;
        }
        OWNER_WAS_IN.remove(pet.getUUID());
        if (!PetsConfig.k9RidesVehicles() || !isK9(pet)) return;
        if (ownerSeat == null || pet.isOrderedToSit() || pet.isPassenger() || PetCarry.isCarried(pet)) return;
        double d = PetsConfig.k9BoardDistance();
        if (pet.distanceToSqr(pet.getOwner()) > d * d) return;
        MtsRide.board(pet, MtsRide.vehicleOf(ownerSeat));
    }

    /** Un animal qui dort est réveillé par les dégâts ; sinon petits « Z » toutes les secondes. */
    @SubscribeEvent
    public static void onSleepTick(LivingEvent.LivingTickEvent ev) {
        if (!(ev.getEntity() instanceof TamableAnimal pet) || pet.level().isClientSide() || pet.tickCount % 20 != 0) return;
        if (!PetActions.isSleeping(pet)) return;
        if (!isPet(pet) || !pet.isTame()) { PetActions.wake(pet); return; }
        PetActions.sleepParticles(pet);
    }

    @SubscribeEvent
    public static void onHurt(net.minecraftforge.event.entity.living.LivingHurtEvent ev) {
        if (ev.getEntity() instanceof TamableAnimal pet && !pet.level().isClientSide() && PetActions.isSleeping(pet) && !PetKo.isUnconscious(pet)) PetActions.wake(pet);
    }

    /** Le carnet de santé commence à l'apprivoisement (date d'adoption). */
    @SubscribeEvent(priority = net.minecraftforge.eventbus.api.EventPriority.LOW)
    public static void onTame(net.minecraftforge.event.entity.living.AnimalTameEvent ev) {
        if (!ev.isCanceled() && !ev.getAnimal().level().isClientSide() && isPet(ev.getAnimal())) PetBook.ensure(ev.getAnimal());
    }

    @SubscribeEvent
    public static void onInteract(PlayerInteractEvent.EntityInteract ev) {
        if (ev.getHand() != InteractionHand.MAIN_HAND || !(ev.getTarget() instanceof TamableAnimal pet) || !isPet(pet) || !pet.isTame()) return;
        boolean parrot = isParrot(pet);
        ItemStack st = ev.getEntity().getItemInHand(InteractionHand.MAIN_HAND);
        boolean client = ev.getLevel().isClientSide();
        // Accroupi, main nue, sur l'animal d'un autre : lire son collier.
        if (!pet.isOwnedBy(ev.getEntity())) {
            if (!ev.getEntity().isShiftKeyDown() || !st.isEmpty()) return;
            ev.setCanceled(true);
            ev.setCancellationResult(InteractionResult.sidedSuccess(client));
            if (!client && ev.getEntity() instanceof ServerPlayer p) readCollar(p, pet);
            return;
        }
        boolean harness = !parrot && st.is(ModItems.K9_HARNESS.get());
        boolean collar = st.is(ModItems.COLLAR.get());
        // Perroquet : l'assise à main nue et l'épaule restent celles de Minecraft.
        if (!harness && !collar && !(!parrot && st.isEmpty() && PetsConfig.sitOnEmptyHand())) return;
        ev.setCanceled(true);
        ev.setCancellationResult(InteractionResult.sidedSuccess(client));
        if (client || !(ev.getEntity() instanceof ServerPlayer p)) return;
        if (harness) toggleK9(p, pet); else if (collar) putCollar(p, pet, st); else toggleSit(p, pet);
    }

    private static void readCollar(ServerPlayer p, TamableAnimal pet) {
        if (!PetBook.hasCollar(pet)) {
            p.displayClientMessage(Component.literal("§7Cet animal n'a pas de collier d'identification."), true);
            return;
        }
        String owner = Compat.displayName(p.server, pet.getOwnerUUID(), PetBook.collarOwner(pet));
        p.displayClientMessage(Component.literal("§fCet animal appartient à §e" + owner + (PetCarnet.isLost(pet) ? " §c§l(signalé perdu)" : "")), true);
    }

    private static void putCollar(ServerPlayer p, TamableAnimal pet, ItemStack st) {
        if (PetBook.hasCollar(pet)) {
            if (PetsConfig.carnetEnabled()) {
                PetCarnet.issue(p, pet);
                p.displayClientMessage(Component.literal("§aNouveau carnet de santé remis : l'ancien n'est plus valable."), true);
            } else {
                p.displayClientMessage(Component.literal("§7Cet animal porte déjà un collier."), true);
            }
            return;
        }
        String owner = Compat.displayName(p.server, p.getUUID(), p.getGameProfile().getName());
        PetBook.putCollar(pet, owner);
        PetBook.ensure(pet);
        PetCarnet.issue(p, pet);
        if (!p.getAbilities().instabuild) st.shrink(1);
        p.displayClientMessage(Component.literal("§aCollier posé : cet animal est enregistré au nom de §e" + owner + "§a."), true);
    }

    /** Assis / debout ; un animal qui dort se réveille (et se lève) au lieu de rester couché. */
    public static void toggleSit(ServerPlayer p, TamableAnimal pet) {
        if (PetKo.isUnconscious(pet)) {
            p.displayClientMessage(Component.literal("§cIl est inconscient : portez-le chez le vétérinaire."), true);
            return;
        }
        boolean sit = !pet.isOrderedToSit() || PetActions.isSleeping(pet);
        if (PetActions.isSleeping(pet)) {
            PetActions.wake(pet);
            sit = false;
        }
        PetActions.setSitting(pet, sit);
        p.displayClientMessage(Component.literal(sit ? "§7Votre animal s'assoit et attend." : "§7Votre animal se lève et vous suit."), true);
    }

    private static void toggleK9(ServerPlayer p, TamableAnimal pet) {
        if (!Compat.canMakeK9(p)) {
            p.displayClientMessage(Component.literal("§cHarnais réservé à la police."), true);
            return;
        }
        if (!isDog(pet)) {
            p.displayClientMessage(Component.literal("§cSeul un chien peut devenir chien K9."), true);
            return;
        }
        boolean on = !isK9(pet);
        if (on) {
            pet.getPersistentData().putBoolean(K9_TAG, true);
            if (!pet.hasCustomName()) {
                pet.setCustomName(Component.literal("§9K9 §f" + speciesName(pet)));
                pet.getPersistentData().putBoolean(K9_NAMED, true);
            }
            K9Net.broadcast(pet, true);
            p.displayClientMessage(Component.literal("§9Chien K9 équipé : il flairera la drogue autour de lui."), true);
        } else {
            pet.getPersistentData().remove(K9_TAG);
            if (pet.getPersistentData().getBoolean(K9_NAMED)) pet.setCustomName(null);
            pet.getPersistentData().remove(K9_NAMED);
            K9Net.broadcast(pet, false);
            p.displayClientMessage(Component.literal("§7Harnais K9 retiré."), true);
        }
    }
}
