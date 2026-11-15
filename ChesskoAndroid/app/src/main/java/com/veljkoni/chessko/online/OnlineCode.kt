package com.veljkoni.chessko.online

import java.security.SecureRandom

/**
 * Kod uređaja za igru preko interneta: 6 znakova, bez 0/1/I/L/O.
 * Isti alfabet kao `CODE_ALPHABET` na serveru (i `OnlineCode` na iOS-u).
 */
object OnlineCode {
    const val ALPHABET = "23456789ABCDEFGHJKMNPQRSTUVWXYZ"
    const val LENGTH = 6

    private val rng = SecureRandom()

    fun generate(): String =
        String(CharArray(LENGTH) { ALPHABET[rng.nextInt(ALPHABET.length)] })

    /** "K7M4QX" → "K7M-4QX" (za prikaz). */
    fun format(code: String): String {
        if (code.length != LENGTH) return code
        return code.take(3) + "-" + code.takeLast(3)
    }

    /** Mala slova, razmaci i crtica se prihvataju; vraća null ako nije ispravan kod. */
    fun normalize(input: String): String? {
        val cleaned = input.uppercase().filter { it != '-' && !it.isWhitespace() }
        if (cleaned.length != LENGTH || !cleaned.all { ALPHABET.contains(it) }) return null
        return cleaned
    }
}
