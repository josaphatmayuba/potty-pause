# Fiche Google Play — Pause Pipi / Potty Pause

Éditeur : Virtuel 3D Studio. Contact : 14735340.canada.inc@gmail.com. Gratuite, sans publicité, sans achat intégré. ID variante Play : `com.pottypause.app`.

## Catégorie suggérée
Parentalité (Parenting). Alternative : Outils. Tags à proposer : Parentalité. À VÉRIFIER dans la Play Console (liste exacte des catégories/tags).

## FR (langue par défaut)

**Titre (≤ 30)** : `Pause Pipi - Potty Pause` (24)

**Description courte (≤ 80)** : `Rappel « pause toilettes » sur la tablette de l'enfant, réglé par le parent.` (76)

**Description longue**
```
Pause Pipi aide les parents à penser aux toilettes pendant que l'enfant joue sur sa tablette.

À intervalle régulier, un écran coloré et amusant s'affiche par-dessus les jeux et invite l'enfant à faire une pause toilettes. L'écran reste quelques minutes, puis disparaît. Un code parent (PIN) protège les réglages.

POUR LE PARENT
• Intervalle du rappel et durée de l'écran
• Message personnalisé, thèmes, animations et personnages
• Heures de fonctionnement
• Pause et reprise en un geste
• Français et anglais

TÉLÉCOMMANDE (OPTIONNELLE)
Connectez-vous avec votre compte Google sur votre téléphone, associez la tablette de l'enfant avec un code à 8 chiffres confirmé sur la tablette grâce au code parent, puis réglez le rappel à distance. Vous voyez si la tablette est connectée et si une autorisation manque.

SANS COMPTE
L'application fonctionne aussi seule, sur la tablette, sans aucun compte ni envoi de données.

VIE PRIVÉE
• Aucune publicité, aucun traceur d'analyse
• Aucune position, aucun contenu d'écran, aucun suivi des applications ou du temps d'écran
• Le code parent n'est jamais envoyé au cloud
• Suppression de votre compte et de vos données depuis l'application
• Données de la télécommande hébergées en Europe (Google Firebase)

Pour les parents : l'enfant n'a aucun compte. Un prénom ou surnom suffit pour nommer la tablette.

AUTORISATIONS
L'application demande l'affichage par-dessus les autres applications (c'est le rappel lui-même), une notification permanente (le rappel reste actif) et le démarrage automatique après redémarrage. Elle vous guide pas à pas pour les accorder.
```

**Mots-clés** (pas de champ dédié sur Play : à intégrer naturellement dans le texte) : rappel toilettes, propreté, apprentissage de la propreté, pipi, pot, minuteur, contrôle parental léger, tablette enfant, parentalité.

**Notes de version (≤ 500)** : `Première version sur Google Play : rappel toilettes pour la tablette de l'enfant, télécommande parent optionnelle (connexion Google), suppression du compte et des données depuis l'application.`

## EN

**Title (≤ 30)**: `Potty Pause - Pause Pipi` (24)

**Short description (≤ 80)**: `Bathroom-break reminder on your child's tablet, set up by the parent.` (69)

**Full description**
```
Potty Pause helps parents remember bathroom breaks while their child plays on a tablet.

At regular intervals, a colorful, friendly screen appears over the games and invites the child to take a bathroom break. It stays for a few minutes, then disappears. A parent code (PIN) protects the settings.

FOR THE PARENT
• Reminder interval and screen duration
• Custom message, themes, animations and characters
• Active hours
• Pause and resume in one tap
• English and French

REMOTE CONTROL (OPTIONAL)
Sign in with your Google account on your phone, pair your child's tablet with an 8-digit code confirmed on the tablet using the parent code, then adjust the reminder remotely. You can see whether the tablet is connected and whether a permission is missing.

NO ACCOUNT NEEDED
The app also works on its own on the tablet, with no account and no data sent.

PRIVACY
• No ads, no analytics trackers
• No location, no screen content, no app or screen-time tracking
• The parent code is never sent to the cloud
• Delete your account and data from within the app
• Remote-control data hosted in Europe (Google Firebase)

For parents: the child has no account. A first name or nickname is enough to name the tablet.

PERMISSIONS
The app asks for display over other apps (this is the reminder itself), a persistent notification (keeps the reminder running) and auto-start after reboot. It guides you step by step.
```

**Keywords** (no dedicated field on Play; weave into text): potty training, toilet reminder, bathroom break, timer, toddler, kids tablet, parenting.

**Release notes (≤ 500)**: `First Google Play release: bathroom-break reminder for your child's tablet, optional parent remote control (Google sign-in), delete your account and data from within the app.`

## Justifications pour les déclarations (anglais, à coller dans la Play Console)

**SYSTEM_ALERT_WINDOW (overlay)**
```
Core function of the app. Potty Pause shows a full-screen reminder ("bathroom break") over the games and apps a young child is using on a tablet, at an interval set by the parent. Without drawing over other apps, the reminder cannot be seen and the app has no purpose. The permission is granted by the parent in system Settings after a clear in-app explanation. The overlay only shows an illustration and a parent-defined message, disappears after a few minutes, and can be stopped by the parent with a PIN. It collects no data from other apps and reads no screen content.
```

**Foreground service, type specialUse** (PROPERTY_SPECIAL_USE_FGS_SUBTYPE: « Rappel parental : pause toilettes après un temps d'écran »)
```
The foreground service keeps a recurring parental reminder timer alive so the "bathroom break" screen appears on time while the child uses other apps. It runs only after the parent turns the reminder on, shows a persistent notification, and stops when the parent stops it. No existing foreground service type fits (it is not media, location, data sync, etc.): it is a user-visible scheduled reminder. A normal background job (WorkManager/AlarmManager alone) is not reliable enough for minute-level reminders while the device is in use or in Doze. A short demo video is provided.
```

## Captures d'écran à fournir (sans enfant réel ni nom réel)
Téléphone (2 à 8, 16:9 ou 9:16, min 320 px, max 3840 px) :
1. Écran de rappel (thème par défaut) sur un jeu.
2. Choix du mode (parent / tablette enfant).
3. Mode parent : liste des tablettes avec pastille d'état.
4. Réglages d'une tablette (intervalle, durée, message).
5. Choix du thème / personnage.
6. Appairage par code à 8 chiffres.
7. Assistant d'autorisations (overlay, notifications, batterie).

Tablette 7" et 10" (recommandé) : écran de rappel plein écran, réglages. Captures FR et EN.
Autres assets : icône 512×512 PNG, image de présentation 1024×500, vidéo d'écran 30 s pour les déclarations (permission, overlay, PIN, arrêt du service), vidéo YouTube facultative.
