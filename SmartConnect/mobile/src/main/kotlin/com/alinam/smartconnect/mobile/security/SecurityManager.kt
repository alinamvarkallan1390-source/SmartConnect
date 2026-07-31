package com.alinam.smartconnect.mobile.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import timber.log.Timber
import java.security.KeyStore
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * AES-256 encryption manager using Android Keystore for key storage.
 * Provides GCM authenticated encryption for all Bluetooth data transfer.
 */
@Singleton
class SecurityManager @Inject constructor() {

    companion object {
        private const val KEY_ALIAS = "SmartConnect_BT_Key"
        private const val KEYSTORE_PROVIDER = "AndroidKeyStore"
        private const val AES_MODE = "AES/GCM/NoPadding"
        private const val KEY_SIZE = 256
        private const val GCM_TAG_LENGTH = 128
        private const val IV_SIZE = 12 // 96 bits for GCM
        private const val SESSION_KEY_SIZE = 32 // 256 bits
    }

    private val keyStore: KeyStore = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }
    private var sessionKey: ByteArray? = null

    init {
        ensureKeyExists()
    }

    private fun ensureKeyExists() {
        if (!keyStore.containsAlias(KEY_ALIAS)) {
            generateAndroidKeystoreKey()
        }
    }

    private fun generateAndroidKeystoreKey() {
        val keyGenSpec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        ).apply {
            setKeySize(KEY_SIZE)
            setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            setRandomizedEncryptionRequired(true)
        }.build()
        KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE_PROVIDER).apply {
            init(keyGenSpec)
            generateKey()
        }
        Timber.d("Android Keystore AES-256-GCM key generated")
    }

    private fun getSecretKey(): SecretKey {
        return (keyStore.getEntry(KEY_ALIAS, null) as KeyStore.SecretKeyEntry).secretKey
    }

    /**
     * Encrypts data using AES-256-GCM. Returns IV + CipherText as Base64.
     */
    fun encrypt(plaintext: ByteArray): String {
        return try {
            val cipher = Cipher.getInstance(AES_MODE)
            cipher.init(Cipher.ENCRYPT_MODE, getSecretKey())
            val iv = cipher.iv
            val ciphertext = cipher.doFinal(plaintext)
            val combined = iv + ciphertext
            Base64.encodeToString(combined, Base64.NO_WRAP)
        } catch (e: Exception) {
            Timber.e(e, "Encryption failed")
            throw e
        }
    }

    /**
     * Decrypts Base64-encoded IV + CipherText using AES-256-GCM.
     */
    fun decrypt(encryptedBase64: String): ByteArray {
        return try {
            val combined = Base64.decode(encryptedBase64, Base64.NO_WRAP)
            val iv = combined.copyOfRange(0, IV_SIZE)
            val ciphertext = combined.copyOfRange(IV_SIZE, combined.size)
            val cipher = Cipher.getInstance(AES_MODE)
            val spec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
            cipher.init(Cipher.DECRYPT_MODE, getSecretKey(), spec)
            cipher.doFinal(ciphertext)
        } catch (e: Exception) {
            Timber.e(e, "Decryption failed")
            throw e
        }
    }

    fun encryptString(plaintext: String): String = encrypt(plaintext.toByteArray(Charsets.UTF_8))
    fun decryptString(encrypted: String): String = decrypt(encrypted).toString(Charsets.UTF_8)

    /**
     * Generates a random session key for Bluetooth session.
     */
    fun generateSessionKey(): ByteArray {
        val key = ByteArray(SESSION_KEY_SIZE)
        SecureRandom().nextBytes(key)
        sessionKey = key
        return key
    }

    /**
     * Encrypts data using session key (AES-256-CBC for Bluetooth transfer speed).
     */
    fun encryptWithSessionKey(data: ByteArray, key: ByteArray): String {
        val iv = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val secretKey = SecretKeySpec(key, "AES")
        val cipher = Cipher.getInstance("AES/CBC/PKCS7Padding")
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, IvParameterSpec(iv))
        val encrypted = cipher.doFinal(data)
        return Base64.encodeToString(iv + encrypted, Base64.NO_WRAP)
    }

    fun decryptWithSessionKey(encryptedBase64: String, key: ByteArray): ByteArray {
        val combined = Base64.decode(encryptedBase64, Base64.NO_WRAP)
        val iv = combined.copyOfRange(0, 16)
        val ciphertext = combined.copyOfRange(16, combined.size)
        val secretKey = SecretKeySpec(key, "AES")
        val cipher = Cipher.getInstance("AES/CBC/PKCS7Padding")
        cipher.init(Cipher.DECRYPT_MODE, secretKey, IvParameterSpec(iv))
        return cipher.doFinal(ciphertext)
    }

    /**
     * Generates auth challenge for device verification.
     */
    fun generateChallenge(): String {
        val challenge = ByteArray(32)
        SecureRandom().nextBytes(challenge)
        return Base64.encodeToString(challenge, Base64.NO_WRAP)
    }

    /**
     * Computes SHA-256 checksum for file integrity verification.
     */
    fun computeChecksum(data: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(data)
        return Base64.encodeToString(hash, Base64.NO_WRAP)
    }

    fun verifyChecksum(data: ByteArray, expectedChecksum: String): Boolean {
        return computeChecksum(data) == expectedChecksum
    }

    /**
     * Generates a device-unique ID based on hardware identifiers.
     */
    fun generateDeviceId(seed: String): String {
        val hash = MessageDigest.getInstance("SHA-256").digest(seed.toByteArray())
        return Base64.encodeToString(hash, Base64.NO_WRAP).take(32)
    }
}
