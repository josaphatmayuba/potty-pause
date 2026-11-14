# Rappel Pipi (Android)

Après X minutes d'écran allumé, la tablette affiche un écran plein écran avec un message
(« Va faire pipi ») et un minuteur. Elle se libère toute seule à la fin, ou avec le code parent.

## Compiler et installer
1. Installer **Android Studio** (gratuit), puis `File > Open` sur ce dossier. Laisser Gradle se synchroniser.
2. `Build > Build APK(s)` → fichier `app/build/outputs/apk/debug/app-debug.apk`.
3. Copier l'APK sur la tablette (USB ou Drive), l'ouvrir, autoriser « sources inconnues » pour ce fichier.
   (Avec Family Link, autoriser l'installation depuis le compte parent.)
4. Ouvrir « Rappel Pipi » : régler les minutes, le message, le code parent, puis « Démarrer ».
   Accepter « Afficher par-dessus les autres applications » et les notifications.

## Limites connues (revue 2026-11-13)
- Un enfant peut contourner : volet de notifications → Réglages → Forcer l'arrêt / retirer la permission / désinstaller. Pas étanche sans Device Owner. Bloquer Réglages et la désinstallation via Family Link.
- Family Link : aucune source officielle sur l'APK hors Play Store ni sur la permission d'affichage en profil supervisé → **tester sur la vraie tablette**.
- Android Studio crée le wrapper Gradle (8.7+, JDK 17) à l'ouverture ; sinon `gradle wrapper --gradle-version 8.7`.
- Le blocage survit à un redémarrage du service ou de la tablette (heure murale dans Prefs). Le temps passé sur l'écran de verrouillage n'est pas compté.
- Code parent : 3 erreurs → 30 s d'attente.

## Bon à savoir
- Éteindre l'écran ne raccourcit pas le blocage ; une pause d'écran éteint ≥ durée de blocage remet le compteur à zéro.
- Redémarre tout seul au démarrage de la tablette (si activé).
- Samsung/Xiaomi : désactiver l'optimisation batterie pour l'app, sinon le service peut être tué.
- Empêcher la désinstallation par l'enfant : le profil enfant Family Link bloque déjà les désinstallations.
