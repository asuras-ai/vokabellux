package com.vokabellux.app

import org.junit.Assert.*
import org.junit.Test

class LearningRulesTest {
    @Test fun reviewsGrowFromOneDayToThirtyDays() {
        var stage=-1
        for(days in listOf(1,3,7,14,30,30)) {
            val next=LearningRules.schedule(stage,true,1000)
            assertEquals(1000L+days*LearningRules.DAY,next.dueAt)
            stage=next.stage
        }
    }
    @Test fun mistakesReturnTomorrow() {
        val next=LearningRules.schedule(4,false,2000)
        assertEquals(0,next.stage)
        assertEquals(2000+LearningRules.DAY,next.dueAt)
    }
    @Test fun punctuationAndCaseDoNotHideCorrectRecall() {
        assertTrue(LearningRules.matches("  IT’S up to you! ","It's up to you."))
        assertTrue(LearningRules.matches("colour","color",listOf("colour")))
        assertFalse(LearningRules.matches("through","thought"))
        assertFalse(LearningRules.matches("children","child"))
    }
    @Test fun skippedSpeechHasNoSpeakingSuccess() {
        val session=Session("Test",listOf(Step("hello",Exercise.SPEAK)))
        val next=session.advance(null,true)
        assertTrue(next.done)
        assertTrue(next.speechPassed.isEmpty())
        assertTrue(next.speechTried.isEmpty())
        assertEquals(0,next.answered)
    }
    @Test fun wrongAnswersReturnLaterOnlyOnceAndStayDue() {
        val session=Session("Test",listOf(Step("a",Exercise.LISTEN),Step("b",Exercise.LISTEN)))
        val next=session.advance(false)
        assertEquals("b",next.current!!.itemId)
        assertEquals(Step("a",Exercise.LISTEN,true),next.steps.last())
        val failedAgain=next.advance(true).advance(false)
        assertTrue(failedAgain.done)
        assertEquals(setOf("a"),failedAgain.failed)
        assertEquals(3,failedAgain.answered)
        assertEquals(1,failedAgain.correct)
    }
    @Test fun speechFailureDoesNotResetComprehension() {
        val next=Session("Test",listOf(Step("a",Exercise.SPEAK))).advance(false,true)
        assertTrue(next.failed.isEmpty())
        assertEquals(setOf("a"),next.speechTried)
    }
    @Test fun sessionRestoresRetryAndProgressExactly() {
        val session=Session("Test",listOf(Step("a",Exercise.LISTEN),Step("b",Exercise.SPEAK))).advance(false)
        assertEquals(session,SessionJson.decode(SessionJson.encode(session)))
        assertNull(SessionJson.decode("broken"))
    }
    @Test fun providerWordErrorsArePreserved() {
        val result=parseAssessment("""{"NBest":[{"PronunciationAssessment":{"AccuracyScore":64,"FluencyScore":80,"CompletenessScore":50},"Words":[{"Word":"hello","PronunciationAssessment":{"AccuracyScore":30,"ErrorType":"Mispronunciation"}},{"Word":"world","PronunciationAssessment":{"AccuracyScore":0,"ErrorType":"Omission"}}]}]}""","hello")
        assertFalse(result.passed)
        assertEquals("Omission",result.words[1].error)
        assertTrue(result.message.contains("missing"))
    }
    @Test(expected=AssessmentException::class) fun missingScoresNeverBecomeAGrade() {parseAssessment("{}","")}
}
