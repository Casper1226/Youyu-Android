package com.youyu.android.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import com.youyu.domain.LedgerException
import com.youyu.domain.requireRule
import org.bouncycastle.crypto.generators.Argon2BytesGenerator
import org.bouncycastle.crypto.params.Argon2Parameters
import java.io.File
import java.nio.ByteBuffer
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/** No financial data or plaintext keys are written outside the encrypted vault. */
class VaultKeys(private val directory: File, private val alias: String = "com.youyu.vault.v1") {
    private val envelope = AtomicFile(File(directory, "key.envelope"))
    val exists: Boolean get() = envelope.baseFile.exists()
    private val random = SecureRandom()

    fun create(password: CharArray): ByteArray {
        requireRule(!exists && !File(directory, "ledger.db").exists(), "金库已存在，请解锁")
        requireRule(password.size >= 12, "金库口令至少需要 12 个字符")
        directory.mkdirs()
        val key = ByteArray(32).also(random::nextBytes)
        val salt = ByteArray(32).also(random::nextBytes)
        val derived = derive(password, salt)
        try {
            val passwordWrapped = encrypt(SecretKeySpec(derived, "AES"), key)
            val inner = ByteBuffer.allocate(4 + salt.size + passwordWrapped.size)
                .putInt(1).put(salt).put(passwordWrapped).array()
            try {
                val sealed = encrypt(deviceKey(create = true), inner)
                val stream = envelope.startWrite()
                try {
                    stream.write(sealed)
                    envelope.finishWrite(stream)
                } catch (error: Exception) {
                    envelope.failWrite(stream)
                    throw error
                }
            } finally { inner.fill(0) }
            return key
        } catch (error: Exception) {
            key.fill(0)
            throw LedgerException("无法创建加密金库，请检查设备安全设置与可用空间")
        } finally { derived.fill(0) }
    }

    fun unlock(password: CharArray): ByteArray {
        requireRule(exists, "未找到金库密钥，不能自动重建已有金库")
        try {
            val sealed = envelope.readFully()
            requireRule(sealed.size in 60..4096, "金库密钥文件无效")
            val inner = decrypt(deviceKey(create = false), sealed)
            try {
                val buffer = ByteBuffer.wrap(inner)
                requireRule(buffer.int == 1, "金库版本不受支持")
                val salt = ByteArray(32).also(buffer::get)
                val wrapped = ByteArray(buffer.remaining()).also(buffer::get)
                val derived = derive(password, salt)
                try {
                    return decrypt(SecretKeySpec(derived, "AES"), wrapped).also {
                        requireRule(it.size == 32, "金库密钥无效")
                    }
                } finally { derived.fill(0) }
            } finally { inner.fill(0) }
        } catch (error: Exception) {
            throw LedgerException("口令不正确，或此设备无法访问金库密钥")
        }
    }

    private fun deviceKey(create: Boolean): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(alias, null) as? SecretKey)?.let { return it }
        requireRule(create, "设备密钥不可用")
        fun generate(strongBox: Boolean): SecretKey {
            val spec = KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setKeySize(256).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true).setIsStrongBoxBacked(strongBox).build()
            return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
                .apply { init(spec) }.generateKey()
        }
        return try { generate(true) } catch (_: android.security.keystore.StrongBoxUnavailableException) { generate(false) }
    }

    private fun derive(password: CharArray, salt: ByteArray): ByteArray {
        val parameters = Argon2Parameters.Builder(Argon2Parameters.ARGON2_id)
            .withVersion(Argon2Parameters.ARGON2_VERSION_13).withSalt(salt)
            .withMemoryAsKB(65536).withIterations(3).withParallelism(1).build()
        return ByteArray(32).also { Argon2BytesGenerator().apply { init(parameters) }.generateBytes(password, it) }
    }
    private fun encrypt(key: SecretKey, bytes: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key) }
        cipher.updateAAD("youyu-vault-v1".toByteArray(Charsets.UTF_8))
        return cipher.iv + cipher.doFinal(bytes)
    }
    private fun decrypt(key: SecretKey, bytes: ByteArray): ByteArray {
        requireRule(bytes.size >= 28, "加密数据无效")
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, bytes.copyOfRange(0, 12)))
            updateAAD("youyu-vault-v1".toByteArray(Charsets.UTF_8))
        }
        return cipher.doFinal(bytes, 12, bytes.size - 12)
    }
}
