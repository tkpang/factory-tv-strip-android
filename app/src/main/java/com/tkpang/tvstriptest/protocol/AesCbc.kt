package com.tkpang.tvstriptest.protocol

import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

object AesCbc {
    fun encrypt(key: ByteArray, iv: ByteArray, plain: ByteArray): ByteArray =
        crypt(Cipher.ENCRYPT_MODE, key, iv, plain)

    fun decrypt(key: ByteArray, iv: ByteArray, encrypted: ByteArray): ByteArray =
        crypt(Cipher.DECRYPT_MODE, key, iv, encrypted)

    private fun crypt(mode: Int, key: ByteArray, iv: ByteArray, input: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(mode, SecretKeySpec(key, "AES"), IvParameterSpec(iv))
        return cipher.doFinal(input)
    }
}
