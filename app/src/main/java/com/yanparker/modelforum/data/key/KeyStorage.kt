package com.yanparker.modelforum.data.key

import android.content.Context
import android.util.Base64
import java.security.KeyStore
import java.util.concurrent.ThreadLocalRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Безопасное хранение API-ключей: шифрование AES/GCM через AndroidKeyStore.
 */
class KeyStorage(context: Context) {

    private val prefs = context.getSharedPreferences("secure_api_keys", Context.MODE_PRIVATE)
    private val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
    private val alias = "model_forum_master"
    private val cipher = Cipher.getInstance("AES/GCM/NoPadding")

    private fun masterKey(): SecretKey {
        (keyStore.getKey(alias, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(java.security.KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            android.security.keystore.KeyGenParameterSpec.Builder(
                alias,
                android.security.keystore.KeyProperties.PURPOSE_ENCRYPT or android.security.keystore.KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(android.security.keystore.KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(android.security.keystore.KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return generator.generateKey()
    }

    private fun encrypt(plain: String): String {
        cipher.init(Cipher.ENCRYPT_MODE, masterKey())
        val enc = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        val iv = cipher.iv
        return Base64.encodeToString(iv, Base64.NO_WRAP) + ":" + Base64.encodeToString(enc, Base64.NO_WRAP)
    }

    private fun decrypt(payload: String): String {
        val parts = payload.split(":", limit = 2)
        if (parts.size != 2) throw IllegalArgumentException("bad payload")
        val iv = Base64.decode(parts[0], Base64.NO_WRAP)
        val data = Base64.decode(parts[1], Base64.NO_WRAP)
        cipher.init(Cipher.DECRYPT_MODE, masterKey(), GCMParameterSpec(128, iv))
        return String(cipher.doFinal(data), Charsets.UTF_8)
    }

    /** Сохраняет ключ под случайным идентификатором, возвращает keyRef. */
    fun putKey(key: String): String {
        val alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"
        val ref = "k_" + buildString {
            val random = ThreadLocalRandom.current()
            repeat(16) { append(alphabet[random.nextInt(alphabet.length)]) }
        }
        prefs.edit().putString(ref, encrypt(key)).apply()
        return ref
    }

    fun putKey(ref: String, key: String) {
        prefs.edit().putString(ref, encrypt(key)).apply()
    }

    fun getKey(ref: String): String? {
        val payload = prefs.getString(ref, null) ?: return null
        return try {
            decrypt(payload)
        } catch (_: Exception) {
            null
        }
    }

    fun removeKey(ref: String) {
        prefs.edit().remove(ref).apply()
    }
}