package com.byd.wjx.data.autoservice

import android.util.Base64
import java.math.BigInteger
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.KeyPair
import java.security.Signature
import java.security.interfaces.RSAPublicKey
import java.util.Arrays

/** ADB RSA challenge signing and daemon-compatible public-key serialization. */
internal object AdbAuth {
    val AUTH_PADDING: ByteArray = byteArrayOf(
        0x30, 0x21, 0x30, 0x09, 0x06, 0x05, 0x2B, 0x0E,
        0x03, 0x02, 0x1A, 0x05, 0x00, 0x04, 0x14,
    )

    fun signToken(token: ByteArray, keyPair: KeyPair): ByteArray {
        val padded = Arrays.copyOf(AUTH_PADDING, AUTH_PADDING.size + token.size)
        System.arraycopy(token, 0, padded, AUTH_PADDING.size, token.size)
        return Signature.getInstance("NONEwithRSA").run {
            initSign(keyPair.private)
            update(padded)
            sign()
        }
    }

    fun serializePublicKey(keyPair: KeyPair): ByteArray {
        val publicKey = keyPair.public as RSAPublicKey
        var modulus = publicKey.modulus
        val one = BigInteger.ONE
        val radix = one.shiftLeft(32)
        val mask = radix.subtract(one)
        val n0Inverse = modulus.and(mask).modInverse(radix).negate().mod(radix)
        var rr = one.shiftLeft(4096).mod(modulus)

        val buffer = ByteBuffer.allocate(ADB_PUBLIC_KEY_BYTES).order(ByteOrder.LITTLE_ENDIAN)
        buffer.putInt(MODULUS_WORDS)
        buffer.putInt(n0Inverse.toInt())
        repeat(MODULUS_WORDS) {
            buffer.putInt(modulus.and(mask).toInt())
            modulus = modulus.shiftRight(32)
        }
        repeat(MODULUS_WORDS) {
            buffer.putInt(rr.and(mask).toInt())
            rr = rr.shiftRight(32)
        }
        buffer.putInt(publicKey.publicExponent.toInt())

        val encoded = Base64.encodeToString(buffer.array(), Base64.NO_WRAP)
        return (encoded + PUBLIC_KEY_HOST_ID).toByteArray(Charsets.UTF_8)
    }

    private const val MODULUS_WORDS = 64
    private const val ADB_PUBLIC_KEY_BYTES = 524
    private const val PUBLIC_KEY_HOST_ID = " wjx@dilink\u0000"
}
