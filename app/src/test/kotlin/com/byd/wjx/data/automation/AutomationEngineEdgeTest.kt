package com.byd.wjx.data.automation

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.byd.wjx.activation.ActivationManager
import com.byd.wjx.data.local.entity.ActionDef
import com.byd.wjx.data.local.entity.RuleEntity
import com.byd.wjx.data.local.entity.TriggerDef
import com.byd.wjx.data.remote.diParsData
import com.byd.wjx.data.repository.FakeTrackingStateRepository
import com.byd.wjx.data.repository.VehicleStateRepository
import com.byd.wjx.data.vehicle.VehicleReadSource
import com.byd.wjx.data.vehicle.VehicleSnapshot
import com.byd.wjx.data.vehicle.VehicleSnapshotKind
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.MutableStateFlow
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Edge-semantics regressions from issue #51: rules firing right after the user
 * saves an edit, deferred fires after cooldown / park gating, and service_start
 * racing cold-start nulls in the very first poll tick.
 */
// SDK 33: on lower levels Robolectric rejects ContextCompat.registerReceiver
// (RECEIVER_NOT_EXPORTED permission fallback) thrown from the engine's init.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class AutomationEngineEdgeTest {

    private fun paramTrigger(param: String, op: String, value: String) = TriggerDef(
        param = param, chineseName = "", operator = op, value = value, displayName = param
    )

    private fun serviceStartTrigger() = TriggerDef(
        param = "", chineseName = "", operator = "", value = "",
        displayName = "start", kind = "service_start"
    )

    private fun rule(
        id: Long,
        triggers: List<TriggerDef>,
        logic: String = "AND",
        cooldown: Int = 0,
        requirePark: Boolean = false,
        lastTriggeredAt: Long? = null,
        exitActions: List<ActionDef> = emptyList(),
        repeatIntervalSeconds: Int = 0,
        actions: List<ActionDef> = listOf(ActionDef("notify", "n", "notification_silent")),
        confirmBeforeExecute: Boolean = false,
    ) = RuleEntity(
        id = id, name = "r$id",
        triggerLogic = logic,
        triggers = TriggerDef.listToJson(triggers),
        actions = ActionDef.listToJson(actions),
        cooldownSeconds = cooldown,
        requirePark = requirePark,
        lastTriggeredAt = lastTriggeredAt,
        exitActions = ActionDef.listToJson(exitActions),
        repeatIntervalSeconds = repeatIntervalSeconds,
        confirmBeforeExecute = confirmBeforeExecute,
    )

    private fun setup(
        rulesProvider: () -> List<RuleEntity>,
    ): Pair<AutomationEngine, AutomationRuleRepository> {
        val ruleRepository = mockk<AutomationRuleRepository>(relaxed = true)
        val engine = AutomationEngine(
            ruleRepository = ruleRepository,
            journalRepository = mockk<AutomationJournalRepository>(relaxed = true),
            actionDispatcher = mockk(relaxed = true),
            automationConfigRepository = object : AutomationConfigRepository {
                override suspend fun current() = AutomationConfigSnapshot(
                    enabledRules = rulesProvider(),
                    initialized = true,
                )
            },
            networkAvailableMonitor = mockk<NetworkAvailableMonitor> {
                every { lastAvailableAt } returns 0L
                every { probePending } returns false
            },
            activationManager = mockk<ActivationManager>(relaxed = true),
            vehicleStateRepository = emptyVehicleRepository(),
            trackingStateRepository = FakeTrackingStateRepository(),
            confirmationPresenter = mockk(relaxed = true),
            context = ApplicationProvider.getApplicationContext<Context>(),
        )
        return engine to ruleRepository
    }

    @Test fun `editing rule triggers reseeds edge state instead of firing`() = runBlocking {
        var current = rule(1, listOf(paramTrigger("Speed", ">", "40")))
        val (engine, ruleRepository) = setup { listOf(current) }

        engine.evaluate(diParsData(speed = 10), null)  // seed: false under OLD triggers

        // User edits the rule: condition is now instantly true. Must reseed, not fire.
        current = current.copy(triggers = TriggerDef.listToJson(listOf(paramTrigger("ExtTemp", ">", "20"))))
        engine.evaluate(diParsData(exteriorTemp = 25), null)
        coVerify(exactly = 0) { ruleRepository.markTriggered(any(), any()) }

        // A genuine front after the reseed still fires.
        engine.evaluate(diParsData(exteriorTemp = 10), null)
        engine.evaluate(diParsData(exteriorTemp = 10), null)
        engine.evaluate(diParsData(exteriorTemp = 25), null)
        engine.evaluate(diParsData(exteriorTemp = 25), null)
        coVerify(exactly = 1) { ruleRepository.markTriggered(1, any()) }
    }

    @Test fun `cold start rule already matched executes immediately`() = runBlocking {
        val r = rule(1, listOf(paramTrigger("ExtTemp", ">", "20")))
        val (engine, ruleRepository) = setup { listOf(r) }

        engine.evaluate(diParsData(exteriorTemp = 25), null)

        coVerify(exactly = 1) { ruleRepository.markTriggered(1, any()) }
    }

    @Test fun `front while park-gated is consumed, not deferred until parked`() = runBlocking {
        val r = rule(1, listOf(paramTrigger("ExtTemp", ">", "23")), requirePark = true)
        val (engine, ruleRepository) = setup { listOf(r) }

        engine.evaluate(diParsData(gear = 1, exteriorTemp = 10), null)  // parked, cold: seed false
        engine.evaluate(diParsData(gear = 4, exteriorTemp = 25), null)  // front happens while driving
        engine.evaluate(diParsData(gear = 1, exteriorTemp = 25), null)  // parked again, still hot

        coVerify(exactly = 0) { ruleRepository.markTriggered(any(), any()) }
    }

    @Test fun `front during cooldown is consumed, not deferred until cooldown expires`() = runBlocking {
        var r = rule(1, listOf(paramTrigger("ExtTemp", ">", "23")), cooldown = 600)
        val (engine, ruleRepository) = setup { listOf(r) }

        engine.evaluate(diParsData(exteriorTemp = 10), null)  // seed false, no cooldown yet

        r = r.copy(lastTriggeredAt = System.currentTimeMillis())  // cooldown engaged
        engine.evaluate(diParsData(exteriorTemp = 25), null)      // front during cooldown

        r = r.copy(lastTriggeredAt = System.currentTimeMillis() - 700_000)  // cooldown over
        engine.evaluate(diParsData(exteriorTemp = 25), null)      // still true, no NEW front

        coVerify(exactly = 0) { ruleRepository.markTriggered(any(), any()) }
    }

    @Test fun `service_start AND-rule fires when param warms up on a later tick`() = runBlocking {
        val r = rule(1, listOf(serviceStartTrigger(), paramTrigger("ExtTemp", ">", "22")), logic = "AND")
        val (engine, ruleRepository) = setup { listOf(r) }

        engine.evaluate(diParsData(exteriorTemp = null), null)  // first tick: data still cold
        engine.evaluate(diParsData(exteriorTemp = 25), null)    // within start window: must fire

        coVerify(exactly = 1) { ruleRepository.markTriggered(1, any()) }
    }

    @Test fun `service_start fires exactly once per rule`() = runBlocking {
        val r = rule(1, listOf(serviceStartTrigger()))
        val (engine, ruleRepository) = setup { listOf(r) }

        engine.evaluate(diParsData(soc = 50), null)
        engine.evaluate(diParsData(soc = 50), null)

        coVerify(exactly = 1) { ruleRepository.markTriggered(1, any()) }
    }

    @Test fun `event OR persistent condition does not double fire on following tick`() = runBlocking {
        val r = rule(
            id = 1,
            triggers = listOf(serviceStartTrigger(), paramTrigger("Speed", ">", "40")),
            logic = "OR",
        )
        val (engine, ruleRepository) = setup { listOf(r) }

        engine.evaluate(diParsData(speed = 50), null)
        engine.evaluate(diParsData(speed = 50), null)
        engine.evaluate(diParsData(speed = 50), null)

        coVerify(exactly = 1) { ruleRepository.markTriggered(1, any()) }
    }

    @Test fun `single transient sample does not fire a stable state rule`() = runBlocking {
        val r = rule(1, listOf(paramTrigger("ExtTemp", ">", "23")))
        val (engine, ruleRepository) = setup { listOf(r) }

        engine.evaluate(diParsData(exteriorTemp = 10), null)
        engine.evaluate(diParsData(exteriorTemp = 25), null)
        engine.evaluate(diParsData(exteriorTemp = 10), null)

        coVerify(exactly = 0) { ruleRepository.markTriggered(any(), any()) }
    }

    @Test fun `missing telemetry does not execute exit or reenter after recovery`() = runBlocking {
        val dispatcher = mockk<ActionDispatcher>(relaxed = true)
        val r = rule(
            id = 1,
            triggers = listOf(paramTrigger("ExtTemp", ">", "23")),
            exitActions = listOf(ActionDef("notify", "exit", "notification_silent")),
        )
        val ruleRepository = mockk<AutomationRuleRepository>(relaxed = true)
        val engine = engineFor(listOf(r), ruleRepository, dispatcher)

        engine.evaluate(diParsData(exteriorTemp = 25), null)
        engine.evaluate(diParsData(exteriorTemp = null), null)
        engine.evaluate(diParsData(exteriorTemp = null), null)
        engine.evaluate(diParsData(exteriorTemp = 25), null)
        engine.evaluate(diParsData(exteriorTemp = 25), null)

        coVerify(timeout = 2_000, exactly = 1) { ruleRepository.markTriggered(1, any()) }
        coVerify(timeout = 2_000, exactly = 0) {
            dispatcher.dispatch(match { it.displayName == "exit" }, any(), any())
        }
    }

    @Test fun `action execution uses latest fast snapshot`() = runBlocking {
        val dispatcher = mockk<ActionDispatcher>(relaxed = true)
        val ruleRepository = mockk<AutomationRuleRepository>(relaxed = true)
        val latestAction = MutableStateFlow<VehicleSnapshot?>(
            snapshot(diParsData(speed = 95, exteriorTemp = 25)),
        )
        val vehicleRepository = mockk<VehicleStateRepository>(relaxed = true) {
            every { actionSnapshot } returns latestAction
        }
        val r = rule(
            id = 1,
            triggers = listOf(paramTrigger("ExtTemp", ">", "23")),
            actions = listOf(ActionDef("车窗全开", "打开所有车窗")),
        )
        val engine = engineFor(listOf(r), ruleRepository, dispatcher, vehicleRepository)

        engine.evaluate(diParsData(speed = 0, exteriorTemp = 25), null)

        coVerify(timeout = 2_000, exactly = 1) {
            dispatcher.dispatch(any(), match { it?.speed == 95 }, any())
        }
    }

    @Test fun `auto parking rule marks outside sound as allowed during parking`() = runBlocking {
        val dispatcher = mockk<ActionDispatcher>(relaxed = true)
        val outsideSound = ActionDef(
            command = "outside_sound",
            displayName = "auto parking sound",
            kind = "outside_sound",
            payload = """{"path":"/tmp/test.mp3","loop":false}""",
        )
        val r = rule(
            id = 1,
            triggers = listOf(paramTrigger("AdasApaParkingStatus", "==", "4")),
            actions = listOf(outsideSound),
        )
        val engine = engineFor(listOf(r), mockk(relaxed = true), dispatcher)

        engine.evaluate(diParsData().copy(adasApaParkingStatus = 4), null)

        coVerify(timeout = 2_000, exactly = 1) {
            dispatcher.dispatch(
                match { action ->
                    JSONObject(action.payload ?: "{}").optBoolean("allowDuringAutoParking", false)
                },
                match { it?.adasApaParkingStatus == 4 },
                "automation:1:enter:0",
            )
        }
    }

    @Test fun `leaving reverse stops looping outside sound`() = runBlocking {
        val dispatcher = mockk<ActionDispatcher>(relaxed = true)
        val loopingOutsideSound = ActionDef(
            command = "outside_sound",
            displayName = "reverse sound",
            kind = "outside_sound",
            payload = """{"path":"/tmp/test.mp3","loop":true}""",
        )
        val r = rule(
            id = 1,
            triggers = listOf(paramTrigger("Gear", "==", "2")),
            actions = listOf(loopingOutsideSound),
        )
        val engine = engineFor(listOf(r), mockk(relaxed = true), dispatcher)

        engine.evaluate(diParsData(gear = 2), null)
        engine.evaluate(diParsData(gear = 4), null)
        engine.evaluate(diParsData(gear = 4), null)

        verify(exactly = 1) {
            dispatcher.stopOutsideSound("automation:1:enter:0")
        }
    }

    @Test fun `confirmation cannot execute after rule is disabled`() = runBlocking {
        lateinit var confirm: () -> Unit
        val presenter = object : AutomationConfirmationPresenter {
            override fun show(
                ruleName: String,
                actionsSummary: String,
                onConfirm: () -> Unit,
                onCancel: () -> Unit,
            ): Boolean {
                confirm = onConfirm
                return true
            }
        }
        val dispatcher = mockk<ActionDispatcher>(relaxed = true)
        val journalRepository = mockk<AutomationJournalRepository>(relaxed = true)
        var currentRule = rule(
            id = 1,
            triggers = listOf(paramTrigger("ExtTemp", ">", "23")),
            confirmBeforeExecute = true,
        )
        val ruleRepository = mockk<AutomationRuleRepository>(relaxed = true) {
            coEvery { find(1) } answers { currentRule }
        }
        val engine = AutomationEngine(
            ruleRepository = ruleRepository,
            journalRepository = journalRepository,
            actionDispatcher = dispatcher,
            automationConfigRepository = object : AutomationConfigRepository {
                override suspend fun current() = AutomationConfigSnapshot(
                    enabledRules = listOf(currentRule).filter { it.enabled },
                    initialized = true,
                )
            },
            networkAvailableMonitor = mockk<NetworkAvailableMonitor> {
                every { lastAvailableAt } returns 0L
                every { probePending } returns false
            },
            activationManager = mockk(relaxed = true),
            vehicleStateRepository = emptyVehicleRepository(),
            trackingStateRepository = FakeTrackingStateRepository(),
            confirmationPresenter = presenter,
            context = ApplicationProvider.getApplicationContext(),
        )

        engine.evaluate(diParsData(exteriorTemp = 25), null)
        currentRule = currentRule.copy(enabled = false)
        confirm()

        coVerify(timeout = 2_000, exactly = 0) { dispatcher.dispatch(any(), any(), any()) }
        coVerify(timeout = 2_000, exactly = 1) {
            journalRepository.record(match { it.actionsResult.contains("stale_confirmation") })
        }
    }

    @Test fun `exit action runs once after a stable true to false transition`() = runBlocking {
        val dispatcher = mockk<ActionDispatcher>(relaxed = true)
        val r = rule(
            id = 1,
            triggers = listOf(paramTrigger("ExtTemp", ">", "23")),
            exitActions = listOf(ActionDef("notify", "exit", "notification_silent")),
        )
        val engine = engineFor(listOf(r), mockk(relaxed = true), dispatcher)

        engine.evaluate(diParsData(exteriorTemp = 25), null)
        engine.evaluate(diParsData(exteriorTemp = 10), null)
        engine.evaluate(diParsData(exteriorTemp = 10), null)
        engine.evaluate(diParsData(exteriorTemp = 10), null)

        coVerify(timeout = 2_000, exactly = 1) {
            dispatcher.dispatch(match { it.displayName == "exit" }, any(), any())
        }
    }

    @Test fun `event pulse does not execute exit action on the next tick`() = runBlocking {
        val dispatcher = mockk<ActionDispatcher>(relaxed = true)
        val r = rule(
            id = 1,
            triggers = listOf(serviceStartTrigger()),
            exitActions = listOf(ActionDef("notify", "exit", "notification_silent")),
        )
        val engine = engineFor(listOf(r), mockk(relaxed = true), dispatcher)

        engine.evaluate(diParsData(soc = 50), null)
        engine.evaluate(diParsData(soc = 50), null)

        coVerify(timeout = 2_000, exactly = 0) {
            dispatcher.dispatch(match { it.displayName == "exit" }, any(), any())
        }
    }

    @Test fun `reenter stops looping media owned by the previous exit phase`() = runBlocking {
        val dispatcher = mockk<ActionDispatcher>(relaxed = true)
        val exitMusic = ActionDef(
            command = "play_music",
            displayName = "exit music",
            kind = "play_music",
            payload = """{"path":"/tmp/music.mp3","loop":true}""",
        )
        val r = rule(
            id = 1,
            triggers = listOf(paramTrigger("ExtTemp", ">", "23")),
            exitActions = listOf(exitMusic),
        )
        val engine = engineFor(listOf(r), mockk(relaxed = true), dispatcher)

        engine.evaluate(diParsData(exteriorTemp = 25), null)
        engine.evaluate(diParsData(exteriorTemp = 10), null)
        engine.evaluate(diParsData(exteriorTemp = 10), null)
        engine.evaluate(diParsData(exteriorTemp = 25), null)
        engine.evaluate(diParsData(exteriorTemp = 25), null)

        coVerify(timeout = 2_000, exactly = 1) {
            dispatcher.dispatch(exitMusic, any(), "automation:1:exit:0")
        }
        verify(exactly = 1) {
            dispatcher.stopTransientMedia("play_music", "automation:1:exit:0")
        }
    }

    @Test fun `reenter stops looping inside sound owned by the previous exit phase`() = runBlocking {
        val dispatcher = mockk<ActionDispatcher>(relaxed = true)
        val exitSound = ActionDef(
            command = "inside_sound",
            displayName = "exit inside sound",
            kind = "inside_sound",
            payload = """{"path":"/tmp/sound.mp3","name":"sound","loop":true}""",
        )
        val r = rule(
            id = 1,
            triggers = listOf(paramTrigger("ExtTemp", ">", "23")),
            exitActions = listOf(exitSound),
        )
        val engine = engineFor(listOf(r), mockk(relaxed = true), dispatcher)

        engine.evaluate(diParsData(exteriorTemp = 25), null)
        engine.evaluate(diParsData(exteriorTemp = 10), null)
        engine.evaluate(diParsData(exteriorTemp = 10), null)
        engine.evaluate(diParsData(exteriorTemp = 25), null)
        engine.evaluate(diParsData(exteriorTemp = 25), null)

        coVerify(timeout = 2_000, exactly = 1) {
            dispatcher.dispatch(exitSound, any(), "automation:1:exit:0")
        }
        verify(exactly = 1) {
            dispatcher.stopTransientMedia("inside_sound", "automation:1:exit:0")
        }
    }

    @Test fun `repeat interval executes only when due`() = runBlocking {
        val r = rule(
            id = 1,
            triggers = listOf(paramTrigger("ExtTemp", ">", "23")),
            repeatIntervalSeconds = 2,
        )
        val (engine, ruleRepository) = setup { listOf(r) }
        var now = 100_000L
        engine.nowProvider = { now }

        engine.evaluate(diParsData(exteriorTemp = 25), null)
        now += 1_999L
        engine.evaluate(diParsData(exteriorTemp = 25), null)
        coVerify(exactly = 1) { ruleRepository.markTriggered(1, any()) }

        now += 1L
        engine.evaluate(diParsData(exteriorTemp = 25), null)
        coVerify(exactly = 2) { ruleRepository.markTriggered(1, any()) }
    }

    private fun engineFor(
        rules: List<RuleEntity>,
        ruleRepository: AutomationRuleRepository,
        dispatcher: ActionDispatcher,
        vehicleRepository: VehicleStateRepository = emptyVehicleRepository(),
    ) = AutomationEngine(
        ruleRepository = ruleRepository,
        journalRepository = mockk<AutomationJournalRepository>(relaxed = true),
        actionDispatcher = dispatcher,
        automationConfigRepository = object : AutomationConfigRepository {
            override suspend fun current() = AutomationConfigSnapshot(
                enabledRules = rules,
                initialized = true,
            )
        },
        networkAvailableMonitor = mockk<NetworkAvailableMonitor> {
            every { lastAvailableAt } returns 0L
            every { probePending } returns false
        },
        activationManager = mockk<ActivationManager>(relaxed = true),
        vehicleStateRepository = vehicleRepository,
        trackingStateRepository = FakeTrackingStateRepository(),
        confirmationPresenter = mockk(relaxed = true),
        context = ApplicationProvider.getApplicationContext(),
    )

    private fun emptyVehicleRepository() = mockk<VehicleStateRepository>(relaxed = true) {
        every { actionSnapshot } returns MutableStateFlow<VehicleSnapshot?>(null)
        every { fullSnapshot } returns MutableStateFlow<VehicleSnapshot?>(null)
    }

    private fun snapshot(data: com.byd.wjx.data.remote.DiParsData) = VehicleSnapshot(
        data = data,
        kind = VehicleSnapshotKind.FULL,
        source = VehicleReadSource.HELPER_BINDER,
        readStartedAtMs = 1L,
        readCompletedAtMs = 2L,
        requestedFieldCount = 1,
        transportSuccessCount = 1,
        decodedFieldCount = 1,
    )
}
