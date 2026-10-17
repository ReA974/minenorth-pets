package fr.minenorth.pets;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.fml.ModList;

import java.lang.reflect.Method;
import java.util.UUID;

/**
 * Liens facultatifs avec les autres mods MineNorth (Police, API, Entreprises), par réflexion : aucune dépendance de compilation, et le mod
 * reste entièrement fonctionnel si l'un d'eux est absent (la fonction concernée est simplement désactivée).
 */
public final class Compat {
    private Compat() {}

    private static Method isPolice, alertPolice, displayName, companies, idOf, isMember;

    private static boolean policeLoaded() { return ModList.get().isLoaded("minenorthpolice"); }
    private static boolean apiLoaded() { return ModList.get().isLoaded("minenorthapi"); }

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

    /** Message dans le canal de la police en service. Sans le mod Police : rien. */
    public static void alertPolice(MinecraftServer s, String text) {
        if (!policeLoaded()) return;
        try {
            if (alertPolice == null) alertPolice = Class.forName("fr.minenorth.police.PoliceService").getMethod("alertPolice", MinecraftServer.class, String.class);
            alertPolice.invoke(null, s, text);
        } catch (Throwable ignored) {
        }
    }

    /** Nom RP « Prénom Nom » (mod Identité via l'API), sinon le repli donné (pseudo). */
    public static String displayName(MinecraftServer s, UUID id, String fallback) {
        if (id == null) return fallback;
        if (!apiLoaded()) return fallback;
        try {
            if (displayName == null) displayName = Class.forName("fr.minenorth.api.MineNorth").getMethod("displayName", MinecraftServer.class, UUID.class);
            Object name = displayName.invoke(null, s, id);
            return name instanceof String str && !str.isBlank() ? str : fallback;
        } catch (Throwable t) {
            return fallback;
        }
    }

    /**
     * Vétérinaire : opérateur (niveau 2), ou patron / employé de l'entreprise nommée dans la config (veterinaire.entreprise, vide = personne,
     * sauf les opérateurs). Vérifié via CompanyService de l'API MineNorth.
     */
    public static boolean isVet(ServerPlayer p) {
        if (p.hasPermissions(2)) return true;
        String company = PetsConfig.vetCompany();
        if (company.isBlank() || !apiLoaded()) return false;
        try {
            if (companies == null) {
                companies = Class.forName("fr.minenorth.api.MineNorth").getMethod("companies");
                Class<?> svc = Class.forName("fr.minenorth.api.CompanyService");
                idOf = svc.getMethod("idOf", MinecraftServer.class, String.class);
                isMember = svc.getMethod("isMember", ServerPlayer.class, int.class, boolean.class);
            }
            Object service = companies.invoke(null);
            int id = (int) idOf.invoke(service, p.server, company);
            return id != 0 && (boolean) isMember.invoke(service, p, id, false);
        } catch (Throwable t) {
            return false;
        }
    }
}
