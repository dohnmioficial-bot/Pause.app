package com.exemplo.pausa

import android.content.Context
import java.security.MessageDigest

object Pin {
    private const val PREFS = "pausa"
    private const val KEY = "pin_hash"

    private fun hash(pin: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(("pausa-salt-" + pin).toByteArray())
            .joinToString("") { "%02x".format(it) }

    fun definido(ctx: Context): Boolean =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).contains(KEY)

    fun salvar(ctx: Context, pin: String) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY, hash(pin)).apply()
    }

    fun confere(ctx: Context, pin: String): Boolean {
        val salvo = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY, null) ?: return false
        return salvo == hash(pin)
    }
}
