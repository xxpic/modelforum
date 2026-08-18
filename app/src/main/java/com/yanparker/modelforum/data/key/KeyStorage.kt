package com.yanparker.modelforum.data.key

import android.content.Context
import android.util.Base64
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.security.SecureRandom

class KeyStorage(context: Context) {

    private val prefs: android.content.SharedPreferences by lazy {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            "secure_api_keys",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    /** Сохраняет ключ под случайным идентификатором, возвращает keyRef. */
    fun putKey(key: String): String {
        val ref = "k_" + SecureRandom().let { r ->
            ByteArray(16).also { r.nextBytes(it) }
        }.let { Base64.encodeToString(it, Base64.NO_WRAP or Base64.NO_PADDING) }
            .replace("/", "_").replace("+", "-")
        prefs.edit().putString(ref, key).apply()
        return ref
    }

    fun putKey(ref: String, key: String) {
        prefs.edit().putString(ref, key).apply()
    }

    fun getKey(ref: String): String? = prefs.getString(ref, null)

    fun removeKey(ref: String) {
        prefs.edit().remove(ref).apply()
    }
}