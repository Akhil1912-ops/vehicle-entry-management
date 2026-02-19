package com.vehicleentry.app

import android.content.Context
import android.content.SharedPreferences
import java.security.MessageDigest

object AdminAuth {
    private const val PREFS_NAME = "admin_auth"
    private const val KEY_PASSWORD_HASH = "password_hash"

    private fun prefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun hasPassword(context: Context): Boolean {
        return try {
            prefs(context).getString(KEY_PASSWORD_HASH, null) != null
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /** Set a new password. Call when first-time setup or changing password. */
    fun setPassword(context: Context, password: String): Boolean {
        if (password.length < 4) return false
        return try {
            prefs(context).edit()
                .putString(KEY_PASSWORD_HASH, sha256(password))
                .apply()
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    fun verify(context: Context, password: String): Boolean {
        return try {
            val stored = prefs(context).getString(KEY_PASSWORD_HASH, null) ?: return false
            stored == sha256(password)
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    private fun sha256(input: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
