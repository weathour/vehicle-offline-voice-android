package com.company.vehiclevoice.core

import com.company.vehiclevoice.action.RecordingUnityEventSink
import com.company.vehiclevoice.action.UnityActionJsonEncoder
import com.company.vehiclevoice.action.UnityActionMapper
import com.company.vehiclevoice.asr.ScriptedAsrEngine
import com.company.vehiclevoice.audio.FakePcmSource
import com.company.vehiclevoice.audio.PcmFrame
import com.company.vehiclevoice.data.MockRedisStore
import com.company.vehiclevoice.data.VehicleStateProjector
import com.company.vehiclevoice.data.readonly.KeyReadStatus
import com.company.vehiclevoice.data.readonly.RedisVehicleSnapshotProvider
import com.company.vehiclevoice.data.readonly.VehicleDataSourceDiagnostics
import com.company.vehiclevoice.data.readonly.VehicleReadOnlySnapshot
import com.company.vehiclevoice.data.readonly.VehicleReadOnlySnapshotProvider
import com.company.vehiclevoice.kws.ScriptedKeywordSpotter
import com.company.vehiclevoice.log.RecordingEventLogSink
import com.company.vehiclevoice.nlu.RuleIntentParser
import com.company.vehiclevoice.template.ReplyTemplateEngine
import com.company.vehiclevoice.tts.MockTtsEngine
import com.company.vehiclevoice.vad.EnergyVadEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReadOnlyVehicleSnapshotPipelineTest {

    @Test
    fun statusAndGroupBQueries_refreshSnapshotButDoNotEmitUnityAction() {
        val groupB = pipelineForQuery("有故障吗")
        val sam = pipelineForQuery("当前协作场景是什么")
        val status = pipelineForQuery("当前状态")

        assertNull(groupB.runUntilSourceEnds().unityJson)
        assertNull(sam.runUntilSourceEnds().unityJson)
        assertNull(status.runUntilSourceEnds().unityJson)
    }

    @Test
    fun failedRefreshClearsOldVehicleNamespaceBeforeReplying() {
        val store = MockRedisStore()
        val provider = SequenceSnapshotProvider(
            listOf(
                RedisVehicleSnapshotProvider.simulated(clockMs = { 1L }).readSnapshot(),
                VehicleReadOnlySnapshot(
                    sourceName = "empty-second-snapshot",
                    diagnostics = VehicleDataSourceDiagnostics("empty-second-snapshot", connected = true, keyCount = 0, detail = "no speed"),
                    keyStatuses = mapOf("BC_Veh_Spd" to KeyReadStatus("BC_Veh_Spd", present = false, decoded = false, error = "missing"))
                )
            )
        )

        val first = pipelineForQuery("速度怎样", store = store, provider = provider).runUntilSourceEnds()
        val second = pipelineForQuery("速度怎样", store = store, provider = provider).runUntilSourceEnds()

        assertTrue(first.reply!!.contains("12.5"))
        assertEquals(null, store.get("vehicle.speed_kmh"))
        assertTrue(second.reply!!.contains("暂未读取到车速"))
        assertNull(second.unityJson)
    }

    @Test
    fun readOnlyQuery_refreshesSimulatedRedisSnapshotAndDoesNotEmitControlAction() {
        val store = MockRedisStore()
        val tts = MockTtsEngine()
        val logSink = RecordingEventLogSink()
        val pipeline = VoicePipeline(
            audioSource = FakePcmSource(listOf(
                PcmFrame.silence(sequence = 0),
                PcmFrame.silence(sequence = 1),
                PcmFrame.constantTone(sequence = 2, amplitude = 10_000),
                PcmFrame.constantTone(sequence = 3, amplitude = 10_000),
                PcmFrame.constantTone(sequence = 4, amplitude = 10_000),
                PcmFrame.silence(sequence = 5),
                PcmFrame.silence(sequence = 6)
            )),
            keywordSpotter = ScriptedKeywordSpotter(wakeSequences = setOf(1L)),
            vadEngine = EnergyVadEngine(),
            asrEngine = ScriptedAsrEngine.single("速度怎样"),
            intentParser = RuleIntentParser(),
            stateStore = store,
            stateProjector = VehicleStateProjector(),
            replyTemplateEngine = ReplyTemplateEngine(),
            ttsEngine = tts,
            unityActionMapper = UnityActionMapper(clockMs = { 123L }),
            unityActionJsonEncoder = UnityActionJsonEncoder(),
            unityEventSink = RecordingUnityEventSink(),
            logSink = logSink,
            readOnlySnapshotProvider = RedisVehicleSnapshotProvider.simulated(clockMs = { 1L })
        )

        val result = pipeline.runUntilSourceEnds()

        assertEquals("vehicle_speed_query", result.intentName)
        assertEquals("12.5", store.get("vehicle.speed_kmh"))
        assertTrue(tts.spokenTexts().single().contains("12.5"))
        assertNull(result.unityJson)
        assertTrue(logSink.lines().any { it.contains("Vehicle read-only snapshot") })
    }
    private fun pipelineForQuery(
        text: String,
        store: MockRedisStore = MockRedisStore(),
        provider: VehicleReadOnlySnapshotProvider = RedisVehicleSnapshotProvider.simulated(clockMs = { 1L })
    ): VoicePipeline = VoicePipeline(
        audioSource = FakePcmSource(listOf(
            PcmFrame.silence(sequence = 0),
            PcmFrame.silence(sequence = 1),
            PcmFrame.constantTone(sequence = 2, amplitude = 10_000),
            PcmFrame.constantTone(sequence = 3, amplitude = 10_000),
            PcmFrame.constantTone(sequence = 4, amplitude = 10_000),
            PcmFrame.silence(sequence = 5),
            PcmFrame.silence(sequence = 6)
        )),
        keywordSpotter = ScriptedKeywordSpotter(wakeSequences = setOf(1L)),
        vadEngine = EnergyVadEngine(),
        asrEngine = ScriptedAsrEngine.single(text),
        intentParser = RuleIntentParser(),
        stateStore = store,
        stateProjector = VehicleStateProjector(),
        replyTemplateEngine = ReplyTemplateEngine(),
        ttsEngine = MockTtsEngine(),
        unityActionMapper = UnityActionMapper(clockMs = { 123L }),
        unityActionJsonEncoder = UnityActionJsonEncoder(),
        unityEventSink = RecordingUnityEventSink(),
        logSink = RecordingEventLogSink(),
        readOnlySnapshotProvider = provider
    )

    private class SequenceSnapshotProvider(private val snapshots: List<VehicleReadOnlySnapshot>) : VehicleReadOnlySnapshotProvider {
        private var index = 0
        override val providerName: String = "sequence"
        override fun readSnapshot(): VehicleReadOnlySnapshot = snapshots.getOrElse(index++) { snapshots.last() }
    }

}
