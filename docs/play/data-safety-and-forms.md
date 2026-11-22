# Formulaires Play Console — réponses proposées

Basé sur le code (variante `play`, `com.pottypause.app`) : Firebase Auth + Firestore uniquement, pas d'analytics, pas de Crashlytics, pas de pub. Tout point non confirmé par le code ou par la Console est marqué « À VÉRIFIER dans la Play Console ». Le propriétaire reste responsable des déclarations.

## 1. Data safety

### Collecte et partage
| Question | Réponse |
|---|---|
| L'appli collecte ou partage-t-elle des données utilisateur ? | **Oui** (mode télécommande uniquement) |
| Toutes les données sont-elles chiffrées en transit ? | **Oui** (TLS Firebase) |
| Les utilisateurs peuvent-ils demander la suppression ? | **Oui** : bouton in-app + e-mail |
| URL de suppression de compte (exigée si comptes) | URL publique de la politique de confidentialité, section 7 (à héberger, voir checklist) — À VÉRIFIER : Google peut exiger une page dédiée à la suppression |
| Partage avec des tiers | **Non** (Google/Firebase = prestataire de traitement : non déclaré comme « partage ») — À VÉRIFIER la définition du « service provider » dans l'aide Play |
| Respect de la politique Families | voir section 4 |

### Types de données (par type : collectée / partagée / traitée de façon éphémère / obligatoire ou facultative / finalité)
| Catégorie > type | Collectée | Partagée | Facultative ? | Finalités |
|---|---|---|---|---|
| Infos personnelles > **Adresse e-mail** (compte Google du parent) | Oui | Non | Facultative (télécommande optionnelle) | Fonctionnalité de l'appli, gestion de compte |
| Infos personnelles > **Nom** (prénom/surnom de l'enfant saisi par le parent) | Oui | Non | Facultative | Fonctionnalité de l'appli |
| Infos personnelles > **Identifiants utilisateur** (uid Firebase parent et tablette) | Oui | Non | Facultative | Fonctionnalité de l'appli, gestion de compte, sécurité |
| Infos et performances de l'appli > **Autres** (version de l'appli, niveau d'API Android, état/alertes techniques, dernière connexion, réglages) | Oui | Non | Facultative | Fonctionnalité de l'appli |
| Activité dans l'appli > **Autre contenu généré par l'utilisateur** (message personnalisé) ; ou classer en « Autres actions » | Oui | Non | Facultative | Fonctionnalité de l'appli — À VÉRIFIER le meilleur rattachement |
| Identifiants d'appareil ou autres | **Non** (le jeton FCM n'est pas utilisé ; pas d'ID publicitaire) | — | — | — |

Types déclarés **Non collectés** : position, contacts, photos/vidéos, audio, fichiers, SMS, historique web, achats, santé, données financières, infos de plantage/diagnostic (Crashlytics absent), activité de navigation.
Remarque : les adresses IP traitées par Google/Firebase pour la connexion ne sont pas lues par l'éditeur. À VÉRIFIER dans la Play Console s'il faut les déclarer (l'adresse IP relève de « Identifiants d'appareil ou autres ») ; décision du propriétaire, la politique de confidentialité la mentionne déjà.

### Pratiques de sécurité
- Chiffrement en transit : oui.
- Demande de suppression : oui.
- « Suit les recommandations de la famille Play (Families Policy) » : voir section 4.
- Examen de sécurité indépendant : non.

## 2. Questionnaire de classification du contenu (IARC)
Catégorie : **Utilitaire / Référence / Autre** (pas un jeu). Réponses attendues, toutes **Non** :
violence, sang, contenu sexuel/nudité, langage grossier, substances (drogue, alcool, tabac), jeux d'argent, contenu terrifiant, **contenu généré par l'utilisateur partagé avec d'autres utilisateurs** (le message personnalisé reste entre le parent et la tablette de son enfant), **partage de position**, achats numériques, **interactions entre utilisateurs** (aucune), accès à Internet non restreint (aucun navigateur).
Question « l'appli permet-elle de communiquer avec d'autres utilisateurs ? » : **Non**.
Résultat attendu : tous publics (PEGI 3 / ESRB Everyone). À VÉRIFIER après soumission (le thème « toilettes » ne doit pas être signalé comme sujet sensible).

## 3. Public cible et contenu
- **Tranche d'âge cible : 18 ans et plus (parents)**. Ne pas cocher les tranches « 5 ans et moins », « 6-8 », « 9-12 » comme public cible.
- « L'appli attire-t-elle involontairement les enfants ? » : thèmes colorés et personnages = risque que Google la juge attirante pour les enfants. Réponse honnête : **l'interface de configuration et le compte s'adressent aux adultes ; l'écran de rappel est vu par un enfant mais n'est pas une appli interactive pour enfants** — À VÉRIFIER dans la Play Console : si le formulaire exige « enfants inclus dans le public cible », répondre en conséquence et appliquer la politique Families (section 4). Ne pas masquer cette ambiguïté.
- Pas de publicité, pas d'achats intégrés. Appli de contrôle parental : voir point `isMonitoringTool` ci-dessous.

## 4. Déclaration Families
- Position proposée : application **destinée aux parents**, pas une appli « pour enfants » au sens Families (aucun compte, formulaire ni contenu interactif côté enfant).
- Si Google classe l'appli « enfants inclus » : conformité requise = pas de SDK publicitaire, pas d'analytics, pas d'ID publicitaire (OK), données de l'enfant limitées (OK : prénom/surnom saisi par le parent), pas de collecte d'informations personnelles auprès de l'enfant (OK), SDK autorisés uniquement. Firebase Auth/Firestore : À VÉRIFIER dans la liste des SDK compatibles Families.
- Écran d'âge neutre : non applicable, l'appli demande un compte parent (connexion Google, adulte) ; le mode tablette n'a pas de compte.
- Programme « Designed for Families » : ne pas s'y inscrire sauf si Google l'exige. À VÉRIFIER.

## 5. Autres déclarations
| Déclaration | Réponse |
|---|---|
| Publicité | Non, aucune |
| Appli gouvernementale / financière / santé | Non (« santé » : le rappel toilettes n'est pas une fonction médicale ; ne revendiquer aucun usage médical) |
| Autorisations sensibles : SYSTEM_ALERT_WINDOW | Justification dans `store-listing.md` ; À VÉRIFIER si un formulaire dédié est demandé |
| Service au premier plan `specialUse` | Remplir la déclaration (texte et vidéo dans `store-listing.md`) |
| `isMonitoringTool` (manifeste) | Non défini. L'appli ne collecte ni position, ni usage, ni contenu : télécommande de réglages seulement. À VÉRIFIER dans la Play Console / l'aide 12955211 si le flag est requis pour une « télécommande parentale » |
| Icône cachée / admin appareil / mise à jour par APK | Absents de la variante `play` (seulement `direct`) : vérifier que l'AAB Play ne les contient pas (manifeste fusionné) |
| Compte requis pour tester | Fournir un compte Google de test (accès Play : instructions d'accès) ; la tablette n'exige aucun compte |
| Appli de connexion Google | Sans mot de passe propre ; suppression de compte in-app disponible |
| Public cible pays | À décider par le propriétaire |
