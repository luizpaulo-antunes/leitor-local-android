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
        engine = TextToSpeech(appContext) { status ->
            val tts = engine
            if (status != TextToSpeech.SUCCESS || tts == null) {
                onResult(emptyList())
                return@TextToSpeech
            }

            val portuguese = tts.voices.orEmpty()
                .filter { voice -> voice.locale.language == Locale.forLanguageTag("pt").language }
                .sortedWith(
                    compareBy(
                        { it.locale.country != "BR" },
                        { it.isNetworkConnectionRequired },
                        { -it.quality },
                        { it.name },
                    ),
                )
                .map { voice ->
                    val highQuality = voice.quality >= Voice.QUALITY_HIGH
                    VoiceOption(
                        name = voice.name,
                        label = buildString {
                            append(voice.locale.getDisplayName(Locale.forLanguageTag("pt-BR")))
                            if (highQuality) append(" · alta qualidade")
                            if (voice.isNetworkConnectionRequired) append(" · usa internet")
                            else append(" · offline")
                        },
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
}
