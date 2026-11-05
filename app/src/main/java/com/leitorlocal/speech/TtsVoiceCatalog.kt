package com.leitorlocal.speech

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.Voice
import com.leitorlocal.model.VoiceOption
import java.util.Locale

class TtsVoiceCatalog(context: Context) {
    private val appContext = context.applicationContext
    private var engine: TextToSpeech? = null

    fun load(onResult: (List<VoiceOption>) -> Unit) {
        engine?.shutdown()
        engine = null
        engine = TextToSpeech(appContext) { status ->
            val tts = engine
            if (status != TextToSpeech.SUCCESS || tts == null) {
                onResult(emptyList())
                return@TextToSpeech
            }

            val portuguese = tts.voices.orEmpty()
                .filter { voice ->
                    voice.locale.language == "pt" && voice.locale.country.equals("BR", ignoreCase = true)
                }
                .sortedWith(
                    compareBy(
                        { it.locale.country != "BR" },
                        { it.isNetworkConnectionRequired },
                        { -it.quality },
                        { it.name },
                    ),
                )
                .mapIndexed { index, voice ->
                    val highQuality = voice.quality >= Voice.QUALITY_HIGH
                    VoiceOption(
                        name = voice.name,
                        label = personaLabel(index, voice),
                        localeTag = voice.locale.toLanguageTag(),
                        requiresNetwork = voice.isNetworkConnectionRequired,
                        quality = voice.quality,
                        isHighQuality = highQuality,
                    )
                }
            onResult(portuguese)
        }
    }

    fun close() {
        engine?.shutdown()
        engine = null
    }

    private fun personaLabel(index: Int, voice: Voice): String {
        val personas = listOf(
            "Aurora — clara e leve",
            "Bento — firme e direto",
            "Cecília — calma e acolhedora",
            "Dante — pausado e sóbrio",
            "Elisa — suave e fluida",
            "Gael — ágil e objetivo",
            "Helena — serena e elegante",
            "Theo — equilibrado e presente",
        )
        val base = personas[index % personas.size]
        val suffix = if (index >= personas.size) " ${index / personas.size + 1}" else ""
        val mode = if (voice.isNetworkConnectionRequired) "online" else "offline"
        return "$base$suffix · $mode"
    }
}
