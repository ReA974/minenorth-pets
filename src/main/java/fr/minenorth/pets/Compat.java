package fr.minenorth.pets;

import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.fml.ModList;

import java.lang.reflect.Method;

/** Liens facultatifs avec le mod Police, par réflexion (même principe que les autres mods MineNorth) : aucune dépendance de compilation. */
public final class Compat {
    private Compat() {}

    private static Method isPolice;

    private static boolean policeLoaded() { return ModList.get().isLoaded("minenorthpolice"); }

    /** Vrai si le joueur fait partie des effectifs de la police. Faux si le mod Police est absent ou si l'appel échoue. */
    public static boolean isPolice(ServerPlayer p) {
        if (!policeLoaded()) return false;
        try {
            if (isPolice == null) isPolice = Class.forName("fr.minenorth.police.api.PoliceApi").getMethod("isPolice", ServerPlayer.class);
            return (boolean) isPolice.invoke(null, p);
        } catch (Throwable t) {
            return false;
        }
    }

    /** Qui peut équiper un chien K9 : un policier ; sans le mod Police, un opérateur (niveau 2). */
    public static boolean canMakeK9(ServerPlayer p) {
        if (!PetsConfig.k9PoliceOnly()) return true;
        return policeLoaded() ? isPolice(p) : p.hasPermissions(2);
    }
}
