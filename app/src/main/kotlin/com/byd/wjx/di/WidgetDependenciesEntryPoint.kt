package com.byd.wjx.di

import com.byd.wjx.data.automation.AutomationButtonDispatcher
import com.byd.wjx.data.repository.TrackingStateRepository
import com.byd.wjx.data.repository.VehicleStateRepository
import com.byd.wjx.domain.calculator.ConsumptionAggregator
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@EntryPoint
@InstallIn(SingletonComponent::class)
interface WidgetDependenciesEntryPoint {
    fun vehicleStateRepository(): VehicleStateRepository
    fun trackingStateRepository(): TrackingStateRepository
    fun automationButtonDispatcher(): AutomationButtonDispatcher
    fun consumptionAggregator(): ConsumptionAggregator
}
