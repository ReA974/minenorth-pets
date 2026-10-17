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
    /** Morceaux de nom d'entité Polly Pets qui ne sont pas des chiens : pas de K9. */
    private static final List<String> NOT_DOGS = List.of("hamster", "kitty", "kitten", "calico", "bombay", "british", "khao", "ferret",
            "snake", "chameleon", "gecko", "tortoise", "egg");

    public static boolean isPollyPet(Entity e) {
        if (!(e instanceof TamableAnimal)) return false;
        ResourceLocation k = ForgeRegistries.ENTITY_TYPES.getKey(e.getType());
        return k != null && k.getNamespace().equals("polly_pets");
    }

    public static boolean isK9(Entity e) { return e.getPersistentData().getBoolean(K9_TAG); }

    private static boolean isDog(Entity e) {
        ResourceLocation k = ForgeRegistries.ENTITY_TYPES.getKey(e.getType());
        if (k == null) return false;
        String path = k.getPath();
        for (String n : NOT_DOGS) if (path.contains(n)) return false;
        return true;
    }

    @SubscribeEvent
    public static void onJoin(EntityJoinLevelEvent ev) {
        if (ev.getLevel().isClientSide() || !(ev.getEntity() instanceof TamableAnimal pet) || !isPollyPet(pet)) return;
        // L'événement peut se répéter pour une même entité : on n'ajoute les comportements qu'une fois.
        if (pet.goalSelector.getAvailableGoals().stream().anyMatch(w -> w.getGoal() instanceof SitWhenOrderedToGoal)) return;
        pet.goalSelector.addGoal(0, new FightGoal(pet));
        pet.goalSelector.addGoal(2, new SitWhenOrderedToGoal(pet));
        pet.goalSelector.addGoal(1, new K9SniffGoal(pet));
        pet.goalSelector.addGoal(2, new StayGoal(pet));
        pet.goalSelector.addGoal(3, new FollowOwnerGoal(pet, PetsConfig.followSpeed(), (float) PetsConfig.followStart(),
                (float) PetsConfig.followStop(), false));
    }

    /** Le K9 monte dans le véhicule MTS de son maître (place passager libre) et en descend avec lui. */
    @SubscribeEvent
    public static void onTick(LivingEvent.LivingTickEvent ev) {
        if (!(ev.getEntity() instanceof TamableAnimal pet) || pet.level().isClientSide() || pet.tickCount % 10 != 0) return;
        if (!PetsConfig.k9RidesVehicles() || !isK9(pet) || !isPollyPet(pet) || !pet.isTame() || !MtsRide.loaded()) return;
        Object ownerSeat = MtsRide.seatOf(pet.getOwner());
        Object petSeat = MtsRide.seatOf(pet);
        if (petSeat != null) {
            if (ownerSeat == null || pet.isOrderedToSit() || MtsRide.vehicleOf(ownerSeat) != MtsRide.vehicleOf(petSeat)) MtsRide.eject(petSeat);
            return;
        }
        if (ownerSeat == null || pet.isOrderedToSit() || pet.isPassenger()) return;
        double d = PetsConfig.k9BoardDistance();
        if (pet.distanceToSqr(pet.getOwner()) > d * d) return;
        MtsRide.board(pet, MtsRide.vehicleOf(ownerSeat));
    }

    /** Un animal qui dort est réveillé par les dégâts ; sinon petits « Z » toutes les secondes. */
    @SubscribeEvent
    public static void onSleepTick(LivingEvent.LivingTickEvent ev) {
        if (!(ev.getEntity() instanceof TamableAnimal pet) || pet.level().isClientSide() || pet.tickCount % 20 != 0) return;
        if (!PetActions.isSleeping(pet)) return;
        if (!isPollyPet(pet) || !pet.isTame()) { PetActions.wake(pet); return; }
        PetActions.sleepParticles(pet);
    }

    @SubscribeEvent
    public static void onHurt(net.minecraftforge.event.entity.living.LivingHurtEvent ev) {
        if (ev.getEntity() instanceof TamableAnimal pet && !pet.level().isClientSide() && PetActions.isSleeping(pet)) PetActions.wake(pet);
    }

    /** Le carnet de santé commence à l'apprivoisement (date d'adoption). */
    @SubscribeEvent(priority = net.minecraftforge.eventbus.api.EventPriority.LOW)
    public static void onTame(net.minecraftforge.event.entity.living.AnimalTameEvent ev) {
        if (!ev.isCanceled() && !ev.getAnimal().level().isClientSide() && isPollyPet(ev.getAnimal())) PetBook.ensure(ev.getAnimal());
    }

    @SubscribeEvent
    public static void onInteract(PlayerInteractEvent.EntityInteract ev) {
        if (ev.getHand() != InteractionHand.MAIN_HAND || !(ev.getTarget() instanceof TamableAnimal pet) || !isPollyPet(pet) || !pet.isTame()) return;
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
        boolean harness = st.is(ModItems.K9_HARNESS.get());
        boolean collar = st.is(ModItems.COLLAR.get());
        if (!harness && !collar && !(st.isEmpty() && PetsConfig.sitOnEmptyHand())) return;
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
        p.displayClientMessage(Component.literal("§fCet animal appartient à §e" + owner + (PetBook.isLost(pet) ? " §c§l(signalé perdu)" : "")), true);
    }

    private static void putCollar(ServerPlayer p, TamableAnimal pet, ItemStack st) {
        if (PetBook.hasCollar(pet)) {
            p.displayClientMessage(Component.literal("§7Cet animal porte déjà un collier."), true);
            return;
        }
        String owner = Compat.displayName(p.server, p.getUUID(), p.getGameProfile().getName());
        PetBook.putCollar(pet, owner);
        PetBook.ensure(pet);
        if (!p.getAbilities().instabuild) st.shrink(1);
        p.displayClientMessage(Component.literal("§aCollier posé : cet animal est enregistré au nom de §e" + owner + "§a."), true);
    }

    /** Assis / debout ; un animal qui dort se réveille (et se lève) au lieu de rester couché. */
    public static void toggleSit(ServerPlayer p, TamableAnimal pet) {
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
                pet.setCustomName(Component.literal("§9K9 §f" + pet.getType().getDescription().getString()));
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
