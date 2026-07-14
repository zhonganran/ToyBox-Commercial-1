package com.byd.wjx.data.automation

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.byd.wjx.activation.ActivationManager
import com.byd.wjx.data.local.dao.RuleDao
import com.byd.wjx.data.local.dao.RuleLogDao
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
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.MutableStateFlow
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
    ) = RuleEntity(
        id = id, name = "r$id",
        triggerLogic = logic,
        triggers = TriggerDef.listToJson(triggers),
        actions = ActionDef.listToJson(listOf(ActionDef("notify", "n", "notification_silent"))),
        cooldownSeconds = cooldown,
        requirePark = requirePark,
        lastTriggeredAt = lastTriggeredAt,
        exitActions = ActionDef.listToJson(exitActions),
        repeatIntervalSeconds = repeatIntervalSeconds,
    )

    private fun setup(rulesProvider: () -> List<RuleEntity>): Pair<AutomationEngine, RuleDao> {
        val ruleDao = mockk<RuleDao>(relaxed = true) {
            coEvery { getEnabled() } answers { rulesProvider() }
        }
        val engine = AutomationEngine(
            ruleDao = ruleDao,
            ruleLogDao = mockk<RuleLogDao>(relaxed = true),
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
            vehicleStateRepository = mockk<VehicleStateRepository>(relaxed = true),
            trackingStateRepository = FakeTrackingStateRepository(),
            context = ApplicationProvider.getApplicationContext<Context>(),
        )
        return engine to ruleDao
    }

    @Test fun `editing rule triggers reseeds edge state instead of firing`() = runBlocking {
        var current = rule(1, listOf(paramTrigger("Speed", ">", "40")))
        val (engine, ruleDao) = setup { listOf(current) }

        engine.evaluate(diParsData(speed = 10), null)  // seed: false under OLD triggers

        // User edits the rule: condition is now instantly true. Must reseed, not fire.
        current = current.copy(triggers = TriggerDef.listToJson(listOf(paramTrigger("ExtTemp", ">", "20"))))
        engine.evaluate(diParsData(exteriorTemp = 25), null)
        coVerify(exactly = 0) { ruleDao.updateLastTriggered(any(), any()) }

        // A genuine front after the reseed still fires.
        engine.evaluate(diParsData(exteriorTemp = 10), null)
        engine.evaluate(diParsData(exteriorTemp = 10), null)
        engine.evaluate(diParsData(exteriorTemp = 25), null)
        engine.evaluate(diParsData(exteriorTemp = 25), null)
        coVerify(exactly = 1) { ruleDao.updateLastTriggered(1, any()) }
    }

    @Test fun `cold start rule already matched executes immediately`() = runBlocking {
        val r = rule(1, listOf(paramTrigger("ExtTemp", ">", "20")))
        val (engine, ruleDao) = setup { listOf(r) }

        engine.evaluate(diParsData(exteriorTemp = 25), null)

        coVerify(exactly = 1) { ruleDao.updateLastTriggered(1, any()) }
    }

    @Test fun `front while park-gated is consumed, not deferred until parked`() = runBlocking {
        val r = rule(1, listOf(paramTrigger("ExtTemp", ">", "23")), requirePark = true)
        val (engine, ruleDao) = setup { listOf(r) }

        engine.evaluate(diParsData(gear = 1, exteriorTemp = 10), null)  // parked, cold: seed false
        engine.evaluate(diParsData(gear = 4, exteriorTemp = 25), null)  // front happens while driving
        engine.evaluate(diParsData(gear = 1, exteriorTemp = 25), null)  // parked again, still hot

        coVerify(exactly = 0) { ruleDao.updateLastTriggered(any(), any()) }
    }

    @Test fun `front during cooldown is consumed, not deferred until cooldown expires`() = runBlocking {
        var r = rule(1, listOf(paramTrigger("ExtTemp", ">", "23")), cooldown = 600)
        val (engine, ruleDao) = setup { listOf(r) }

        engine.evaluate(diParsData(exteriorTemp = 10), null)  // seed false, no cooldown yet

        r = r.copy(lastTriggeredAt = System.currentTimeMillis())  // cooldown engaged
        engine.evaluate(diParsData(exteriorTemp = 25), null)      // front during cooldown

        r = r.copy(lastTriggeredAt = System.currentTimeMillis() - 700_000)  // cooldown over
        engine.evaluate(diParsData(exteriorTemp = 25), null)      // still true, no NEW front

        coVerify(exactly = 0) { ruleDao.updateLastTriggered(any(), any()) }
    }

    @Test fun `service_start AND-rule fires when param warms up on a later tick`() = runBlocking {
        val r = rule(1, listOf(serviceStartTrigger(), paramTrigger("ExtTemp", ">", "22")), logic = "AND")
        val (engine, ruleDao) = setup { listOf(r) }

        engine.evaluate(diParsData(exteriorTemp = null), null)  // first tick: data still cold
        engine.evaluate(diParsData(exteriorTemp = 25), null)    // within start window: must fire

        coVerify(exactly = 1) { ruleDao.updateLastTriggered(1, any()) }
    }

    @Test fun `service_start fires exactly once per rule`() = runBlocking {
        val r = rule(1, listOf(serviceStartTrigger()))
        val (engine, ruleDao) = setup { listOf(r) }

        engine.evaluate(diParsData(soc = 50), null)
        engine.evaluate(diParsData(soc = 50), null)

        coVerify(exactly = 1) { ruleDao.updateLastTriggered(1, any()) }
    }

    @Test fun `single transient sample does not fire a stable state rule`() = runBlocking {
        val r = rule(1, listOf(paramTrigger("ExtTemp", ">", "23")))
        val (engine, ruleDao) = setup { listOf(r) }

        engine.evaluate(diParsData(exteriorTemp = 10), null)
        engine.evaluate(diParsData(exteriorTemp = 25), null)
        engine.evaluate(diParsData(exteriorTemp = 10), null)

        coVerify(exactly = 0) { ruleDao.updateLastTriggered(any(), any()) }
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

    @Test fun `repeat interval executes only when due`() = runBlocking {
        val r = rule(
            id = 1,
            triggers = listOf(paramTrigger("ExtTemp", ">", "23")),
            repeatIntervalSeconds = 2,
        )
        val (engine, ruleDao) = setup { listOf(r) }
        var now = 100_000L
        engine.nowProvider = { now }

        engine.evaluate(diParsData(exteriorTemp = 25), null)
        now += 1_999L
        engine.evaluate(diParsData(exteriorTemp = 25), null)
        coVerify(exactly = 1) { ruleDao.updateLastTriggered(1, any()) }

        now += 1L
        engine.evaluate(diParsData(exteriorTemp = 25), null)
        coVerify(exactly = 2) { ruleDao.updateLastTriggered(1, any()) }
    }

    @Test fun `button trigger still requires the other AND conditions`() = runBlocking {
        val r = rule(
            id = 1,
            triggers = listOf(
                TriggerDef(
                    param = "",
                    chineseName = "",
                    operator = "",
                    value = "2",
                    displayName = "button",
                    kind = "button_press",
                ),
                paramTrigger("ExtTemp", ">", "20"),
            ),
        )
        val ruleDao = mockk<RuleDao>(relaxed = true)
        val snapshots = MutableStateFlow(snapshot(exteriorTemp = 10))
        val vehicleRepository = mockk<VehicleStateRepository>(relaxed = true) {
            every { fullSnapshot } returns snapshots
        }
        val engine = engineFor(listOf(r), ruleDao, mockk(relaxed = true), vehicleRepository)

        assertEquals(0, engine.onButtonPress(2))
        snapshots.value = snapshot(exteriorTemp = 25)
        assertEquals(1, engine.onButtonPress(2))
        coVerify(exactly = 1) { ruleDao.updateLastTriggered(1, any()) }
    }

    private fun engineFor(
        rules: List<RuleEntity>,
        ruleDao: RuleDao,
        dispatcher: ActionDispatcher,
        vehicleRepository: VehicleStateRepository = mockk(relaxed = true),
    ) = AutomationEngine(
        ruleDao = ruleDao,
        ruleLogDao = mockk<RuleLogDao>(relaxed = true),
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
        context = ApplicationProvider.getApplicationContext(),
    )

    private fun snapshot(exteriorTemp: Int) = VehicleSnapshot(
        data = diParsData(exteriorTemp = exteriorTemp),
        kind = VehicleSnapshotKind.FULL,
        source = VehicleReadSource.HELPER_BINDER,
        readStartedAtMs = 1L,
        readCompletedAtMs = 2L,
        requestedFieldCount = 1,
        transportSuccessCount = 1,
        decodedFieldCount = 1,
    )
}
