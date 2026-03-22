package com.example.alarmaapp.ble

import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

class AES {
    fun secretKeyFromString(keyStr: String): SecretKey {
        val keyBytes = keyStr.toByteArray(Charsets.UTF_8)
        return SecretKeySpec(keyBytes, "AES")
    }
    fun aesEncrypt(data: ByteArray, secretKey: SecretKey): ByteArray {
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        val ivParameterSpec = IvParameterSpec(byteArrayOf(0xff.toByte(), 0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07,
        0x08, 0x09, 0x0a, 0x0b, 0x0c, 0x0d, 0x0e, 0x0f)) // Use a secure IV in production
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, ivParameterSpec)
        return cipher.doFinal(data)
    }
    fun aesDecrypt(encryptedData: ByteArray, secretKey: SecretKey): ByteArray {
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        val ivParameterSpec = IvParameterSpec(byteArrayOf(0xff.toByte(), 0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07,
            0x08, 0x09, 0x0a, 0x0b, 0x0c, 0x0d, 0x0e, 0x0f)) // Use the same IV as used in encryption
        cipher.init(Cipher.DECRYPT_MODE, secretKey, ivParameterSpec)
        return cipher.doFinal(encryptedData)
    }
}