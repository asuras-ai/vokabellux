package com.vokabellux.app

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.*
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.random.Random

private val Ink=Color(0xFF173F3A)
private val Cream=Color(0xFFFAF8F3)
private val Lime=Color(0xFFD3EFAC)
private val Peach=Color(0xFFF6C6AE)
private val Muted=Color(0xFF657570)
private val Outline=Color(0xFFE2E5DA)
private val Palette=lightColorScheme(primary=Ink,onPrimary=Color.White,primaryContainer=Lime,onPrimaryContainer=Ink,secondary=Color(0xFF9B4F35),secondaryContainer=Peach,background=Cream,surface=Cream,surfaceVariant=Color(0xFFEEEFE7),outline=Muted,error=Color(0xFFAD433C))
class MainActivity: ComponentActivity() {
    private val vm:LearningViewModel by viewModels()
    override fun onCreate(savedInstanceState:Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme(colorScheme=Palette,typography=Typography()) { Vokabellux(vm) } }
    }
    override fun onStop() { vm.background(); super.onStop() }
}
@Composable
fun Vokabellux(vm:LearningViewModel) {
    var tab by rememberSaveable { mutableStateOf("Today") }
    var inSession by rememberSaveable { mutableStateOf(false) }
    var detail by remember { mutableStateOf<Vocabulary?>(null) }
    var replaceAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    fun launch(action:()->Unit) {
        if(vm.session!=null && vm.session?.done==false) replaceAction=action else { action(); inSession=true }
    }
    val notice=vm.notice
    val snackbar=remember { SnackbarHostState() }
    LaunchedEffect(notice) { notice?.let { snackbar.showSnackbar(it); vm.clearNotice() } }
    if(replaceAction!=null) AlertDialog(onDismissRequest={replaceAction=null},title={Text("Start a new session?")},text={Text("Your unfinished session will be replaced. Completed progress is already saved.")},
        confirmButton={TextButton(onClick={replaceAction?.invoke(); replaceAction=null; inSession=true}) { Text("Start new") }},dismissButton={TextButton(onClick={replaceAction=null}) {Text("Keep current")}})
    detail?.let { item -> VocabularyDialog(item,vm,onDismiss={detail=null}) }
    Scaffold(containerColor=Cream,snackbarHost={SnackbarHost(snackbar)},bottomBar={
        if(!inSession) NavigationBar(containerColor=Cream,tonalElevation=0.dp) {
            listOf("Today" to Icons.Rounded.WbSunny,"Lessons" to Icons.AutoMirrored.Rounded.MenuBook,"Review" to Icons.Rounded.Replay,"Words" to Icons.AutoMirrored.Rounded.LibraryBooks,"Settings" to Icons.Rounded.Tune).forEach { (name,icon)->
                NavigationBarItem(selected=tab==name,onClick={tab=name;vm.audio.stop()},icon={Icon(icon,name)},label={Text(name)},colors=NavigationBarItemDefaults.colors(indicatorColor=Lime))
            }
        }
    }) { padding ->
        if(!vm.ready) Box(Modifier.fillMaxSize().padding(padding),contentAlignment=Alignment.Center) {CircularProgressIndicator()}
        else if(inSession && vm.session!=null) SessionScreen(vm,Modifier.padding(padding),onExit={inSession=false;vm.clearSpeech();vm.audio.stop()},onSettings={inSession=false;tab="Settings"})
        else when(tab) {
            "Today" -> TodayScreen(vm,Modifier.padding(padding),onStart={launch {vm.startToday()}},onResume={inSession=true},onReview={launch {vm.start("Time to remember",vm.due.take(8),true)}},onLessons={tab="Lessons"})
            "Lessons" -> LessonsScreen(vm,Modifier.padding(padding)) { title,words -> launch { vm.start(title,words,false) } }
            "Review" -> ReviewScreen(vm,Modifier.padding(padding)) { words -> launch {vm.start("Time to remember",words,true)} }
            "Words" -> WordsScreen(vm,Modifier.padding(padding)) {detail=it}
            else -> SettingsScreen(vm,Modifier.padding(padding))
        }
    }
}
@Composable
private fun PageHeading(eyebrow:String,title:String,subtitle:String?=null) {
    Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
        Text(eyebrow.uppercase(),color=Muted,style=MaterialTheme.typography.labelMedium,letterSpacing=2.sp)
        Text(title,color=Ink,style=MaterialTheme.typography.headlineLarge,fontWeight=FontWeight.Bold)
        if(subtitle!=null) Text(subtitle,color=Muted,style=MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun Pill(text:String,color:Color=Lime) {Surface(shape=RoundedCornerShape(50),color=color) {Text(text,Modifier.padding(horizontal=12.dp,vertical=6.dp),style=MaterialTheme.typography.labelMedium,color=Ink)}}
@Composable
private fun Primary(text:String,onClick:()->Unit,modifier:Modifier=Modifier,enabled:Boolean=true,icon:ImageVector=Icons.AutoMirrored.Rounded.ArrowForward) {
    Button(onClick,modifier.heightIn(min=52.dp),enabled=enabled,shape=RoundedCornerShape(16.dp)) {Text(text,fontWeight=FontWeight.SemiBold);Spacer(Modifier.width(10.dp));Icon(icon,null,Modifier.size(19.dp))}
}
@Composable
fun TodayScreen(vm:LearningViewModel,modifier:Modifier,onStart:()->Unit,onResume:()->Unit,onReview:()->Unit,onLessons:()->Unit) {
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),verticalArrangement=Arrangement.spacedBy(22.dp)) {
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically) {
            Text("vokabellux",fontWeight=FontWeight.ExtraBold,fontSize=24.sp,color=Ink)
            Pill("ENGLISH · YOUR WORLD",Color(0xFFEEEFE7))
        }
        PageHeading("A little every day","Make yourself\nat home in English.","See it. Hear it. Say it. Make it yours.")
        Card(shape=RoundedCornerShape(28.dp),colors=CardDefaults.cardColors(containerColor=Ink)) {
            Column(Modifier.padding(22.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {Pill("YOUR DAILY MIX");Text("~10 min",color=Color.White,style=MaterialTheme.typography.labelLarge)}
                SceneImage(vm.course.items.first {it.id=="happy"},Modifier.fillMaxWidth().height(180.dp).clip(RoundedCornerShape(20.dp)),crop=true)
                Text("Small moments.\nReal understanding.",color=Color.White,fontSize=27.sp,fontWeight=FontWeight.Bold,lineHeight=32.sp)
                Button(onClick=onStart,Modifier.fillMaxWidth().height(54.dp),colors=ButtonDefaults.buttonColors(containerColor=Lime,contentColor=Ink),shape=RoundedCornerShape(16.dp)) {Text("Start today's practice",fontWeight=FontWeight.Bold);Spacer(Modifier.weight(1f));Icon(Icons.AutoMirrored.Rounded.ArrowForward,null)}
            }
        }
        if(vm.session!=null && vm.session?.done==false) OutlinedButton(onClick=onResume,Modifier.fillMaxWidth(),shape=RoundedCornerShape(16.dp)) {Icon(Icons.Rounded.PlayArrow,null);Text("Resume ${vm.session!!.title}")}
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(12.dp)) {
            Stat("${vm.learned}","words explored",Modifier.weight(1f))
            Stat("${vm.due.size}","ready to review",Modifier.weight(1f),Peach)
        }
        Column(verticalArrangement=Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {Text("Today's little goal",fontWeight=FontWeight.Bold,color=Ink);Text("${vm.todayAnswers} / ${vm.preferences.goal}",color=Muted)}
            LinearProgressIndicator(progress={ (vm.todayAnswers.toFloat()/vm.preferences.goal).coerceIn(0f,1f) },Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(8.dp)),color=Ink,trackColor=Outline)
            Text("Every answer helps your English grow.",style=MaterialTheme.typography.bodySmall,color=Muted)
        }
        if(vm.due.isNotEmpty()) Primary("Review familiar words",onReview,Modifier.fillMaxWidth(),icon=Icons.Rounded.Replay)
        TextButton(onClick=onLessons) {Text("Explore all six themes");Icon(Icons.AutoMirrored.Rounded.ArrowForward,null)}
    }
}
@Composable
private fun Stat(value:String,label:String,modifier:Modifier,color:Color=Lime) {
    Surface(modifier,shape=RoundedCornerShape(20.dp),color=color) {Column(Modifier.padding(18.dp)) {Text(value,fontSize=30.sp,fontWeight=FontWeight.Bold,color=Ink);Text(label,color=Ink,style=MaterialTheme.typography.bodySmall)}}
}
@Composable
fun LessonsScreen(vm:LearningViewModel,modifier:Modifier,onStart:(String,List<Vocabulary>)->Unit) {
    var expanded by rememberSaveable { mutableStateOf<String?>(null) }
    LazyColumn(modifier.fillMaxSize(),contentPadding=PaddingValues(24.dp),verticalArrangement=Arrangement.spacedBy(18.dp)) {
        item {PageHeading("Your learning path","A world of words","Six themes. Everyday moments. English that stays with you.")}
        items(vm.course.themes) { theme ->
            val words=vm.course.items.filter {it.theme==theme.id};val count=words.count {(vm.progress[it.id]?.reviews ?:0)>0}
            Card(onClick={expanded=if(expanded==theme.id)null else theme.id},shape=RoundedCornerShape(24.dp),colors=CardDefaults.cardColors(containerColor=Color.White)) {
                Column(Modifier.padding(18.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                    SceneImage(words.first(),Modifier.fillMaxWidth().height(160.dp).clip(RoundedCornerShape(16.dp)))
                    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {Pill(theme.title);Text("$count / ${words.size}",color=Muted)}
                    Text(theme.headline,fontSize=24.sp,fontWeight=FontWeight.Bold,color=Ink)
                    Text(theme.description,color=Muted)
                    LinearProgressIndicator(progress={count.toFloat()/words.size},Modifier.fillMaxWidth(),color=Ink,trackColor=Outline)
                    if(expanded==theme.id) {
                        words.chunked(5).forEachIndexed { index, chunk ->
                            val completed=chunk.all {(vm.progress[it.id]?.reviews ?:0)>0}
                            OutlinedButton(onClick={onStart("${theme.title} · ${index+1}",chunk)},Modifier.fillMaxWidth(),shape=RoundedCornerShape(12.dp)) {
                                Icon(if(completed) Icons.Rounded.CheckCircle else Icons.Rounded.PlayArrow,null,Modifier.size(18.dp));Spacer(Modifier.width(8.dp));Text("Lesson ${index+1}");Spacer(Modifier.weight(1f));Text("${chunk.size} words")
                            }
                        }
                    } else Text("Tap to choose a lesson",color=Ink,style=MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}
@Composable
fun ReviewScreen(vm:LearningViewModel,modifier:Modifier,onStart:(List<Vocabulary>)->Unit) {
    val learned=vm.course.items.filter {(vm.progress[it.id]?.reviews ?:0)>0}
    LazyColumn(modifier.fillMaxSize(),contentPadding=PaddingValues(24.dp),verticalArrangement=Arrangement.spacedBy(18.dp)) {
        item {PageHeading("Make it stick","Time to remember",if(vm.due.isEmpty()) "You're all caught up. Familiar words will return when it is time to practise." else "${vm.due.size} words are ready for another moment with you.")}
        item {Row(horizontalArrangement=Arrangement.spacedBy(12.dp)) {Stat("${learned.size}","words explored",Modifier.weight(1f));Stat("${vm.progress.values.count {it.speechMastered}}","speaking successes",Modifier.weight(1f),Peach)}}
        if(vm.due.isNotEmpty()) item {Primary("Start review",{onStart(vm.due.take(8))},Modifier.fillMaxWidth(),icon=Icons.Rounded.Replay)}
        else if(learned.isNotEmpty()) item {OutlinedButton(onClick={onStart(learned.shuffled().take(5))},Modifier.fillMaxWidth()) {Text("Practise familiar words")}}
        if(learned.isEmpty()) item {Text("Complete your first lesson to begin your review collection.",color=Muted)}
        items(learned.sortedBy {vm.progress[it.id]?.dueAt}) {item->
            val p=vm.progress[item.id]!!
            Row(Modifier.fillMaxWidth().padding(vertical=6.dp),verticalAlignment=Alignment.CenterVertically) {
                SceneImage(item,Modifier.size(64.dp).clip(RoundedCornerShape(14.dp)))
                Column(Modifier.weight(1f).padding(start=14.dp)) {Text(item.term,fontWeight=FontWeight.Bold,color=Ink);Text(if(p.dueAt<=System.currentTimeMillis()) "Ready now" else "Next: ${java.text.SimpleDateFormat("d MMM",java.util.Locale.UK).format(java.util.Date(p.dueAt))}",color=Muted,style=MaterialTheme.typography.bodySmall)}
                if(p.speechScore!=null) Icon(if(p.speechMastered) Icons.Rounded.Mic else Icons.Rounded.Hearing,"Speaking practised",tint=Ink)
            }
            HorizontalDivider(color=Outline)
        }
    }
}
@Composable
fun WordsScreen(vm:LearningViewModel,modifier:Modifier,onDetail:(Vocabulary)->Unit) {
    var query by rememberSaveable {mutableStateOf("")};var theme by rememberSaveable {mutableStateOf<String?>(null)}
    val words=vm.course.items.filter {(theme==null || it.theme==theme) && (it.term.contains(query,true) || it.example.contains(query,true))}
    Column(modifier.fillMaxSize().padding(horizontal=24.dp)) {
        Spacer(Modifier.height(24.dp));PageHeading("Your collection","Words, in context","${vm.course.items.size} words and expressions from your vocabulary pages.")
        Spacer(Modifier.height(16.dp))
        OutlinedTextField(query,{query=it},Modifier.fillMaxWidth(),label={Text("Find an English word")},leadingIcon={Icon(Icons.Rounded.Search,null)},singleLine=true,shape=RoundedCornerShape(16.dp))
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(vertical=12.dp),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            FilterChip(selected=theme==null,onClick={theme=null},label={Text("All")})
            vm.course.themes.forEach {t->FilterChip(selected=theme==t.id,onClick={theme=t.id},label={Text(t.title)})}
        }
        LazyColumn(verticalArrangement=Arrangement.spacedBy(10.dp),contentPadding=PaddingValues(bottom=24.dp)) {
            items(words,key={it.id}) {item ->
                Surface(onClick={onDetail(item)},shape=RoundedCornerShape(18.dp),color=Color.White) {
                    Row(Modifier.padding(12.dp),verticalAlignment=Alignment.CenterVertically) {
                        SceneImage(item,Modifier.size(76.dp).clip(RoundedCornerShape(14.dp)))
                        Column(Modifier.weight(1f).padding(horizontal=12.dp)) {Text(item.term,fontWeight=FontWeight.Bold,color=Ink);Text(item.example,maxLines=2,style=MaterialTheme.typography.bodySmall,color=Muted)}
                        IconButton(onClick={vm.audio.play(item.audio)}) {Icon(Icons.AutoMirrored.Rounded.VolumeUp,"Listen to ${item.spoken}",tint=Ink)}
                    }
                }
            }
            if(words.isEmpty()) item {Text("No words found. Try another English word.",color=Muted)}
        }
    }
}
@Composable
private fun VocabularyDialog(item:Vocabulary,vm:LearningViewModel,onDismiss:()->Unit) {
    AlertDialog(onDismissRequest={vm.audio.stop();onDismiss()},title={Text(item.term)},text={Column(verticalArrangement=Arrangement.spacedBy(14.dp)) {
        SceneImage(item,Modifier.fillMaxWidth().height(220.dp).clip(RoundedCornerShape(18.dp)))
        Text(item.example,color=Ink)
        AudioButtons(item,vm,true)
        if(item.forms.isNotEmpty()) Text("Also: ${item.forms.joinToString()}",color=Muted)
        Text("Vocabulary page ${217+item.page} · ${if(item.locale=="en-US") "American" else "British"} English",style=MaterialTheme.typography.labelSmall,color=Muted)
    }},confirmButton={TextButton(onClick=onDismiss){Text("Done")}})
}

private object IllustrationCache {
    val cache=android.util.LruCache<String,ImageBitmap>(4)
}
@Composable
fun SceneImage(item:Vocabulary,modifier:Modifier=Modifier,crop:Boolean=false) {
    val context=LocalContext.current
    val bitmap by produceState<ImageBitmap?>(initialValue=IllustrationCache.cache.get(item.image),item.image) {
        if(value==null) value=withContext(Dispatchers.IO) {
            runCatching {context.assets.open(item.image).use {BitmapFactory.decodeStream(it)?.asImageBitmap()} }.getOrNull()?.also {IllustrationCache.cache.put(item.image,it)}
        }
    }
    val image=bitmap
    if(image!=null) {
        Canvas(modifier.clipToBounds().background(Color(0xFFF1EDE3)).semantics {contentDescription=item.example}) {
            val cellW=image.width/4;val cellH=image.height/4
            val bounds=item.bounds.takeIf { it.size==4 } ?: listOf((item.panel%4)*cellW+2,(item.panel/4)*cellH+2,cellW-4,cellH-4)
            val scale=if(crop) maxOf(size.width/bounds[2],size.height/bounds[3]) else minOf(size.width/bounds[2],size.height/bounds[3])
            val target=IntSize((bounds[2]*scale).toInt(),(bounds[3]*scale).toInt())
            drawImage(image,srcOffset=IntOffset(bounds[0],bounds[1]),srcSize=IntSize(bounds[2],bounds[3]),dstOffset=IntOffset((size.width.toInt()-target.width)/2,(size.height.toInt()-target.height)/2),dstSize=target)
        }
    } else Box(modifier.background(Color(0xFFE8ECDD)),contentAlignment=Alignment.Center) {
        Column(horizontalAlignment=Alignment.CenterHorizontally,modifier=Modifier.padding(14.dp)) {Icon(Icons.Rounded.Image,null,tint=Ink,modifier=Modifier.size(36.dp));Text(item.example,color=Ink,textAlign=TextAlign.Center,style=MaterialTheme.typography.bodySmall)}
    }
}
@Composable
private fun AudioButtons(item:Vocabulary,vm:LearningViewModel,example:Boolean=false) {
    Row(horizontalArrangement=Arrangement.spacedBy(10.dp)) {
        OutlinedButton(onClick={vm.audio.play(if(example)item.exampleAudio else item.audio)},shape=RoundedCornerShape(14.dp)) {Icon(Icons.AutoMirrored.Rounded.VolumeUp,null,Modifier.size(19.dp));Spacer(Modifier.width(6.dp));Text("Listen")}
        TextButton(onClick={vm.audio.play(if(example)item.exampleAudio else item.audio,true)}) {Icon(Icons.Rounded.Speed,null,Modifier.size(19.dp));Spacer(Modifier.width(6.dp));Text("Slower")}
    }
}

@Composable
fun SessionScreen(vm:LearningViewModel,modifier:Modifier,onExit:()->Unit,onSettings:()->Unit) {
    val session=vm.session ?: return
    androidx.activity.compose.BackHandler {vm.clearSpeech();onExit()}
    val step=session.current
    if(step==null) {
        Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(28.dp),verticalArrangement=Arrangement.spacedBy(24.dp),horizontalAlignment=Alignment.CenterHorizontally) {
            Spacer(Modifier.height(30.dp));Surface(shape=RoundedCornerShape(40.dp),color=Lime) {Icon(Icons.Rounded.Check, null,Modifier.padding(24.dp).size(48.dp),tint=Ink)}
            Text("A little more English.\nA little more you.",fontSize=32.sp,lineHeight=38.sp,fontWeight=FontWeight.Bold,color=Ink,textAlign=TextAlign.Center)
            Text("You explored ${session.steps.map {it.itemId}.distinct().size} words in real situations.",color=Muted,textAlign=TextAlign.Center)
            Row(horizontalArrangement=Arrangement.spacedBy(12.dp)) {Stat("${session.correct}/${session.answered}","answers correct",Modifier.weight(1f));Stat("${session.speechPassed.size}","words spoken well",Modifier.weight(1f),Peach)}
            Text(if(session.failed.isEmpty()) "Your words will come back for review over the next few days." else "The tricky words will return tomorrow. You can also practise them again now.",color=Muted,textAlign=TextAlign.Center)
            if(vm.saving) CircularProgressIndicator()
            Primary("Back to my day",onExit,Modifier.fillMaxWidth(),enabled=!vm.saving)
            if(session.failed.isNotEmpty()) OutlinedButton(onClick={vm.start("Another little try",session.failed.map {vm.item(it)},true)},enabled=!vm.saving) {Text("Practise tricky words")}
        }
        return
    }
    val item=vm.item(step.itemId)
    val isSpeech=step.kind in listOf(Exercise.SPEAK,Exercise.DIALOGUE)
    var checked by rememberSaveable(session.token,session.index) {mutableStateOf<Boolean?>(null)}
    var typed by rememberSaveable(session.token,session.index) {mutableStateOf("")}
    var chosen by rememberSaveable(session.token,session.index) {mutableStateOf<String?>(null)}
    var ordered by rememberSaveable(session.token,session.index) {mutableStateOf(listOf<Int>())}
    val options=remember(session.token,session.index) { exerciseOptions(vm.course.items,item,session.seed+session.index) }
    val sentenceTokens=remember(item.id) {item.example.split(" ")}
    val tokenOrder=remember(session.token,session.index) {sentenceTokens.indices.shuffled(Random(session.seed+session.index))}
    LaunchedEffect(session.token,session.index) {if(step.kind==Exercise.LISTEN || step.kind==Exercise.SPELL) vm.audio.play(item.audio)}
    Column(modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal=14.dp,vertical=6.dp),verticalAlignment=Alignment.CenterVertically) {
            IconButton(onClick=onExit) {Icon(Icons.Rounded.Close,"Save and leave",tint=Ink)}
            Column(Modifier.weight(1f)) {Text(session.title,color=Ink,style=MaterialTheme.typography.labelLarge);Text("${session.index+1} of ${session.steps.size}",color=Muted,style=MaterialTheme.typography.labelSmall)}
            if(step.retry) Pill("ANOTHER TRY",Peach)
        }
        LinearProgressIndicator(progress={session.index.toFloat()/session.steps.size},Modifier.fillMaxWidth().height(4.dp),color=Ink,trackColor=Outline)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(24.dp),verticalArrangement=Arrangement.spacedBy(18.dp)) {
            Text(when(step.kind) {
                Exercise.DISCOVER -> "MEET THE WORD"
                Exercise.LISTEN -> "LISTEN & NOTICE"
                Exercise.PICTURE -> "CONNECT THE MEANING"
                Exercise.CONTEXT -> "IN THE MOMENT"
                Exercise.ORDER -> "BUILD A SENTENCE"
                Exercise.SPELL -> "LISTEN & WRITE"
                Exercise.SPEAK -> "YOUR TURN TO SPEAK"
                Exercise.DIALOGUE -> "TELL THE STORY"
            },color=Muted,style=MaterialTheme.typography.labelMedium,letterSpacing=2.sp)
            Text(when(step.kind) {
                Exercise.DISCOVER -> item.term
                Exercise.LISTEN -> "Which picture matches?"
                Exercise.PICTURE -> "Find the right words."
                Exercise.CONTEXT -> "What fits this moment?"
                Exercise.ORDER -> "Put the words in order."
                Exercise.SPELL -> "Write what you hear."
                Exercise.SPEAK -> "Listen. Then make it yours."
                Exercise.DIALOGUE -> "What is happening here?"
            },color=Ink,fontSize=28.sp,fontWeight=FontWeight.Bold,lineHeight=34.sp)
            when(step.kind) {
                Exercise.DISCOVER -> {
                    SceneImage(item,Modifier.fillMaxWidth().height(260.dp).clip(RoundedCornerShape(24.dp)))
                    AudioButtons(item,vm)
                    Text(item.example,color=Ink,fontSize=20.sp,lineHeight=28.sp)
                    AudioButtons(item,vm,true)
                    if(item.forms.isNotEmpty()) Text("Also: ${item.forms.joinToString(" · ")}",color=Muted)
                    Text(if(item.locale=="en-US") "American English" else "British English",color=Muted,style=MaterialTheme.typography.labelSmall)
                }
                Exercise.LISTEN -> {
                    AudioButtons(item,vm)
                    options.chunked(2).forEach {row->
                        Row(horizontalArrangement=Arrangement.spacedBy(12.dp)) {row.forEach {candidate->
                            Surface(onClick={if(checked==null) {chosen=candidate.id;checked=candidate.id==item.id}},Modifier.weight(1f).testTag("picture_${candidate.id}"),shape=RoundedCornerShape(18.dp),border=BorderStroke(if(chosen==candidate.id) 3.dp else 1.dp,if(chosen==candidate.id) Ink else Outline)) {
                                SceneImage(candidate,Modifier.fillMaxWidth().aspectRatio(1f))
                            }
                        }}
                    }
                }
                Exercise.PICTURE,Exercise.CONTEXT -> {
                    SceneImage(item,Modifier.fillMaxWidth().height(220.dp).clip(RoundedCornerShape(24.dp)))
                    if(step.kind==Exercise.CONTEXT) {
                        val gap=LearningRules.gap(item)
                        if(gap!=null) Text(gap,fontSize=21.sp,lineHeight=29.sp,color=Ink)
                        else Text("Choose the expression for this scene.",color=Muted)
                    }
                    options.forEach {candidate->
                        OutlinedButton(onClick={chosen=candidate.id;checked=candidate.id==item.id},Modifier.fillMaxWidth(),enabled=checked==null,shape=RoundedCornerShape(14.dp),border=BorderStroke(if(chosen==candidate.id) 2.dp else 1.dp,if(chosen==candidate.id) Ink else Outline)) {
                            Text(candidate.spoken,Modifier.fillMaxWidth().padding(vertical=5.dp),textAlign=TextAlign.Start,color=Ink)
                        }
                    }
                }
                Exercise.ORDER -> {
                    SceneImage(item,Modifier.fillMaxWidth().height(170.dp).clip(RoundedCornerShape(20.dp)))
                    AudioButtons(item,vm,true)
                    Surface(shape=RoundedCornerShape(16.dp),color=Color.White,border=BorderStroke(1.dp,Outline)) {Text(ordered.joinToString(" ") {sentenceTokens[it]}.ifBlank {"Tap the words below…"},Modifier.fillMaxWidth().heightIn(min=90.dp).padding(16.dp),color=Ink,fontSize=20.sp)}
                    @OptIn(ExperimentalLayoutApi::class)
                    FlowRow(horizontalArrangement=Arrangement.spacedBy(7.dp),verticalArrangement=Arrangement.spacedBy(2.dp)) {
                        tokenOrder.forEach {index->OutlinedButton(onClick={ordered=ordered+index},modifier=Modifier.testTag("token_$index"),enabled=index !in ordered && checked==null,contentPadding=PaddingValues(horizontal=12.dp,vertical=4.dp),shape=RoundedCornerShape(12.dp)) {Text(sentenceTokens[index])}}
                    }
                    TextButton(onClick={ordered=emptyList()},enabled=checked==null) {Icon(Icons.Rounded.RestartAlt,null);Text("Start again")}
                }
                Exercise.SPELL -> {
                    SceneImage(item,Modifier.fillMaxWidth().height(220.dp).clip(RoundedCornerShape(24.dp)))
                    AudioButtons(item,vm)
                    OutlinedTextField(typed,{typed=it},Modifier.fillMaxWidth(),enabled=checked==null,label={Text("Your word or expression")},shape=RoundedCornerShape(16.dp),keyboardOptions=KeyboardOptions(autoCorrectEnabled=false))
                }
                Exercise.SPEAK,Exercise.DIALOGUE -> {
                    SceneImage(item,Modifier.fillMaxWidth().height(210.dp).clip(RoundedCornerShape(24.dp)))
                    Text(if(step.kind==Exercise.DIALOGUE)item.example else item.spoken,color=Ink,fontSize=23.sp,lineHeight=30.sp,fontWeight=FontWeight.Medium)
                    AudioButtons(item,vm,step.kind==Exercise.DIALOGUE)
                    SpeechPractice(vm,onSettings)
                }
            }
            if(checked!=null) {
                Surface(shape=RoundedCornerShape(18.dp),color=if(checked==true)Lime else Peach) {
                    Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
                        Text(if(checked==true) "That's it!" else "Let's take another look.",fontWeight=FontWeight.Bold,color=Ink)
                        if(checked==false) {Text(if(step.kind==Exercise.ORDER)item.example else item.spoken,color=Ink);Text("This will come back for another try.",color=Ink,style=MaterialTheme.typography.bodySmall)}
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
        }
        Surface(shadowElevation=2.dp,color=Cream) {
            Column(Modifier.padding(horizontal=24.dp,vertical=12.dp)) {
                when {
                    step.kind==Exercise.DISCOVER -> Primary("Got it. Let's try.",{vm.advance()},Modifier.fillMaxWidth())
                    isSpeech -> {
                        val feedback=vm.speech.feedback
                        if(feedback!=null) Primary("Continue",{vm.advance(feedback.passed)},Modifier.fillMaxWidth(),enabled=!vm.speech.submitting)
                        else TextButton(onClick={vm.advance()},Modifier.fillMaxWidth(),enabled=!vm.speech.submitting && !vm.speech.finishing) {Text("Skip speaking for now")}
                    }
                    checked!=null -> Primary("Continue",{vm.advance(checked)},Modifier.fillMaxWidth())
                    step.kind==Exercise.ORDER -> Primary("Check sentence",{checked=LearningRules.matches(ordered.joinToString(" "){sentenceTokens[it]},item.example)},Modifier.fillMaxWidth(),enabled=ordered.size==sentenceTokens.size,icon=Icons.Rounded.Check)
                    step.kind==Exercise.SPELL -> Primary("Check answer",{checked=LearningRules.matches(typed,item.spoken,spellingVariants(item.id))},Modifier.fillMaxWidth(),enabled=typed.isNotBlank(),icon=Icons.Rounded.Check)
                    else -> Text("Choose one answer",Modifier.fillMaxWidth().padding(10.dp),color=Muted,textAlign=TextAlign.Center,style=MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}
private fun spellingVariants(id:String)=when(id) {"color"->listOf("colour");"neighbor"->listOf("neighbour");"center"->listOf("centre");"key_word"->listOf("keyword");else->emptyList()}
private fun exerciseOptions(all:List<Vocabulary>,target:Vocabulary,seed:Int):List<Vocabulary> {
    val closeGroups=listOf(setOf("computer","pc","laptop","device"),setOf("link","hyperlink"),setOf("sad","unhappy","upset","disappointed","hurt"),setOf("shocked","surprised","terrified","nervous"),setOf("review","rating","reviewer"),setOf("to_think_of","to_come_up_with_something","thought"),setOf("to_search","to_search_for","to_look_up","to_research"),setOf("to_turn_on","to_turn_off"),setOf("to_type","keyboard","to_enter"),setOf("to_chat","chat","message","to_send","to_share"),setOf("travel","to_travel"),setOf("feature","tower","sight","attraction"),setOf("as_like","as_while"))
    val excluded=closeGroups.filter {target.id in it}.flatten().toSet()+target.id
    val same=all.filter {it.theme==target.theme && it.id !in excluded}.shuffled(Random(seed))
    val other=all.filter {it.id !in excluded && it !in same}.shuffled(Random(seed+1))
    return ((same+other).take(3)+target).shuffled(Random(seed+2))
}
@Composable
private fun SpeechPractice(vm:LearningViewModel,onSettings:()->Unit) {
    val speech=vm.speech
    val context=LocalContext.current
    val permission=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {granted->if(granted) vm.startRecording() else vm.permissionDenied()}
    fun record() {
        if(ContextCompat.checkSelfPermission(context,Manifest.permission.RECORD_AUDIO)==PackageManager.PERMISSION_GRANTED) vm.startRecording() else permission.launch(Manifest.permission.RECORD_AUDIO)
    }
    if(!vm.speechConfigured || !vm.preferences.speechConsent) {
        Surface(color=Color(0xFFEEEFE7),shape=RoundedCornerShape(18.dp)) {Column(Modifier.padding(18.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
            Text("Set up your speaking practice",fontWeight=FontWeight.Bold,color=Ink)
            Text("Add your speech key in Settings to get pronunciation feedback. Submitted recordings are processed online.",color=Muted)
            OutlinedButton(onClick=onSettings) {Text("Open Settings")}
        }}
    }
    when {
        speech.recording -> {
            Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {Icon(Icons.Rounded.FiberManualRecord,"Recording",tint=MaterialTheme.colorScheme.error);Text("Listening… up to 15 seconds",color=Ink)}
            Primary("Stop recording",vm::stopRecording,Modifier.fillMaxWidth(),icon=Icons.Rounded.Stop)
        }
        speech.finishing || speech.submitting -> Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {CircularProgressIndicator(Modifier.size(24.dp));Text(if(speech.submitting) "Reviewing your pronunciation…" else "Finishing your recording…",color=Muted)}
        speech.feedback!=null -> {
            val feedback=speech.feedback
            Surface(color=if(feedback.passed)Lime else Peach,shape=RoundedCornerShape(20.dp)) {
                Column(Modifier.padding(18.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
                    Text(feedback.message,fontWeight=FontWeight.Bold,color=Ink)
                    Text("Clarity ${feedback.accuracy.toInt()} / 100",fontSize=24.sp,fontWeight=FontWeight.Bold,color=Ink)
                    feedback.fluency?.let {Text("Fluency ${it.toInt()} / 100",color=Ink)}
                    @OptIn(ExperimentalLayoutApi::class)
                    FlowRow(horizontalArrangement=Arrangement.spacedBy(7.dp),verticalArrangement=Arrangement.spacedBy(5.dp)) {
                        feedback.words.forEach {word->Surface(color=if(word.accuracy<70 || word.error=="Omission") Color(0xFFF7E7DB) else Color.White.copy(alpha=.6f),shape=RoundedCornerShape(8.dp)) {Text(word.word,Modifier.padding(7.dp),color=Ink,fontWeight=if(word.accuracy<70)FontWeight.Bold else FontWeight.Normal)}}
                    }
                    Text("An automated practice estimate. Clear communication matters more than a perfect score.",style=MaterialTheme.typography.bodySmall,color=Ink)
                }
            }
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {OutlinedButton(onClick={record()}) {Text("Try again")};TextButton(onClick=vm::playRecording) {Text("Hear myself")}}
        }
        speech.hasRecording -> {
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick=vm::playRecording) {Icon(Icons.Rounded.PlayArrow,null);Text("Hear myself")}
                TextButton(onClick={record()}) {Text("Record again")}
            }
            if(vm.speechConfigured && vm.preferences.speechConsent) {
                Primary("Submit for feedback",vm::submitRecording,Modifier.fillMaxWidth(),icon=Icons.Rounded.CloudUpload)
                Text("Only Submit sends this recording to Azure Speech.",color=Muted,style=MaterialTheme.typography.bodySmall)
            } else Text("You can listen back and compare with the example. Online feedback needs speech setup.",color=Muted,style=MaterialTheme.typography.bodySmall)
        }
        else -> Primary("Record my voice",{record()},Modifier.fillMaxWidth(),icon=Icons.Rounded.Mic)
    }
    speech.error?.let {Text(it,color=MaterialTheme.colorScheme.error)}
    Text("${vm.preferences.usage} / ${vm.preferences.cap} speech requests used today",style=MaterialTheme.typography.labelSmall,color=Muted)
}

@Composable
fun SettingsScreen(vm:LearningViewModel,modifier:Modifier) {
    var goal by remember(vm.preferences.goal) {mutableStateOf(vm.preferences.goal.toString())}
    var cap by remember(vm.preferences.cap) {mutableStateOf(vm.preferences.cap.toString())}
    var consent by remember(vm.preferences.speechConsent) {mutableStateOf(vm.preferences.speechConsent)}
    var region by remember(vm.speechRegion) {mutableStateOf(vm.speechRegion)}
    var key by remember {mutableStateOf("")}
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),verticalArrangement=Arrangement.spacedBy(20.dp)) {
        PageHeading("Make it yours","Your practice,\nyour pace.")
        Text("Daily practice",fontSize=21.sp,fontWeight=FontWeight.Bold,color=Ink)
        OutlinedTextField(goal,{goal=it.filter(Char::isDigit).take(3)},Modifier.fillMaxWidth(),label={Text("Daily answer goal (5–100)")},keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Number),singleLine=true,shape=RoundedCornerShape(16.dp))
        HorizontalDivider(color=Outline)
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically) {Text("Speaking feedback",fontSize=21.sp,fontWeight=FontWeight.Bold,color=Ink);Pill(if(vm.speechConfigured)"KEY SAVED" else "SETUP NEEDED",if(vm.speechConfigured)Lime else Peach)}
        Text("Use your own Azure Speech resource. Copy its key and region from Azure → Speech resource → Keys and Endpoint. Use a regional resource, for example westeurope. No separate server is needed.",color=Muted)
        Text("Your key stays encrypted on this device. Speech assessment uses your Azure subscription and may incur charges.",color=Muted,style=MaterialTheme.typography.bodySmall)
        OutlinedTextField(key,{key=it},Modifier.fillMaxWidth(),label={Text(if(vm.speechConfigured) "Replace speech key (optional)" else "Azure Speech key")},visualTransformation=PasswordVisualTransformation(),singleLine=true,shape=RoundedCornerShape(16.dp),keyboardOptions=KeyboardOptions(autoCorrectEnabled=false))
        OutlinedTextField(region,{region=it.lowercase().trim()},Modifier.fillMaxWidth(),label={Text("Region, e.g. westeurope")},singleLine=true,shape=RoundedCornerShape(16.dp),keyboardOptions=KeyboardOptions(autoCorrectEnabled=false))
        OutlinedTextField(cap,{cap=it.filter(Char::isDigit).take(3)},Modifier.fillMaxWidth(),label={Text("Daily speech request cap (1–500)")},keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Number),singleLine=true,shape=RoundedCornerShape(16.dp))
        Row(verticalAlignment=Alignment.Top) {Checkbox(checked=consent,onCheckedChange={consent=it});Text("Allow recordings I submit to be processed by Azure Speech for pronunciation feedback. Recordings are removed from this device when I leave the exercise.",Modifier.padding(top=10.dp),color=Ink,style=MaterialTheme.typography.bodyMedium)}
        Primary("Save settings",{vm.saveSettings(goal.toIntOrNull() ?:20,cap.toIntOrNull() ?:50,consent,key,region);key=""},Modifier.fillMaxWidth(),enabled=goal.toIntOrNull() in 5..100 && cap.toIntOrNull() in 1..500,icon=Icons.Rounded.Check)
        if(vm.speechConfigured) TextButton(onClick={vm.removeCredentials();key=""}) {Text("Remove speech credentials")}
        HorizontalDivider(color=Outline)
        Text("About your vocabulary",fontSize=21.sp,fontWeight=FontWeight.Bold,color=Ink)
        Text("${vm.course.items.size} English words and expressions from Zoe Vokabeln 2610.pdf, pages 218–224. British English is the default, with marked American variants. Lessons and audio work offline; speaking assessment needs internet.",color=Muted)
        Text("One learner · Progress stored on this device · Version 1.0",color=Muted,style=MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(20.dp))
    }
}
