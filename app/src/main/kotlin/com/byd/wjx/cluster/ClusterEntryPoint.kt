package com.byd.wjx.cluster

import com.byd.wjx.activation.ActivationManager
import com.byd.wjx.data.automation.AutomationButtonDispatcher
import com.byd.wjx.data.vehicle.HelperBootstrap
import com.byd.wjx.data.vehicle.HelperClient
import com.byd.wjx.vehicle.CarProfileDetector
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Bridges the @Singleton HelperClient/HelperBootstrap into SteeringWheelKeyService,
 * which is an AccessibilityService (instantiated by the framework, not Hilt).
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface ClusterEntryPoint {
    fun helperClient(): HelperClient
    fun helperBootstrap(): HelperBootstrap
    fun activationManager(): ActivationManager
    fun carProfileDetector(): CarProfileDetector
    fun automationButtonDispatcher(): AutomationButtonDispatcher
}
