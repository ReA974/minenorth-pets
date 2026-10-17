package fr.minenorth.pets;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.TamableAnimal;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Carnet de santé et collier d'un animal, dans les données persistantes de l'entité (ils suivent donc l'animal, pas l'objet).
 * Carnet : date d'adoption, puce, vaccins, soins, remarques. Collier : nom du maître, drapeau « perdu ».
 */
public final class PetBook {
    private PetBook() {}

    private static final String BOOK = "minenorthpets_book";
    private static final String COLLAR = "minenorthpets_collar";
    private static final int MAX_VACCINES = 12, MAX_REMARKS = 8, MAX_REMARK_LENGTH = 48;

    private static String today() { return LocalDate.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy")); }

    /** Le carnet de l'animal ; créé (date d'adoption = aujourd'hui) s'il n'existe pas encore. */
    public static CompoundTag ensure(Entity pet) {
        CompoundTag data = pet.getPersistentData();
        if (!data.contains(BOOK)) {
            CompoundTag b = new CompoundTag();
            b.putString("adopt", today());
            data.put(BOOK, b);
        }
        return data.getCompound(BOOK);
    }

    public static boolean hasChip(Entity pet) { return !ensure(pet).getString("chip").isEmpty(); }

    /** Pose la puce (numéro de 15 chiffres commençant par 250) ; renvoie le numéro, ou null si l'animal en a déjà une. */
    public static String chip(Entity pet) {
        CompoundTag b = ensure(pet);
        if (!b.getString("chip").isEmpty()) return null;
        StringBuilder sb = new StringBuilder("250");
        for (int i = 0; i < 12; i++) sb.append(ThreadLocalRandom.current().nextInt(10));
        b.putString("chip", sb.toString());
        return sb.toString();
    }

    public static void vaccinate(Entity pet) {
        ListTag list = ensure(pet).getList("vaccines", Tag.TAG_STRING);
        list.add(StringTag.valueOf(today()));
        while (list.size() > MAX_VACCINES) list.remove(0);
        ensure(pet).put("vaccines", list);
    }

    public static void care(Entity pet) {
        CompoundTag b = ensure(pet);
        b.putInt("cares", b.getInt("cares") + 1);
        b.putString("lastCare", today());
    }

    public static void remark(Entity pet, String text) {
        ListTag list = ensure(pet).getList("remarks", Tag.TAG_STRING);
        list.add(StringTag.valueOf(today() + " : " + clean(text)));
        while (list.size() > MAX_REMARKS) list.remove(0);
        ensure(pet).put("remarks", list);
    }

    /** Texte d'une remarque : sans codes de couleur ni retours à la ligne, 48 caractères au plus. */
    public static String clean(String text) {
        String t = text.replace("§", "").replaceAll("[\\r\\n\\t]", " ").trim();
        return t.length() > MAX_REMARK_LENGTH ? t.substring(0, MAX_REMARK_LENGTH) : t;
    }

    // ------------------------------------------------------------------ collier

    public static boolean hasCollar(Entity pet) { return pet.getPersistentData().contains(COLLAR); }

    public static String collarOwner(Entity pet) { return pet.getPersistentData().getCompound(COLLAR).getString("owner"); }

    public static boolean isLost(Entity pet) { return pet.getPersistentData().getCompound(COLLAR).getBoolean("lost"); }

    public static void putCollar(Entity pet, String ownerName) {
        CompoundTag c = new CompoundTag();
        c.putString("owner", ownerName);
        pet.getPersistentData().put(COLLAR, c);
    }

    public static void setLost(Entity pet, boolean lost) {
        if (hasCollar(pet)) pet.getPersistentData().getCompound(COLLAR).putBoolean("lost", lost);
    }

    // ------------------------------------------------------------------ lecture

    /** Lignes de l'écran « Carnet de santé » (texte français avec codes de couleur). */
    public static List<String> lines(MinecraftServer server, TamableAnimal pet) {
        CompoundTag b = ensure(pet);
        List<String> out = new ArrayList<>();
        out.add("§e§lCarnet de santé");
        out.add("§7Nom : §f" + (pet.hasCustomName() ? pet.getCustomName().getString() : "—"));
        out.add("§7Espèce : §f" + pet.getType().getDescription().getString());
        String fallback = pet.getOwner() != null ? pet.getOwner().getName().getString() : "Inconnu";
        out.add("§7Maître : §f" + Compat.displayName(server, pet.getOwnerUUID(), hasCollar(pet) ? collarOwner(pet) : fallback));
        out.add("§7Adoption : §f" + b.getString("adopt"));
        String chip = b.getString("chip");
        out.add("§7Puce : " + (chip.isEmpty() ? "§cnon pucé" : "§f" + chip));
        ListTag vaccines = b.getList("vaccines", Tag.TAG_STRING);
        out.add("§7Vaccins : " + (vaccines.isEmpty() ? "§caucun" : "§f" + vaccines.size() + " (dernier : " + vaccines.getString(vaccines.size() - 1) + ")"));
        int cares = b.getInt("cares");
        out.add("§7Soins : " + (cares == 0 ? "§faucun" : "§f" + cares + " (dernier : " + b.getString("lastCare") + ")"));
        out.add("§7Collier : " + (hasCollar(pet) ? (isLost(pet) ? "§c§lSIGNALÉ PERDU" : "§aen règle") : "§fnon"));
        ListTag remarks = b.getList("remarks", Tag.TAG_STRING);
        out.add("§7Remarques :" + (remarks.isEmpty() ? " §8aucune" : ""));
        for (int i = 0; i < remarks.size(); i++) out.add("§f - " + remarks.getString(i));
        return out;
    }
}
