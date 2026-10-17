package fr.minenorth.pets;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.registries.ForgeRegistries;

import javax.annotation.Nullable;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

/**
 * Lien avec Minecraft Transport Simulator / Immersive Vehicles (modid « mts »), par réflexion : rien n'est requis à la compilation.
 * Un joueur assis dans un véhicule MTS est passager d'une entité « mts:builder_seat » dont le champ « entity » est le siège (PartSeat) ;
 * le siège connaît son véhicule (vehicleOn) et la liste de toutes les pièces (allParts). Un animal s'y installe avec PartSeat.setRider,
 * comme le fait MTS pour un animal tenu en laisse. Si une version de MTS change ces noms, on ne fait rien (aucun plantage).
 */
public final class MtsRide {
    private MtsRide() {}

    private static Method wrapperFor;

    public static boolean loaded() { return ModList.get().isLoaded("mts"); }

    private static boolean isBuilderSeat(@Nullable Entity e) {
        if (e == null) return false;
        ResourceLocation k = ForgeRegistries.ENTITY_TYPES.getKey(e.getType());
        return k != null && "mts".equals(k.getNamespace()) && "builder_seat".equals(k.getPath());
    }

    @Nullable
    private static Object field(@Nullable Object o, String name) {
        if (o == null) return null;
        for (Class<?> c = o.getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
            try {
                Field f = c.getDeclaredField(name);
                f.setAccessible(true);
                return f.get(o);
            } catch (NoSuchFieldException ignored) {
            } catch (Throwable t) {
                return null;
            }
        }
        return null;
    }

    private static boolean isPartSeat(@Nullable Object o) { return o != null && o.getClass().getSimpleName().equals("PartSeat"); }

    private static boolean isBuilderExisting(@Nullable Entity e) {
        if (e == null) return false;
        ResourceLocation k = ForgeRegistries.ENTITY_TYPES.getKey(e.getType());
        return k != null && "mts".equals(k.getNamespace()) && "builder_existing".equals(k.getPath());
    }

    private static boolean isVehicleObject(@Nullable Object o) {
        for (Class<?> c = o == null ? null : o.getClass(); c != null; c = c.getSuperclass()) {
            if (c.getSimpleName().equals("EntityVehicleF_Physics")) return true;
        }
        return false;
    }

    /** Le véhicule MTS le plus proche de cette entité dans ce rayon (blocs), sinon null. */
    @Nullable
    public static Object nearestVehicle(Entity around, double range) {
        Object best = null;
        double bestDist = Double.MAX_VALUE;
        for (Entity e : around.level().getEntities(around, around.getBoundingBox().inflate(range))) {
            if (!isBuilderExisting(e)) continue;
            Object inner = field(e, "entity");
            if (!isVehicleObject(inner)) continue;
            double d = e.distanceToSqr(around);
            if (d <= range * range && d < bestDist) {
                bestDist = d;
                best = inner;
            }
        }
        return best;
    }

    /** Siège MTS dans lequel cette entité est assise, sinon null. */
    @Nullable
    public static Object seatOf(@Nullable Entity rider) {
        if (rider == null) return null;
        Entity v = rider.getVehicle();
        if (!isBuilderSeat(v)) return null;
        Object seat = field(v, "entity");
        return isPartSeat(seat) ? seat : null;
    }

    @Nullable
    public static Object vehicleOf(@Nullable Object seat) { return field(seat, "vehicleOn"); }

    /** Installe l'animal dans une place passager libre du véhicule (jamais le poste de conduite). Vrai si c'est fait. */
    public static boolean board(Entity pet, @Nullable Object vehicle) {
        if (vehicle == null || !(field(vehicle, "allParts") instanceof List<?> parts)) return false;
        try {
            if (wrapperFor == null) wrapperFor = Class.forName("mcinterface1201.WrapperEntity").getMethod("getWrapperFor", Entity.class);
            Object wrapper = wrapperFor.invoke(null, pet);
            for (Object part : parts) {
                if (!isPartSeat(part) || field(part, "rider") != null) continue;
                Object def = field(part, "placementDefinition");
                if (def == null || Boolean.TRUE.equals(field(def, "isController"))) continue;
                Object active = field(field(part, "isActiveVar"), "isActive");
                if (Boolean.FALSE.equals(active)) continue;
                for (Method m : part.getClass().getMethods()) {
                    if (m.getName().equals("setRider") && m.getParameterCount() == 2 && m.getParameterTypes()[1] == boolean.class) {
                        if (Boolean.TRUE.equals(m.invoke(part, wrapper, true))) return true;
                    }
                }
            }
        } catch (Throwable t) {
            return false;
        }
        return false;
    }

    /** Fait descendre l'occupant de ce siège. */
    public static void eject(Object seat) {
        try {
            seat.getClass().getMethod("removeRider").invoke(seat);
        } catch (Throwable ignored) {
        }
    }
}
