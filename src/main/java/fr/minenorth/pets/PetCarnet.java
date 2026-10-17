package fr.minenorth.pets;

import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.PacketDistributor;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Logique du carnet de santé de poche : remise au maître à la pose du collier, ouverture, « perdu / retrouvé » (animal chargé ou non) et abandon
 * avec confirmation. Le registre LostPets fait foi : un carnet n'est valable que s'il porte le numéro courant de son animal ET que son porteur est le maître.
 */
public final class PetCarnet {
    private PetCarnet() {}

    public static final String PET = "pet", SERIAL = "serial", LOST = "lost", SPECIES = "species", POS = "pos", LINES = "lines";
    private static final int CONFIRM_TICKS = 100;
    private static final Map<UUID, Integer> ARMED = new HashMap<>();

    // ------------------------------------------------------------------ outils

    @Nullable
    public static TamableAnimal findLoaded(MinecraftServer server, UUID id) {
        for (ServerLevel l : server.getAllLevels()) {
            if (l.getEntity(id) instanceof TamableAnimal t && t.isAlive()) return t;
        }
        return null;
    }

    private static String posText(TamableAnimal pet) {
        return pet.blockPosition().getX() + " " + pet.blockPosition().getY() + " " + pet.blockPosition().getZ()
                + " (" + pet.level().dimension().location().getPath() + ")";
    }

    private static void refresh(LostPets.Entry e, TamableAnimal pet) {
        e.species = PetEvents.speciesName(pet);
        e.name = pet.hasCustomName() ? pet.getCustomName().getString() : "";
        e.chip = PetBook.ensure(pet).getString("chip");
        e.pos = posText(pet);
    }

    private static void bar(ServerPlayer p, String text) { p.displayClientMessage(Component.literal(text), true); }

    /** Dernier lieu connu d'un animal collé à un collier, mis à jour quand il quitte le monde (déchargement, dimension). */
    public static void touch(TamableAnimal pet) {
        MinecraftServer server = pet.level().getServer();
        if (server == null) return;
        LostPets data = LostPets.get(server);
        LostPets.Entry e = data.entry(pet.getUUID());
        if (e == null) return;
        e.pos = posText(pet);
        data.setDirty();
    }

    // ------------------------------------------------------------------ remise du carnet

    /** Remet un nouveau carnet au maître (l'ancien devient invalide : numéro de carnet incrémenté, exemplaires de l'inventaire retirés). */
    public static void issue(ServerPlayer p, TamableAnimal pet) {
        if (!PetsConfig.carnetEnabled()) return;
        LostPets data = LostPets.get(p.server);
        LostPets.Entry e = data.getOrCreate(pet.getUUID(), p.getUUID());
        e.owner = p.getUUID();
        e.ownerPseudo = p.getGameProfile().getName();
        e.serial++;
        e.abandoned = false;
        e.lost = PetBook.isLost(pet);
        refresh(e, pet);
        data.setDirty();
        for (int i = 0; i < p.getInventory().getContainerSize(); i++) {
            ItemStack s = p.getInventory().getItem(i);
            if (s.is(ModItems.CARNET.get()) && s.hasTag() && pet.getUUID().toString().equals(s.getTag().getString(PET))) p.getInventory().setItem(i, ItemStack.EMPTY);
        }
        ItemStack book = new ItemStack(ModItems.CARNET.get());
        write(book, p.server, pet, e);
        if (!p.getInventory().add(book)) p.drop(book, false);
    }

    private static List<String> view(MinecraftServer server, @Nullable TamableAnimal pet, LostPets.Entry e, ItemStack st) {
        List<String> out = new ArrayList<>();
        if (pet != null) {
            for (String line : PetBook.lines(server, pet)) if (!line.startsWith("§7Collier :")) out.add(line);
        } else {
            ListTag saved = st.getTag().getList(LINES, Tag.TAG_STRING);
            for (int i = 0; i < saved.size() && i < 40; i++) {
                String line = saved.getString(i);
                if (!line.startsWith("§7Collier :")) out.add(line.length() > 200 ? line.substring(0, 200) : line);
            }
            out.add("§8(animal non chargé : dernier relevé)");
        }
        out.add("§7Signalement : " + (e.lost ? "§c§lPERDU" : "§aen règle"));
        out.add("§7Dernier lieu connu : §f" + (e.pos.isEmpty() ? "inconnu" : e.pos));
        return out;
    }

    private static void write(ItemStack st, MinecraftServer server, @Nullable TamableAnimal pet, LostPets.Entry e) {
        var tag = st.getOrCreateTag();
        if (pet != null) {
            tag.putString(PET, pet.getUUID().toString());
        }
        tag.putInt(SERIAL, e.serial);
        tag.putBoolean(LOST, e.lost);
        tag.putString(SPECIES, e.species);
        tag.putString(POS, e.pos);
        if (pet != null) {
            ListTag lines = new ListTag();
            for (String l : PetBook.lines(server, pet)) lines.add(StringTag.valueOf(l));
            tag.put(LINES, lines);
        }
    }

    // ------------------------------------------------------------------ ouverture

    /** Le registre dit que ce carnet est valable pour ce joueur (même numéro, même maître, animal pas abandonné) ? Sinon null + message. */
    @Nullable
    private static LostPets.Entry valid(ServerPlayer p, ItemStack st, UUID petId) {
        LostPets.Entry e = LostPets.get(p.server).entry(petId);
        if (e == null || e.abandoned || !p.getUUID().equals(e.owner) || !st.hasTag() || st.getTag().getInt(SERIAL) != e.serial) {
            bar(p, "§cCe carnet n'est plus valable.");
            return null;
        }
        TamableAnimal pet = findLoaded(p.server, petId);
        if (pet != null && !pet.isOwnedBy(p)) {
            bar(p, "§cCe carnet n'est plus valable.");
            return null;
        }
        return e;
    }

    @Nullable
    private static UUID petOf(ItemStack st) {
        if (!st.hasTag() || !st.getTag().contains(PET)) return null;
        try {
            return UUID.fromString(st.getTag().getString(PET));
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    public static void open(ServerPlayer p, ItemStack st) {
        if (!PetsConfig.carnetEnabled()) { bar(p, "§7Les carnets de santé sont désactivés."); return; }
        UUID petId = petOf(st);
        if (petId == null) { bar(p, "§7Ce carnet n'est lié à aucun animal."); return; }
        LostPets.Entry e = valid(p, st, petId);
        if (e == null) return;
        TamableAnimal pet = findLoaded(p.server, petId);
        if (pet != null) {
            refresh(e, pet);
            LostPets.get(p.server).setDirty();
            write(st, p.server, pet, e);
        }
        K9Net.CHANNEL.send(PacketDistributor.PLAYER.with(() -> p), new K9Net.BookOpen(view(p.server, pet, e, st), e.lost));
    }

    // ------------------------------------------------------------------ perdu / retrouvé

    /** Pose ou retire le drapeau « perdu » : registre, animal (s'il est chargé) et message à la police en service avec le pseudo du maître. */
    public static void setLost(ServerPlayer p, UUID petId, @Nullable TamableAnimal pet, boolean lost) {
        LostPets data = LostPets.get(p.server);
        LostPets.Entry e = data.getOrCreate(petId, p.getUUID());
        e.owner = p.getUUID();
        e.ownerPseudo = p.getGameProfile().getName();
        if (pet != null) {
            PetBook.setLost(pet, lost);
            refresh(e, pet);
        }
        e.lost = lost;
        data.setDirty();
        if (!PetsConfig.lostAlertsPolice()) return;
        String what = e.species + (e.name.isEmpty() ? "" : " « " + e.name + " »");
        String chip = e.chip.isEmpty() ? "" : ", puce " + e.chip;
        Compat.alertPolice(p.server, lost
                ? "§e[Animaux] §cAnimal perdu §f: " + what + " §7(maître : " + e.ownerPseudo + chip + ") §7— dernier lieu connu : " + (e.pos.isEmpty() ? "inconnu" : e.pos)
                : "§e[Animaux] §aAnimal retrouvé §f: " + what + " §7(maître : " + e.ownerPseudo + ")");
    }

    // ------------------------------------------------------------------ actions de l'écran du carnet

    private static ItemStack heldBook(ServerPlayer p) {
        for (InteractionHand h : InteractionHand.values()) {
            ItemStack s = p.getItemInHand(h);
            if (s.is(ModItems.CARNET.get())) return s;
        }
        return ItemStack.EMPTY;
    }

    /** kind 0 : perdu / retrouvé ; kind 1 : abandonner (premier appel = armement, second dans les 5 s = exécution). */
    public static void action(ServerPlayer p, int kind) {
        ItemStack st = heldBook(p);
        UUID petId = st.isEmpty() ? null : petOf(st);
        if (!PetsConfig.carnetEnabled() || petId == null) { bar(p, "§7Tenez le carnet de santé en main."); return; }
        LostPets.Entry e = valid(p, st, petId);
        if (e == null) return;
        TamableAnimal pet = findLoaded(p.server, petId);
        if (kind == 0) {
            boolean lost = !e.lost;
            setLost(p, petId, pet, lost);
            st.getOrCreateTag().putBoolean(LOST, lost);
            bar(p, lost ? "§cAnimal signalé perdu. La police est prévenue." : "§aAnimal signalé retrouvé.");
            return;
        }
        if (kind != 1) return;
        Integer armed = ARMED.get(p.getUUID());
        int now = p.server.getTickCount();
        if (armed == null || now - armed > CONFIRM_TICKS) {
            ARMED.put(p.getUUID(), now);
            bar(p, "§c§lCliquez de nouveau dans 5 s pour confirmer l'abandon de votre animal.");
            return;
        }
        ARMED.remove(p.getUUID());
        abandon(p, st, petId, e, pet);
    }

    private static void abandon(ServerPlayer p, ItemStack st, UUID petId, LostPets.Entry e, @Nullable TamableAnimal pet) {
        String who = Compat.displayName(p.server, p.getUUID(), p.getGameProfile().getName());
        String note = "Abandonné par " + who + " le " + PetBook.todayText();
        String species = e.species;
        if (pet != null) {
            apply(pet, note);
            LostPets.get(p.server).remove(petId);
        } else {
            e.abandoned = true;
            e.note = note;
            LostPets.get(p.server).setDirty();
        }
        st.shrink(1);
        bar(p, "§cVous avez abandonné votre " + species + ".");
    }

    /** L'animal perd son maître et redevient libre : collier, K9, combat, KO, portage, assise et sommeil sont retirés ; la note reste dans son carnet. */
    public static void apply(TamableAnimal pet, String note) {
        PetCarry.forget(pet);
        PetFight.forget(pet);
        PetKo.clear(pet);
        pet.getPersistentData().remove(PetActions.SLEEP_TAG);
        pet.getPersistentData().remove(PetActions.STAY_TAG);
        K9Net.broadcastSleep(pet, false);
        if (PetEvents.isK9(pet)) {
            pet.getPersistentData().remove(PetEvents.K9_TAG);
            K9Net.broadcast(pet, false);
        }
        PetBook.removeCollar(pet);
        PetActions.setSitting(pet, false);
        pet.setTame(false);
        pet.setOwnerUUID(null);
        if (note != null && !note.isEmpty()) PetBook.addLine(pet, note);
    }

    /** À chaque chargement d'un animal : applique l'abandon en attente, ou aligne le drapeau « perdu » sur le registre. */
    public static void applyOnLoad(TamableAnimal pet) {
        MinecraftServer server = pet.level().getServer();
        if (server == null) return;
        LostPets data = LostPets.get(server);
        LostPets.Entry e = data.entry(pet.getUUID());
        if (e == null) return;
        if (e.abandoned) {
            apply(pet, e.note);
            data.remove(pet.getUUID());
        } else if (PetBook.hasCollar(pet) && PetBook.isLost(pet) != e.lost) {
            PetBook.setLost(pet, e.lost);
        }
    }

    /** Déconnexion : l'armement de l'abandon ne doit pas survivre. */
    public static void forgetPlayer(UUID id) { ARMED.remove(id); }

    public static boolean isLost(TamableAnimal pet) {
        if (PetBook.isLost(pet)) return true;
        MinecraftServer server = pet.level().getServer();
        if (server == null) return false;
        LostPets.Entry e = LostPets.get(server).entry(pet.getUUID());
        return e != null && e.lost;
    }
}
