package com.vokabellux.app

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class LearningFlowTest {
    @get:Rule val ui=createAndroidComposeRule<MainActivity>()
    private fun vm()=ViewModelProvider(ui.activity)[LearningViewModel::class.java]
    @Test fun everyWordHasItsOwnCompleteImageRegion() {
        ui.waitUntil(10000) {vm().ready}
        val words=vm().course.items
        assertEquals(188,words.size)
        assertEquals(188,words.map {it.image to it.bounds}.toSet().size)
        val dimensions=words.map {it.image}.distinct().associateWith {path->
            val options=android.graphics.BitmapFactory.Options().apply {inJustDecodeBounds=true}
            ui.activity.assets.open(path).use {android.graphics.BitmapFactory.decodeStream(it,null,options)}
            options.outWidth to options.outHeight
        }
        for(word in words) {
            assertEquals(word.id,4,word.bounds.size)
            val (width,height)=dimensions.getValue(word.image)
            val b=word.bounds
            assertTrue(word.id,b[0]>=0 && b[1]>=0 && b[2]>0 && b[3]>0)
            assertTrue(word.id,b[0]+b[2]<=width && b[1]+b[3]<=height)
        }
    }
    @Test fun completeOfflineLessonAndSaveProgress() {
        ui.waitUntil(10000) {vm().ready}
        ui.runOnUiThread {vm().abandon();vm().start("Test lesson",listOf(vm().course.items.first()),false)}
        ui.onNodeWithText("Resume Test lesson").performScrollTo().performClick()
        ui.onNodeWithText("Got it. Let's try.").performClick()
        ui.onNodeWithTag("picture_confused").performScrollTo().performClick()
        ui.onNodeWithText("That's it!").assertExists()
        ui.onNodeWithText("Continue").performClick()
        ui.onNodeWithText("confused",useUnmergedTree=false).performScrollTo().performClick()
        ui.onNodeWithText("Continue").performClick()
        ui.onNodeWithText("confused",useUnmergedTree=false).performScrollTo().performClick()
        ui.onNodeWithText("Continue").performClick()
        val tokens=vm().course.items.first().example.split(" ")
        tokens.indices.forEach {ui.onNodeWithTag("token_$it").performScrollTo().performClick()}
        ui.onNodeWithText("Check sentence").performClick()
        ui.onNodeWithText("Continue").performClick()
        ui.onNodeWithText("Your word or expression").performScrollTo().performTextInput("confused")
        ui.onNodeWithText("Check answer").performClick()
        ui.onNodeWithText("Continue").performClick()
        ui.onNodeWithText("Skip speaking for now").performClick()
        ui.onNodeWithText("Skip speaking for now").performClick()
        ui.waitUntil(10000) {vm().progress["confused"]?.reviews ?:0 >0 && !vm().saving}
        ui.onNodeWithText("Back to my day").assertExists()
        assertFalse(vm().progress["confused"]!!.speechMastered)
        assertTrue(vm().progress["confused"]!!.dueAt>System.currentTimeMillis())
        ui.onNodeWithText("Back to my day").performClick()
    }
    @Test fun leavingAnExerciseKeepsItResumable() {
        ui.waitUntil(10000) {vm().ready}
        ui.runOnUiThread {vm().abandon();vm().start("Resume check",listOf(vm().course.items[1]),false)}
        ui.onNodeWithText("Resume Resume check").performScrollTo().performClick()
        ui.onNodeWithText("Got it. Let's try.").performClick()
        ui.onNodeWithContentDescription("Save and leave").performClick()
        ui.onNodeWithText("Resume Resume check").performScrollTo().performClick()
        ui.onNodeWithText("Which picture matches?").assertExists()
    }
}
