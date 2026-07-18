package com.byd.wjx.di

import com.byd.wjx.data.automation.AutomationActionSnapshotFieldProvider
import com.byd.wjx.data.automation.AutomationConfirmationPresenter
import com.byd.wjx.data.automation.AutomationConfigRepository
import com.byd.wjx.data.automation.AutomationNotificationPresenter
import com.byd.wjx.data.automation.RoomAutomationConfigRepository
import com.byd.wjx.data.autoservice.AdbOnDeviceClient
import com.byd.wjx.data.autoservice.AdbOnDeviceClientImpl
import com.byd.wjx.data.charging.AutoserviceChargingTelemetryReader
import com.byd.wjx.data.charging.ChargingTelemetryReader
import com.byd.wjx.data.energy.EnergyFlowStatsRepository
import com.byd.wjx.data.energy.EnergyFlowStatsStore
import com.byd.wjx.data.nativestack.ActionSnapshotFieldProvider
import com.byd.wjx.data.repository.TrackingStateRepository
import com.byd.wjx.data.repository.TrackingStateStore
import com.byd.wjx.data.vehicle.HelperClient
import com.byd.wjx.data.vehicle.HelperClientImpl
import com.byd.wjx.data.vehicle.HelperVehicleGateway
import com.byd.wjx.data.vehicle.VehicleApi
import com.byd.wjx.data.vehicle.VehicleApiImpl
import com.byd.wjx.data.vehicle.VehicleGateway
import com.byd.wjx.domain.tracker.DriveSessionPersistence
import com.byd.wjx.service.SessionPersistence
import com.byd.wjx.ui.overlay.ConfirmOverlayManager
import com.byd.wjx.ui.overlay.OverlayNotificationManager
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** Interface ownership is explicit here; concrete construction stays with @Inject constructors. */
@Module
@InstallIn(SingletonComponent::class)
abstract class AppBindingsModule {
    @Binds @Singleton
    abstract fun bindAdbOnDeviceClient(impl: AdbOnDeviceClientImpl): AdbOnDeviceClient

    @Binds @Singleton
    abstract fun bindHelperClient(impl: HelperClientImpl): HelperClient

    @Binds @Singleton
    abstract fun bindVehicleGateway(impl: HelperVehicleGateway): VehicleGateway

    @Binds @Singleton
    abstract fun bindVehicleApi(impl: VehicleApiImpl): VehicleApi

    @Binds @Singleton
    abstract fun bindTrackingStateRepository(impl: TrackingStateStore): TrackingStateRepository

    @Binds @Singleton
    abstract fun bindDriveSessionPersistence(impl: SessionPersistence): DriveSessionPersistence

    @Binds @Singleton
    abstract fun bindAutomationConfirmationPresenter(
        impl: ConfirmOverlayManager,
    ): AutomationConfirmationPresenter

    @Binds @Singleton
    abstract fun bindAutomationNotificationPresenter(
        impl: OverlayNotificationManager,
    ): AutomationNotificationPresenter

    @Binds @Singleton
    abstract fun bindEnergyFlowStatsRepository(
        impl: EnergyFlowStatsStore,
    ): EnergyFlowStatsRepository

    @Binds @Singleton
    abstract fun bindAutomationConfigRepository(
        impl: RoomAutomationConfigRepository,
    ): AutomationConfigRepository

    @Binds @Singleton
    abstract fun bindActionSnapshotFieldProvider(
        impl: AutomationActionSnapshotFieldProvider,
    ): ActionSnapshotFieldProvider

    @Binds @Singleton
    abstract fun bindChargingTelemetryReader(
        impl: AutoserviceChargingTelemetryReader,
    ): ChargingTelemetryReader
}
