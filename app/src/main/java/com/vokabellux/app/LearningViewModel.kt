package com.vokabellux.app

import android.app.Application
import android.media.MediaPlayer
import androidx.compose.runtime.*
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import androidx.room.withTransaction
import kotlinx.coroutines.*
import java.io.File

class AudioPlayer(private val app: Application) {
    private var player: MediaPlayer? = null
    fun play(path: String, slow: Boolean = false) {
        stop()
        runCatching {
            val asset=app.assets.openFd(path)
            player=MediaPlayer().apply {
                setDataSource(asset.fileDescriptor,asset.startOffset,asset.length); asset.close()
                prepare(); playbackParams=playbackParams.setSpeed(if(slow) .75f else 1f); setOnCompletionListener { stop() }; start()
            }
        }
    }
    fun playFile(file:File) {
        stop(); runCatching { player=MediaPlayer().apply { setDataSource(file.absolutePath); prepare(); setOnCompletionListener { stop() }; start() } }
    }
    fun stop() { player?.let { runCatching { it.stop() }; it.release() }; player=null }
}
data class SpeechUi(val recording: Boolean=false, val finishing: Boolean=false, val hasRecording: Boolean=false, val submitting: Boolean=false, val feedback: SpeechFeedback?=null, val error:String?=null)
class LearningViewModel(app: Application): AndroidViewModel(app) {
    val course=loadCourse(app)
    private val database=Room.databaseBuilder(app,LearningDatabase::class.java,"learning.db").build()
    private val dao=database.dao()
    private val settings=SettingsStore(app)
    private val vault=CredentialVault(app)
    private val service: PronunciationService=AzurePronunciationService()
    val audio=AudioPlayer(app)
    var progress by mutableStateOf<Map<String,Progress>>(emptyMap()); private set
    var daily by mutableStateOf<List<Daily>>(emptyList()); private set
    var preferences by mutableStateOf(Preferences()); private set
    var session by mutableStateOf<Session?>(null); private set
    var speech by mutableStateOf(SpeechUi()); private set
    var speechConfigured by mutableStateOf(vault.load().valid); private set
    var speechRegion by mutableStateOf(vault.load().region); private set
    var notice by mutableStateOf<String?>(null); private set
    var saving by mutableStateOf(false); private set
    var ready by mutableStateOf(false); private set
    private var recorder: WavRecorder?=null
    private var recordingJob: Job?=null
    private var speechJob: Job?=null
    private var recordingFile: File?=null
    private var generation=0
    private var restored=false
    init {
        app.cacheDir.listFiles()?.filter { it.name.startsWith("practice_") }?.forEach { it.delete() }
        viewModelScope.launch { dao.observeProgress().collect { progress=it.associateBy { p->p.id } } }
        viewModelScope.launch { dao.observeDaily().collect { daily=it } }
        viewModelScope.launch { settings.flow.collect { p ->
            preferences=p
            if(!restored) {
                restored=true
                session=SessionJson.decode(p.session)?.takeIf { s -> s.steps.all { step -> course.items.any { it.id==step.itemId } } }
                if(session?.done==true) finishSession(session!!)
                ready=true
            }
        } }
    }
    val learned get()=progress.values.count { it.reviews>0 }
    val due get()=course.items.filter { progress[it.id]?.let { p -> p.reviews>0 && p.dueAt<=System.currentTimeMillis() }==true }
    val todayAnswers get()=daily.firstOrNull { it.day==LearningRules.dayKey() }?.answers ?: 0
    fun item(id:String)=course.items.first { it.id==id }
    fun startToday() {
        val new=course.items.filter { (progress[it.id]?.reviews ?: 0)==0 }.take(5)
        start("Your daily mix",(new+due.take(2)).distinctBy { it.id }.ifEmpty { course.items.shuffled().take(5) },false)
    }
    fun start(title:String,items:List<Vocabulary>,review:Boolean) {
        if(items.isEmpty() || saving) return
        clearSpeech(); audio.stop(); session=SessionFactory.create(title,items,review)
        persistSession()
    }
    fun abandon() { clearSpeech(); audio.stop(); session=null; persistSession() }
    private fun persistSession() { val snapshot=session; viewModelScope.launch { settings.saveSession(snapshot) } }
    fun advance(success:Boolean?=null) {
        if(saving) return
        val current=session ?: return
        val spoken=current.current?.kind in listOf(Exercise.SPEAK,Exercise.DIALOGUE)
        val next=current.advance(success,spoken)
        clearSpeech(); audio.stop(); session=next
        viewModelScope.launch {
            settings.saveSession(next)
            if(next.done) finishSession(next)
        }
    }
    private suspend fun finishSession(s:Session) {
        saving=true
        try {
            database.withTransaction {
                if(dao.markComplete(CompletedSession(s.token))!=-1L) {
                    val now=System.currentTimeMillis()
                    for(id in s.steps.map { it.itemId }.distinct()) {
                        val old=dao.get(id) ?: Progress(id)
                        val review=LearningRules.schedule(old.stage,id !in s.failed,now)
                        dao.put(old.copy(stage=review.stage,dueAt=review.dueAt,reviews=old.reviews+1,lastSeen=now))
                    }
                    val old=dao.daily(LearningRules.dayKey()) ?: Daily(LearningRules.dayKey())
                    dao.putDaily(old.copy(answers=old.answers+s.answered,sessions=old.sessions+1))
                }
            }
        } catch (_:Exception) { notice="Progress could not be saved. Please reopen the app to retry." }
        finally { saving=false }
    }
    fun saveSettings(goal:Int,cap:Int,consent:Boolean,key:String,region:String) {
        val old=vault.load()
        val credentials=SpeechCredentials(key.trim().ifBlank { old.key },region.trim())
        if(credentials.key.isNotBlank() && !credentials.valid) { notice="Enter a valid speech region, such as westeurope."; return }
        runCatching { if(credentials.key.isNotBlank()) vault.save(credentials) }.onFailure { notice="The speech credentials could not be saved."; return }
        speechConfigured=credentials.valid; speechRegion=credentials.region
        viewModelScope.launch { settings.settings(goal.coerceIn(5,100),cap.coerceIn(1,500),consent); notice="Settings saved." }
    }
    fun removeCredentials() { clearSpeech(); vault.clear(); speechConfigured=false; speechRegion=""; notice="Speech credentials removed." }
    fun clearNotice() { notice=null }
    fun permissionDenied() { speech=SpeechUi(error="Microphone access is needed to record. You can allow it in Android Settings, or skip speaking.") }
    fun startRecording() {
        if(speech.submitting || speech.recording || speech.finishing) return
        clearSpeech(); audio.stop()
        val token=generation
        val file=File(getApplication<Application>().cacheDir,"practice_${System.nanoTime()}.wav")
        recordingFile=file
        val capture=WavRecorder(getApplication()); recorder=capture
        speech=SpeechUi(recording=true)
        recordingJob=viewModelScope.launch {
            try {
                capture.record(file)
                if(token==generation) speech=if(file.length()>3244) SpeechUi(hasRecording=true) else SpeechUi(error="The recording was too short. Please try again.")
            } catch (_:Exception) {
                if(token==generation) speech=SpeechUi(error="The microphone is unavailable. Check permission and try again.")
            } finally { if(token!=generation) file.delete() }
        }
    }
    fun stopRecording() { if(speech.recording) { speech=speech.copy(recording=false,finishing=true); recorder?.stop() } }
    fun playRecording() { recordingFile?.takeIf { speech.hasRecording }?.let { audio.playFile(it) } }
    fun submitRecording() {
        val current=session?.current ?: return
        val word=item(current.itemId)
        val file=recordingFile ?: return
        if(!speech.hasRecording || speech.submitting || speech.feedback!=null) return
        if(!preferences.speechConsent || !speechConfigured) { speech=speech.copy(error="Set up online speech in Settings first."); return }
        val token=generation
        speech=speech.copy(submitting=true,error=null)
        speechJob=viewModelScope.launch {
            if(!settings.reserveAssessment()) { if(token==generation) speech=speech.copy(submitting=false,error="Your daily speech limit is reached. You can change it in Settings or continue tomorrow."); return@launch }
            try {
                val feedback=service.assess(file,if(current.kind==Exercise.DIALOGUE) word.example else word.spoken,word.locale,vault.load())
                if(token==generation) {
                    speech=speech.copy(submitting=false,feedback=feedback)
                    val old=dao.get(word.id) ?: Progress(word.id)
                    dao.put(old.copy(speechScore=feedback.accuracy,speechAttempts=old.speechAttempts+1,speechMastered=feedback.passed))
                }
            } catch (e:Exception) {
                if(token==generation) speech=speech.copy(submitting=false,error=if(e is AssessmentException) e.message else "Speech assessment is unavailable. Check your connection and try again.")
            }
        }
    }
    fun clearSpeech() {
        generation++; recorder?.stop(); recorder=null
        val job=recordingJob; val file=recordingFile
        if(job?.isActive==true) viewModelScope.launch { job.join(); file?.delete() } else file?.delete()
        recordingFile=null; recordingJob=null
        speechJob?.cancel(); speechJob=null
        speech=SpeechUi()
    }
    fun background() { audio.stop(); clearSpeech() }
    override fun onCleared() { recorder?.stop(); audio.stop(); recordingFile?.delete(); database.close() }
}
