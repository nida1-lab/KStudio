package com.nida.kstudio

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class RunSettings(context: Context) {
    private val prefs = context.getSharedPreferences("remote_build", Context.MODE_PRIVATE)
    var repository: String
        get() = prefs.getString("repository", "nida1-lab/KStudio")!!
        set(value) { prefs.edit().putString("repository", value).apply() }
    var uploadConsent: Boolean
        get() = prefs.getBoolean("upload_consent", false)
        set(value) { prefs.edit().putBoolean("upload_consent", value).apply() }
    var allowPublic: Boolean
        get() = prefs.getBoolean("allow_public", false)
        set(value) { prefs.edit().putBoolean("allow_public", value).apply() }

    fun readToken(): String {
        val encoded = prefs.getString("token", null) ?: return ""
        val parts = encoded.split(":", limit = 2)
        require(parts.size == 2) { "保存済みのトークンを読み取れません。再入力してください。" }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, Base64.decode(parts[0], Base64.NO_WRAP)))
        return cipher.doFinal(Base64.decode(parts[1], Base64.NO_WRAP)).toString(Charsets.UTF_8)
    }

    fun saveToken(token: String) {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val encrypted = cipher.doFinal(token.toByteArray(Charsets.UTF_8))
        prefs.edit().putString("token", Base64.encodeToString(cipher.iv, Base64.NO_WRAP) + ":" +
            Base64.encodeToString(encrypted, Base64.NO_WRAP)).apply()
    }

    fun clearToken() { prefs.edit().remove("token").apply() }
    fun pending(): String? = prefs.getString("pending", null)
    fun savePending(value: String?) { prefs.edit().putString("pending", value).commit() }

    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey("kstudio.run.token", null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder("kstudio.run.token",
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }
}
