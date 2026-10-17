package fr.minenorth.pets;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Registre serveur des animaux munis d'un carnet de santé (par UUID d'animal) : maître, numéro de carnet valide, drapeau « perdu », abandon en attente,
 * dernier lieu connu. C'est lui qui fait foi (jamais le NBT du carnet) et qui permet de signaler perdu ou d'abandonner un animal non chargé :
 * l'état est alors appliqué à l'animal dès son chargement (PetEvents.onJoin) et consulté par la lecture du collier.
 */
public final class LostPets extends SavedData {
    private static final String NAME = "minenorthpets_lost";

    public static final class Entry {
        public UUID owner;
        public String ownerPseudo = "";
        public int serial;
        public boolean lost, abandoned;
        public long lastIssue;
        public String note = "", species = "", name = "", chip = "", pos = "";
    }

    private final Map<UUID, Entry> map = new HashMap<>();

    public static LostPets get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(LostPets::load, LostPets::new, NAME);
    }

    @Nullable
    public Entry entry(UUID pet) { return map.get(pet); }

    public Entry getOrCreate(UUID pet, UUID owner) {
        return map.computeIfAbsent(pet, k -> {
            Entry e = new Entry();
            e.owner = owner;
            setDirty();
            return e;
        });
    }

    public void remove(UUID pet) {
        if (map.remove(pet) != null) setDirty();
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag list = new ListTag();
        for (Map.Entry<UUID, Entry> en : map.entrySet()) {
            Entry e = en.getValue();
            if (e.owner == null) continue;
            CompoundTag c = new CompoundTag();
            c.putUUID("pet", en.getKey());
            c.putUUID("owner", e.owner);
            c.putString("ownerPseudo", e.ownerPseudo);
            c.putInt("serial", e.serial);
            c.putLong("lastIssue", e.lastIssue);
            c.putBoolean("lost", e.lost);
            c.putBoolean("abandoned", e.abandoned);
            c.putString("note", e.note);
            c.putString("species", e.species);
            c.putString("name", e.name);
            c.putString("chip", e.chip);
            c.putString("pos", e.pos);
            list.add(c);
        }
        tag.put("pets", list);
        return tag;
    }

    private static LostPets load(CompoundTag tag) {
        LostPets data = new LostPets();
        ListTag list = tag.getList("pets", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag c = list.getCompound(i);
            if (!c.hasUUID("pet") || !c.hasUUID("owner")) continue;
            Entry e = new Entry();
            e.owner = c.getUUID("owner");
            e.ownerPseudo = c.getString("ownerPseudo");
            e.serial = c.getInt("serial");
            e.lastIssue = c.getLong("lastIssue");
            e.lost = c.getBoolean("lost");
            e.abandoned = c.getBoolean("abandoned");
            e.note = c.getString("note");
            e.species = c.getString("species");
            e.name = c.getString("name");
            e.chip = c.getString("chip");
            e.pos = c.getString("pos");
            data.map.put(c.getUUID("pet"), e);
        }
        return data;
    }
}
