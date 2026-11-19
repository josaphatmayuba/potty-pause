package com.famille.rappelpipi

import android.graphics.Bitmap

/**
 * Etat partage par toutes les scenes de theme (dessinees dans un repere logique 320 x 200).
 * Contrat commun a IdeaScenesA (geant, bd, sablier, feu, nuit, ballon, carte, porte),
 * IdeaScenesB (bouton, aqua, dino, fusee, train, goutte, photo) et BlocsAnim (blocs).
 * Chaque objet expose : fun draw(id: String, g: Canvas, t: Float, s: SceneState): Boolean
 * (true si l'id est gere). t = secondes depuis l'affichage. Le cadrage 320 x 200,
 * la barre de progression, les confettis et les diamants sont dessines par ThemeView.
 */
class SceneState {
    /** 0..1 : avancement du blocage. */
    var progress = 0f
    /** Mode fete apres la recompense. */
    var celebrate = false
    /** Secondes depuis le debut de la fete (0 si pas de fete). */
    var celebrateT = 0f
    /** Image choisie par le parent (theme photo), ou null. */
    var image: Bitmap? = null
    /** Personnage du theme blocs (0..15). */
    var kind = 0
    /** Sprunki 1..12 si le personnage est un Sprunki (0 = Minecraft, kind utilise). */
    var sprunki = 0
    /** Animation du theme blocs (0..5). */
    var variant = 0
    /** Diamants gagnes (0..5) et coffre ouvert. */
    var diamonds = 0
    var chest = false
}
