package com.famille.rappelpipi

import android.content.Context

/**
 * Listes de choix partagees entre les reglages de la tablette (MainActivity) et le mode parent (ParentActivity).
 * Les valeurs doivent rester identiques a RemoteConfig.kt et firebase/firestore.rules.
 */
object Choices {
    val themeIds = arrayOf(
        "blocs", "geant", "bd", "sablier", "feu", "nuit", "ballon", "carte", "porte",
        "bouton", "aqua", "dino", "fusee", "train", "goutte", "photo", "hasard"
    )
    val animIds = arrayOf("hasard", "0", "1", "2", "3", "4", "5", "6")
    val langIds = arrayOf("", "fr", "en")

    fun themeName(c: Context, id: String): String {
        val rid = c.resources.getIdentifier("theme_$id", "string", c.packageName)
        return if (rid != 0) c.getString(rid) else id
    }

    fun animName(c: Context, id: String): String {
        val rid = c.resources.getIdentifier("anim_$id", "string", c.packageName)
        return if (rid != 0) c.getString(rid) else id
    }

    /** Identifiants des personnages ; "" = separateur non selectionnable. */
    fun charIds(): ArrayList<String> = ArrayList<String>().apply {
        add("hasard"); add("fam:mc"); add("fam:sp"); add("")
        for (i in 0..17) add("mc:$i")
        for (i in 1..12) add("sp:$i")
    }

    fun charLabels(c: Context): ArrayList<String> = ArrayList<String>().apply {
        add(c.getString(R.string.char_random)); add(c.getString(R.string.char_random_mc)); add(c.getString(R.string.char_random_sp))
        add(c.getString(R.string.char_sep))
        c.resources.getStringArray(R.array.char_names).forEach { add(c.getString(R.string.char_prefix_mc) + " " + it) }
        c.resources.getStringArray(R.array.sprunki_names).forEach { add(c.getString(R.string.char_prefix_sp) + " " + it) }
    }

    fun langLabels(c: Context): List<String> =
        listOf(c.getString(R.string.lang_phone), c.getString(R.string.lang_fr), c.getString(R.string.lang_en))
}
