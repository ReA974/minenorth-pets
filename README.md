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

## K9 : pseudo, jamais le nom RP
Les messages de détection du K9 (au maître, au suspect) affichent le pseudo Minecraft du suspect (`getGameProfile().getName()`). Le nom RP (`Compat.displayName`) n'est utilisé que pour le collier, le carnet de santé et le signalement d'animal perdu.

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
