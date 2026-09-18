import com.amehrug.app.crypto.FileCrypto
import com.amehrug.app.crypto.Hex
import com.amehrug.app.crypto.KeyEnvelope
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.security.GeneralSecurityException
import java.security.SecureRandom

fun ok(condition: Boolean, what: String) {
    if (!condition) throw AssertionError(what)
    println("ok  $what")
}

fun fails(what: String, block: () -> Unit) {
    try {
        block()
        throw AssertionError("should have failed: $what")
    } catch (e: GeneralSecurityException) {
        println("ok  refused: $what")
    } catch (e: IllegalArgumentException) {
        println("ok  refused: $what")
    }
}

fun seal(key: ByteArray, plain: ByteArray): ByteArray {
    val out = ByteArrayOutputStream()
    FileCrypto.encrypt(key, ByteArrayInputStream(plain), out, SecureRandom())
    return out.toByteArray()
}

fun open(key: ByteArray, sealed: ByteArray): ByteArray {
    val out = ByteArrayOutputStream()
    FileCrypto.decrypt(key, ByteArrayInputStream(sealed), out)
    return out.toByteArray()
}

fun main() {
    ok(Hex.encode(byteArrayOf(0, 15, 16, -1)) == "000f10ff", "hex encode")
    ok(Hex.decode("000f10ff").toList() == listOf<Byte>(0, 15, 16, -1), "hex decode")
    fails("odd hex") { Hex.decode("abc") }
    fails("not hex") { Hex.decode("zz") }

    val nonce = ByteArray(12) { it.toByte() }
    val wrapped = ByteArray(48) { (it * 3).toByte() }
    val (n2, w2) = KeyEnvelope.decode(KeyEnvelope.encode(nonce, wrapped))
    ok(n2.contentEquals(nonce) && w2.contentEquals(wrapped), "key envelope round trip")
    fails("truncated envelope") { KeyEnvelope.decode(KeyEnvelope.encode(nonce, wrapped).copyOf(10)) }
    fails("wrong envelope version") {
        val bad = KeyEnvelope.encode(nonce, wrapped)
        bad[0] = 9
        KeyEnvelope.decode(bad)
    }

    val random = SecureRandom()
    val key = ByteArray(32).also { random.nextBytes(it) }
    val other = ByteArray(32).also { random.nextBytes(it) }

    for (size in listOf(0, 1, 1000, FileCrypto.CHUNK_SIZE, FileCrypto.CHUNK_SIZE + 1, 3 * FileCrypto.CHUNK_SIZE + 7)) {
        val plain = ByteArray(size).also { random.nextBytes(it) }
        val sealed = seal(key, plain)
        ok(open(key, sealed).contentEquals(plain), "round trip of $size bytes")
        ok(!sealed.contentEquals(plain) || size == 0, "$size bytes are not stored in clear")
    }

    val plain = ByteArray(3 * FileCrypto.CHUNK_SIZE + 7).also { random.nextBytes(it) }
    val sealed = seal(key, plain)
    ok(seal(key, plain).size == sealed.size && !seal(key, plain).contentEquals(sealed), "same input gives a different file")

    fails("wrong key") { open(other, sealed) }
    fails("flipped bit") {
        val bad = sealed.copyOf()
        bad[bad.size / 2] = (bad[bad.size / 2].toInt() xor 1).toByte()
        open(key, bad)
    }
    fails("wrong magic") {
        val bad = sealed.copyOf()
        bad[1] = 'X'.code.toByte()
        open(key, bad)
    }
    fails("file cut in the middle of a chunk") { open(key, sealed.copyOf(sealed.size - 100)) }

    // A whole final chunk removed must be refused: the last chunk is marked as such.
    val oneChunkLess = ByteArrayOutputStream()
    run {
        val header = 4 + FileCrypto.PREFIX_SIZE
        val chunk = 4 + FileCrypto.CHUNK_SIZE + FileCrypto.TAG_BITS / 8
        oneChunkLess.write(sealed, 0, header + 3 * chunk)
    }
    fails("last chunk removed") { open(key, oneChunkLess.toByteArray()) }

    // Two chunks swapped must be refused: the chunk number is part of the tag.
    val swapped = sealed.copyOf()
    run {
        val header = 4 + FileCrypto.PREFIX_SIZE
        val chunk = 4 + FileCrypto.CHUNK_SIZE + FileCrypto.TAG_BITS / 8
        val first = sealed.copyOfRange(header, header + chunk)
        val second = sealed.copyOfRange(header + chunk, header + 2 * chunk)
        second.copyInto(swapped, header)
        first.copyInto(swapped, header + chunk)
    }
    fails("two chunks swapped") { open(key, swapped) }

    fails("key of the wrong size") { seal(ByteArray(16), byteArrayOf(1)) }
    println("ALL PASSED")
}
