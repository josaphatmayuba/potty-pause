# Checklist de publication — de zéro à la production

P = propriétaire, C = Claude. État de départ : v1.8, flavor `play` (`com.pottypause.app`) dans le code, pas encore de compte Play. Détail et sources : `docs/PLAN_TELECOMMANDE_ET_PLAY.md`.

## A. Compte et identité
1. **P** Créer le compte Play Console (25 USD, pièce d'identité, téléphone). Choisir personnel (test fermé obligatoire) ou organisation (D-U-N-S, pas de contrainte de testeurs). **Lancer tout de suite** : la vérification prend des jours.
2. **P** Décider ce qui s'affiche publiquement : nom d'éditeur (Virtuel 3D Studio), e-mail de contact, éventuellement adresse postale. Aucun autre fait ne doit être inventé.

## B. Firebase (projet `rappelpipi`)
3. **P** Vérifier Auth : Google activé, Anonyme activé, e-mail/mot de passe désactivé. Firestore en `eur3`.
4. **C** Relire `firestore.rules` vs `DATA_MODEL.md` ; **P** déployer : `firebase deploy --only firestore:rules`. **C** lancer les tests d'émulateur (non exécutés à ce jour).
5. **P** Activer le **TTL Firestore** sur `pairingCodes.expires` (Console Firebase > Firestore > TTL). La politique de confidentialité l'annonce : à activer avant publication.
6. **P** Enregistrer l'application `com.pottypause.app` dans Firebase, récupérer `google-services.json` (variante play) ; **C** l'intégrer sans le committer s'il est sensible.
7. **P** Ajouter les empreintes dans Firebase (Paramètres du projet > Votre application Android) : SHA-1 et SHA-256 de la clé de debug, de la **clé d'upload**, puis de la **clé de signature Play App Signing** (visible dans Play Console > Intégrité de l'appli, après la 1re mise en ligne). Sans la dernière, la connexion Google échoue en version Play. Penser au client OAuth Android correspondant (Google Cloud).
8. **C+P** **App Check** (Play Integrity) : **C** ajoute le SDK et l'enregistrement dans le code ; **P** l'active d'abord en « surveillance », vérifie les métriques, puis passe en **enforce** pour Firestore et Auth. À ce jour le code n'inclut pas App Check.

## C. Build et signature
9. **P** Générer la **clé d'upload** (keystore + mots de passe), la sauvegarder chiffrée hors OneDrive et hors git.
10. **C** Passer `targetSdk`/`compileSdk` à 36 si ce n'est pas fait ; produire l'**AAB** `bundlePlayRelease` signé (R8/minify, règles Firebase) ; vérifier le manifeste fusionné : pas de `REQUEST_INSTALL_PACKAGES`, admin d'appareil, FileProvider d'APK ni alias cachable.
11. **P** Activer **Play App Signing** à la création de la 1re version (par défaut) et noter l'empreinte de la clé de signature (étape 7).
12. **C** Tests manuels Android 14, 15, 16 : overlay, service `specialUse`, redémarrage, appairage, suppression du compte.

## D. Confidentialité (en parallèle)
13. **P** Relire et valider `privacy-policy-fr.md` / `-en.md`.
14. **P** Héberger sur une URL publique stable (ex. GitHub Pages, une page FR + une EN) ; **C** convertit le Markdown en HTML et prépare la page. L'URL va dans la fiche Play et dans l'écran de connexion de l'appli. Y indiquer aussi la procédure de suppression (exigée pour les applis avec comptes).
15. **C** Ajouter le lien vers la politique et le consentement dans l'appli (écran parent, avant la connexion Google) ; vérifier que le bouton « Supprimer mon compte et mes données » est présent et testé.

## E. Fiche et déclarations (Console)
16. **C** Préparer textes (`store-listing.md`), icône 512, image 1024×500, captures, vidéo 30 s pour overlay/service. **P** fournir tablettes et appareils pour les captures (aucun enfant réel, aucun vrai prénom).
17. **P** Créer l'appli : FR par défaut, EN en plus, gratuite. Catégorie, contact, pays.
18. **P** Remplir, avec `data-safety-and-forms.md` : Data safety, classification du contenu, public cible, Families, publicité (non), déclaration du service `specialUse`, justification de l'overlay, accès à l'appli (compte Google de test). **Résoudre chaque point « À VÉRIFIER »** et me communiquer les écarts pour que la politique reste identique.
19. **P** Accepter les conditions Play et le DPA Firebase.

## F. Pistes de test
20. **P** Créer la piste **test interne** (jusqu'à 100 testeurs), y envoyer l'AAB ; **C** corrige les rejets ou retours.
21. **P** Recruter **au moins 12 testeurs** (viser 15-20, adresses Gmail) dans une liste ou un groupe Google.
22. **P** Lancer le **test fermé** ; les testeurs restent inscrits **14 jours consécutifs** sans interruption (compte personnel créé après le 13/11/2023). Ne pas retirer de testeur. Relever leurs retours.
23. **P** Après 14 jours : demander l'**accès à la production** (questionnaire Play). Délai de revue : quelques jours. À VÉRIFIER les exigences actuelles dans la Play Console.

## G. Production
24. **P** Créer la version de production, déploiement progressif 20 % puis 100 %.
25. **C** Désactiver l'ancien `config.json` public et mettre à jour README/CHANGELOG ; **P** décider de la suite des APK `direct`.
26. **P+C** Suivre les rapports Android vitals et les avis ; répondre aux demandes de suppression reçues par e-mail sous 30 jours (engagement de la politique).
