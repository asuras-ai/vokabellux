package com.vokabellux.app

import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.microsoft.cognitiveservices.speech.*
import com.microsoft.cognitiveservices.speech.audio.AudioConfig
import kotlinx.coroutines.*
import org.json.JSONObject
import java.io.File
import java.io.RandomAccessFile
import java.security.KeyStore
import java.util.concurrent.atomic.AtomicBoolean
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

data class SpeechCredentials(val key: String, val region: String) { val valid get() = key.isNotBlank() && region.matches(Regex("[a-z][a-z0-9-]{2,40}")) }
class CredentialVault(context: Context) {
    private val prefs=context.getSharedPreferences("speech_vault",Context.MODE_PRIVATE)
    private fun key(): SecretKey {
        val ks=KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        return (ks.getKey("vokabellux_speech",null) as? SecretKey) ?: KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore").run {
            init(KeyGenParameterSpec.Builder("vokabellux_speech",KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT).setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build()); generateKey()
        }
    }
    fun save(credentials: SpeechCredentials) {
        val cipher=Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE,key()) }
        val data=JSONObject().put("key",credentials.key.trim()).put("region",credentials.region.trim()).toString().toByteArray()
        prefs.edit().putString("secret",Base64.encodeToString(cipher.iv+cipher.doFinal(data),Base64.NO_WRAP)).apply()
    }
    fun load(): SpeechCredentials = runCatching {
        val bytes=Base64.decode(prefs.getString("secret","") ?: "",Base64.NO_WRAP)
        val cipher=Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.DECRYPT_MODE,key(),GCMParameterSpec(128,bytes.copyOfRange(0,12))) }
        val json=JSONObject(String(cipher.doFinal(bytes.copyOfRange(12,bytes.size))))
        SpeechCredentials(json.getString("key"),json.getString("region"))
    }.getOrDefault(SpeechCredentials("",""))
    fun clear() { prefs.edit().clear().apply() }
}

/** Records at most 15 seconds of 16-kHz mono PCM. No audio leaves the device here. */
class WavRecorder(private val context: Context) {
    private val active=AtomicBoolean(true)
    fun stop() { active.set(false) }
    @Suppress("MissingPermission")
    suspend fun record(file: File) = withContext(Dispatchers.IO) {
        val minimum=AudioRecord.getMinBufferSize(16000,AudioFormat.CHANNEL_IN_MONO,AudioFormat.ENCODING_PCM_16BIT)
        require(minimum>0) { "This microphone does not support recording." }
        val recorder=AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION,16000,AudioFormat.CHANNEL_IN_MONO,AudioFormat.ENCODING_PCM_16BIT,maxOf(minimum*2,4096))
        try {
            check(recorder.state==AudioRecord.STATE_INITIALIZED) { "The microphone is unavailable. Close other recording apps and try again." }
            if(!active.get()) return@withContext
            RandomAccessFile(file,"rw").use { out ->
                out.setLength(0); out.write(ByteArray(44))
                val buffer=ByteArray(4096); var bytes=0
                recorder.startRecording()
                while (active.get() && currentCoroutineContext().isActive && bytes < 16000*2*15) {
                    val count=recorder.read(buffer,0,minOf(buffer.size,16000*2*15-bytes))
                    check(count>=0) { "Recording was interrupted. Please try again." }
                    if(count>0) { out.write(buffer,0,count); bytes+=count }
                }
                recorder.stop()
                out.seek(0)
                fun leInt(value:Int) { out.writeInt(Integer.reverseBytes(value)) }
                fun leShort(value:Int) { out.writeShort(java.lang.Short.reverseBytes(value.toShort()).toInt()) }
                out.writeBytes("RIFF"); leInt(bytes+36); out.writeBytes("WAVEfmt "); leInt(16); leShort(1); leShort(1)
                leInt(16000); leInt(32000); leShort(2); leShort(16); out.writeBytes("data"); leInt(bytes)
            }
        } finally { active.set(false); recorder.release() }
    }
}
class AssessmentException(message: String): Exception(message)
interface PronunciationService {
    suspend fun assess(file: File, reference: String, locale: String, credentials: SpeechCredentials): SpeechFeedback
}
class AzurePronunciationService: PronunciationService {
    override suspend fun assess(file: File, reference: String, locale: String, credentials: SpeechCredentials): SpeechFeedback = withContext(Dispatchers.IO) {
        val config=SpeechConfig.fromSubscription(credentials.key,credentials.region)
        config.speechRecognitionLanguage=locale
        val audio=AudioConfig.fromWavFileInput(file.absolutePath)
        val assessment=PronunciationAssessmentConfig(reference,PronunciationAssessmentGradingSystem.HundredMark,PronunciationAssessmentGranularity.Word,true)
        val recognizer=SpeechRecognizer(config,audio)
        try {
            assessment.applyTo(recognizer)
            val task=recognizer.recognizeOnceAsync()
            val result=try { task.get(30,java.util.concurrent.TimeUnit.SECONDS) } catch (_: java.util.concurrent.TimeoutException) {
                task.cancel(true); throw AssessmentException("The connection took too long. Your recording is ready to retry.")
            }
            try {
                when(result.reason) {
                    ResultReason.RecognizedSpeech -> parseAssessment(result.properties.getProperty(PropertyId.SpeechServiceResponse_JsonResult),result.text)
                    ResultReason.NoMatch -> throw AssessmentException("I could not hear clear speech. Move closer to the microphone and record again.")
                    ResultReason.Canceled -> {
                        val details=CancellationDetails.fromResult(result)
                        throw AssessmentException(when(details.errorCode) {
                            CancellationErrorCode.AuthenticationFailure, CancellationErrorCode.Forbidden -> "Check your speech key and region in Settings."
                            CancellationErrorCode.TooManyRequests -> "The speech service is busy or its quota is reached. Try later."
                            else -> "Speech assessment is unavailable. Check your connection and try again."
                        })
                    }
                    else -> throw AssessmentException("Speech assessment is unavailable. Please try again.")
                }
            } finally { result.close() }
        } finally { recognizer.close(); assessment.close(); audio.close(); config.close() }
    }
}
fun parseAssessment(raw: String, recognized: String): SpeechFeedback {
    val best=JSONObject(raw).optJSONArray("NBest")?.optJSONObject(0) ?: throw AssessmentException("No assessment was returned. Please try again.")
    val scores=best.optJSONObject("PronunciationAssessment") ?: throw AssessmentException("No pronunciation score was returned.")
    if(!scores.has("AccuracyScore")) throw AssessmentException("No pronunciation score was returned.")
    val words=best.optJSONArray("Words")
    val feedback=(0 until (words?.length() ?: 0)).map { index ->
        val w=words!!.getJSONObject(index); val score=w.optJSONObject("PronunciationAssessment")
        WordFeedback(w.getString("Word"),score?.optDouble("AccuracyScore",0.0) ?: 0.0,score?.optString("ErrorType","") ?: "")
    }
    return SpeechFeedback(scores.getDouble("AccuracyScore"),if(scores.has("FluencyScore")) scores.getDouble("FluencyScore") else null,
        if(scores.has("CompletenessScore")) scores.getDouble("CompletenessScore") else null,feedback,recognized)
}
