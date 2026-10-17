# MineNorth Pets

Mod Forge 1.20.1 (`minenorthpets`), complément de **Polly Pets** (obligatoire) ; GeckoLib 4.x requis (déjà nécessaire à Polly Pets ; compilé contre 4.8.4, copier le jar dans `libs/` pour compiler). Polly Pets n'a pas de sources : ce mod s'y branche par événements et ne dépend d'aucune autre classe de compilation. Mod Police et mod Drogue facultatifs (liens par réflexion / identifiants d'objets).

## Animaux apprivoisés
Les animaux de Polly Pets s'apprivoisent déjà à la nourriture (1 chance sur 3 par offrande). Ce mod ajoute ce qui manquait ensuite :
- **Suivi** : un animal apprivoisé suit son maître (et se téléporte s'il est trop loin).
- **Assis / debout** : clic droit **à main nue** sur son animal (réglage `asseoirMainNue`). Polly Pets n'a pas d'animation d'assise pour la plupart de ses modèles : la pose assise est calculée côté client à partir des os du modèle (valeurs de l'animation « sit » du beagle). Reptiles et serpent exclus.

## Menu d'actions (Ctrl + clic droit)
Ctrl + clic droit sur son animal apprivoisé ouvre un menu (écran client arrondi). Chaque bouton envoie une action au serveur, qui vérifie que le joueur est le maître et à moins de 8 blocs. Le Ctrl + clic n'asseoit pas l'animal et n'utilise pas l'objet en main : le clic est intercepté sur la touche « utiliser » du client (`InputEvent.InteractionKeyMappingTriggered`), car annuler `EntityInteract` côté client arrive trop tard (le paquet d'interaction est déjà parti).
- **Caresser** : cœurs, petit bruit, +1 cœur de vie, pause de 5 s.
- **Jouer** : petit saut, notes de musique, bruit joyeux (pause de 5 s).
- **Dormir / Réveiller** : couché (pose calculée côté client), petits « Z » qui montent, immobile ; réveil par le menu, par un clic à main nue ou par des dégâts.
- **S'asseoir / Se lever** et **Rester ici / Suivre** (reste sur place sans s'asseoir, jusqu'à « Suivre »).
Animations GeckoLib d'origine de Polly Pets (`assets/polly_pets/animations`) : seuls le beagle et le teckel ont `sleep`, `sit` et `play` ; le chat (`kitty`) a `attack`, le carlin `idle2`, la tortue `upside` ; tous les autres modèles n'ont que `idle` et `walk`. Comme Polly Pets ne les déclenche jamais (et que ça ne marcherait que pour 2 modèles), toutes les actions passent par des poses calculées, des particules et des sons : elles marchent donc pour toutes les espèces (reptiles et serpent : pas de pose assise/couchée).

## Vétérinaire et carnet de santé
Chaque animal apprivoisé a un carnet (dans les données persistantes de l'entité : il suit l'animal) : date d'adoption (au moment de l'apprivoisement), puce (numéro de 15 chiffres généré, commençant par 250), vaccins (12 derniers), soins, 8 dernières remarques (48 caractères), état du collier.
- **Carnet de santé** (bouton du menu Ctrl + clic droit, lecture seule) : le maître, les vétérinaires et la police (`PoliceApi.isPolice`).
- **Vétérinaire** : opérateur, ou patron / employé de l'entreprise `[veterinaire] entreprise` (nom exact de l'entreprise, vide = personne) via `CompanyService` de l'API MineNorth, appelé par réflexion (le mod fonctionne sans l'API : seuls les opérateurs sont alors vétérinaires). Actions : **Vacciner**, **Soigner** (vie complète + cœurs), **Ajouter une remarque**, **Poser la puce**. Le vétérinaire ouvre le menu sur l'animal de n'importe qui ; le serveur revérifie le droit à chaque action.

## Collier d'identification et animal perdu
- **Collier d'identification** (5 cuirs + 1 pépite de fer, onglet Animaux & Vêtements) : clic droit sur son animal = enregistre le nom RP du maître (identité de l'API si dispo, sinon pseudo). Le collier est consommé.
- **Accroupi, main nue, sur l'animal d'un autre** : « Cet animal appartient à <nom> » (signalé perdu le cas échéant).
- **Signaler perdu / retrouvé** (menu, collier requis) : drapeau lisible sur le collier et dans le carnet, et message à la police en service (`PoliceService.alertPolice` par réflexion ; `[collier] alerterPolice` pour le couper). Pas de fourrière ni de faim.

## Combats d'animaux
Jeu sans mort entre animaux apprivoisés de **maîtres différents**, uniquement si les deux maîtres sont d'accord.
1. **Défier** (menu Ctrl + clic droit) : l'animal apprivoisé le plus proche (≤ `porteeDefi` blocs) dont le maître est un autre joueur, en ligne.
2. Le maître défié reçoit un message cliquable et accepte avec **Accepter le combat** (menu de son animal) ou `/pets accepter`, dans les `delaiReponseSecondes` ; sans accord, rien ne se passe.
3. Combat : `FightGoal` (priorité 0) rapproche les animaux ; un coup de `degats` points de vie toutes les 15 ticks (source `mobAttack` entre les deux animaux seulement, son de grognement, particules, balancement de patte). Pas d'animation d'attaque GeckoLib (seul le chat en a une, jamais déclenchée par Polly Pets).
4. **KO** : un coup qui ferait passer un animal sous `seuilKoPourcent` % de sa vie max ne passe pas ; le combat s'arrête, vainqueur annoncé aux deux maîtres (barre d'action), cœurs / feux d'artifice, le perdant reste assis 10 s. Filet de sécurité : tout dégât mortel sur un animal en combat est annulé et déclenche le KO. Durée max `dureeMaxSecondes`, puis match nul. **Séparer** arrête le combat.
Rien n'est enregistré sur disque : un redémarrage met fin à tout combat. Config `[combat]` : `actif`, `dureeMaxSecondes`, `degats`, `seuilKoPourcent`, `porteeDefi`, `delaiReponseSecondes`.

## Animaux en voiture (MTS)
Menu Ctrl + clic droit : **Monter en voiture** / **Descendre** (animaux de Polly Pets, pas le perroquet ; propriétaire uniquement, le serveur revérifie propriété et portée). « Monter » installe l'animal (`MtsRide.board` : place passager libre, jamais le poste de conduite) dans le véhicule MTS où le propriétaire est assis, sinon dans le véhicule le plus proche (6 blocs) ; l'animal doit être à moins de `[k9] distanceMontee` blocs. Refus avec message si l'animal est assis, endormi, inconscient, en combat, porté ou déjà monté, s'il n'y a aucun véhicule ou aucune place libre.
Un animal monté descend si on le met assis, ou quand son propriétaire descend du véhicule **après y avoir été avec lui** (monté depuis l'extérieur par le menu, il reste en place jusqu'à « Descendre »). Seul le **K9** monte tout seul (`[k9] monteEnVoiture`). Config `[vehicule] menu` pour couper l'action manuelle.

## Carnet de santé de poche
- **Remise** : en posant le collier sur son animal, le maître reçoit l'objet `carnet_sante` (au sol à ses pieds si l'inventaire est plein). Il porte l'UUID de l'animal et un instantané lisible (nom, espèce, puce, vaccins, soins, remarques, adoption, dernier lieu connu, signalement), rafraîchi à chaque ouverture si l'animal est chargé. **Un seul carnet valable par animal** : poser de nouveau un collier sur un animal déjà équipé (clic droit, le collier n'est pas consommé) remet un carnet et invalide l'ancien.
- **Clic droit sur le carnet** : fiche + **Signaler perdu / retrouvé** + **Abandonner l'animal**.
- **Perdu / retrouvé** : bascule le drapeau sur l'animal s'il est chargé, dans l'instantané, et dans le registre serveur `LostPets` (`SavedData` `minenorthpets_lost`, par UUID d'animal). Si l'animal n'est pas chargé, le drapeau est appliqué à son chargement ; la lecture du collier consulte aussi le registre. Message à la police en service (`Compat.alertPolice`) avec le **pseudo** du maître, l'espèce, la puce et le dernier lieu connu (mis à jour à chaque fois que l'animal quitte le monde). Le menu Ctrl + clic « Signaler perdu » utilise le même code, donc le même état.
- **Abandonner** : confirmation par un second clic dans les 5 s (le serveur arme puis exécute ; le bouton passe en rouge). L'animal chargé perd son maître et son collier (`setTame(false)`, propriétaire effacé, états K9 / combat / inconscient / portage / assise / sommeil retirés) et redevient libre ; s'il n'est pas chargé, l'abandon est enregistré dans le registre et appliqué à son chargement. Le carnet est consommé et une note « Abandonné par <nom RP> le <date> » est gardée dans le carnet de l'animal.
- **Récupérer le carnet** (menu de l'animal) : si le maître a perdu l'objet (jeté, détruit, mort), un **nouveau carnet** lui est remis gratuitement, après un délai de sécurité (`[carnet] delaiRecuperationSecondes`, 60 s depuis la dernière remise). Le registre serveur (`LostPets`) est la source de vérité : le numéro de carnet est incrémenté et les exemplaires de l'inventaire retirés ; l'ancien carnet, où qu'il soit, répond « Ce carnet n'est plus valide ». Maître vérifié par UUID, animal chargé et à portée ; collier obligatoire.
- **Sécurité** : le serveur vérifie toujours le registre (maître + numéro de carnet) et, si l'animal est chargé, qu'il appartient bien au porteur. Un carnet d'un autre joueur ou périmé ne fonctionne pas. `[carnet] actif` pour couper la fonction.

## Perroquet et Gris du Gabon
- **Gris du Gabon** : espèce virtuelle `minenorthpets:gris_du_gabon` dans la liste des certificats d'adoption (onglet Animaux & Vêtements, `/pets certificat` avec autocomplétion, boutiques via le NBT). Le certificat fait apparaître un `minecraft:parrot` de variante grise (`Parrot.Variant.GRAY`), apprivoisé au nom de l'acheteur, avec son carnet de santé. Pas de nouveau modèle.
- **Tout perroquet apprivoisé** (toute variante, `[perroquet] actif`) est un animal de compagnie : menu Ctrl + clic droit (**Caresser**, **S'asseoir / Se lever**, **Faire parler**, **Signaler perdu / retrouvé** avec collier, **Carnet de santé**, actions du vétérinaire), collier d'identification, **inconscient à la place de la mort** et **Réanimer**.
- **Faire parler** : son d'ambiance du perroquet + notes de musique, pause de 3 s.
- Volontairement **sans** K9, combat, portage, jeu, sommeil ni « Rester ici ». L'assise à main nue, le vol et l'épaule (accroupi + clic droit) restent ceux de Minecraft (le perroquet vanilla a déjà son `FollowOwnerGoal` et son `SitWhenOrderedToGoal` : rien n'est ajouté).
- Code : `PetEvents.isPollyPet` (Polly Pets : tout), `isParrot`, `isPet` (Polly Pets + perroquet) ; `PetActions.performChecked` refuse côté serveur les actions qui n'existent pas pour le perroquet.

## Porter son animal
Menu Ctrl + clic droit -> **Porter** (propriétaire, à portée ; le serveur revérifie). L'animal n'est pas passager du joueur (un passager de joueur est posé à hauteur de la tête et bouche la vue) : il est replacé à chaque tick dans les bras du porteur (devant, à droite, à hauteur de poitrine), assis, sans IA ni gravité. Le porteur reçoit Lenteur (`[portage] lenteur`, 0 = aucune ; `actif`). Un animal assis, endormi ou inconscient peut être porté, un animal en combat non.
**Poser** : menu (Ctrl + clic droit, y compris dans le vide quand on porte : le client le sait par un petit paquet), ou `/pets poser`. Pose automatique à la déconnexion, à la mort du porteur, au changement de dimension et à l'entrée dans un véhicule MTS. Une téléportation dans la même dimension emmène l'animal. Un animal marqué « porté » sans porteur (redémarrage) est libéré à son chargement. Pendant le portage, Ctrl + clic droit est réservé au menu (pas de clic droit + Ctrl sur un bloc).

## Mort d'un animal : inconscient
Un animal apprivoisé de Polly Pets avec un maître qui devrait mourir ne meurt pas (`LivingDeathEvent` annulé, remis à 1 PV) : il est **inconscient** (IA coupée, invulnérable, couché avec petits « Z », plus ciblé par les mobs). État et échéance (temps de jeu) dans son NBT : ils survivent aux redémarrages. Le maître le **porte** jusqu'au vétérinaire (entreprise `[veterinaire]` ou opérateur) qui utilise **Réanimer** (menu, visible seulement sur un animal inconscient) : au moins 50 % de vie, IA rétablie, cœurs, ligne « Réanimé par <nom RP> le <date> » dans le carnet. Délai `[mort] delaiMinutes` écoulé : mort réelle (message au maître, rappel à la dernière minute). `/kill` et le vide (sources qui contournent l'invulnérabilité) tuent toujours vraiment. Animaux sauvages ou sans maître : mort normale. `[mort] actif` pour couper la fonction.

## K9 : pseudo, jamais le nom RP
Les messages de détection du K9 (au maître, au suspect) et les annonces de combat (défi, accord) affichent le pseudo Minecraft du joueur (`getGameProfile().getName()`, jamais `getDisplayName()`). Le nom RP (`Compat.displayName`) n'est utilisé que pour le collier, le carnet de santé et le signalement d'animal perdu.

## Langues
Tout est en français. `en_us.json` reprend le même français (le client peut être en anglais) pour ne jamais afficher de clé brute. `assets/polly_pets/lang/fr_fr.json` et `en_us.json` (dans ce mod) traduisent en français les entités, œufs d'apparition, niches, litières, gamelles et paniers de Polly Pets, avec les mêmes clés que le jar d'origine ; ce mod se charge après Polly Pets, donc sa traduction l'emporte.

## Onglet créatif
`minenorthpets:animaux_vetements` (« Animaux & Vêtements » / « Animals & Clothing ») : harnais K9 et certificats d'adoption. Le mod `minenorth-vetements` s'y ajoute via `BuildCreativeModeTabContentsEvent` sur cette clé. Ces objets ne sont plus dans l'onglet commun MineNorth.

## Acheter un animal
- **Certificat d'adoption** (`certificat_adoption`) : l'espèce est dans le NBT. Clic droit sur un bloc : l'animal apparaît **déjà apprivoisé**, à son acheteur (le certificat est consommé).
- Admin : `/pets certificat <espece>` (ex. `polly_pets:german_shepherd`, avec autocomplétion), ou l'onglet créatif MineNorth (un certificat par espèce, adultes).
- Vente : dans `/shops`, ajouter l'article en le choisissant dans l'inventaire (`minenorth_shops` conserve le NBT) et fixer le prix. Un article par espèce à vendre.

## Chien policier K9
- **Harnais K9** (`harnais_k9`, 4 pépites de fer + 4 cuirs + 1 laisse en établi) : clic droit sur **son chien apprivoisé** pour l'équiper (ou le déséquiper). Réservé aux policiers (`reserveAPolice` ; sans mod Police, aux opérateurs). Seuls les chiens peuvent être K9 (pas les chats, hamsters, reptiles…).
- Le K9 flaire les joueurs dans un rayon de `rayon` blocs toutes les `intervalleTicks` ticks. S'il trouve de la drogue dans leur inventaire, il va vers eux, grogne, et le maître reçoit une alerte. Les policiers ne sont pas signalés (`ignorerPolice`), ni le maître.
- Objets flairés : liste `objetsFlaires` (par défaut tous les objets du mod Drogue : joint, cocaïne, méthamphétamine, feuilles, pâte, poudre, cristaux, graines).
- **Véhicules MTS** : quand son maître s'assoit dans un véhicule MTS, le K9 (à moins de `distanceMontee` blocs) monte sur une place passager libre (jamais le poste de conduite) ; il en descend quand le maître descend ou l'assoit (`monteEnVoiture`).
- **Harnais visible** : le K9 porte des sangles bleues avec badges jaunes, dessinés par une couche GeckoLib sur le corps (le statut K9 est envoyé aux clients par un paquet : même jar des deux côtés). Journaux `[MineNorthPets]` dans le `latest.log` du client : installation des couches, statut K9 reçu, harnais dessiné, pose assise appliquée (ou refusée, avec la raison).
- Un K9 assis ne flaire pas.

Config : `config/minenorth_pets.toml` (serveur dédié).

## Licence
**Tous droits réservés - MineNorthRP.** Voir [LICENSE](LICENSE).

## Compilation
Mettre `geckolib-forge-1.20.1-4.8.4.jar` dans `libs/` (compilation seulement, non versionné), puis `./gradlew build` (JDK 17).
