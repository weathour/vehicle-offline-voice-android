package com.company.vehiclevoice.core

import com.company.vehiclevoice.log.RecordingEventLogSink
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class VoicePipelineControllerTest {
    @Test
    fun controller_runsPipelineOffCallerAndReachesCompletedState() {
        val logSink = RecordingEventLogSink()
        val ran = CountDownLatch(1)
        val controller = VoicePipelineController(
            pipelineFactory = {
                ran.countDown()
                VoicePipelineFactory.createServicePreviewPipeline(logSink)
            },
            logSink = logSink
        )

        assertTrue(controller.start())
        assertFalse(controller.start())
        assertTrue(ran.await(2, TimeUnit.SECONDS))
        waitForState(controller, VoicePipelineController.State.Completed)
        assertEquals("air_conditioner_on", controller.lastResult()?.intentName)
        controller.close()
        assertEquals(VoicePipelineController.State.Closed, controller.currentState())
    }

    @Test
    fun closedController_refusesRestart() {
        val controller = VoicePipelineController(
            pipelineFactory = { throw AssertionError("should not run") },
            logSink = RecordingEventLogSink()
        )
        controller.close()
        assertFalse(controller.start())
        assertEquals(VoicePipelineController.State.Closed, controller.currentState())
    }

    private fun waitForState(controller: VoicePipelineController, expected: VoicePipelineController.State) {
        repeat(50) {
            if (controller.currentState() == expected) return
            Thread.sleep(20)
        }
        assertEquals(expected, controller.currentState())
    }
}
