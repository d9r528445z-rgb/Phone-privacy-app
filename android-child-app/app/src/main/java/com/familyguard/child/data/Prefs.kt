package com.familyguard.child.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * Encrypted settings store. Holds the parent PIN (as a salted hash — never
 * the plaintext), the consent flag, and the local-server port.
 */
class Prefs private constructor(private val sp: SharedPreferences) {

    companion object {
        private const val FILE = "familyguard_secure_prefs"
        private const val KEY_PIN_HASH = "pin_hash"
        private const val KEY_PIN_SALT = "pin_salt"
        private const val KEY_CONSENT = "consent_accepted"
        private const val KEY_PORT = "server_port"
        private const val KEY_SERVER_ON = "server_enabled"

        @Volatile private var INSTANCE: Prefs? = null

        fun get(context: Context): Prefs = INSTANCE ?: synchronized(this) {
            INSTANCE ?: build(context).also { INSTANCE = it }
        }

        private fun build(context: Context): Prefs {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            val sp = EncryptedSharedPreferences.create(
                context,
                FILE,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
            return Prefs(sp)
        }

        private fun sha256(input: ByteArray): String =
            MessageDigest.getInstance("SHA-256").digest(input)
                .joinToString("") { "%02x".format(it) }
    }

    var consentAccepted: Boolean
        get() = sp.getBoolean(KEY_CONSENT, false)
        set(v) = sp.edit().putBoolean(KEY_CONSENT, v).apply()

    var serverPort: Int
        get() = sp.getInt(KEY_PORT, 8766)
        set(v) = sp.edit().putInt(KEY_PORT, v).apply()

    var serverEnabled: Boolean
        get() = sp.getBoolean(KEY_SERVER_ON, true)
        set(v) = sp.edit().putBoolean(KEY_SERVER_ON, v).apply()

    val isPinSet: Boolean get() = sp.contains(KEY_PIN_HASH)

    /** Store a salted hash of the PIN. Minimum 4 digits enforced by the UI. */
    fun setPin(pin: String) {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val saltHex = salt.joinToString("") { "%02x".format(it) }
        val hash = sha256((saltHex + pin).toByteArray())
        sp.edit().putString(KEY_PIN_SALT, saltHex).putString(KEY_PIN_HASH, hash).apply()
    }

    /** Constant-ish comparison against stored salted hash. */
    fun verifyPin(pin: String): Boolean {
        val salt = sp.getString(KEY_PIN_SALT, null) ?: return false
        val stored = sp.getString(KEY_PIN_HASH, null) ?: return false
        val candidate = sha256((salt + pin).toByteArray())
        return constantTimeEquals(candidate, stored)
    }

    private fun constantTimeEquals(a: String, b: String): Boolean {
        if (a.length != b.length) return false
        var result = 0
        for (i in a.indices) result = result or (a[i].code xor b[i].code)
        return result == 0
    }
}
