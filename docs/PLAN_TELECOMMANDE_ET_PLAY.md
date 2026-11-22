# Plan : télécommande multi-tablettes + publication Google Play

Date : 2026-11-22. État actuel : v1.8 (versionCode 9, targetSdk 34), config via `config.json` public GitHub (`RemoteConfig.kt`), mise à jour par APK GitHub (`Updater.kt`, `REQUEST_INSTALL_PACKAGES`), PIN stocké en clair dans SharedPreferences (`Prefs.pin`).

Les points marqués **[à vérifier]** n'ont pas pu être confirmés dans les textes officiels lus : relire la page Play Console citée avant d'agir.

## 0. Constats de la recherche (politiques Play)

| Sujet | Constat | Conséquence |
|---|---|---|
| Auto-mise à jour | « Les applis ne peuvent pas se modifier, se remplacer ni se mettre à jour par un autre moyen que le mécanisme de Google Play » (Device and Network Abuse) ; télécharger du code exécutable hors Play est interdit | Variante Play : supprimer `Updater`, `REQUEST_INSTALL_PACKAGES`, FileProvider d'APK. MAJ distante = Play uniquement |
| Foreground service | Type déclaré obligatoire (Android 14+) + formulaire de déclaration dans Play Console ; l'usage doit être visible/arrêtable par l'utilisateur et lié à la fonction cœur | `specialUse` : rédiger la justification (rappel parental récurrent) + vidéo courte. Notification permanente déjà présente = bon point |
| SYSTEM_ALERT_WINDOW | Play demande d'envoyer l'utilisateur à l'écran Réglages système pour l'accorder (déjà le cas) ; pas de formulaire dédié trouvé **[à vérifier]** | Expliquer l'usage dans la fiche + divulgation visible dans l'appli avant d'ouvrir les Réglages |
| Device admin | Non couvert dans les pages lues **[à vérifier]** | Voir à quoi sert `AdminReceiver` ; s'il ne sert qu'à gêner la désinstallation, le retirer de la variante Play (risque de rejet « comportement de type stalkerware/évasion ») |
| Applis de surveillance parentale | Flag manifeste `isMonitoringTool` (`child_monitoring`) pour les applis de contrôle parental ; divulgation visible + consentement requis (User Data) | Notre appli ne collecte ni position, ni usage, ni contenu : seulement « dernière connexion » et réglages. Probablement pas un « monitoring tool », mais c'est de la télécommande parentale : décrire honnêtement, ne pas cacher l'icône côté Play **[à vérifier : faut-il le flag ?]** |
| Families / enfants | S'applique si l'enfant est une cible. Mixte = écran d'âge neutre. SDK pubs certifiés uniquement. COPPA + RGPD à respecter. Les pages lues ne définissent pas de catégorie « parents seuls » | Déclarer l'audience cible = adultes (parents), jamais de pub, pas d'analytics/Crashlytics, aucune donnée personnelle de l'enfant. L'overlay vu par l'enfant n'a aucun compte ni donnée |
| Compte développeur | 25 USD unique, perso ou organisation. Organisation = numéro D-U-N-S (gratuit, jusqu'à ~30 jours) | Compte perso suffit pour démarrer ; nom légal/adresse potentiellement publics |
| Tests fermés | Comptes perso créés après le 13/11/2023 : test fermé avec **12 testeurs minimum, opt-in continu 14 jours**, puis demande d'accès production (support Play 14151465). Comptes organisation exemptés | Prévoir 3 semaines + recruter 12 testeurs (famille/amis/parents d'école) |
| API cible | Nouvelles applis et MAJ doivent cibler Android 16 (API 36) dès le 31/08/2026 | Passer `targetSdk`/`compileSdk` à 36 (actuellement 34) : à faire dès l'étape 0 |
| Autres | Android 15+ : `FOREGROUND_SERVICE_SPECIAL_USE` à tester ; `BootReceiver` OK | Tester l'overlay et le service sous Android 14, 15, 16 |

Sources : support.google.com/googleplay/android-developer/answer/9888379 (Device and Network Abuse), 9893335 (Families), 12955211 (isMonitoringTool), 14151465 (tests fermés), 13634885 (types de compte), 11926878 (API cible), developer.android.com/distribute/play-policies.

## 1. Architecture recommandée (une option : Firebase)

| Critère | A. Firebase (Auth + Firestore + FCM) | B. Backend maison (nglu-app/NestJS) | C. Supabase / autre BaaS |
|---|---|---|---|
| Coût | 0 $ (Spark/Blaze avec quotas gratuits largement suffisants : quelques écritures/jour/tablette) | Serveur à payer et surveiller (serveur dev à 442 Mo déjà saturé) | Gratuit puis pause projets inactifs / ~25 $/mois |
| Complexité | Faible : SDK Android officiel, règles de sécurité déclaratives, pas de serveur | Élevée : auth, push, règles, hébergement, sauvegardes, astreinte | Moyenne : SDK Kotlin moins mature, push à gérer soi-même |
| Temps réel / réveil | Écouteur Firestore + FCM data haute priorité = quelques secondes | À construire (WebSocket + FCM quand même) | Realtime OK, push à ajouter |
| Vie privée | Données chez Google (région UE `eur3`/`europe-west`), minimales ; DPA Google | Maîtrise totale, mais responsabilité RGPD complète | Hébergement UE possible |
| Play / Families | SDK Firebase Auth/Firestore/FCM acceptés ; ne PAS ajouter Analytics/Crashlytics/AdMob | Aucun SDK tiers | Idem |
| Risque | Dépendance Google, quotas | Panne = tout tombe, mainteneur unique | Dépendance, moins de doc Android |

**Recommandation : A (Firebase Auth + Firestore + FCM)**, plan Spark, sans Analytics ni Crashlytics. Raisons : 0 € d'exploitation, pas de serveur à maintenir, le parent n'a plus rien à toucher hors de l'appli, push quasi instantané, règles Firestore par propriétaire. Éviter Cloud Functions au départ (Blaze requis) : l'envoi FCM se fait depuis l'appli parent via un second mécanisme simple (voir § 3.4).

Flux technique :
1. Parent : Firebase Auth (e-mail/mot de passe, ou Google Sign-In).
2. Tablette enfant : Auth **anonyme** + appairage par code.
3. Parent écrit les réglages dans Firestore ; la tablette a un écouteur sur son doc `devices/{id}/settings` (actif tant que le service tourne) + secours WorkManager 15 min si le listener est coupé (Doze).
4. Push « réveil » : le parent crée une commande ; la tablette l'écoute en temps réel (listener). FCM data message sert de réveil quand le processus est endormi. Envoi FCM sans serveur : **Cloud Function** (Blaze, quota gratuit, carte requise) = solution propre ; sinon repli sur listener + WorkManager (délai jusqu'à 15 min). Décision propriétaire n°3.

## 2. Modèle de données Firestore

```
users/{parentUid}                     { email, createdAt, locale, pinHashHint? }  // pas de PIN ici
households/{hid}                      { ownerUid, memberUids[], createdAt }       // 2 parents possibles
households/{hid}/children/{cid}       { name, birthYear?, color }                 // prénom seul, pas de photo
households/{hid}/devices/{did}        { childId, label, tabletUid, pairedAt, status: active|revoked,
                                        appVersion, androidApi, lastSeenAt, lastApplyAt, overlayGranted,
                                        batteryOptimIgnored, serviceRunning, fcmToken }
households/{hid}/devices/{did}/settings/current
                                      { rev, intervalMin, lockMin, message, theme, anim, character,
                                        iconHidden, language, enabled, pinSalt, pinHash, pinIter,
                                        updatedAt, updatedBy }
households/{hid}/devices/{did}/commands/{cmdId}
                                      { type: applyNow|lockNow|pauseUntil|ping, payload, createdAt,
                                        expiresAt, status: pending|done|failed, doneAt }
pairingCodes/{code}                   { hid, childId, createdAt, expiresAt, used }  // code 6 chiffres, 10 min
```
Notes : les champs de `settings` reprennent ceux de `RemoteConfig.applySettings` (mêmes plages validées : interval 1..600, lock 1..120, listes `themes`/`anims`/`character`). `rev` reste l'anti-rejeu. Une tablette = un enfant ; un enfant peut avoir plusieurs tablettes (réglages par tablette, copie en un clic). Pas de suppression physique : `status=revoked`.

## 3. Parcours

### 3.1 Parent crée un compte
Installer l'appli → choix « Je suis le parent » → inscription e-mail/mot de passe (ou Google) → création du foyer → case de consentement (politique de confidentialité) → écran « Ajouter un enfant ».

### 3.2 Ajouter une tablette (code ou QR)
1. Parent : « Ajouter une tablette » → choisit/crée l'enfant → l'appli affiche un code à 6 chiffres + QR (valide 10 min, usage unique).
2. Sur la tablette de l'enfant (appli installée via Play) : « Cette tablette est celle d'un enfant » → saisie du code ou scan QR (caméra : permission `CAMERA` ; ou saisie seule pour éviter la permission, **recommandé v1**).
3. La tablette s'authentifie en anonyme, lit `pairingCodes/{code}`, s'enregistre dans `devices/{did}` et marque le code utilisé (transaction).
4. Le parent voit « Tablette de Léo connectée ». Puis assistant existant : autorisation overlay, notifications, batterie, démarrage auto.

### 3.3 Réglages par enfant/tablette
Écran parent par tablette : intervalle, durée, message, thème, animation, personnage, icône cachée (désactivée dans la variante Play), langue, activer/pauser. Bouton « Appliquer » → écriture `settings/current` avec `rev+1`. Option « Appliquer à toutes les tablettes de cet enfant ».

### 3.4 Application à distance en quelques secondes
Écriture Firestore → listener de la tablette (service au premier plan déjà actif) → application via les mêmes validations que `RemoteConfig` → écriture `lastApplyAt` / `rev` appliquée. Si la tablette dort : FCM data (Cloud Function) ou prochain WorkManager. Le parent voit « Appliqué à 14:03:12 » ou « En attente ».

### 3.5 État
Heartbeat : la tablette écrit `lastSeenAt` toutes les 15 min (WorkManager) et à chaque changement (batterie, service arrêté, permission retirée). Parent : pastille verte (< 30 min), orange (< 24 h), rouge (> 24 h), + alertes « overlay refusé », « service arrêté », « optimisation batterie active ». Aucune donnée d'usage de l'enfant, ni position.

## 4. Mode parent vs mode enfant : une seule appli (recommandé)

Une appli, deux modes choisis au premier lancement :
- **Mode tablette enfant** : comportement actuel (service + overlay), réglé à distance, PIN local pour ouvrir les réglages locaux (hors-ligne OK).
- **Mode parent** : comptes, enfants, tablettes, réglages, état. Peut tourner sur le téléphone du parent.
- Une appli = une fiche Play, une seule revue ; changement de mode derrière le PIN/compte parent.
- Deux applis écartées : double revue Play, double maintenance, double fiche Families.
- Fonctionnement **hors ligne conservé** : sans compte, l'appli marche en autonome (comme aujourd'hui) = pas d'obligation de créer un compte pour un parent qui n'en veut pas.

Code : séparer en packages `core/` (overlay, service, thèmes, existants), `remote/` (Firestore, appairage), `parent/` (écrans), et `flavors`.

## 5. Sécurité et vie privée

- **PIN jamais en clair dans le cloud ni en local** : le parent définit un PIN ; l'appli calcule PBKDF2 (sel aléatoire, ≥ 100 000 itérations) et ne stocke que `pinSalt/pinHash/pinIter` ; la tablette vérifie localement. Migrer l'actuel `Prefs.pin` (en clair) vers le hash. Limite de tentatives déjà présente (`failUntil`) à conserver. Un PIN de 4 chiffres reste faible face à un enfant déterminé sans limitation : garder le délai croissant ; ne rien promettre de plus dans la fiche.
- **Règles Firestore** (principe, à écrire à l'étape 2) : un parent n'accède qu'à `households` dont il est membre ; `settings` en écriture parent seulement ; une tablette n'accède qu'à son `devices/{did}` (champ `tabletUid == request.auth.uid`) en lecture de `settings`/`commands` et en écriture limitée (`lastSeenAt`, statut, `status` de commande) ; `pairingCodes` : lecture par code exact, création parent, `used` par transaction, expiration vérifiée ; validation des bornes numériques côté règles ; aucune liste/requête globale.
- **Auth anonyme tablette** : UID lié au device à l'appairage ; si la tablette est réinitialisée, nouvel UID → ré-appairage ; révocation par le parent (`status=revoked`) coupe l'accès.
- **App Check** (Play Integrity) : activer en v2 pour limiter les abus d'API ; gratuit.
- **Données collectées** : e-mail du parent, prénom de l'enfant (saisi par le parent), modèle/version Android et état des permissions, dernière connexion, jeton FCM. Pas de publicité, pas d'ID publicitaire, pas d'analytics, pas de position.
- **RGPD** : base légale = contrat/consentement du parent ; région Firestore UE ; droit à l'effacement = bouton « Supprimer mon compte et mes données » dans l'appli (obligatoire Play aussi) + page web de suppression ; DPA Google acceptée par défaut dans Firebase ; minimiser l'âge (année de naissance facultative, ou rien).
- **COPPA** : l'enfant n'utilise aucun compte ni formulaire ; les données sont fournies par le parent. À mentionner dans la politique (consentement parental vérifiable = le compte parent adulte).
- Retirer `config.json` du dépôt (ou le vider) une fois le client migré, et **révoquer l'accès**; ne pas laisser d'URL publique de config.

## 6. Deux variantes de build (product flavors)

| | `play` | `direct` (GitHub) |
|---|---|---|
| applicationId | `com.famille.rappelpipi` (celui de Play) ; **choisir définitivement avant la 1re mise en ligne** | suffixe `.direct` (installables côte à côte) ou même id si le propriétaire abandonne les anciens APK **[décision]** |
| Auto-MAJ APK (`Updater`, `REQUEST_INSTALL_PACKAGES`, FileProvider) | **supprimé** | conservé (lit les releases GitHub) |
| Télécommande | Firebase | Firebase (même backend) ; `config.json` supprimé des deux |
| Cacher l'icône | retiré/désactivé (transparence) **[à vérifier avec la politique]** | conservé |
| Device admin | retiré si non indispensable | conservé |
| Signature | clé d'upload + **Play App Signing** | clé actuelle |
| `targetSdk` | 36 | 36 |
| Mise à jour distante | uniquement via Google Play (+ nudge « Une mise à jour est disponible » via In-App Updates API de Play, autorisée) | via GitHub comme aujourd'hui |

Mise en œuvre : `productFlavors { play; direct }` avec dimension unique, sources `src/play`, `src/direct` pour `UpdaterImpl` et le manifeste fusionné ; CI/CD : `bundleRelease` (AAB) pour `play`, `assembleRelease` (APK) pour `direct`. Passer de l'APK au AAB. Prévoir R8/minify et règles proguard pour Firebase.

## 7. Checklist de publication Play

Prérequis légaux
- [ ] Compte développeur (25 USD), vérification d'identité, adresse e-mail/téléphone de contact.
- [ ] **Politique de confidentialité** hébergée sur une URL publique stable (page web GitHub Pages OK, FR + EN). Contenu : responsable, données collectées (liste § 5), finalités, base légale, destinataires (Google/Firebase, région UE), durée de conservation, droits RGPD + procédure de suppression, enfants/COPPA, contact. Rédaction : étape 5.
- [ ] URL/page de suppression de compte et de données (exigée pour les applis avec comptes).

Console
- [ ] Création de l'appli, langue par défaut FR, anglais en second ; gratuite, sans pub, sans achat intégré.
- [ ] **Public cible et contenu** : adultes (parents) uniquement ; pas de pub ; questionnaire IARC (classification tous publics) ; réponses cohérentes avec la description.
- [ ] **Data safety** : e-mail (compte), identifiants d'appareil/FCM, infos d'appli (version), chiffrement en transit oui, suppression possible oui, pas de partage avec des tiers, pas de données collectées auprès de l'enfant.
- [ ] Déclarations : **foreground service `specialUse`** (justification + vidéo), SYSTEM_ALERT_WINDOW (usage cœur : rappel par-dessus les autres applis, accordé par l'utilisateur dans les Réglages), éventuel device admin, `RECEIVE_BOOT_COMPLETED`, `POST_NOTIFICATIONS`.
- [ ] Éventuel flag `isMonitoringTool` **[décision après relecture de la politique]**.
- [ ] Divulgation visible dans l'appli avant d'ouvrir les Réglages overlay/notifications (texte clair, bouton « Continuer »).
- [ ] Pays de distribution, contact support, adresse e-mail publique.

Assets
- [ ] Icône 512×512 PNG, image de présentation 1024×500, 2 à 8 captures téléphone (≥ 2) + captures tablette 7"/10" (recommandé), vidéo YouTube facultative.
- [ ] Textes FR/EN : titre ≤ 30 car. (« Pause Pipi - Potty Pause »), description courte ≤ 80 car., description longue ≤ 4000 car. expliquant : outil de parent, réglages à distance, ce que voit l'enfant, aucun suivi, aucun contenu enfant à collecter.
- [ ] Vidéo d'écran pour la déclaration FGS/overlay (30 s : permission → overlay → PIN).

Tests et mise en production
- [ ] Piste **test interne** (rapide, jusqu'à 100 testeurs) pour valider.
- [ ] Piste **test fermé : 12 testeurs, opt-in continu 14 jours** (compte perso) ; recruter ≥ 15 pour marge ; ne pas redémarrer l'horloge (un testeur qui sort peut réinitialiser).
- [ ] Demande d'accès à la production (questionnaire : retours des testeurs, préparation).
- [ ] Passage en production, déploiement progressif 20 % → 100 %.
- [ ] Après publication : surveiller Android vitals et rapports de plantages Play (sans SDK tiers), répondre aux avis.

Pièges connus : rejet pour « permissions non justifiées » (overlay, FGS) ; rejet Families si l'audience déclarée est incohérente ; clé d'upload perdue (la sauvegarder hors OneDrive partagé et chiffrée) ; mot de passe/keystore jamais dans git.

## 8. Étapes livrables et estimations

Estimation en jours-développeur effectifs (1 personne, tests inclus), hors délais d'attente Play.

| # | Étape | Livrable | Estim. | Qui |
|---|---|---|---|---|
| 0 | Socle : flavors `play/direct`, passage target/compileSdk 36, hash PBKDF2 du PIN (migration), extraction `Updater` vers flavor `direct`, test sous Android 14-16 | Deux builds propres, AAB signable | 2-3 j | Dev |
| 1 | Projet Firebase + `google-services.json`, Auth e-mail, anonymous, Firestore EU, règles v1, écran choix de mode | Comptes parent + tablette anonyme | 2 j | Propriétaire (création) + Dev |
| 2 | Modèle de données, règles Firestore complètes + tests d'émulateur, appairage par code (+ QR en option) | Tablette appairée à un foyer | 3-4 j | Dev |
| 3 | Mode parent : enfants, tablettes, réglages par tablette, « Appliquer », état/dernière connexion, alertes | Parcours complet de bout en bout | 5-6 j | Dev |
| 4 | Côté tablette : écouteur `settings/commands`, heartbeat WorkManager, FCM (si Cloud Function retenue), remplacement de `RemoteConfig` GitHub, rev anti-rejeu, révocation | Appliqué en < 10 s en ligne | 3-4 j | Dev |
| 5 | Confidentialité : rédiger la politique FR/EN, page de suppression, bouton de suppression de compte (Auth + Firestore), divulgations in-app, textes de fiche, captures, vidéo | Dossier légal et assets | 3 j | Dev + Propriétaire (relecture) |
| 6 | QA : 2 tablettes + 1 téléphone parent, Doze/batterie, Android 14-16, perte réseau, désappairage, mises à jour de version | Rapport QA | 2-3 j | Dev + Propriétaire (appareils) |
| 7 | Test interne puis test fermé 14 jours (12 testeurs) | Accès production demandé | 1 j actif + ≥ 14 j d'attente + revue Google (qq jours) | Propriétaire (testeurs) |
| 8 | Production progressive, désactivation `config.json` GitHub, README/CHANGELOG | Publiée | 1 j | Dev + Propriétaire |

Total : environ 21-26 jours de dev + ~3 semaines de calendrier de test. **Chemin critique** : créer le compte Play **dès maintenant** (vérification d'identité de quelques jours) et lancer le recrutement des 12 testeurs en parallèle de l'étape 2.

Jalon intermédiaire utile : après l'étape 4, la variante `direct` peut déjà remplacer `config.json` pour la famille, sans attendre Play.

## 9. Actions que SEUL le propriétaire peut faire

1. Créer le **compte Google Play Console** (25 USD, carte bancaire, pièce d'identité) ; choisir perso (12 testeurs/14 jours) ou organisation (D-U-N-S, sans exigence de testeurs).
2. Créer le **projet Firebase** (compte Google), activer Auth (e-mail + anonyme), Firestore en région UE, FCM ; télécharger **`google-services.json`** (ne pas le committer s'il contient des clés ; restreindre la clé API à l'appli + SHA) ; décider Spark ou Blaze.
3. Fournir/valider les **empreintes SHA-1/SHA-256** (clé de debug + clé d'upload + clé Play App Signing) pour Firebase.
4. Générer et **sauvegarder la clé d'upload** (keystore + mots de passe) hors du dépôt.
5. Décider **l'identité de l'éditeur** affichée sur Play (nom légal/pseudo, adresse e-mail de contact, adresse postale éventuellement publique).
6. **Valider et publier la politique de confidentialité** (hébergement d'une URL publique) et l'adresse e-mail de contact RGPD.
7. Remplir les **déclarations Play Console** (public cible, Data safety, FGS, permissions) sous sa responsabilité légale.
8. **Recruter 12+ testeurs** (adresses Gmail) et les maintenir 14 jours.
9. Fournir **tablettes de test** et captures/validation visuelle finale (enfant réel : à ne pas montrer en capture de la fiche).
10. Accepter les **conditions de distribution** Play et le DPA Firebase ; décider de la **suppression de `config.json`** et de l'ancien dépôt/URL.

## 10. Décisions à prendre (propriétaire)

1. Firebase (recommandé) plutôt que backend maison ? Oui/non.
2. Compte Play personnel (12 testeurs, 14 jours) ou organisation (D-U-N-S, 1 mois de délai possible) ?
3. Envoi du push : Cloud Function (Blaze, carte requise, ~instantané) ou listener + WorkManager (gratuit, délai max 15 min hors appli active) ?
4. Variante Play : retirer « Cacher l'icône » et device admin ? (recommandé : oui)
5. applicationId final et sort des anciens APK `direct` (même id ou suffixe).
6. Fonctionnement hors compte conservé pour un parent qui ne veut aucun cloud ? (recommandé : oui)
7. Langues de la fiche : FR + EN au lancement ?
