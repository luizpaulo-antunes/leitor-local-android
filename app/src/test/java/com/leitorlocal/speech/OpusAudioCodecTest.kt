package com.leitorlocal.speech

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OpusAudioCodecTest {
    @Test
    fun roundTripPreservesDurationAndCompressesKokoroAudio() {
        val sampleRate = 24_000
        val samples = FloatArray(sampleRate * 3) { index ->
            val envelope = when {
                index < 800 -> index / 800f
                index > sampleRate * 3 - 800 -> (sampleRate * 3 - index) / 800f
                else -> 1f
            }
            (sin(2.0 * PI * 180.0 * index / sampleRate) * 0.35 * envelope).toFloat()
        }
        val encoded = ByteArrayOutputStream().also { bytes ->
            DataOutputStream(bytes).use { OpusAudioCodec.write(NeuralAudio(samples, sampleRate), it) }
        }.toByteArray()
        val decoded = DataInputStream(ByteArrayInputStream(encoded)).use(OpusAudioCodec::read)

        assertEquals(sampleRate, decoded.sampleRate)
        assertEquals(samples.size, decoded.samples.size)
        assertTrue(encoded.size < samples.size / 2)
        assertTrue("Cache Opus pequeno demais: ${encoded.size}", encoded.size > 1_000)
        val decodedPeak = decoded.samples.maxOf(::abs)
        val decodedMeanEnergy = decoded.samples.sumOf { (it * it).toDouble() }.div(samples.size)
        assertTrue("Pico decodificado: $decodedPeak", decodedPeak > 0.1f)
        assertTrue("Energia média decodificada: $decodedMeanEnergy", decodedMeanEnergy > 0.005)
    }

    @Test
    fun rejectsSampleRatesOutsideOpusSet() {
        assertTrue(OpusAudioCodec.supports(24_000))
        assertTrue(!OpusAudioCodec.supports(22_050))
    }
}
