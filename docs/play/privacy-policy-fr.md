# Politique de confidentialité — Pause Pipi / Potty Pause

Dernière mise à jour : 2026-11-22

**Éditeur et responsable du traitement :** Virtuel 3D Studio
**Contact (confidentialité, accès, suppression) :** 14735340.canada.inc@gmail.com

## 1. À qui s'adresse l'application
Pause Pipi / Potty Pause est un outil pour les **parents** : elle affiche sur la tablette de l'enfant un rappel périodique « pause toilettes ». Seul un parent (adulte) peut créer un compte et gérer l'application. **L'enfant n'a aucun compte**, ne saisit aucune information et n'utilise aucun formulaire. L'application n'est pas destinée à être utilisée en autonomie par des enfants.

## 2. Deux modes
- **Sans compte (mode autonome)** : l'application fonctionne entièrement sur la tablette. Les réglages restent sur l'appareil. Aucune donnée n'est envoyée à nos serveurs.
- **Avec télécommande (optionnel)** : le parent connecte son compte Google et associe une ou plusieurs tablettes pour régler le rappel à distance. Seul ce mode envoie des données dans le cloud (section 3).

## 3. Données collectées (mode télécommande uniquement)
**Compte du parent** (connexion Google)
- Identifiant de compte (uid Firebase), adresse e-mail du compte Google, langue de l'appareil (fr/en), date de création.
- Nous ne recevons pas votre mot de passe Google.

**Tablette** (connexion anonyme, sans e-mail ni nom)
- Identifiant anonyme (uid), date de création.
- Nom de la tablette : **prénom ou surnom de l'enfant saisi par le parent** (40 caractères max). Nous recommandons un prénom ou un surnom, pas un nom complet.
- Réglages choisis par le parent : intervalle, durée du blocage, message personnalisé (200 car. max), thème, animation, personnage, langue, rappel actif/en pause, numéro de révision.
- État technique : date de dernière connexion (« lastSeen », environ toutes les 15 minutes), version de l'application, version d'Android (niveau d'API), date et numéro de la dernière révision appliquée.
- Alertes techniques : autorisation d'affichage par-dessus les autres applications refusée, rappel arrêté, optimisation de batterie active, notifications refusées, tablette hors ligne longtemps.
- Pendant l'appairage : un code à 8 chiffres temporaire (10 minutes) et, pour une demande en cours (5 minutes), l'identifiant et l'e-mail du compte parent demandeur (affichés sur la tablette pour confirmation).

**Ce que nous ne collectons PAS** : position, contacts, photos, micro, caméra, contenu de l'écran, applications utilisées, temps d'écran, historique de navigation, identifiant publicitaire. L'image éventuellement choisie pour le thème « photo » reste sur la tablette.

**Le code parent (PIN) n'est jamais envoyé au cloud.** Il est conservé uniquement sur la tablette, sous forme de condensat (PBKDF2 avec sel), et sert à protéger les réglages locaux et à confirmer un appairage.

Comme tout service en ligne, Google/Firebase traite techniquement l'adresse IP et des journaux de connexion pour faire fonctionner et sécuriser le service.

## 4. Finalités et base légale
- Faire fonctionner la télécommande (comptes, appairage, réglages, état) : exécution du service demandé par le parent.
- Sécurité (limiter les abus, expirer les codes) : intérêt légitime.
- Consentement : le parent accepte cette politique à la création de son compte.

Nous n'utilisons pas ces données pour la publicité, le profilage ni la prise de décision automatisée.

## 5. Destinataires
Aucun partage, vente ni cession à des tiers. Seul sous-traitant : **Google (Firebase Authentication et Cloud Firestore)**. Les données Firestore sont hébergées en **Europe (multi-région eur3)** ; Google peut toutefois traiter certaines données techniques (ex. authentification) hors d'Europe, selon ses conditions de traitement des données. **Pas de publicité, pas de kit d'analyse (analytics), pas de rapport de plantage tiers** dans l'application.

## 6. Conservation
- Compte parent et documents de ses tablettes : conservés **jusqu'à ce que le parent les supprime** (suppression d'une tablette ou du compte, section 7).
- Codes d'appairage : durée de vie de 10 minutes ; effacés par la tablette dès l'appairage, et purgés automatiquement par une politique d'expiration (TTL) Firestore.
- Demandes d'appairage : valables 5 minutes.
- Après suppression, Google peut conserver des copies techniques de sauvegarde pendant une durée limitée avant effacement définitif.
- Une tablette qui quitte le mode télécommande supprime son document cloud. Son identifiant anonyme d'authentification (sans donnée personnelle) peut subsister ; nous le supprimons sur demande.

## 7. Vos droits et suppression
Selon votre lieu de résidence (RGPD dans l'UE/EEE ; LPRPDE et loi québécoise sur la protection des renseignements personnels dans le secteur privé au Canada ; autres lois locales), vous pouvez demander l'accès, la rectification, l'effacement, la limitation, la portabilité, vous opposer au traitement et retirer votre consentement. Vous pouvez aussi déposer une plainte auprès de votre autorité (CNIL, Commission d'accès à l'information du Québec, Commissariat à la protection de la vie privée du Canada, etc.).

- **Supprimer vos données depuis l'application** : mode parent > « Supprimer mon compte et mes données ». Cela supprime toutes vos tablettes associées, votre fiche et votre compte de connexion. Une nouvelle connexion Google peut être demandée. Les tablettes concernées redeviennent « non appairées ».
- **Supprimer une seule tablette** : dissocier la tablette dans le mode parent, ou depuis la tablette (derrière le code parent).
- **Par e-mail** : 14735340.canada.inc@gmail.com, depuis l'adresse du compte concerné ; réponse sous 30 jours.

Désinstaller l'application ne supprime pas les données cloud : utilisez l'une des méthodes ci-dessus.

## 8. Enfants (COPPA et équivalents)
L'application s'adresse aux parents. Aucun compte n'est créé pour un enfant, et nous ne collectons aucune information directement auprès de lui. Les seules informations touchant l'enfant (prénom ou surnom, réglages) sont saisies par le parent, adulte titulaire du compte, qui consent en son nom. Un parent peut à tout moment les consulter, les modifier ou les supprimer (section 7). Si vous pensez qu'un enfant nous a transmis des données, écrivez-nous et nous les supprimerons.

## 9. Sécurité
Échanges chiffrés (HTTPS/TLS) avec Firebase ; accès aux données limité par des règles Firestore : un parent ne voit que ses propres tablettes ; une tablette n'écrit que son propre état ; un parent ne devient gestionnaire d'une tablette qu'après confirmation sur la tablette avec son code. Aucun système n'étant infaillible, nous ne pouvons garantir une sécurité absolue.

## 10. Permissions Android
Affichage par-dessus les autres applications (le rappel), service au premier plan avec notification (maintenir le rappel actif), notifications, démarrage au redémarrage de l'appareil, Internet (télécommande). Ces permissions ne servent pas à collecter des données.

## 11. Modifications
Toute modification importante sera indiquée dans l'application et par la date ci-dessus.
