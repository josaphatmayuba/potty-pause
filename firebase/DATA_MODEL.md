# Contrat de données Firestore (v2) — Pause Pipi / Potty Pause

Projet Firebase `rappelpipi`, base `(default)`, région eur3. Règles : `firestore.rules` (ce document et les règles forment le contrat ; ne pas changer l'un sans l'autre).
Pas de Cloud Function. Tout se fait côté client avec les règles. Pas de PIN dans le cloud (ni clair, ni hash).

## Identités
- **Tablette** : Firebase Auth **anonyme**. `tabletId` = `auth.uid` de la tablette. Si la tablette est réinitialisée, nouvel UID, donc ré-appairage.
- **Parent** : connexion **Google uniquement** (`sign_in_provider == google.com`). `ownerUid` = `auth.uid` du parent. Les autres méthodes (e-mail/mot de passe, etc.) sont refusées par les règles.

## Collections

### `users/{parentUid}` (parent seul : get / create / update / delete)
| champ | type | notes |
|---|---|---|
| email | string ≤ 254 | optionnel |
| locale | string ≤ 10 | `fr`, `en` |
| createdAt | timestamp | optionnel |
| deleteRequested | bool | optionnel |

### `tablets/{tabletId}`
Écrit par la tablette (état), par le parent (demande d'appairage, puis nom et réglages) et par la tablette (confirmation). Aucun champ en dehors de cette liste.

| champ | type | écrit par | notes |
|---|---|---|---|
| ownerUid | string \| **null** | tablette (création = `null`, confirmation) | `null` = non appairée. Seule la **tablette** peut le renseigner, en confirmant |
| createdAt | timestamp | tablette (création) | `serverTimestamp()` |
| lastSeen | timestamp | tablette | `serverTimestamp()` obligatoire (battement ~15 min + à chaque changement d'état) |
| appVersion | string ≤ 20 | tablette | ex. `"1.8"` |
| appCode | int 0..99999 | tablette | versionCode |
| androidApi | int 1..99 | tablette | |
| alerts | list ≤ 10 de string | tablette | valeurs : `overlay_denied`, `service_stopped`, `battery_optimized`, `notifications_denied`, `offline_long` |
| applied | map `{rev:int, at:timestamp}` | tablette | dernière révision de réglages appliquée ; `at` = `serverTimestamp()` |
| pairCode | string `^[0-9]{8}$` | tablette | modifiable seulement tant que `ownerUid == null` |
| pairExpires | timestamp | tablette | dans le futur et ≤ now + 15 min (usage : now + 10 min) |
| pendingOwnerUid | string | parent (demande) | = `auth.uid` du parent demandeur |
| pendingOwnerEmail | string | parent (demande) | = `auth.token.email` ; **à afficher sur la tablette** pour la confirmation |
| pendingAt | timestamp | parent (demande) | `request.time` ; la demande expire après 5 min |
| claimCode | string | parent (demande) | doit être égal à `pairCode` |
| name | string 1..40 | parent | nom de la tablette / de l'enfant (conseiller un prénom ou surnom, pas de nom complet) |
| settings | map | parent propriétaire | voir ci-dessous |

`settings` (clés autorisées uniquement ; seule `rev` est obligatoire) :
| clé | type / bornes | équivalent RemoteConfig |
|---|---|---|
| rev | int ≥ 1, **strictement croissant** | `rev` (anti-rejeu) |
| interval | int 1..600 | `interval` |
| lock | int 1..120 | `lock` |
| message | string ≤ 200 | message personnalisé |
| theme | `blocs geant bd sablier feu nuit ballon carte porte bouton aqua dino fusee train goutte photo hasard` | `theme` |
| anim | **string** `hasard`, `0`..`6` | `anim` |
| character | `hasard`, `fam:mc`, `fam:sp`, `mc:0`..`mc:17`, `sp:1`..`sp:12` | `character` |
| iconHidden | bool | `iconHidden` (ignoré en variante Play) |
| language | `""`, `fr`, `en` | langue |
| enabled | bool | rappel actif / en pause |
| updateNow | int ≥ 0 | `updateNow` (déclencheur ; la tablette applique si > dernier traité) |
| updatedAt | timestamp | **toujours** `serverTimestamp()` (voir ci-dessous) |

**`settings.updatedAt` :** les règles exigent `updatedAt == request.time` dès que `settings` contient la clé. Donc, à chaque écriture de `settings` : soit **réécrire la map entière** (avec `updatedAt = serverTimestamp()`), soit y mettre toujours `updatedAt = serverTimestamp()`. Une écriture partielle par chemin (`settings.theme`) conserve l'ancien `updatedAt` et sera **refusée**.

Règle côté tablette (inchangée par rapport à `RemoteConfig.kt`) : n'appliquer que si `settings.rev > rev local`, valider chaque valeur, ignorer l'invalide, puis écrire `applied = {rev, at}`.

### `pairingCodes/{code}` (`code` = **8 chiffres**, ID du document)
| champ | type | notes |
|---|---|---|
| tabletId | string | = `auth.uid` de la tablette qui l'écrit |
| expires | timestamp | futur et ≤ now + 15 min |

Lecture : parent seul, get par code exact (pas de liste), seulement si non expiré. **Un code absent, expiré ou invalide donne `permission-denied`** : le client parent doit le traiter comme « code invalide » (pas comme une panne). Un code expiré peut être écrasé par une autre tablette.
**TTL :** activer une politique TTL Firestore sur `pairingCodes.expires` (console Firebase > Firestore > TTL) pour effacer les codes périmés.

## Flux d'appairage en deux temps (sans Cloud Function)

**Tablette** (mode « tablette enfant »)
1. `signInAnonymously()` (garder l'UID).
2. Tirer un code à **8 chiffres**. Écrire `pairingCodes/{code} = {tabletId: uid, expires: now+10min}`. En cas de refus (collision), tirer un autre code.
3. **Document `tablets/{uid}`** : le créer **seulement s'il est absent** (`ownerUid: null`, `createdAt`, `lastSeen`, `pairCode`, `pairExpires`, `appVersion`, `appCode`, `androidApi`). S'il existe déjà (et `ownerUid == null`), faire seulement un `update` de `pairCode`, `pairExpires`, `lastSeen`.
4. Écouter `tablets/{uid}`. Quand `pendingOwnerUid` apparaît (et `ownerUid == null`) : afficher **« Le compte <pendingOwnerEmail> demande à gérer cette tablette »** et demander le **PIN local** (le PIN de la tablette, jamais envoyé au cloud).
   - **Accepté** (PIN correct) : `update {ownerUid: pendingOwnerUid, lastSeen}` — uniquement ces deux champs. Refusé par les règles si la demande a plus de 5 min.
   - **Refusé / abandon / PIN incorrect / demande expirée** : **supprimer puis recréer** son doc `tablets/{uid}` (efface la demande) avec un nouveau code (étape 2-3).
5. Une fois `ownerUid != null` : supprimer `pairingCodes/{code}` ; afficher « connectée ».
6. Code expiré non réclamé : en générer un nouveau (étapes 2-3).

**Parent**
1. Connexion Google. Créer `users/{uid}` si absent.
2. Saisir le code. `get pairingCodes/{code}` : `permission-denied` ou absent → « code invalide » ; sinon lire `tabletId`.
3. **Demande** : `update tablets/{tabletId}` avec exactement `{pendingOwnerUid: myUid, pendingOwnerEmail: <token.email>, pendingAt: serverTimestamp(), claimCode: code, name?: "..."}`. Les règles vérifient : tablette libre, `claimCode == pairCode`, code non expiré, **pas de demande depuis moins de 2 minutes**. Une nouvelle demande du même parent avec le même code passe après ce délai : seul `pendingAt` change, et les règles exigent que `pendingAt` soit modifié. Le parent **n'est pas encore propriétaire**.
4. Attendre la confirmation sur la tablette (écouter `tablets/{tabletId}` ; il pourra lire le doc seulement une fois `ownerUid == myUid`, donc avant cela il attend et réessaie un `get`). Si `ownerUid == myUid` : appairé.
5. Ensuite : lister ses tablettes avec `where('ownerUid', '==', myUid).limit(n)` (**`limit` ≤ 20 obligatoire**) ; écrire `name` et `settings` (avec `rev` + 1 et `updatedAt`) ; supprimer `pairingCodes/{code}` si la tablette ne l'a pas fait.

**Désappairage** : le parent supprime `tablets/{id}`, ou la tablette supprime son propre doc (réinitialisation). Doc absent ou `ownerUid == null` = non appairée : la tablette efface son état distant local et repart à l'étape 2 de « Tablette ».

## Suppression de compte et de données (côté client, exigée par Play/RGPD)
Le parent, dans l'appli : (1) liste ses tablettes (`where ownerUid == uid`, `limit` ≤ 20, boucler tant qu'il en reste) et supprime chaque `tablets/{id}` ; (2) supprime `users/{uid}` ; (3) supprime son compte Auth (`user.delete()`, réauthentification Google si besoin). Les règles autorisent ces suppressions. Les tablettes concernées repassent « non appairées ».

## À activer avant la publication Play
- **App Check en mode « enforce »** (Play Integrity) pour Firestore et Auth, et l'enregistrer dans le code client des deux variantes. Réduit l'essai massif de codes et les abus d'API.
- **TTL** sur `pairingCodes.expires` (voir plus haut).
- Relancer les tests de règles dans l'émulateur (non exécutés à ce jour, voir ci-dessous).

## Points d'attention pour les devs
- `lastSeen`, `applied.at`, `createdAt`, `pendingAt`, `settings.updatedAt` doivent être des `serverTimestamp()` (les règles exigent `== request.time`).
- Une écriture de la tablette ne doit toucher que ses champs : utiliser `update` avec les seuls champs modifiés. Ne jamais renvoyer `ownerUid` (sauf à la confirmation), `name`, `settings`, `claimCode`, `pending*`.
- Listes de valeurs (`theme`, `anim`, `character`) à garder identiques entre `RemoteConfig.kt`, l'UI parent et `firestore.rules`.
- Pas de `get` global, pas de requête sans `where ownerUid == uid` et sans `limit` ≤ 20 : refusé.
- Les règles autorisent un parent `google.com` uniquement ; la tablette reste anonyme.
- **Tests non exécutés** : `npm i @firebase/rules-unit-testing` + émulateur Firestore (`firebase emulators:exec`, Java 21 pour firebase-tools récent), voir `firebase.json`.
- Déploiement des règles (par le propriétaire) : `firebase deploy --only firestore:rules` depuis ce dossier.
