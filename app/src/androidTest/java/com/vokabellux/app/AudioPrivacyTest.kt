package com.vokabellux.app

import android.Manifest
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

@RunWith(AndroidJUnit4::class)
class AudioPrivacyTest {
    @get:Rule val microphone: GrantPermissionRule=GrantPermissionRule.grant(Manifest.permission.RECORD_AUDIO)
    private val context get()=ApplicationProvider.getApplicationContext<Context>()
    @Test fun recordingProducesBoundedMonoWavAndCanBeStopped()=runBlocking {
        val file=File(context.cacheDir,"test_recording.wav")
        try {
            val recorder=WavRecorder(context)
            val recording=launch {recorder.record(file)}
            delay(600)
            recorder.stop()
            withTimeout(5000) {recording.join()}
            val bytes=file.readBytes()
            assertTrue(bytes.size>44)
            assertTrue(bytes.size<=480044)
            assertEquals("RIFF",String(bytes.copyOfRange(0,4)))
            assertEquals("WAVE",String(bytes.copyOfRange(8,12)))
            assertEquals(16000,ByteBuffer.wrap(bytes,24,4).order(ByteOrder.LITTLE_ENDIAN).int)
            assertEquals(bytes.size-44,ByteBuffer.wrap(bytes,40,4).order(ByteOrder.LITTLE_ENDIAN).int)
        } finally {file.delete()}
    }
    @Test fun stoppingBeforeCaptureNeverStartsARecording()=runBlocking {
        val file=File(context.cacheDir,"test_stopped.wav")
        try {
            val recorder=WavRecorder(context)
            recorder.stop()
            withTimeout(5000) {recorder.record(file)}
            assertFalse(file.exists())
        } finally {file.delete()}
    }
    @Test fun speechSdkLoadsAndConfiguresWithoutSendingAudio() {
        val config=com.microsoft.cognitiveservices.speech.SpeechConfig.fromSubscription("test-only-not-a-real-key","westeurope")
        try {
            config.speechRecognitionLanguage="en-GB"
            val assessment=com.microsoft.cognitiveservices.speech.PronunciationAssessmentConfig("hello",
                com.microsoft.cognitiveservices.speech.PronunciationAssessmentGradingSystem.HundredMark,
                com.microsoft.cognitiveservices.speech.PronunciationAssessmentGranularity.Word,true)
            assessment.close()
            assertEquals("en-GB",config.speechRecognitionLanguage)
        } finally {config.close()}
    }
    @Test fun credentialsAreEncryptedAndCanBeRemoved() {
        val vault=CredentialVault(context)
        val old=vault.load()
        try {
            val fake=SpeechCredentials("test-only-not-a-real-subscription-key","westeurope")
            vault.save(fake)
            assertEquals(fake,vault.load())
            val stored=context.getSharedPreferences("speech_vault",Context.MODE_PRIVATE).getString("secret","")!!
            assertFalse(stored.contains(fake.key))
            vault.clear()
            assertFalse(vault.load().valid)
        } finally {if(old.valid) vault.save(old) else vault.clear()}
    }
}
