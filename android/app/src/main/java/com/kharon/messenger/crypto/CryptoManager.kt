package com.kharon.messenger.crypto

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.goterl.lazysodium.LazySodiumAndroid
import com.goterl.lazysodium.SodiumAndroid
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Base64
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CryptoManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val sodium = LazySodiumAndroid(SodiumAndroid())

    private val BOX_PUBLICKEYBYTES = 32
    private val BOX_SECRETKEYBYTES = 32
    private val BOX_NONCEBYTES     = 24
    private val BOX_MACBYTES       = 16

    private val prefs by lazy {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            "kharon_keys",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    fun getOrCreateKeyPair(): KeyPair {
        val existingPub = prefs.getString(KEY_PUBLIC, null)
        val existingSec = prefs.getString(KEY_SECRET, null)
        if (existingPub != null && existingSec != null) {
            return KeyPair(publicKey = existingPub, secretKey = existingSec)
        }
        return generateAndSaveKeyPair()
    }

    private fun generateAndSaveKeyPair(): KeyPair {
        val pubKey = ByteArray(BOX_PUBLICKEYBYTES)
        val secKey = ByteArray(BOX_SECRETKEYBYTES)
        sodium.cryptoBoxKeypair(pubKey, secKey)
        val pubB64 = pubKey.toBase64()
        val secB64 = secKey.toBase64()
        prefs.edit().putString(KEY_PUBLIC, pubB64).putString(KEY_SECRET, secB64).apply()
        return KeyPair(publicKey = pubB64, secretKey = secB64)
    }

    fun encrypt(plaintext: String, recipientPubKey: String, mySecretKey: String): EncryptResult {
        return try {
            val message    = plaintext.toByteArray(Charsets.UTF_8)
            val nonce      = generateNonce()
            val recipient  = recipientPubKey.fromBase64()
            val secret     = mySecretKey.fromBase64()
            val ciphertext = ByteArray(message.size + BOX_MACBYTES)
            val ok = sodium.cryptoBoxEasy(ciphertext, message, message.size.toLong(), nonce, recipient, secret)
            if (!ok) return EncryptResult.Error("encryption failed")
            val payload = nonce + ciphertext
            EncryptResult.Success(payload.toBase64())
        } catch (e: Exception) {
            EncryptResult.Error(e.message ?: "unknown error")
        }
    }

    fun decrypt(payload: String, senderPubKey: String, mySecretKey: String): DecryptResult {
        return try {
            val raw = payload.fromBase64()
            if (raw.size < BOX_NONCEBYTES + BOX_MACBYTES + 1) {
                return DecryptResult.Error("payload too short")
            }
            val nonce      = raw.sliceArray(0 until BOX_NONCEBYTES)
            val ciphertext = raw.sliceArray(BOX_NONCEBYTES until raw.size)
            val sender     = senderPubKey.fromBase64()
            val secret     = mySecretKey.fromBase64()
            val plaintext  = ByteArray(ciphertext.size - BOX_MACBYTES)
            val ok = sodium.cryptoBoxOpenEasy(plaintext, ciphertext, ciphertext.size.toLong(), nonce, sender, secret)
            if (!ok) return DecryptResult.Error("decryption failed")
            DecryptResult.Success(String(plaintext, Charsets.UTF_8))
        } catch (e: Exception) {
            DecryptResult.Error(e.message ?: "unknown error")
        }
    }

    private fun generateNonce(): ByteArray {
        return sodium.randomBytesBuf(BOX_NONCEBYTES)
    }

    private fun ByteArray.toBase64(): String = Base64.getEncoder().encodeToString(this)
    private fun String.fromBase64(): ByteArray = Base64.getDecoder().decode(this)

    companion object {
        private const val KEY_PUBLIC = "pub_key"
        private const val KEY_SECRET = "sec_key"
    }
}

data class KeyPair(val publicKey: String, val secretKey: String)

sealed class EncryptResult {
    data class Success(val payload: String) : EncryptResult()
    data class Error(val reason: String)    : EncryptResult()
}

sealed class DecryptResult {
    data class Success(val plaintext: String) : DecryptResult()
    data class Error(val reason: String)      : DecryptResult()
}
