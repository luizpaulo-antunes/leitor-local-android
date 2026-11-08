package com.leitorlocal.speech

import io.github.jaredmdobson.concentus.OpusApplication
import io.github.jaredmdobson.concentus.OpusDecoder
import io.github.jaredmdobson.concentus.OpusEncoder
import io.github.jaredmdobson.concentus.OpusSignal
import java.io.DataInputStream
import java.io.DataOutputStream
import kotlin.math.roundToInt

/**
 * Small private Opus container used only by the neural cache. Concentus is packaged in the APK,
 * so encoding and decoding do not depend on an Android vendor codec or a network service.
 */
internal object OpusAudioCodec {
    fun supports(sampleRate: Int): Boolean = sampleRate in SUPPORTED_SAMPLE_RATES

    fun write(audio: NeuralAudio, output: DataOutputStream) {
        require(supports(audio.sampleRate)) { "Taxa não suportada pelo cache Opus." }
        val frameSize = audio.sampleRate * FRAME_MILLIS / 1_000
        val encoder = OpusEncoder(
            audio.sampleRate,
            CHANNELS,
            OpusApplication.OPUS_APPLICATION_RESTRICTED_LOWDELAY,
        )
            .apply {
                bitrate = BITRATE
                complexity = COMPLEXITY
                signalType = OpusSignal.OPUS_SIGNAL_VOICE
                useVBR = true
                useConstrainedVBR = false
            }
        val preSkip = encoder.lookahead.coerceAtLeast(0)
        val encodedSampleCount = audio.samples.size + preSkip
        val frameCount = (encodedSampleCount + frameSize - 1) / frameSize
        val pcm = ShortArray(frameCount * frameSize)
        audio.samples.forEachIndexed { index, sample ->
            pcm[index] = (sample.coerceIn(-1f, 1f) * Short.MAX_VALUE).roundToInt().toShort()
        }

        output.writeInt(audio.sampleRate)
        output.writeInt(audio.samples.size)
        output.writeInt(frameSize)
        output.writeInt(preSkip)
        output.writeInt(frameCount)
        val packet = ByteArray(MAX_PACKET_BYTES)
        repeat(frameCount) { frameIndex ->
            val packetSize = encoder.encode(
                pcm,
                frameIndex * frameSize,
                frameSize,
                packet,
                0,
                packet.size,
            )
            check(packetSize in 1..MAX_PACKET_BYTES) { "Pacote Opus inválido." }
            output.writeShort(packetSize)
            output.write(packet, 0, packetSize)
        }
    }

    fun read(input: DataInputStream): NeuralAudio {
        val sampleRate = input.readInt()
        val sampleCount = input.readInt()
        val frameSize = input.readInt()
        val preSkip = input.readInt()
        val frameCount = input.readInt()
        check(supports(sampleRate)) { "Taxa Opus inválida." }
        check(sampleCount in 1..MAX_SAMPLE_COUNT) { "Tamanho Opus inválido." }
        check(frameSize == sampleRate * FRAME_MILLIS / 1_000) { "Quadro Opus inválido." }
        check(preSkip in 0..frameSize) { "Atraso Opus inválido." }
        check(frameCount in 1..MAX_FRAME_COUNT) { "Quantidade de quadros Opus inválida." }

        val decoder = OpusDecoder(sampleRate, CHANNELS)
        val decodedFrame = ShortArray(frameSize)
        val decoded = ShortArray(frameCount * frameSize)
        var decodedOffset = 0
        repeat(frameCount) {
            val packetSize = input.readUnsignedShort()
            check(packetSize in 1..MAX_PACKET_BYTES) { "Pacote Opus inválido." }
            val packet = ByteArray(packetSize)
            input.readFully(packet)
            val decodedSamples = decoder.decode(
                packet,
                0,
                packetSize,
                decodedFrame,
                0,
                frameSize,
                false,
            )
            check(decodedSamples in 1..frameSize) { "Falha ao decodificar o cache Opus." }
            decodedFrame.copyInto(
                destination = decoded,
                destinationOffset = decodedOffset,
                endIndex = decodedSamples,
            )
            decodedOffset += decodedSamples
        }
        check(preSkip + sampleCount <= decodedOffset) { "Cache Opus incompleto." }
        return NeuralAudio(
            samples = FloatArray(sampleCount) { index ->
                decoded[preSkip + index].toFloat() / 32_768f
            },
            sampleRate = sampleRate,
        )
    }

    private const val CHANNELS = 1
    private const val FRAME_MILLIS = 20
    private const val BITRATE = 32_000
    private const val COMPLEXITY = 3
    private const val MAX_PACKET_BYTES = 4_000
    private const val MAX_SAMPLE_COUNT = 96_000 * 60 * 10
    private const val MAX_FRAME_COUNT = 60 * 60 * 10 * 1_000 / FRAME_MILLIS
    private val SUPPORTED_SAMPLE_RATES = setOf(8_000, 12_000, 16_000, 24_000, 48_000)
}
