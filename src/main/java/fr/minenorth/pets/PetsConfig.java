package fr.minenorth.pets;

import net.minecraftforge.common.ForgeConfigSpec;

import java.util.List;
import java.util.function.Supplier;

/**
 * config/minenorth_pets.toml (serveur dédié). Hors serveur dédié la config n'est pas chargée : les valeurs par défaut s'appliquent.
 */
public final class PetsConfig {
    public static final ForgeConfigSpec SPEC;
    private static final ForgeConfigSpec.DoubleValue FOLLOW_START, FOLLOW_STOP, FOLLOW_SPEED;
    private static final ForgeConfigSpec.BooleanValue SIT_ON_EMPTY_HAND;
    private static final ForgeConfigSpec.BooleanValue K9_POLICE_ONLY, K9_IGNORE_POLICE, K9_RIDE;
    private static final ForgeConfigSpec.IntValue K9_RADIUS, K9_INTERVAL, K9_COOLDOWN, K9_BOARD;
    private static final ForgeConfigSpec.ConfigValue<List<? extends String>> K9_ITEMS;
    private static final ForgeConfigSpec.ConfigValue<String> VET_COMPANY;
    private static final ForgeConfigSpec.BooleanValue LOST_ALERT, COMBAT_ENABLED;
    private static final ForgeConfigSpec.IntValue COMBAT_SECONDS, COMBAT_KO_PERCENT, COMBAT_RANGE, COMBAT_ANSWER;
    private static final ForgeConfigSpec.DoubleValue COMBAT_DAMAGE;
    private static final ForgeConfigSpec.BooleanValue CARRY_ENABLED, KO_ENABLED, PARROT_ENABLED;
    private static final ForgeConfigSpec.IntValue CARRY_SLOWNESS, KO_MINUTES;

    private static final List<String> DEFAULT_ITEMS = List.of(
            "minenorthdrogue:joint", "minenorthdrogue:cocaine", "minenorthdrogue:meth",
            "minenorthdrogue:cannabis_leaf", "minenorthdrogue:coca_leaf", "minenorthdrogue:ephedra",
            "minenorthdrogue:coca_paste", "minenorthdrogue:cocaine_powder", "minenorthdrogue:meth_crystals",
            "minenorthdrogue:cannabis_seeds", "minenorthdrogue:coca_seeds", "minenorthdrogue:ephedra_seeds");

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();
        b.comment("Animaux apprivoisés (Polly Pets)").push("compagnon");
        FOLLOW_START = b.comment("Distance (blocs) à partir de laquelle l'animal rejoint son maître").defineInRange("distanceSuivi", 8.0, 2.0, 64.0);
        FOLLOW_STOP = b.comment("Distance (blocs) à laquelle l'animal s'arrête près de son maître").defineInRange("distanceArret", 3.0, 1.0, 32.0);
        FOLLOW_SPEED = b.comment("Vitesse de l'animal quand il suit son maître").defineInRange("vitesse", 1.1, 0.3, 3.0);
        SIT_ON_EMPTY_HAND = b.comment("Clic droit à main nue sur son animal apprivoisé : s'asseoir / se lever").define("asseoirMainNue", true);
        b.pop();
        b.comment("Chien policier K9").push("k9");
        K9_POLICE_ONLY = b.comment("Seuls les policiers (mod Police) peuvent équiper un chien en K9").define("reserveAPolice", true);
        K9_IGNORE_POLICE = b.comment("Le chien K9 ne signale pas les policiers").define("ignorerPolice", true);
        K9_RADIUS = b.comment("Rayon de flair (blocs)").defineInRange("rayon", 8, 2, 32);
        K9_INTERVAL = b.comment("Délai (ticks) entre deux flairs").defineInRange("intervalleTicks", 20, 5, 200);
        K9_COOLDOWN = b.comment("Secondes avant que le chien signale à nouveau la même personne").defineInRange("secondesEntreAlertes", 15, 1, 600);
        K9_RIDE = b.comment("Le K9 monte dans les véhicules MTS de son maître (place passager libre)").define("monteEnVoiture", true);
        K9_BOARD = b.comment("Distance maximale (blocs) entre le K9 et son maître pour qu'il monte").defineInRange("distanceMontee", 10, 2, 32);
        K9_ITEMS = b.comment("Objets que le chien sait flairer (id complet, ex. minenorthdrogue:meth)")
                .defineList("objetsFlaires", DEFAULT_ITEMS, o -> o instanceof String);
        b.pop();
        b.comment("Vétérinaire et carnet de santé").push("veterinaire");
        VET_COMPANY = b.comment("Nom de l'entreprise (mod Entreprises) dont les membres sont vétérinaires. Vide = personne (les opérateurs le sont toujours)")
                .define("entreprise", "");
        b.pop();
        b.comment("Collier d'identification").push("collier");
        LOST_ALERT = b.comment("« Signaler perdu / retrouvé » envoie un message à la police en service (mod Police)").define("alerterPolice", true);
        b.pop();
        b.comment("Combats d'animaux (jeu : sans mort)").push("combat");
        COMBAT_ENABLED = b.comment("Autorise « Défier » entre animaux apprivoisés de maîtres différents").define("actif", true);
        COMBAT_SECONDS = b.comment("Durée maximale d'un combat (secondes), puis match nul").defineInRange("dureeMaxSecondes", 60, 10, 600);
        COMBAT_DAMAGE = b.comment("Dégâts de chaque coup (en points de vie ; 1.0 = un demi-coeur), un coup toutes les 15 ticks").defineInRange("degats", 1.0, 0.5, 4.0);
        COMBAT_KO_PERCENT = b.comment("Seuil de KO : le combat s'arrête quand la vie passerait sous ce pourcentage de la vie maximale").defineInRange("seuilKoPourcent", 30, 5, 60);
        COMBAT_RANGE = b.comment("Portée du défi (blocs) : l'adversaire est l'animal apprivoisé le plus proche dans ce rayon").defineInRange("porteeDefi", 8, 2, 32);
        COMBAT_ANSWER = b.comment("Temps laissé au maître défié pour accepter (secondes)").defineInRange("delaiReponseSecondes", 30, 5, 300);
        b.pop();
        b.comment("Perroquet vanilla (dont le Gris du Gabon)").push("perroquet");
        PARROT_ENABLED = b.comment("Le perroquet apprivoisé est un animal de compagnie : menu, carnet de santé, collier, inconscient à la place de la mort, « Faire parler »").define("actif", true);
        b.pop();
        b.comment("Porter son animal").push("portage");
        CARRY_ENABLED = b.comment("Autorise « Porter » / « Poser » dans le menu de l'animal").define("actif", true);
        CARRY_SLOWNESS = b.comment("Niveau de Lenteur du porteur (0 = aucune, 1 = Lenteur I, 2 = Lenteur II…)").defineInRange("lenteur", 1, 0, 4);
        b.pop();
        b.comment("Mort d'un animal apprivoisé : inconscient, à porter chez le vétérinaire").push("mort");
        KO_ENABLED = b.comment("Un animal apprivoisé qui devrait mourir tombe inconscient (/kill et le vide le tuent toujours)").define("actif", true);
        KO_MINUTES = b.comment("Minutes avant que l'animal inconscient meure pour de bon").defineInRange("delaiMinutes", 10, 1, 120);
        b.pop();
        SPEC = b.build();
    }

    private PetsConfig() {}

    /** Lit une valeur ; la config n'existe pas hors serveur dédié, on retombe alors sur la valeur par défaut. */
    private static <T> T val(Supplier<T> s, T def) {
        try { return s.get(); } catch (IllegalStateException e) { return def; }
    }

    public static double followStart() { return val(FOLLOW_START::get, 8.0); }
    public static double followStop() { return val(FOLLOW_STOP::get, 3.0); }
    public static double followSpeed() { return val(FOLLOW_SPEED::get, 1.1); }
    public static boolean sitOnEmptyHand() { return val(SIT_ON_EMPTY_HAND::get, true); }
    public static boolean k9PoliceOnly() { return val(K9_POLICE_ONLY::get, true); }
    public static boolean k9IgnorePolice() { return val(K9_IGNORE_POLICE::get, true); }
    public static int k9Radius() { return val(K9_RADIUS::get, 8); }
    public static int k9Interval() { return val(K9_INTERVAL::get, 20); }
    public static int k9CooldownSeconds() { return val(K9_COOLDOWN::get, 15); }
    public static boolean k9RidesVehicles() { return val(K9_RIDE::get, true); }
    public static int k9BoardDistance() { return val(K9_BOARD::get, 10); }
    public static String vetCompany() { return val(() -> VET_COMPANY.get(), ""); }
    public static boolean lostAlertsPolice() { return val(LOST_ALERT::get, true); }
    public static boolean parrotEnabled() { return val(PARROT_ENABLED::get, true); }
    public static boolean carryEnabled() { return val(CARRY_ENABLED::get, true); }
    public static int carrySlowness() { return val(CARRY_SLOWNESS::get, 1); }
    public static boolean koEnabled() { return val(KO_ENABLED::get, true); }
    public static int koMinutes() { return val(KO_MINUTES::get, 10); }
    public static boolean combatEnabled() { return val(COMBAT_ENABLED::get, true); }
    public static int combatSeconds() { return val(COMBAT_SECONDS::get, 60); }
    public static double combatDamage() { return val(COMBAT_DAMAGE::get, 1.0); }
    public static double combatKoRatio() { return val(COMBAT_KO_PERCENT::get, 30) / 100.0; }
    public static int combatRange() { return val(COMBAT_RANGE::get, 8); }
    public static int combatAnswerSeconds() { return val(COMBAT_ANSWER::get, 30); }
    @SuppressWarnings("unchecked")
    public static List<String> k9Items() { return val(() -> (List<String>) K9_ITEMS.get(), DEFAULT_ITEMS); }
}
