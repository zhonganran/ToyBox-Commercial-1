plugins {
    id("com.android.application") version "8.7.3" apply false
    id("org.jetbrains.kotlin.android") version "2.1.0" apply false
    id("org.jetbrains.kotlin.jvm") version "2.1.0" apply false
    id("com.google.devtools.ksp") version "2.1.0-1.0.29" apply false
    id("com.google.dagger.hilt.android") version "2.53.1" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.1.0" apply false
}

/**
 * Ratchets for structural cleanup. Existing hotspots remain visible, while new changes cannot
 * silently grow the largest orchestration files or introduce UI access to low-level transports.
 */
tasks.register("verifySourceHealth") {
    group = "verification"
    description = "Checks source-size ratchets and package dependency boundaries."

    val mainSources = fileTree("app/src/main/kotlin") { include("**/*.kt") }
    val mainResources = fileTree("app/src/main/res") { include("**/*") }
    inputs.files(mainSources, mainResources)
    inputs.file("app/lint.xml")

    doLast {
        val violations = mutableListOf<String>()
        val lineLimits = mapOf(
            "app/src/main/kotlin/com/byd/wjx/ui/navigation/AppNavigation.kt" to 260,
            "app/src/main/kotlin/com/byd/wjx/ui/settings/SettingsViewModel.kt" to 170,
            "app/src/main/kotlin/com/byd/wjx/ui/settings/SettingsActivationUiController.kt" to 145,
            "app/src/main/kotlin/com/byd/wjx/ui/settings/SettingsEnergyUiController.kt" to 160,
            "app/src/main/kotlin/com/byd/wjx/ui/settings/SettingsMaintenanceUiController.kt" to 105,
            "app/src/main/kotlin/com/byd/wjx/ui/settings/SettingsVehicleUiController.kt" to 115,
            "app/src/main/kotlin/com/byd/wjx/ui/settings/SettingsEnergyCoordinator.kt" to 145,
            "app/src/main/kotlin/com/byd/wjx/ui/settings/SettingsPreferenceCoordinator.kt" to 60,
            "app/src/main/kotlin/com/byd/wjx/ui/settings/SettingsVehicleProfileCoordinator.kt" to 55,
            "app/src/main/kotlin/com/byd/wjx/ui/settings/SettingsUiState.kt" to 125,
            "app/src/main/kotlin/com/byd/wjx/ui/settings/SettingsVehicleEventSoundState.kt" to 40,
            "app/src/main/kotlin/com/byd/wjx/ui/settings/VehicleEventSoundCoordinator.kt" to 195,
            "app/src/main/kotlin/com/byd/wjx/ui/settings/SettingsVehicleSoundSection.kt" to 305,
            "app/src/main/kotlin/com/byd/wjx/ui/settings/SettingsMaintenanceCoordinator.kt" to 180,
            "app/src/main/kotlin/com/byd/wjx/ui/settings/ServiceQrRepository.kt" to 130,
            "app/src/main/kotlin/com/byd/wjx/ui/settings/ServiceQrTransportPolicy.kt" to 80,
            "app/src/main/kotlin/com/byd/wjx/ui/settings/SettingsServiceQrCoordinator.kt" to 85,
            "app/src/main/kotlin/com/byd/wjx/ui/settings/SettingsServiceAuthorizationSection.kt" to 220,
            "app/src/main/kotlin/com/byd/wjx/ui/settings/SettingsServiceQrContent.kt" to 310,
            "app/src/main/kotlin/com/byd/wjx/service/TrackingService.kt" to 320,
            "app/src/main/kotlin/com/byd/wjx/ui/automation/AutomationActionEditor.kt" to 500,
            "app/src/main/kotlin/com/byd/wjx/ui/automation/AutomationActionPicker.kt" to 350,
            "app/src/main/kotlin/com/byd/wjx/ui/automation/AutomationTriggerEditor.kt" to 320,
            "app/src/main/kotlin/com/byd/wjx/ui/automation/AutomationScheduleTriggerEditor.kt" to 230,
            "app/src/main/kotlin/com/byd/wjx/ui/automation/AutomationTriggerPicker.kt" to 200,
            "app/src/main/kotlin/com/byd/wjx/ui/automation/AutomationSystemActionEditor.kt" to 260,
            "app/src/main/kotlin/com/byd/wjx/ui/automation/AutomationCallActionEditor.kt" to 230,
            "app/src/main/kotlin/com/byd/wjx/ui/automation/AutomationNavigateActionEditor.kt" to 200,
            "app/src/main/kotlin/com/byd/wjx/ui/automation/AutomationUrlActionEditor.kt" to 200,
            "app/src/main/kotlin/com/byd/wjx/activation/ActivationManager.kt" to 350,
            "app/src/main/kotlin/com/byd/wjx/activation/ActivationApiClient.kt" to 220,
            "app/src/main/kotlin/com/byd/wjx/activation/ActivationDeviceIdentityProvider.kt" to 190,
            "app/src/main/kotlin/com/byd/wjx/activation/ActivationEntitlementStore.kt" to 190,
            "app/src/main/kotlin/com/byd/wjx/activation/ActivationPaymentCheckoutPolicy.kt" to 100,
            "app/src/main/kotlin/com/byd/wjx/ui/settings/SettingsAuthorizationSection.kt" to 350,
            "app/src/main/kotlin/com/byd/wjx/ui/settings/ActivationPaymentDialog.kt" to 240,
            "app/src/main/kotlin/com/byd/wjx/ui/settings/SettingsCapabilityCard.kt" to 90,
            "app/src/main/kotlin/com/byd/wjx/ui/settings/SettingsActivationCoordinator.kt" to 90,
            "app/src/main/kotlin/com/byd/wjx/ui/settings/PaymentCheckoutContent.kt" to 230,
            "app/src/main/kotlin/com/byd/wjx/ui/settings/IpixelFaPresetPreview.kt" to 100,
            "app/src/main/kotlin/com/byd/wjx/data/sound/LocalSoundLibraryCoordinator.kt" to 130,
            "app/src/main/kotlin/com/byd/wjx/storage/SoundLibrary.kt" to 195,
            "app/src/main/kotlin/com/byd/wjx/ui/sound/SoundViewModel.kt" to 350,
            "app/src/main/kotlin/com/byd/wjx/ui/sound/SoundScreen.kt" to 130,
            "app/src/main/kotlin/com/byd/wjx/ui/sound/SoundLocalTab.kt" to 340,
            "app/src/main/kotlin/com/byd/wjx/ui/sound/SoundLocalCards.kt" to 440,
            "app/src/main/kotlin/com/byd/wjx/ui/sound/SoundArtworkCatalog.kt" to 105,
            "app/src/main/kotlin/com/byd/wjx/ui/sound/SoundTuningPanel.kt" to 250,
            "app/src/main/kotlin/com/byd/wjx/ui/sound/SoundSceneHeader.kt" to 195,
            "app/src/main/kotlin/com/byd/wjx/ui/sound/SoundUiState.kt" to 30,
            "app/src/main/kotlin/com/byd/wjx/ui/sound/SoundRealtimeHailingState.kt" to 50,
            "app/src/main/kotlin/com/byd/wjx/desktopcard/DesktopQuickCardHailingController.kt" to 205,
            "app/src/main/kotlin/com/byd/wjx/audio/WjxRealtimeHailingService.kt" to 345,
            "app/src/main/kotlin/com/byd/wjx/audio/RealtimeHailingState.kt" to 110,
            "app/src/main/kotlin/com/byd/wjx/audio/RealtimeHailingServiceCommands.kt" to 135,
            "app/src/main/kotlin/com/byd/wjx/audio/RealtimeHailingEngine.kt" to 285,
            "app/src/main/kotlin/com/byd/wjx/audio/RealtimeHailingAudioResources.kt" to 185,
            "app/src/main/kotlin/com/byd/wjx/audio/RealtimeHailingAudioEffects.kt" to 130,
            "app/src/main/kotlin/com/byd/wjx/audio/RealtimeHailingNotification.kt" to 50,
            "app/src/main/kotlin/com/byd/wjx/audio/RealtimeHailingAudioSpec.kt" to 20,
            "app/src/main/kotlin/com/byd/wjx/audio/HailingAudioProcessor.kt" to 85,
            "app/src/main/kotlin/com/byd/wjx/ui/lyrics/LyricDisplayScreen.kt" to 110,
            "app/src/main/kotlin/com/byd/wjx/ui/lyrics/LyricDisplayViewModel.kt" to 290,
            "app/src/main/kotlin/com/byd/wjx/lyrics/LyricRuntime.kt" to 380,
            "app/src/main/kotlin/com/byd/wjx/lyrics/LyricRuntimePolicies.kt" to 55,
            "app/src/main/kotlin/com/byd/wjx/led/LedVehicleSignalSampling.kt" to 230,
            "app/src/main/kotlin/com/byd/wjx/led/ipixelfa/IpixelFaBleClient.kt" to 430,
            "app/src/main/kotlin/com/byd/wjx/led/ipixelfa/IpixelFaGattTransport.kt" to 190,
            "app/src/main/kotlin/com/byd/wjx/led/ipixelfa/IpixelFaBleCallbacks.kt" to 45,
            "app/src/main/kotlin/com/byd/wjx/led/ipixelfa/IpixelFaConnectionPolicy.kt" to 30,
            "app/src/main/kotlin/com/byd/wjx/led/ipixelfa/IpixelFaRuntimeCoordinator.kt" to 400,
            "app/src/main/kotlin/com/byd/wjx/led/mafu/MafuBleClient.kt" to 500,
            "app/src/main/kotlin/com/byd/wjx/led/mafu/MafuGattTransport.kt" to 300,
            "app/src/main/kotlin/com/byd/wjx/led/mafu/MafuBleProtocolSession.kt" to 150,
            "app/src/main/kotlin/com/byd/wjx/led/mafu/MafuConnectionPolicy.kt" to 30,
            "app/src/main/kotlin/com/byd/wjx/led/mafu/MafuBleCallbacks.kt" to 30,
            "app/src/main/kotlin/com/byd/wjx/led/mafu/MafuRuntimeCoordinator.kt" to 420,
            "app/src/main/kotlin/com/byd/wjx/helper/HelperLockSoundRuntime.kt" to 390,
            "app/src/main/kotlin/com/byd/wjx/helper/HelperLockSoundMonitor.kt" to 370,
            "app/src/main/kotlin/com/byd/wjx/helper/HelperLockSoundConfigStore.kt" to 110,
            "app/src/main/kotlin/com/byd/wjx/helper/HelperLockSoundStateStore.kt" to 100,
            "app/src/main/kotlin/com/byd/wjx/helper/HelperLockSoundPlaybackEngine.kt" to 380,
            "app/src/main/kotlin/com/byd/wjx/helper/HelperLockSoundPlaybackQueue.kt" to 50,
            "app/src/main/kotlin/com/byd/wjx/helper/HelperLockSoundMediaPlayer.kt" to 105,
            "app/src/main/kotlin/com/byd/wjx/helper/HelperLockSoundPcmPlayer.kt" to 420,
            "app/src/main/kotlin/com/byd/wjx/helper/HelperLockSoundSignalReader.kt" to 165,
            "app/src/main/kotlin/com/byd/wjx/helper/HelperLockSoundSignalModels.kt" to 95,
            "app/src/main/kotlin/com/byd/wjx/helper/HelperSentryEvidenceReader.kt" to 180,
            "app/src/main/kotlin/com/byd/wjx/helper/HelperShellExecutor.kt" to 270,
            "app/src/main/kotlin/com/byd/wjx/helper/HelperFileLogger.kt" to 35,
            "app/src/main/kotlin/com/byd/wjx/helper/HelperAutoserviceTransport.kt" to 50,
            "app/src/main/kotlin/com/byd/wjx/helper/HelperActivityTaskPlatform.kt" to 175,
            "app/src/main/kotlin/com/byd/wjx/helper/HelperVehicleIdentityPlatform.kt" to 210,
            "app/src/main/kotlin/com/byd/wjx/helper/HelperVirtualDisplayPlatform.kt" to 80,
            "app/src/main/kotlin/com/byd/wjx/helper/HelperPlatformErrors.kt" to 15,
            "app/src/main/kotlin/com/byd/wjx/helper/HelperDaemon.kt" to 90,
            "app/src/main/kotlin/com/byd/wjx/helper/HelperDaemonProcessLock.kt" to 30,
            "app/src/main/kotlin/com/byd/wjx/helper/HelperLockSoundPolicies.kt" to 140,
            "app/src/main/kotlin/com/byd/wjx/helper/HelperDaemonRuntime.kt" to 30,
            "app/src/main/kotlin/com/byd/wjx/helper/HelperBinderService.kt" to 35,
            "app/src/main/kotlin/com/byd/wjx/helper/HelperTransactionDispatcher.kt" to 80,
            "app/src/main/kotlin/com/byd/wjx/helper/HelperVehicleIoTransactions.kt" to 125,
            "app/src/main/kotlin/com/byd/wjx/helper/HelperProjectionTransactions.kt" to 180,
            "app/src/main/kotlin/com/byd/wjx/helper/HelperSystemTransactions.kt" to 65,
            "app/src/main/kotlin/com/byd/wjx/helper/HelperVehicleFeatureTransactions.kt" to 145,
            "app/src/main/kotlin/com/byd/wjx/data/automation/AutomationEngine.kt" to 400,
            "app/src/main/kotlin/com/byd/wjx/data/automation/AutomationRuleRuntimeState.kt" to 180,
            "app/src/main/kotlin/com/byd/wjx/data/automation/AutomationActionExecutionCoordinator.kt" to 200,
            "app/src/main/kotlin/com/byd/wjx/data/automation/AutomationConfirmationNotificationGateway.kt" to 200,
            "app/src/main/kotlin/com/byd/wjx/data/automation/ActionDispatcher.kt" to 150,
            "app/src/main/kotlin/com/byd/wjx/data/automation/AutomationActionPolicies.kt" to 60,
            "app/src/main/kotlin/com/byd/wjx/data/automation/AutomationVehicleActionHandler.kt" to 80,
            "app/src/main/kotlin/com/byd/wjx/data/automation/AutomationNotificationActionHandler.kt" to 85,
            "app/src/main/kotlin/com/byd/wjx/data/automation/AutomationExternalActionHandler.kt" to 130,
            "app/src/main/kotlin/com/byd/wjx/data/automation/AutomationHttpActionHandler.kt" to 65,
            "app/src/main/kotlin/com/byd/wjx/data/automation/AutomationMediaActionHandler.kt" to 260,
            "app/src/main/kotlin/com/byd/wjx/ui/map/MapScreen.kt" to 140,
            "app/src/main/kotlin/com/byd/wjx/ui/map/MapPane.kt" to 140,
            "app/src/main/kotlin/com/byd/wjx/ui/map/MapScreenControls.kt" to 65,
            "app/src/main/kotlin/com/byd/wjx/ui/map/MapLocationState.kt" to 80,
            "app/src/main/kotlin/com/byd/wjx/ui/map/OsmdroidMapContent.kt" to 230,
            "app/src/main/kotlin/com/byd/wjx/ui/map/TripRecordsPanel.kt" to 300,
            "app/src/main/kotlin/com/byd/wjx/ui/map/TripRouteUtils.kt" to 45,
            "app/src/main/kotlin/com/byd/wjx/ui/home/ProHomePanels.kt" to 320,
            "app/src/main/kotlin/com/byd/wjx/ui/home/ProHomePanelFrame.kt" to 80,
            "app/src/main/kotlin/com/byd/wjx/ui/home/ProHomeVehiclePanels.kt" to 185,
            "app/src/main/kotlin/com/byd/wjx/ui/home/ProHomeTripChargePanels.kt" to 245,
            "app/src/main/kotlin/com/byd/wjx/ui/home/ProHomeInsightPanel.kt" to 225,
            "app/src/main/kotlin/com/byd/wjx/ui/pro/ProScaffold.kt" to 85,
            "app/src/main/kotlin/com/byd/wjx/ui/pro/ProStatusBar.kt" to 165,
            "app/src/main/kotlin/com/byd/wjx/ui/pro/ProDynamicBackground.kt" to 140,
            "app/src/main/kotlin/com/byd/wjx/ui/pro/ProBottomNavigation.kt" to 115,
            "app/src/main/kotlin/com/byd/wjx/ui/pro/ProComponents.kt" to 135,
            "app/src/main/kotlin/com/byd/wjx/ui/settings/SettingsDisplaySection.kt" to 55,
            "app/src/main/kotlin/com/byd/wjx/ui/settings/SettingsExteriorSoundChannelSection.kt" to 105,
            "app/src/main/kotlin/com/byd/wjx/ui/settings/SettingsKeyShortcutCoordinator.kt" to 155,
            "app/src/main/kotlin/com/byd/wjx/ui/settings/SettingsClusterProjectionSection.kt" to 420,
            "app/src/main/kotlin/com/byd/wjx/ui/settings/SettingsShortcutSection.kt" to 170,
            "app/src/main/kotlin/com/byd/wjx/ui/settings/SettingsShortcutModels.kt" to 75,
            "app/src/main/kotlin/com/byd/wjx/ui/settings/SettingsShortcutEditorDialog.kt" to 220,
            "app/src/main/kotlin/com/byd/wjx/ui/settings/SettingsSteeringKeyLearning.kt" to 155,
            "app/src/main/kotlin/com/byd/wjx/ui/settings/SettingsClusterGeometryControl.kt" to 75,
            "app/src/main/kotlin/com/byd/wjx/ui/settings/SettingsAppSection.kt" to 40,
            "app/src/main/kotlin/com/byd/wjx/ui/settings/SettingsAppearanceSection.kt" to 190,
            "app/src/main/kotlin/com/byd/wjx/ui/settings/SettingsDataSection.kt" to 190,
            "app/src/main/kotlin/com/byd/wjx/ui/settings/SettingsDiagnosticLogSection.kt" to 110,
            "app/src/main/kotlin/com/byd/wjx/ui/settings/SettingsAboutSection.kt" to 120,
            "app/src/main/kotlin/com/byd/wjx/ui/settings/LedLinkSettingsSection.kt" to 135,
            "app/src/main/kotlin/com/byd/wjx/ui/settings/IpixelFaSettingsPane.kt" to 255,
            "app/src/main/kotlin/com/byd/wjx/ui/settings/LedBluetoothUi.kt" to 170,
            "app/src/main/kotlin/com/byd/wjx/ui/settings/MafuSettingsPane.kt" to 130,
            "app/src/main/kotlin/com/byd/wjx/ui/settings/MafuSettingsCards.kt" to 290,
            "app/src/main/kotlin/com/byd/wjx/ui/settings/LedLinkageRulesEditor.kt" to 150,
            "app/src/main/kotlin/com/byd/wjx/ui/settings/IpixelFaLinkageRuleEditorDialog.kt" to 260,
            "app/src/main/kotlin/com/byd/wjx/ui/settings/MafuLinkageRulesEditor.kt" to 150,
            "app/src/main/kotlin/com/byd/wjx/ui/settings/MafuLinkageRuleEditorDialog.kt" to 145,
            "app/src/main/kotlin/com/byd/wjx/ui/settings/LedLinkageEditorComponents.kt" to 185,
            "app/src/main/kotlin/com/byd/wjx/cluster/ClusterProjectionManager.kt" to 240,
            "app/src/main/kotlin/com/byd/wjx/cluster/ClusterProjectionRuntime.kt" to 340,
            "app/src/main/kotlin/com/byd/wjx/cluster/ClusterProjectionDisplayEnvironment.kt" to 125,
            "app/src/main/kotlin/com/byd/wjx/cluster/ClusterProjectionDisplayGateway.kt" to 160,
            "app/src/main/kotlin/com/byd/wjx/cluster/ClusterProjectionOverlayController.kt" to 140,
            "app/src/main/kotlin/com/byd/wjx/data/system/SystemMetricsReader.kt" to 100,
            "app/src/main/kotlin/com/byd/wjx/data/system/SystemMetricsModels.kt" to 110,
            "app/src/main/kotlin/com/byd/wjx/data/system/SystemMetricsParsers.kt" to 175,
            "app/src/main/kotlin/com/byd/wjx/data/system/SystemMetricsTextSource.kt" to 90,
            "app/src/main/kotlin/com/byd/wjx/data/system/SystemMetricsHardwareReader.kt" to 200,
            "app/src/main/kotlin/com/byd/wjx/ui/automation/AutomationEditorDialog.kt" to 185,
            "app/src/main/kotlin/com/byd/wjx/ui/automation/AutomationEditorComponents.kt" to 145,
            "app/src/main/kotlin/com/byd/wjx/ui/automation/AutomationEditorTriggerColumn.kt" to 160,
            "app/src/main/kotlin/com/byd/wjx/ui/automation/AutomationEditorActionColumn.kt" to 60,
            "app/src/main/kotlin/com/byd/wjx/ui/automation/AutomationEditorActionList.kt" to 75,
            "app/src/main/kotlin/com/byd/wjx/ui/automation/AutomationEditorExecutionSettings.kt" to 145,
            "app/src/main/kotlin/com/byd/wjx/ui/automation/AutomationOverview.kt" to 175,
            "app/src/main/kotlin/com/byd/wjx/ui/automation/AutomationSceneHero.kt" to 300,
            "app/src/main/kotlin/com/byd/wjx/ui/automation/AutomationRuleCard.kt" to 235,
            "app/src/main/kotlin/com/byd/wjx/ui/automation/AutomationSummaryPanels.kt" to 225,
            "app/src/main/kotlin/com/byd/wjx/data/backup/BackupManager.kt" to 65,
            "app/src/main/kotlin/com/byd/wjx/data/backup/BackupModels.kt" to 25,
            "app/src/main/kotlin/com/byd/wjx/data/backup/BackupPreferencesCodec.kt" to 95,
            "app/src/main/kotlin/com/byd/wjx/data/backup/BackupManifestCodec.kt" to 70,
            "app/src/main/kotlin/com/byd/wjx/data/backup/BackupArchiveIO.kt" to 150,
            "app/src/main/kotlin/com/byd/wjx/data/backup/BackupPreferenceStore.kt" to 90,
            "app/src/main/kotlin/com/byd/wjx/data/backup/BackupDatabaseValidator.kt" to 95,
            "app/src/main/kotlin/com/byd/wjx/data/backup/BackupExporter.kt" to 100,
            "app/src/main/kotlin/com/byd/wjx/data/backup/BackupRestorer.kt" to 110,
            "app/src/main/kotlin/com/byd/wjx/audio/WjxOutsidePlayService.kt" to 430,
            "app/src/main/kotlin/com/byd/wjx/audio/OutsidePlaybackPlayer.kt" to 130,
            "app/src/main/kotlin/com/byd/wjx/audio/OutsidePlaybackCommands.kt" to 100,
            "app/src/main/kotlin/com/byd/wjx/audio/OutsidePlaybackNotification.kt" to 45,
            "app/src/main/kotlin/com/byd/wjx/lyrics/LyricOverlayView.kt" to 300,
            "app/src/main/kotlin/com/byd/wjx/lyrics/LyricFrame.kt" to 20,
            "app/src/main/kotlin/com/byd/wjx/lyrics/LyricOverlayPolicies.kt" to 180,
            "app/src/main/kotlin/com/byd/wjx/lyrics/LyricLineRenderer.kt" to 220,
            "app/src/main/kotlin/com/byd/wjx/ui/desktopcomponents/DesktopComponentsScreen.kt" to 150,
            "app/src/main/kotlin/com/byd/wjx/ui/desktopcomponents/DesktopComponentPage.kt" to 40,
            "app/src/main/kotlin/com/byd/wjx/ui/desktopcomponents/DesktopComponentPreview.kt" to 360,
            "app/src/main/kotlin/com/byd/wjx/desktopcard/DesktopQuickCardSettingsContent.kt" to 190,
            "app/src/main/kotlin/com/byd/wjx/desktopcard/DesktopQuickCardIconSection.kt" to 200,
            "app/src/main/kotlin/com/byd/wjx/desktopcard/DesktopQuickCardSettingsComponents.kt" to 180,
            "app/src/main/kotlin/com/byd/wjx/desktopcard/DesktopQuickCardPickers.kt" to 140,
            "app/src/main/kotlin/com/byd/wjx/data/nativestack/NativeParsReader.kt" to 215,
            "app/src/main/kotlin/com/byd/wjx/data/nativestack/NativeVehicleSnapshotParser.kt" to 260,
            "app/src/main/kotlin/com/byd/wjx/data/nativestack/NativeReadPlans.kt" to 40,
            "app/src/main/kotlin/com/byd/wjx/data/charging/AutoserviceChargingDetector.kt" to 245,
            "app/src/main/kotlin/com/byd/wjx/data/charging/ChargingCatchUpModels.kt" to 25,
            "app/src/main/kotlin/com/byd/wjx/data/charging/ChargingCatchUpPolicies.kt" to 110,
            "app/src/main/kotlin/com/byd/wjx/data/charging/CatchUpSessionRecorder.kt" to 105,
            "app/src/main/kotlin/com/byd/wjx/data/vehicle/HelperClient.kt" to 125,
            "app/src/main/kotlin/com/byd/wjx/data/vehicle/HelperClientImpl.kt" to 320,
            "app/src/main/kotlin/com/byd/wjx/data/vehicle/HelperBinderTransport.kt" to 95,
            "app/src/main/kotlin/com/byd/wjx/data/vehicle/AutoDoorController.kt" to 220,
            "app/src/main/kotlin/com/byd/wjx/data/vehicle/AutoDoorHardwareGateway.kt" to 90,
            "app/src/main/kotlin/com/byd/wjx/data/vehicle/AutoDoorDiagnostics.kt" to 145,
            "app/src/main/kotlin/com/byd/wjx/data/vehicle/AutoDoorModels.kt" to 75,
            "app/src/main/kotlin/com/byd/wjx/data/vehicle/AutoDoorPolicies.kt" to 80,
            "app/src/main/kotlin/com/byd/wjx/data/vehicle/AutoDoorCatalog.kt" to 55,
            "app/src/main/kotlin/com/byd/wjx/data/local/EnergyDataReader.kt" to 110,
            "app/src/main/kotlin/com/byd/wjx/data/local/EnergyDataSource.kt" to 130,
            "app/src/main/kotlin/com/byd/wjx/data/local/EnergyDatabaseReader.kt" to 75,
            "app/src/main/kotlin/com/byd/wjx/data/local/EnergyDataDiagnostics.kt" to 110,
            "app/src/main/kotlin/com/byd/wjx/data/local/EnergyDataQueries.kt" to 45,
            "app/src/main/kotlin/com/byd/wjx/data/local/EnergyDataModels.kt" to 40,
            "app/src/main/kotlin/com/byd/wjx/data/autoservice/AdbProtocol.kt" to 15,
            "app/src/main/kotlin/com/byd/wjx/data/autoservice/AdbProtocolClient.kt" to 190,
            "app/src/main/kotlin/com/byd/wjx/data/autoservice/AdbWire.kt" to 85,
            "app/src/main/kotlin/com/byd/wjx/data/autoservice/AdbAuth.kt" to 70,
            "app/src/main/kotlin/com/byd/wjx/data/autoservice/AdbSocketTransport.kt" to 70,
            "app/src/main/kotlin/com/byd/wjx/data/autoservice/AdbOnDeviceClient.kt" to 30,
            "app/src/main/kotlin/com/byd/wjx/data/autoservice/AdbOnDeviceClientImpl.kt" to 135,
            "app/src/main/kotlin/com/byd/wjx/data/autoservice/AdbConnectionManager.kt" to 150,
            "app/src/main/kotlin/com/byd/wjx/data/autoservice/AdbShellCommands.kt" to 135,
            "app/src/main/kotlin/com/byd/wjx/data/autoservice/AdbKeyStore.kt" to 100,
            "app/src/main/kotlin/com/byd/wjx/data/local/HistoryImporter.kt" to 130,
            "app/src/main/kotlin/com/byd/wjx/data/local/HistoryImportModels.kt" to 105,
            "app/src/main/kotlin/com/byd/wjx/data/local/EnergyHistoryImporter.kt" to 90,
            "app/src/main/kotlin/com/byd/wjx/data/local/DiPlusHistoryImporter.kt" to 75,
            "app/src/main/kotlin/com/byd/wjx/data/local/HistoryMaintenanceCoordinator.kt" to 85,
            "app/src/main/kotlin/com/byd/wjx/ui/automation/AutomationActionDefinitions.kt" to 430,
            "app/src/main/kotlin/com/byd/wjx/ui/help/UsageGuideScreen.kt" to 405,
            "app/src/main/kotlin/com/byd/wjx/clustercard/ClusterCardSettingsContent.kt" to 395,
            "app/src/main/kotlin/com/byd/wjx/desktopcard/DesktopQuickCardContent.kt" to 390,
            "app/src/main/kotlin/com/byd/wjx/data/vehicle/VehicleApiImpl.kt" to 105,
            "app/src/main/kotlin/com/byd/wjx/data/vehicle/VehicleWriteExecutor.kt" to 240,
            "app/src/main/kotlin/com/byd/wjx/data/vehicle/VehicleWriteAuditLogger.kt" to 85,
            "app/src/main/kotlin/com/byd/wjx/data/vehicle/VehicleSeatCommandResolver.kt" to 40,
            "app/src/main/kotlin/com/byd/wjx/lyrics/MusicPlaybackModels.kt" to 85,
            "app/src/main/kotlin/com/byd/wjx/lyrics/MusicIdentity.kt" to 120,
            "app/src/main/kotlin/com/byd/wjx/lyrics/MusicPlaybackStore.kt" to 135,
            "app/src/main/kotlin/com/byd/wjx/lyrics/SupportedMusicPackages.kt" to 75,
            "app/src/main/kotlin/com/byd/wjx/ui/help/UsageGuideCatalog.kt" to 360,
            "app/src/main/kotlin/com/byd/wjx/ui/lyrics/LyricDisplaySettingsPanels.kt" to 360,
            "app/src/main/kotlin/com/byd/wjx/ui/automation/AutomationViewModel.kt" to 350,
            "app/src/main/kotlin/com/byd/wjx/cluster/SteeringWheelKeyService.kt" to 340,
            "app/src/main/kotlin/com/byd/wjx/lyrics/MusicNotificationListenerService.kt" to 340,
            "app/src/main/kotlin/com/byd/wjx/desktopcard/DesktopQuickCardWindowController.kt" to 340,
            "app/src/main/kotlin/com/byd/wjx/ui/charges/ChargesStats.kt" to 340,
            "app/src/main/kotlin/com/byd/wjx/ui/automation/AutomationCatalog.kt" to 340,
            "app/src/main/kotlin/com/byd/wjx/ui/charges/ChargeEditDialog.kt" to 340,
            "app/src/main/kotlin/com/byd/wjx/desktopcard/DesktopQuickCardModels.kt" to 325,
            "app/src/main/kotlin/com/byd/wjx/data/vehicle/WriteAllowlist.kt" to 320,
            "app/src/main/kotlin/com/byd/wjx/ui/dashboard/DashboardViewModel.kt" to 290,
            "app/src/main/kotlin/com/byd/wjx/ui/dashboard/DashboardModels.kt" to 160,
            "app/src/main/kotlin/com/byd/wjx/ui/dashboard/DashboardTimeRanges.kt" to 60,
            "app/src/main/kotlin/com/byd/wjx/ui/charges/ChargesViewModel.kt" to 300,
            "app/src/main/kotlin/com/byd/wjx/ui/charges/ChargesModels.kt" to 70,
            "app/src/main/kotlin/com/byd/wjx/ui/charges/ChargesTimeRanges.kt" to 40,
            "app/src/main/kotlin/com/byd/wjx/ui/charges/ChargesGroupBuilder.kt" to 80,
            "app/src/main/kotlin/com/byd/wjx/lyrics/LyricRepository.kt" to 65,
            "app/src/main/kotlin/com/byd/wjx/lyrics/LyricModels.kt" to 35,
            "app/src/main/kotlin/com/byd/wjx/lyrics/LyricParsers.kt" to 135,
            "app/src/main/kotlin/com/byd/wjx/lyrics/LocalLyricFileSource.kt" to 60,
            "app/src/main/kotlin/com/byd/wjx/lyrics/OnlineLyricMatcher.kt" to 65,
            "app/src/main/kotlin/com/byd/wjx/lyrics/OnlineLyricSource.kt" to 40,
            "app/src/main/kotlin/com/byd/wjx/lyrics/OnlineLyricProviders.kt" to 185,
            "app/src/main/kotlin/com/byd/wjx/ui/home/HomeDetailScreens.kt" to 185,
            "app/src/main/kotlin/com/byd/wjx/ui/home/HomeDetailComponents.kt" to 195,
            "app/src/main/kotlin/com/byd/wjx/ui/home/HomeDetailMetrics.kt" to 120,
            "app/src/main/kotlin/com/byd/wjx/ui/home/ProHomeHero.kt" to 60,
            "app/src/main/kotlin/com/byd/wjx/ui/home/ProHomeGauges.kt" to 160,
            "app/src/main/kotlin/com/byd/wjx/ui/home/ProHomeVehicleStage.kt" to 180,
            "app/src/main/kotlin/com/byd/wjx/ui/home/ProHomeHeroComponents.kt" to 150,
        )
        lineLimits.forEach { (path, limit) ->
            val count = file(path).useLines { lines -> lines.count() }
            if (count > limit) violations += "$path has $count lines; limit is $limit"
        }
        mainSources.files.forEach { source ->
            val relativePath = source.relativeTo(rootDir).invariantSeparatorsPath
            val count = source.useLines { lines -> lines.count() }
            if (count >= 300 && relativePath !in lineLimits) {
                violations += "$relativePath has $count lines without an explicit source-health ceiling"
            }
        }

        val removedFeaturePathFragments = listOf(
            "/phonecast/",
            "/electronicmirror/",
            "/led/a950/",
            "/ui/places/",
        )
        val removedFeatureFiles = listOf(
            fileTree("app/src") { include("**/*.kt", "**/*.xml") },
            fileTree("app/schemas") { include("**/*.json") },
            fileTree("server/platform") {
                include("**/*.py", "**/*.js", "**/*.html", "**/*.css")
            },
        ).flatMap { it.files } + file("app/build.gradle.kts")
        removedFeatureFiles.forEach { source ->
            val relativePath = source.relativeTo(rootDir).invariantSeparatorsPath
            removedFeaturePathFragments.firstOrNull(relativePath::contains)?.let { fragment ->
                violations += "$relativePath restores removed feature path $fragment"
            }
        }
        val removedFeatureTokens = listOf(
            "com.byd.wjx.phonecast",
            "com.byd.wjx.electronicmirror",
            "com.byd.wjx.led.a950",
            "com.byd.wjx.ui.places",
            "PlaceEntity",
            "PlaceDao",
            "PlaceRepository",
            "PlacesScreen",
            "place_enter",
            "place_exit",
            "SoundOnlineTab",
            "SoundLibraryRemoteRepository",
            "SoundDownloadStore",
            "/api/sounds",
        )
        removedFeatureFiles.forEach { source ->
            val sourceText = source.readText()
            removedFeatureTokens.firstOrNull(sourceText::contains)?.let { token ->
                violations +=
                    "${source.relativeTo(rootDir)} restores removed feature token $token"
            }
        }
        val removedCommercialFiles = listOf(
            "app/src/main/kotlin/com/byd/wjx/activation/OfflineLicenseStore.kt",
            "app/src/main/kotlin/com/byd/wjx/ui/automation/AutomationPresets.kt",
            "app/src/test/kotlin/com/byd/wjx/activation/OfflineLicenseVerifierTest.kt",
            "app/src/test/kotlin/com/byd/wjx/ui/automation/AutomationPresetsTest.kt",
        )
        removedCommercialFiles.filter { file(it).exists() }.forEach { path ->
            violations += "$path restores removed commercial compatibility code"
        }
        val activationCredentialSources =
            fileTree("app/src/main/kotlin/com/byd/wjx/activation") { include("**/*.kt") }.files +
            listOf(
                file("server/platform/app.py"),
                file("server/platform/platform_config.py"),
                file("server/platform/platform_license.py"),
                file("server/platform/platform_storage.py"),
            )
        val removedActivationCredentialTokens = listOf(
            "deviceToken",
            "credentialVersion",
            "deviceProof",
            "offlineLicense",
            "api_token_hash",
            "api_token_issued_at",
            "OFFLINE_LICENSE",
        )
        activationCredentialSources.forEach { source ->
            val sourceText = source.readText()
            removedActivationCredentialTokens.firstOrNull(sourceText::contains)?.let { token ->
                violations +=
                    "${source.relativeTo(rootDir)} restores removed activation credential $token"
            }
        }
        val mySoundSources = listOf(
            file("app/src/main/kotlin/com/byd/wjx/ui/sound/SoundScreen.kt"),
            file("app/src/main/kotlin/com/byd/wjx/ui/sound/SoundLocalTab.kt"),
            file("app/src/main/kotlin/com/byd/wjx/ui/sound/SoundLocalCards.kt"),
            file("app/src/main/kotlin/com/byd/wjx/ui/sound/SoundViewModel.kt"),
        )
        val removedMySoundImportTokens = listOf("ImportSoundCard", "importSound(", "OpenDocument")
        mySoundSources.forEach { source ->
            val sourceText = source.readText()
            removedMySoundImportTokens.firstOrNull(sourceText::contains)?.let { token ->
                violations +=
                    "${source.relativeTo(rootDir)} restores My Sounds import entry $token"
            }
        }
        val schemaDirectory =
            file("app/schemas/com.byd.wjx.data.local.database.AppDatabase")
        val exportedSchemas = schemaDirectory.listFiles()
            .orEmpty()
            .filter { it.extension == "json" }
            .map { it.name }
            .toSet()
        if (exportedSchemas != setOf("21.json")) {
            violations +=
                "fresh-install database must export only schema 21; found ${exportedSchemas.sorted()}"
        }
        val clusterCardController =
            file("app/src/main/kotlin/com/byd/wjx/clustercard/ClusterCardController.kt")
        val clusterCardView =
            file("app/src/main/kotlin/com/byd/wjx/clustercard/ClusterCardView.kt")
        val clusterCardText = clusterCardController.readText() + clusterCardView.readText()
        listOf("Configuration.UI_MODE_NIGHT", "applyNightMode(", "nightMode:")
            .firstOrNull(clusterCardText::contains)?.let { detail ->
                violations +=
                    "instrument card restores deleted system font-color switching: $detail"
            }
        listOf(
            "labelPaint.color = Color.argb(210, 255, 255, 255)",
            "valuePaint.color = Color.WHITE",
        ).firstOrNull { it !in clusterCardView.readText() }?.let { guard ->
            violations += "instrument card is missing fixed high-contrast palette: $guard"
        }

        val automationEngine =
            file("app/src/main/kotlin/com/byd/wjx/data/automation/AutomationEngine.kt")
        val automationEngineText = automationEngine.readText()
        listOf(
            "AutomationRuleRuntimeState()",
            "AutomationActionExecutionCoordinator(",
            "AutomationConfirmationNotificationGateway(",
            "runtimeState.stabilize(",
            "executionCoordinator.executeConfirmed(",
        ).firstOrNull { guard -> guard !in automationEngineText }?.let { guard ->
            violations +=
                "${automationEngine.relativeTo(rootDir)} is missing automation boundary: $guard"
        }
        listOf(
            "NotificationChannel",
            "PendingIntent",
            "BroadcastReceiver",
            "JSONArray",
            "ConcurrentHashMap",
        ).firstOrNull(automationEngineText::contains)?.let { forbidden ->
            violations +=
                "${automationEngine.relativeTo(rootDir)} owns extracted runtime detail: $forbidden"
        }
        val confirmationGateway = file(
            "app/src/main/kotlin/com/byd/wjx/data/automation/AutomationConfirmationNotificationGateway.kt",
        )
        val confirmationGatewayText = confirmationGateway.readText()
        listOf(
            "NOTIFICATION_BASE_ID + rule.id.toInt()",
            "setTimeoutAfter(CONFIRM_TIMEOUT_MS)",
            "vehicleStateRepository.actionSnapshot.value?.data",
            "ContextCompat.RECEIVER_NOT_EXPORTED",
        ).firstOrNull { guard -> guard !in confirmationGatewayText }?.let { guard ->
            violations +=
                "${confirmationGateway.relativeTo(rootDir)} is missing confirmation guard: $guard"
        }
        val actionDispatcher =
            file("app/src/main/kotlin/com/byd/wjx/data/automation/ActionDispatcher.kt")
        val actionDispatcherText = actionDispatcher.readText()
        listOf(
            "AutomationVehicleActionHandler(",
            "AutomationNotificationActionHandler(",
            "AutomationExternalActionHandler(",
            "AutomationHttpActionHandler(",
            "AutomationMediaActionHandler(",
        ).firstOrNull { guard -> guard !in actionDispatcherText }?.let { guard ->
            violations +=
                "${actionDispatcher.relativeTo(rootDir)} is missing action boundary: $guard"
        }
        listOf(
            "NotificationChannel",
            "NotificationCompat.Builder",
            "Request.Builder",
            "AutomationMusicService.play(",
            "WjxOutsidePlayService.play(",
            "context.startActivity(",
            "vehicleApi.dispatch(",
        ).firstOrNull(actionDispatcherText::contains)?.let { forbidden ->
            violations +=
                "${actionDispatcher.relativeTo(rootDir)} owns extracted action detail: $forbidden"
        }
        val mediaActionHandler = file(
            "app/src/main/kotlin/com/byd/wjx/data/automation/AutomationMediaActionHandler.kt",
        )
        val mediaActionHandlerText = mediaActionHandler.readText()
        listOf(
            "fun stopMediaOwnedByInactiveRules(",
            "fun stopMediaOwnedByRule(",
            "shouldSkipAutomationOutsideSound(",
            "adjustStreamVolume = false",
        ).firstOrNull { guard -> guard !in mediaActionHandlerText }?.let { guard ->
            violations +=
                "${mediaActionHandler.relativeTo(rootDir)} is missing media guard: $guard"
        }
        val mapScreen = file("app/src/main/kotlin/com/byd/wjx/ui/map/MapScreen.kt")
        val mapScreenText = mapScreen.readText()
        listOf(
            "MapPane(",
            "TripRecordsPanel(",
            "PeriodSelector(",
            "rememberCurrentLocation()",
        ).firstOrNull { guard -> guard !in mapScreenText }?.let { guard ->
            violations += "${mapScreen.relativeTo(rootDir)} is missing map boundary: $guard"
        }
        listOf(
            "MapView(",
            "AndroidView(",
            "LocationManager",
            "LazyColumn(",
            "Polyline(",
        ).firstOrNull(mapScreenText::contains)?.let { forbidden ->
            violations +=
                "${mapScreen.relativeTo(rootDir)} owns extracted map detail: $forbidden"
        }
        val osmdroidMapContent =
            file("app/src/main/kotlin/com/byd/wjx/ui/map/OsmdroidMapContent.kt")
        val osmdroidMapContentText = osmdroidMapContent.readText()
        listOf(
            "OsmdroidRuntime.ensureInitialized(context)",
            "decimateTripPoints(route.points, MAX_ROUTE_POINTS)",
            "addRouteBoundaries(map, route)",
            "updateMapViewport(",
            "mapView.onDetach()",
        ).firstOrNull { guard -> guard !in osmdroidMapContentText }?.let { guard ->
            violations +=
                "${osmdroidMapContent.relativeTo(rootDir)} is missing map runtime guard: $guard"
        }
        val proHomePanels =
            file("app/src/main/kotlin/com/byd/wjx/ui/home/ProHomePanels.kt")
        val proHomePanelsText = proHomePanels.readText()
        listOf(
            "BatteryHealthPanel(",
            "VehicleEnvironmentPanel(",
            "TripRoutePanel(",
            "ChargeRecordPanel(",
            "LocalInsightPanel(",
        ).firstOrNull(proHomePanelsText::contains)?.let { extractedPanel ->
            violations +=
                "${proHomePanels.relativeTo(rootDir)} still owns extracted panel: $extractedPanel"
        }
        val homePanelFrame =
            file("app/src/main/kotlin/com/byd/wjx/ui/home/ProHomePanelFrame.kt")
        if ("internal fun HomeDarkPanel(" !in homePanelFrame.readText()) {
            violations +=
                "${homePanelFrame.relativeTo(rootDir)} is missing the shared home panel frame"
        }
        val tripChargePanels =
            file("app/src/main/kotlin/com/byd/wjx/ui/home/ProHomeTripChargePanels.kt")
        listOf(
            "internal fun TripRoutePanel(",
            "internal fun ChargeRecordPanel(",
            "private fun ChargeSocProgress(",
        ).firstOrNull { guard -> guard !in tripChargePanels.readText() }?.let { guard ->
            violations +=
                "${tripChargePanels.relativeTo(rootDir)} is missing trip/charge guard: $guard"
        }
        val proScaffold = file("app/src/main/kotlin/com/byd/wjx/ui/pro/ProScaffold.kt")
        val proScaffoldText = proScaffold.readText()
        listOf(
            "ProDynamicBackground(",
            "ProTopStatusBar(",
            "ProBottomNavigation(",
            "CompositionLocalProvider(LocalDensity provides compactDensity)",
        ).firstOrNull { guard -> guard !in proScaffoldText }?.let { guard ->
            violations +=
                "${proScaffold.relativeTo(rootDir)} is missing scaffold boundary: $guard"
        }
        listOf(
            "rememberInfiniteTransition",
            "SimpleDateFormat",
            "animateColorAsState",
            "fun ProPageHeader(",
            "Canvas(",
        ).firstOrNull(proScaffoldText::contains)?.let { forbidden ->
            violations +=
                "${proScaffold.relativeTo(rootDir)} owns extracted scaffold detail: $forbidden"
        }
        val settingsDisplay =
            file("app/src/main/kotlin/com/byd/wjx/ui/settings/SettingsDisplaySection.kt")
        val settingsDisplayText = settingsDisplay.readText()
        listOf(
            "ExteriorSoundChannelSection(",
            "SettingsKeyShortcutCoordinator(",
            "ClusterProjectionSettingsSection(",
        ).firstOrNull { guard -> guard !in settingsDisplayText }?.let { guard ->
            violations +=
                "${settingsDisplay.relativeTo(rootDir)} is missing display boundary: $guard"
        }
        listOf(
            "EntryPointAccessors",
            "ClusterProjectionManager.",
            "KeyShortcutSettings(",
            "rememberLauncherForActivityResult",
            "ClusterSizeSlider(",
        ).firstOrNull(settingsDisplayText::contains)?.let { forbidden ->
            violations +=
                "${settingsDisplay.relativeTo(rootDir)} owns extracted display detail: $forbidden"
        }
        val settingsShortcut =
            file("app/src/main/kotlin/com/byd/wjx/ui/settings/SettingsShortcutSection.kt")
        val settingsShortcutText = settingsShortcut.readText()
        listOf(
            "AlertDialog(",
            "SteeringWheelKeyService.learnMode",
            "ClusterSizeSlider(",
            "data class KeyShortcutEditorState",
            "LazyRow(",
        ).firstOrNull(settingsShortcutText::contains)?.let { forbidden ->
            violations +=
                "${settingsShortcut.relativeTo(rootDir)} owns extracted shortcut detail: $forbidden"
        }
        val shortcutEditor = file(
            "app/src/main/kotlin/com/byd/wjx/ui/settings/SettingsShortcutEditorDialog.kt",
        )
        val shortcutEditorText = shortcutEditor.readText()
        listOf(
            "internal fun KeyShortcutEditorDialog(",
            "internal fun validateKeyShortcutEditor(",
            "ShortcutSoundPicker(",
        ).firstOrNull { guard -> guard !in shortcutEditorText }?.let { guard ->
            violations +=
                "${shortcutEditor.relativeTo(rootDir)} is missing shortcut editor guard: $guard"
        }

        val settingsApp = file("app/src/main/kotlin/com/byd/wjx/ui/settings/SettingsAppSection.kt")
        val settingsAppText = settingsApp.readText()
        listOf(
            "SettingsAppearanceSection(",
            "SettingsDataSection(",
            "SettingsDiagnosticLogSection(",
            "SettingsAboutSection(",
        ).firstOrNull { it !in settingsAppText }?.let { guard ->
            violations += "${settingsApp.relativeTo(rootDir)} is missing app-settings boundary: $guard"
        }
        listOf("AlertDialog(", "LazyRow(", "Card(").firstOrNull(settingsAppText::contains)
            ?.let { detail ->
                violations += "${settingsApp.relativeTo(rootDir)} owns extracted app-settings detail: $detail"
            }

        val ledSettings = file(
            "app/src/main/kotlin/com/byd/wjx/ui/settings/LedLinkSettingsSection.kt",
        )
        val ledSettingsText = ledSettings.readText()
        listOf("IpixelFaSettingsPane(", "MafuSettingsPane(").firstOrNull {
            it !in ledSettingsText
        }?.let { guard ->
            violations += "${ledSettings.relativeTo(rootDir)} is missing LED pane boundary: $guard"
        }
        listOf("Manifest.permission", "ContextCompat", "Dialog(", "Slider(")
            .firstOrNull(ledSettingsText::contains)?.let { detail ->
                violations += "${ledSettings.relativeTo(rootDir)} owns extracted LED detail: $detail"
            }

        val projectionManager = file(
            "app/src/main/kotlin/com/byd/wjx/cluster/ClusterProjectionManager.kt",
        )
        val projectionManagerText = projectionManager.readText()
        listOf(
            "ClusterProjectionRuntime()",
            "runtime.resize(",
            "runtime.project(",
            "runtime.releaseRemoteDisplayIfAlive(",
        ).firstOrNull { it !in projectionManagerText }?.let { guard ->
            violations +=
                "${projectionManager.relativeTo(rootDir)} is missing projection runtime boundary: $guard"
        }
        listOf("DisplayManager", "android.view.Surface", "SurfaceView(", "remoteDisplayId", "projectedPackage")
            .firstOrNull(projectionManagerText::contains)?.let { detail ->
                violations +=
                    "${projectionManager.relativeTo(rootDir)} owns extracted projection detail: $detail"
            }

        val systemMetricsReader = file(
            "app/src/main/kotlin/com/byd/wjx/data/system/SystemMetricsReader.kt",
        )
        val systemMetricsReaderText = systemMetricsReader.readText()
        listOf(
            "SystemMetricsTextSource(helperClient)",
            "SystemMetricsHardwareReader(textSource)",
            "calculateCpuUsage(previousCpuTimes, cpuTimes)",
            "calculateDiskIo(",
        ).firstOrNull { it !in systemMetricsReaderText }?.let { guard ->
            violations +=
                "${systemMetricsReader.relativeTo(rootDir)} is missing metrics boundary: $guard"
        }
        listOf("Environment.", "StatFs(", "Build.VERSION", "File(", "fun parseMemInfo(")
            .firstOrNull(systemMetricsReaderText::contains)?.let { detail ->
                violations +=
                    "${systemMetricsReader.relativeTo(rootDir)} owns extracted metrics detail: $detail"
            }

        val automationEditor = file(
            "app/src/main/kotlin/com/byd/wjx/ui/automation/AutomationEditorDialog.kt",
        )
        val automationEditorText = automationEditor.readText()
        listOf(
            "AutomationEditorTriggerColumn(",
            "AutomationEditorActionColumn(",
            "AutomationEditorFooter(",
        ).firstOrNull { it !in automationEditorText }?.let { guard ->
            violations +=
                "${automationEditor.relativeTo(rootDir)} is missing editor boundary: $guard"
        }
        listOf("TriggerRow(", "ActionRow(", "OutlinedTextField(", "AddTriggerButton(")
            .firstOrNull(automationEditorText::contains)?.let { detail ->
                violations +=
                    "${automationEditor.relativeTo(rootDir)} owns extracted editor detail: $detail"
            }

        val backupManager = file(
            "app/src/main/kotlin/com/byd/wjx/data/backup/BackupManager.kt",
        )
        val backupManagerText = backupManager.readText()
        listOf(
            "BackupPreferenceStore(",
            "BackupExporter(",
            "BackupRestorer(",
            "BackupPreferencesCodec.serialize(data)",
        ).firstOrNull { it !in backupManagerText }?.let { guard ->
            violations +=
                "${backupManager.relativeTo(rootDir)} is missing backup boundary: $guard"
        }
        listOf("SQLiteDatabase", "ZipInputStream", "ZipOutputStream", "JSONObject", "SharedPreferences")
            .firstOrNull(backupManagerText::contains)?.let { detail ->
                violations +=
                    "${backupManager.relativeTo(rootDir)} owns extracted backup detail: $detail"
            }
        val backupArchive = file(
            "app/src/main/kotlin/com/byd/wjx/data/backup/BackupArchiveIO.kt",
        )
        val backupArchiveText = backupArchive.readText()
        listOf(
            "requiredEntries = setOf(ENTRY_DATABASE, ENTRY_PREFERENCES, ENTRY_MANIFEST)",
            "MAX_DATABASE_BYTES",
            "MAX_PREFERENCES_BYTES",
            "MAX_MANIFEST_BYTES",
            "if (!seenEntries.add(entry.name))",
        ).firstOrNull { it !in backupArchiveText }?.let { guard ->
            violations +=
                "${backupArchive.relativeTo(rootDir)} is missing archive safety guard: $guard"
        }
        val backupExporter = file(
            "app/src/main/kotlin/com/byd/wjx/data/backup/BackupExporter.kt",
        )
        listOf(
            "VACUUM INTO",
            "FileOutputStream(pendingFile)",
            "pendingFile.renameTo(destination)",
            "databaseSnapshot.delete()",
            "pendingFile.delete()",
        )
            .firstOrNull { it !in backupExporter.readText() }?.let { guard ->
                violations +=
                    "${backupExporter.relativeTo(rootDir)} is missing atomic file export guard: $guard"
            }
        val backupDestinationProvider = file(
            "app/src/main/kotlin/com/byd/wjx/data/backup/BackupDestinationProvider.kt",
        )
        listOf("WjxDirectories.ensureBaseDirs()", "WjxDirectories.baseDir", "ToyBox_backup_")
            .firstOrNull { it !in backupDestinationProvider.readText() }?.let { guard ->
                violations +=
                    "${backupDestinationProvider.relativeTo(rootDir)} is missing WJX destination guard: $guard"
            }

        val outsidePlaybackService = file(
            "app/src/main/kotlin/com/byd/wjx/audio/WjxOutsidePlayService.kt",
        )
        val outsidePlaybackServiceText = outsidePlaybackService.readText()
        listOf(
            "OutsidePlaybackContract.parse(intent)",
            "OutsidePlaybackPlayer()",
            "OutsidePlaybackNotification(this)",
            "OutsidePlaybackStarter.play(",
            "OutsidePlaybackStarter.stop(",
            "OutsidePlaybackStarter.updateVolume(",
        ).firstOrNull { it !in outsidePlaybackServiceText }?.let { guard ->
            violations +=
                "${outsidePlaybackService.relativeTo(rootDir)} is missing outside-playback boundary: $guard"
        }
        listOf(
            "MediaPlayer",
            "LoudnessEnhancer",
            "NotificationChannel",
            "ContextCompat.startForegroundService",
        ).firstOrNull(outsidePlaybackServiceText::contains)?.let { detail ->
            violations +=
                "${outsidePlaybackService.relativeTo(rootDir)} owns extracted playback detail: $detail"
        }
        val outsidePlaybackPlayer = file(
            "app/src/main/kotlin/com/byd/wjx/audio/OutsidePlaybackPlayer.kt",
        )
        listOf(
            "private var player: MediaPlayer?",
            "private var enhancer: LoudnessEnhancer?",
            "internal fun outsidePlaybackEnhancerGain(",
        ).firstOrNull { it !in outsidePlaybackPlayer.readText() }?.let { guard ->
            violations +=
                "${outsidePlaybackPlayer.relativeTo(rootDir)} is missing player ownership guard: $guard"
        }
        val outsidePlaybackCommands = file(
            "app/src/main/kotlin/com/byd/wjx/audio/OutsidePlaybackCommands.kt",
        )
        listOf(
            "internal object OutsidePlaybackContract",
            "fun parse(intent: Intent?)",
            "internal object OutsidePlaybackStarter",
        ).firstOrNull { it !in outsidePlaybackCommands.readText() }?.let { guard ->
            violations +=
                "${outsidePlaybackCommands.relativeTo(rootDir)} is missing command ownership guard: $guard"
        }
        val outsidePlaybackNotification = file(
            "app/src/main/kotlin/com/byd/wjx/audio/OutsidePlaybackNotification.kt",
        )
        listOf("NotificationChannel(", "service.startForeground(")
            .firstOrNull { it !in outsidePlaybackNotification.readText() }?.let { guard ->
                violations +=
                    "${outsidePlaybackNotification.relativeTo(rootDir)} is missing notification guard: $guard"
            }

        val lyricOverlayView = file(
            "app/src/main/kotlin/com/byd/wjx/lyrics/LyricOverlayView.kt",
        )
        val lyricOverlayViewText = lyricOverlayView.readText()
        listOf(
            "LyricLineRenderer(resources.displayMetrics)",
            "lyricAnimationDirection(frame, value)",
            "lyricTransitionOffsets(",
            "lyricDesiredHeightPx(",
        ).firstOrNull { it !in lyricOverlayViewText }?.let { guard ->
            violations +=
                "${lyricOverlayView.relativeTo(rootDir)} is missing lyric rendering boundary: $guard"
        }
        listOf("TextPaint", "Typeface.create", "RectF(", ".pow(", "Math.PI")
            .firstOrNull(lyricOverlayViewText::contains)?.let { detail ->
                violations +=
                    "${lyricOverlayView.relativeTo(rootDir)} owns extracted lyric detail: $detail"
            }
        val lyricLineRenderer = file(
            "app/src/main/kotlin/com/byd/wjx/lyrics/LyricLineRenderer.kt",
        )
        listOf("private val textPaint", "fun applyFontPreset(", "fun drawLine(")
            .firstOrNull { it !in lyricLineRenderer.readText() }?.let { guard ->
                violations +=
                    "${lyricLineRenderer.relativeTo(rootDir)} is missing line-renderer guard: $guard"
            }

        val legacyHelperPlatform =
            file("app/src/main/kotlin/com/byd/wjx/helper/HelperDaemonPlatform.kt")
        if (legacyHelperPlatform.exists()) {
            violations +=
                "${legacyHelperPlatform.relativeTo(rootDir)} must stay split into platform owners"
        }
        val helperPlatformBoundaries = mapOf(
            file("app/src/main/kotlin/com/byd/wjx/helper/HelperShellExecutor.kt") to listOf(
                "internal fun sentryShellRead(",
                "internal fun execShellRead(",
                "internal fun shExec(",
                "internal fun enableAccessibilityService(",
            ),
            file("app/src/main/kotlin/com/byd/wjx/helper/HelperAutoserviceTransport.kt") to listOf(
                "internal fun <T> withAutoserviceTransactionLock(",
                "internal fun autoserviceTransact(",
            ),
            file("app/src/main/kotlin/com/byd/wjx/helper/HelperActivityTaskPlatform.kt") to listOf(
                "internal fun findTaskId(",
                "internal fun launchAndForce(",
            ),
            file("app/src/main/kotlin/com/byd/wjx/helper/HelperVehicleIdentityPlatform.kt") to listOf(
                "internal fun acquireSystemContext(",
                "internal fun readVehicleVinFromBodywork(",
            ),
            file("app/src/main/kotlin/com/byd/wjx/helper/HelperVirtualDisplayPlatform.kt") to listOf(
                "internal fun createVirtualDisplay(",
            ),
        )
        helperPlatformBoundaries.forEach { (owner, boundaries) ->
            val ownerText = owner.readText()
            boundaries.firstOrNull { it !in ownerText }?.let { boundary ->
                violations += "${owner.relativeTo(rootDir)} is missing platform boundary: $boundary"
            }
        }

        val helperDaemon = file("app/src/main/kotlin/com/byd/wjx/helper/HelperDaemon.kt")
        val helperDaemonText = helperDaemon.readText()
        listOf(
            "@file:JvmName(\"HelperDaemon\")",
            "fun main(args: Array<String>)",
            "HelperBinderService(",
            "Looper.loop()",
        ).firstOrNull { guard -> guard !in helperDaemonText }?.let { guard ->
            violations += "${helperDaemon.relativeTo(rootDir)} is missing entry-point guard: $guard"
        }
        listOf(
            "override fun onTransact",
            "when (code)",
            "Surface.CREATOR",
            "autoserviceTransact(",
        ).firstOrNull(helperDaemonText::contains)?.let { detail ->
            violations += "${helperDaemon.relativeTo(rootDir)} owns Binder dispatch detail: $detail"
        }
        val transactionDispatcher =
            file("app/src/main/kotlin/com/byd/wjx/helper/HelperTransactionDispatcher.kt")
        val transactionDispatcherText = transactionDispatcher.readText()
        listOf("VEHICLE_IO", "PROJECTION", "SYSTEM", "VEHICLE_FEATURE").forEach { group ->
            if ("HelperTransactionGroup.$group" !in transactionDispatcherText) {
                violations +=
                    "${transactionDispatcher.relativeTo(rootDir)} is missing transaction group $group"
            }
        }
        val binderTransactionOwners = mapOf(
            "HelperVehicleIoTransactions.kt" to "handleVehicleIoTransaction(",
            "HelperProjectionTransactions.kt" to "handleProjectionTransaction(",
            "HelperSystemTransactions.kt" to "handleSystemTransaction(",
            "HelperVehicleFeatureTransactions.kt" to "handleVehicleFeatureTransaction(",
        )
        binderTransactionOwners.forEach { (fileName, boundary) ->
            val owner = file("app/src/main/kotlin/com/byd/wjx/helper/$fileName")
            if (boundary !in owner.readText()) {
                violations += "${owner.relativeTo(rootDir)} is missing Binder boundary: $boundary"
            }
        }
        val helperSources =
            fileTree("app/src/main/kotlin/com/byd/wjx/helper") { include("**/*.kt") }.files
        mapOf(
            "ProcessBuilder(" to file(
                "app/src/main/kotlin/com/byd/wjx/helper/HelperShellExecutor.kt",
            ),
            "Parcel.obtain()" to file(
                "app/src/main/kotlin/com/byd/wjx/helper/HelperAutoserviceTransport.kt",
            ),
            "android.app.ActivityTaskManager" to file(
                "app/src/main/kotlin/com/byd/wjx/helper/HelperActivityTaskPlatform.kt",
            ),
            "displayManager.createVirtualDisplay(" to file(
                "app/src/main/kotlin/com/byd/wjx/helper/HelperVirtualDisplayPlatform.kt",
            ),
        ).forEach { (primitive, owner) ->
            helperSources.filter { primitive in it.readText() }.filterNot { it == owner }.forEach {
                source ->
                violations +=
                    "${source.relativeTo(rootDir)} owns $primitive outside ${owner.relativeTo(rootDir)}"
            }
        }

        val lockSoundPlaybackEngine =
            file("app/src/main/kotlin/com/byd/wjx/helper/HelperLockSoundPlaybackEngine.kt")
        val lockSoundPlaybackEngineText = lockSoundPlaybackEngine.readText()
        listOf(
            "LockSoundPlaybackQueue()",
            "LockSoundPcmPlayer(",
            "LockSoundMediaPlayer()",
        ).firstOrNull { boundary -> boundary !in lockSoundPlaybackEngineText }?.let { boundary ->
            violations +=
                "${lockSoundPlaybackEngine.relativeTo(rootDir)} is missing playback boundary: $boundary"
        }
        listOf(
            "AudioTrack.Builder()",
            "MediaPlayer().apply",
        ).firstOrNull(lockSoundPlaybackEngineText::contains)?.let { lowLevelApi ->
            violations +=
                "${lockSoundPlaybackEngine.relativeTo(rootDir)} owns low-level audio API: $lowLevelApi"
        }

        val lockSoundSignalReader =
            file("app/src/main/kotlin/com/byd/wjx/helper/HelperLockSoundSignalReader.kt")
        val lockSoundSignalReaderText = lockSoundSignalReader.readText()
        if ("WjxSentryEvidenceReader(" !in lockSoundSignalReaderText) {
            violations +=
                "${lockSoundSignalReader.relativeTo(rootDir)} is missing the sentry evidence boundary"
        }
        listOf(
            "logcat -d -v threadtime",
            "settings get global sentrymode_",
            "wjx_sentry_msg3_cursor",
        ).firstOrNull(lockSoundSignalReaderText::contains)?.let { sentryDetail ->
            violations +=
                "${lockSoundSignalReader.relativeTo(rootDir)} owns sentry evidence detail: $sentryDetail"
        }

        mainSources.files.forEach { source ->
            val count = source.useLines { lines -> lines.count() }
            if (count > 900) {
                violations += "${source.relativeTo(rootDir)} has $count lines; split files before 900"
            }
        }

        val forbiddenUiImports = listOf(
            "import com.byd.wjx.helper.",
            "import com.byd.wjx.data.autoservice.Adb",
            "import com.byd.wjx.data.vehicle.HelperClient",
            "import com.byd.wjx.data.vehicle.VehicleGateway",
        )
        fileTree("app/src/main/kotlin/com/byd/wjx/ui") { include("**/*.kt") }.files.forEach { source ->
            source.useLines { lines ->
                lines.forEachIndexed { index, line ->
                    forbiddenUiImports.firstOrNull(line::startsWith)?.let { forbidden ->
                        violations += "${source.relativeTo(rootDir)}:${index + 1} depends on $forbidden"
                    }
                    if ("GlobalScope" in line) {
                        violations += "${source.relativeTo(rootDir)}:${index + 1} uses GlobalScope"
                    }
                }
            }
        }

        val automationPersistenceAdapter =
            file("app/src/main/kotlin/com/byd/wjx/data/automation/AutomationPersistenceRepositories.kt")
        val forbiddenAutomationDaoImports = listOf(
            "import com.byd.wjx.data.local.dao.RuleDao",
            "import com.byd.wjx.data.local.dao.RuleLogDao",
        )
        listOf(
            fileTree("app/src/main/kotlin/com/byd/wjx/data/automation") { include("**/*.kt") },
            fileTree("app/src/main/kotlin/com/byd/wjx/ui/automation") { include("**/*.kt") },
        ).flatMap { it.files }.filterNot { it == automationPersistenceAdapter }.forEach { source ->
            source.useLines { lines ->
                lines.forEachIndexed { index, line ->
                    forbiddenAutomationDaoImports.firstOrNull(line::startsWith)?.let { forbidden ->
                        violations += "${source.relativeTo(rootDir)}:${index + 1} depends on $forbidden"
                    }
                }
            }
        }

        val activationManager =
            file("app/src/main/kotlin/com/byd/wjx/activation/ActivationManager.kt")
        val activationManagerText = activationManager.readText()
        if (Regex("""preferences\.(get|edit|contains)""").containsMatchIn(activationManagerText)) {
            violations +=
                "${activationManager.relativeTo(rootDir)} accesses entitlement preferences directly"
        }

        val deviceBindingOwner = file(
            "app/src/main/kotlin/com/byd/wjx/data/system/AppScopedDeviceBindingProvider.kt",
        )
        val androidIdReaders = mainSources.files.filter { source ->
            source != deviceBindingOwner &&
            "Settings.Secure.ANDROID_ID" in source.readText()
        }
        if (androidIdReaders.isNotEmpty()) {
            violations += "Settings.Secure.ANDROID_ID must remain inside AppScopedDeviceBindingProvider"
        }
        if (!deviceBindingOwner.exists() ||
            "Settings.Secure.ANDROID_ID" !in deviceBindingOwner.readText()
        ) {
            violations += "AppScopedDeviceBindingProvider must own the app-scoped device binding"
        }
        val hardwareIdSuppressions = mainSources.files.filter { source ->
            source != deviceBindingOwner &&
            "@SuppressLint(\"HardwareIds\")" in source.readText()
        }
        if (hardwareIdSuppressions.isNotEmpty()) {
            violations += "HardwareIds suppression must remain inside AppScopedDeviceBindingProvider"
        }

        val settingsViewModel =
            file("app/src/main/kotlin/com/byd/wjx/ui/settings/SettingsViewModel.kt")
        val settingsViewModelText = settingsViewModel.readText()
        if ("import com.byd.wjx.activation.ActivationManager" in settingsViewModelText) {
            violations +=
                "${settingsViewModel.relativeTo(rootDir)} must use SettingsActivationCoordinator"
        }
        val forbiddenSettingsCoordinatorDependencies = listOf(
            "import com.byd.wjx.data.repository.SettingsRepository",
            "import com.byd.wjx.vehicle.CarProfileDetector",
            "ServiceQrRepository",
        )
        forbiddenSettingsCoordinatorDependencies.firstOrNull(settingsViewModelText::contains)?.let {
            violations +=
                "${settingsViewModel.relativeTo(rootDir)} must use settings coordinators for $it"
        }
        val forbiddenSettingsEnergyDependencies = listOf(
            "import com.byd.wjx.data.local.HistoryImporter",
            "import com.byd.wjx.data.repository.TripRepository",
        )
        forbiddenSettingsEnergyDependencies.firstOrNull(settingsViewModelText::contains)?.let {
            violations +=
                "${settingsViewModel.relativeTo(rootDir)} must use SettingsEnergyCoordinator for $it"
        }
        val forbiddenSettingsEnergyKeys = listOf(
            "KEY_BATTERY_CAPACITY",
            "KEY_FUEL_TANK_CAPACITY",
            "KEY_HOME_TARIFF",
            "KEY_DC_TARIFF",
            "KEY_FUEL_PRICE_PER_LITER",
            "KEY_TRIP_COST_TARIFF",
            "KEY_CONSUMPTION_GOOD",
            "KEY_CONSUMPTION_BAD",
        )
        forbiddenSettingsEnergyKeys.firstOrNull(settingsViewModelText::contains)?.let { key ->
            violations +=
                "${settingsViewModel.relativeTo(rootDir)} accesses energy persistence key $key directly"
        }
        val forbiddenMaintenanceImports = listOf(
            "import com.byd.wjx.data.backup.BackupManager",
            "import com.byd.wjx.service.UpdateChecker",
            "import com.byd.wjx.service.BootDiagnosticsStore",
        )
        forbiddenMaintenanceImports.firstOrNull(settingsViewModelText::contains)?.let {
            violations +=
                "${settingsViewModel.relativeTo(rootDir)} must use SettingsMaintenanceCoordinator for $it"
        }
        listOf(
            "SettingsActivationUiController(",
            "SettingsEnergyUiController(",
            "SettingsMaintenanceUiController(",
            "SettingsVehicleUiController(",
        ).firstOrNull { controller -> controller !in settingsViewModelText }?.let { controller ->
            violations +=
                "${settingsViewModel.relativeTo(rootDir)} must delegate through $controller"
        }
        val forbiddenSettingsViewModelImplementation = listOf(
            "CancellationException",
            "ActivationPaymentUpdate",
            "paymentRequestId",
            "downloadUpdates()",
            "normalizeCapacities(",
            "delay(",
            "VehicleEventSoundChange",
            "VehicleEventSoundState",
            ".withSnapshot(",
            ".copy(activation =",
            ".copy(activationPayment =",
            ".copy(appPreferences =",
            ".copy(energy =",
            ".copy(maintenance =",
            ".copy(vehicleProfile =",
            ".copy(vehicleEventSound =",
        )
        forbiddenSettingsViewModelImplementation
            .firstOrNull(settingsViewModelText::contains)
            ?.let { implementation ->
                violations +=
                    "${settingsViewModel.relativeTo(rootDir)} owns controller implementation: " +
                        implementation
            }
        val settingsActivationUiController =
            file(
                "app/src/main/kotlin/com/byd/wjx/ui/settings/" +
                    "SettingsActivationUiController.kt",
            )
        val settingsActivationUiControllerText = settingsActivationUiController.readText()
        listOf(
            "private var paymentJob: Job?",
            "private var paymentRequestId",
            "CancellationException",
            "coordinator.paymentUpdates(paymentType).collect",
        ).firstOrNull { guard -> guard !in settingsActivationUiControllerText }?.let { guard ->
            violations +=
                "${settingsActivationUiController.relativeTo(rootDir)} is missing payment guard: $guard"
        }
        val settingsEnergyUiController =
            file("app/src/main/kotlin/com/byd/wjx/ui/settings/SettingsEnergyUiController.kt")
        val settingsEnergyUiControllerText = settingsEnergyUiController.readText()
        listOf(
            "energyCoordinator.normalizeCapacities(",
            "energyCoordinator.saveCapacities(",
            "energyCoordinator.recalculateAllCosts()",
            "preferenceCoordinator.setThemeMode(mode)",
            "preferenceCoordinator.setTripGpsRecordingEnabled(enabled)",
            "delay(STATUS_CLEAR_DELAY_MS)",
        ).firstOrNull { guard -> guard !in settingsEnergyUiControllerText }?.let { guard ->
            violations +=
                "${settingsEnergyUiController.relativeTo(rootDir)} is missing energy UI guard: $guard"
        }
        val settingsMaintenanceUiController =
            file("app/src/main/kotlin/com/byd/wjx/ui/settings/SettingsMaintenanceUiController.kt")
        val settingsMaintenanceUiControllerText = settingsMaintenanceUiController.readText()
        listOf(
            "private var downloadJob: Job?",
            "fun loadDiagnostics()",
            "coordinator.downloadUpdates().collect",
            "CancellationException",
        ).firstOrNull { guard -> guard !in settingsMaintenanceUiControllerText }?.let { guard ->
            violations +=
                "${settingsMaintenanceUiController.relativeTo(rootDir)} is missing maintenance UI guard: $guard"
        }
        val settingsVehicleUiController =
            file("app/src/main/kotlin/com/byd/wjx/ui/settings/SettingsVehicleUiController.kt")
        val settingsVehicleUiControllerText = settingsVehicleUiController.readText()
        listOf(
            "eventSoundCoordinator.setEnabled(event, enabled)",
            "eventSoundCoordinator.sync(reason)",
            "vehicleEventSound = it.vehicleEventSound.merge(eventSound, syncing)",
        ).firstOrNull { guard -> guard !in settingsVehicleUiControllerText }?.let { guard ->
            violations +=
                "${settingsVehicleUiController.relativeTo(rootDir)} is missing vehicle UI guard: $guard"
        }
        val serviceQrContent =
            file("app/src/main/kotlin/com/byd/wjx/ui/settings/SettingsServiceQrContent.kt")
        val serviceQrContentText = serviceQrContent.readText()
        listOf(
            "java.net.URL",
            "java.io.File",
            "Dispatchers.IO",
            "withContext(",
            ".openStream()",
            "cacheDir",
        ).firstOrNull(serviceQrContentText::contains)?.let { forbidden ->
            violations +=
                "${serviceQrContent.relativeTo(rootDir)} performs QR I/O directly: $forbidden"
        }
        val serviceQrRepository =
            file("app/src/main/kotlin/com/byd/wjx/ui/settings/ServiceQrRepository.kt")
        val serviceQrRepositoryText = serviceQrRepository.readText()
        val serviceQrTransportPolicy =
            file("app/src/main/kotlin/com/byd/wjx/ui/settings/ServiceQrTransportPolicy.kt")
        val serviceQrSecurityText = serviceQrRepositoryText + serviceQrTransportPolicy.readText()
        listOf(
            "isAllowedServiceQrMetadataUrl",
            "isAllowedServiceQrImageUrl",
            "SERVICE_QR_METADATA_MAX_BYTES",
            "SERVICE_QR_IMAGE_MAX_BYTES",
            "serviceQrImageCacheName",
            "readLimitedBytes",
            "requireValidServiceQrImage",
        ).firstOrNull { guard -> guard !in serviceQrSecurityText }?.let { guard ->
            violations +=
                "service QR transport is missing guard: $guard"
        }
        val vehicleEventSoundCoordinator =
            file("app/src/main/kotlin/com/byd/wjx/ui/settings/VehicleEventSoundCoordinator.kt")
        val vehicleEventSoundCoordinatorText = vehicleEventSoundCoordinator.readText()
        listOf(
            "suspend fun snapshot(",
            "suspend fun setEnabled(",
            "suspend fun setPath(",
            "withContext(Dispatchers.IO)",
        ).firstOrNull { guard -> guard !in vehicleEventSoundCoordinatorText }?.let { guard ->
            violations +=
                "${vehicleEventSoundCoordinator.relativeTo(rootDir)} is missing IO boundary: $guard"
        }
        val localSoundCoordinator =
            file("app/src/main/kotlin/com/byd/wjx/data/sound/LocalSoundLibraryCoordinator.kt")
        val localSoundCoordinatorText = localSoundCoordinator.readText()
        listOf(
            "suspend fun listSounds(",
            "suspend fun importSound(",
            "suspend fun deleteSound(",
            "suspend fun moveSound(",
            "withContext(Dispatchers.IO)",
            "lockSoundSettings.clearIfPath(path)",
            "lockSoundConfigSync.sync(SYNC_REASON_DELETE)",
        ).firstOrNull { guard -> guard !in localSoundCoordinatorText }?.let { guard ->
            violations +=
                "${localSoundCoordinator.relativeTo(rootDir)} is missing local sound guard: $guard"
        }
        val soundLibrary = file("app/src/main/kotlin/com/byd/wjx/storage/SoundLibrary.kt")
        val soundLibraryText = soundLibrary.readText()
        listOf(
            "MAX_SOUND_BYTES",
            "copySoundBytes(input, output, MAX_SOUND_BYTES)",
            "if (!isManagedSoundFile(path)) return false",
        ).firstOrNull { guard -> guard !in soundLibraryText }?.let { guard ->
            violations += "${soundLibrary.relativeTo(rootDir)} is missing import guard: $guard"
        }
        listOf(
            file("app/src/main/kotlin/com/byd/wjx/ui/sound/SoundViewModel.kt"),
            vehicleEventSoundCoordinator,
        ).forEach { source ->
            listOf(
                "OpenableColumns",
                ".contentResolver",
                "soundLibrary.saveSound(",
                "soundLibrary.deleteSound(",
            ).firstOrNull(source.readText()::contains)?.let { forbidden ->
                violations +=
                "${source.relativeTo(rootDir)} bypasses LocalSoundLibraryCoordinator: $forbidden"
            }
        }
        val realtimeHailingService =
            file("app/src/main/kotlin/com/byd/wjx/audio/WjxRealtimeHailingService.kt")
        val realtimeHailingServiceText = realtimeHailingService.readText()
        listOf(
            "Channel<RealtimeHailingServiceCommand>(Channel.UNLIMITED)",
            "for (command in commands)",
            "isStartStillRequested(channel)",
            "snapshot().isStartRequestFor(channel)",
            "RealtimeHailingServiceCommandContract.fromIntent(intent)",
            "RealtimeHailingStateContract.intent(packageName, state)",
        ).firstOrNull { guard -> guard !in realtimeHailingServiceText }?.let { guard ->
            violations +=
                "${realtimeHailingService.relativeTo(rootDir)} is missing lifecycle guard: $guard"
        }
        listOf(
            "serviceActive",
            "ACTION_TOGGLE",
            "GlobalScope",
        ).firstOrNull(realtimeHailingServiceText::contains)?.let { forbidden ->
            violations +=
                "${realtimeHailingService.relativeTo(rootDir)} uses legacy hailing state: $forbidden"
        }
        val realtimeHailingEngine =
            file("app/src/main/kotlin/com/byd/wjx/audio/RealtimeHailingEngine.kt")
        val realtimeHailingEngineText = realtimeHailingEngine.readText()
        listOf(
            "RealtimeHailingAudioResourceFactory(context, channel)",
            "RealtimeHailingAudioEffects.create(",
            "stopForRouteRelease()",
            "recordThread?.join(350L)",
            "playbackThread?.join(350L)",
        ).firstOrNull { guard -> guard !in realtimeHailingEngineText }?.let { guard ->
            violations +=
                "${realtimeHailingEngine.relativeTo(rootDir)} is missing audio lifecycle guard: $guard"
        }
        listOf(
            file("app/src/main/kotlin/com/byd/wjx/ui/sound/SoundViewModel.kt"),
            file("app/src/main/kotlin/com/byd/wjx/desktopcard/DesktopQuickCardHailingController.kt"),
        ).forEach { source ->
            val sourceText = source.readText()
            if ("RealtimeHailingStateContract" !in sourceText) {
                violations +=
                    "${source.relativeTo(rootDir)} must consume the typed realtime hailing state contract"
            }
            listOf(
                "WjxRealtimeHailingService.ACTION_HAILING_STATE",
                "WjxRealtimeHailingService.STATE_STARTED",
                "WjxRealtimeHailingService.STATE_STOPPED",
                "WjxRealtimeHailingService.STATE_ERROR",
            ).firstOrNull(sourceText::contains)?.let { forbidden ->
                violations +=
                    "${source.relativeTo(rootDir)} parses legacy realtime hailing state: $forbidden"
            }
        }
        val lyricDisplayScreen =
            file("app/src/main/kotlin/com/byd/wjx/ui/lyrics/LyricDisplayScreen.kt")
        val desktopComponentsScreen =
            file("app/src/main/kotlin/com/byd/wjx/ui/desktopcomponents/DesktopComponentsScreen.kt")
        listOf(lyricDisplayScreen, desktopComponentsScreen).forEach { source ->
            listOf(
                "LyricSettings(",
                "EntryPointAccessors",
                "ActivationManager",
                "SharedPreferences",
            ).firstOrNull(source.readText()::contains)?.let { forbidden ->
                violations +=
                    "${source.relativeTo(rootDir)} bypasses LyricDisplayViewModel: $forbidden"
            }
        }
        val desktopComponentsText = desktopComponentsScreen.readText()
        listOf("DesktopComponentPreviewStage(", "DesktopComponentSettingsContainer(")
            .firstOrNull { it !in desktopComponentsText }?.let { guard ->
                violations +=
                    "${desktopComponentsScreen.relativeTo(rootDir)} is missing desktop-component boundary: $guard"
            }
        listOf("detectHorizontalDragGestures", "Brush.verticalGradient", "PreviewBadge(")
            .firstOrNull(desktopComponentsText::contains)?.let { detail ->
                violations +=
                    "${desktopComponentsScreen.relativeTo(rootDir)} owns extracted preview detail: $detail"
            }
        val quickCardSettings = file(
            "app/src/main/kotlin/com/byd/wjx/desktopcard/DesktopQuickCardSettingsContent.kt",
        )
        val quickCardSettingsText = quickCardSettings.readText()
        listOf(
            "DesktopQuickCardIconSection(",
            "DesktopQuickCardItemSection(",
            "DesktopQuickCardCommandPicker(",
            "DesktopQuickCardSoundPicker(",
        ).firstOrNull { it !in quickCardSettingsText }?.let { guard ->
            violations +=
                "${quickCardSettings.relativeTo(rootDir)} is missing quick-card settings boundary: $guard"
        }
        listOf("AlertDialog(", "Slider(", "DesktopQuickCardIconImage(", "Card(")
            .firstOrNull(quickCardSettingsText::contains)?.let { detail ->
                violations +=
                    "${quickCardSettings.relativeTo(rootDir)} owns extracted settings detail: $detail"
            }
        val lyricDisplayViewModel =
            file("app/src/main/kotlin/com/byd/wjx/ui/lyrics/LyricDisplayViewModel.kt")
        val lyricDisplayViewModelText = lyricDisplayViewModel.readText()
        listOf(
            "MutableStateFlow(readState(featureAllowed = canUseLyrics()))",
            "registerOnSharedPreferenceChangeListener(settingsListener)",
            "decideLyricModeChange(",
            "panelPreferences.setExpanded(",
        ).firstOrNull { guard -> guard !in lyricDisplayViewModelText }?.let { guard ->
            violations +=
                "${lyricDisplayViewModel.relativeTo(rootDir)} is missing state ownership guard: $guard"
        }
        val nativeParsReader = file(
            "app/src/main/kotlin/com/byd/wjx/data/nativestack/NativeParsReader.kt",
        )
        val nativeParsReaderText = nativeParsReader.readText()
        listOf(
            "NativeVehicleSnapshotParser()",
            "NativeReadPlans.full",
            "parser.parse(",
        ).firstOrNull { it !in nativeParsReaderText }?.let { guard ->
            violations +=
                "${nativeParsReader.relativeTo(rootDir)} is missing native-reader boundary: $guard"
        }
        listOf("DiParsData(", "ParamDecoder.decode", "BydSignalSemantics.select")
            .firstOrNull(nativeParsReaderText::contains)?.let { detail ->
                violations +=
                    "${nativeParsReader.relativeTo(rootDir)} owns extracted snapshot detail: $detail"
            }
        val nativeSnapshotParser = file(
            "app/src/main/kotlin/com/byd/wjx/data/nativestack/NativeVehicleSnapshotParser.kt",
        )
        listOf("fun parse(", "private fun decode(", "val data = DiParsData(")
            .firstOrNull { it !in nativeSnapshotParser.readText() }?.let { guard ->
                violations +=
                    "${nativeSnapshotParser.relativeTo(rootDir)} is missing parser guard: $guard"
            }
        val chargingDetector = file(
            "app/src/main/kotlin/com/byd/wjx/data/charging/AutoserviceChargingDetector.kt",
        )
        val chargingDetectorText = chargingDetector.readText()
        listOf(
            "CatchUpSessionRecorder(",
            "shouldRejectCatchUpCharge(",
            "resolveCatchUpEnergy(",
            "catchUpElapsedHours(",
        ).firstOrNull { it !in chargingDetectorText }?.let { guard ->
            violations +=
                "${chargingDetector.relativeTo(rootDir)} is missing charging boundary: $guard"
        }
        listOf("ChargeEntity(", "BatterySnapshotEntity(", "calculateCapacity(")
            .firstOrNull(chargingDetectorText::contains)?.let { detail ->
                violations +=
                    "${chargingDetector.relativeTo(rootDir)} owns extracted session detail: $detail"
            }
        val catchUpRecorder = file(
            "app/src/main/kotlin/com/byd/wjx/data/charging/CatchUpSessionRecorder.kt",
        )
        listOf("ChargeEntity(", "BatterySnapshotEntity(", "stateStore.save(")
            .firstOrNull { it !in catchUpRecorder.readText() }?.let { guard ->
                violations +=
                    "${catchUpRecorder.relativeTo(rootDir)} is missing session recorder guard: $guard"
            }
        val helperClientApi = file(
            "app/src/main/kotlin/com/byd/wjx/data/vehicle/HelperClient.kt",
        )
        val helperClientApiText = helperClientApi.readText()
        if ("interface HelperClient" !in helperClientApiText) {
            violations += "${helperClientApi.relativeTo(rootDir)} is missing the helper API"
        }
        listOf("Parcel", "IBinder", "transactParsed(", "ServiceManager")
            .firstOrNull(helperClientApiText::contains)?.let { detail ->
                violations +=
                    "${helperClientApi.relativeTo(rootDir)} owns Binder implementation detail: $detail"
            }
        val helperClientImpl = file(
            "app/src/main/kotlin/com/byd/wjx/data/vehicle/HelperClientImpl.kt",
        )
        val helperClientImplText = helperClientImpl.readText()
        listOf(
            "open class HelperClientImpl",
            "HelperBinderTransport(::resolveBinder)",
            "transactParsed(",
            "resolveBinder()",
        ).firstOrNull { it !in helperClientImplText }?.let { guard ->
                violations +=
                    "${helperClientImpl.relativeTo(rootDir)} is missing Binder client guard: $guard"
            }
        listOf("DeadObjectException", "Mutex()", "withTimeoutOrNull", "cached: IBinder")
            .firstOrNull(helperClientImplText::contains)?.let { detail ->
                violations +=
                    "${helperClientImpl.relativeTo(rootDir)} owns Binder transport detail: $detail"
            }
        val helperBinderTransport = file(
            "app/src/main/kotlin/com/byd/wjx/data/vehicle/HelperBinderTransport.kt",
        )
        val helperBinderTransportText = helperBinderTransport.readText()
        listOf(
            "HelperProcessGate.withOperation",
            "withTimeoutOrNull(timeoutMs)",
            "catch (error: DeadObjectException)",
            "private val readMutex = Mutex()",
            "private val commandMutex = Mutex()",
            "private var cached: IBinder?",
        ).firstOrNull { it !in helperBinderTransportText }?.let { guard ->
            violations +=
                "${helperBinderTransport.relativeTo(rootDir)} is missing Binder transport guard: $guard"
        }
        val vehicleApi = file(
            "app/src/main/kotlin/com/byd/wjx/data/vehicle/VehicleApiImpl.kt",
        )
        val vehicleApiText = vehicleApi.readText()
        listOf(
            "VehicleWriteExecutor(",
            "VehicleWriteAuditLogger(writeLogDao)",
            "VehicleSeatCommandResolver.resolve(",
            "writeExecutor.rejectUnknownCommand(commandString)",
        ).firstOrNull { it !in vehicleApiText }?.let { guard ->
            violations +=
                "${vehicleApi.relativeTo(rootDir)} is missing vehicle facade guard: $guard"
        }
        listOf(
            "VehicleWriteLogEntity",
            "android.util.Log",
            "kotlinx.coroutines.delay",
            "readBackAfterWrite(",
            "writeLogDao.insert(",
            "writeLogDao.complete(",
        ).firstOrNull(vehicleApiText::contains)?.let { detail ->
            violations +=
                "${vehicleApi.relativeTo(rootDir)} owns extracted write detail: $detail"
        }
        val vehicleWriteExecutor = file(
            "app/src/main/kotlin/com/byd/wjx/data/vehicle/VehicleWriteExecutor.kt",
        )
        val vehicleWriteExecutorText = vehicleWriteExecutor.readText()
        listOf(
            "allowlist.find(actionName)",
            "gateway.writeStatus(entry.dev, entry.writeFid, value)",
            "auditLogger.logAttempt(actionName, entry, value)",
            "private suspend fun readBackAfterWrite(",
            "VehicleWriteError.ReadbackMismatch(",
            "READBACK_RETRY_DELAYS_MS",
        ).firstOrNull { it !in vehicleWriteExecutorText }?.let { guard ->
            violations +=
                "${vehicleWriteExecutor.relativeTo(rootDir)} is missing write executor guard: $guard"
        }
        val vehicleWriteAuditLogger = file(
            "app/src/main/kotlin/com/byd/wjx/data/vehicle/VehicleWriteAuditLogger.kt",
        )
        val vehicleWriteAuditLoggerText = vehicleWriteAuditLogger.readText()
        listOf(
            "writeLogDao.insert(",
            "writeLogDao.complete(",
            "runCatching {",
            "const val ATTEMPT_STATUS = -2",
        ).firstOrNull { it !in vehicleWriteAuditLoggerText }?.let { guard ->
            violations +=
                "${vehicleWriteAuditLogger.relativeTo(rootDir)} is missing write audit guard: $guard"
        }
        val vehicleSeatResolver = file(
            "app/src/main/kotlin/com/byd/wjx/data/vehicle/VehicleSeatCommandResolver.kt",
        )
        listOf(
            "SeatGroup.DRIVER_HEAT",
            "SeatGroup.DRIVER_VENT",
            "SeatGroup.PASSENGER_HEAT",
            "SeatGroup.PASSENGER_VENT",
            "value in 2..6 -> value - 1",
        ).firstOrNull { it !in vehicleSeatResolver.readText() }?.let { guard ->
            violations +=
                "${vehicleSeatResolver.relativeTo(rootDir)} is missing seat mapping guard: $guard"
        }
        val autoDoorController = file(
            "app/src/main/kotlin/com/byd/wjx/data/vehicle/AutoDoorController.kt",
        )
        val autoDoorControllerText = autoDoorController.readText()
        listOf(
            "hardwareGateway.ensureReady(",
            "hardwareGateway.readCommandReadings(",
            "hardwareGateway.writeCommand(",
            "diagnostics.commandCompleted(",
            "autoDoorSafetyBlockReason(",
        ).firstOrNull { it !in autoDoorControllerText }?.let { guard ->
            violations +=
                "${autoDoorController.relativeTo(rootDir)} is missing auto-door boundary: $guard"
        }
        listOf(
            "HelperClient",
            "HelperBootstrap",
            "SentinelDecoder",
            "Build.",
            "SimpleDateFormat",
            ".read(AutoDoorCatalog",
            ".writeStatus(",
        ).firstOrNull(autoDoorControllerText::contains)?.let { detail ->
            violations +=
                "${autoDoorController.relativeTo(rootDir)} owns extracted auto-door detail: $detail"
        }
        val autoDoorGateway = file(
            "app/src/main/kotlin/com/byd/wjx/data/vehicle/AutoDoorHardwareGateway.kt",
        )
        listOf(
            "HelperBootstrap",
            "HelperClient",
            "SentinelDecoder::decodeInt",
            "helper.writeStatus(",
        ).firstOrNull { it !in autoDoorGateway.readText() }?.let { guard ->
            violations +=
                "${autoDoorGateway.relativeTo(rootDir)} is missing hardware gateway guard: $guard"
        }
        val autoDoorDiagnostics = file(
            "app/src/main/kotlin/com/byd/wjx/data/vehicle/AutoDoorDiagnostics.kt",
        )
        listOf("Build.MANUFACTURER", "WjxFileLogger.appendSection(", "fun groupCompleted(")
            .firstOrNull { it !in autoDoorDiagnostics.readText() }?.let { guard ->
                violations +=
                    "${autoDoorDiagnostics.relativeTo(rootDir)} is missing diagnostics guard: $guard"
            }
        val energyDataReader = file(
            "app/src/main/kotlin/com/byd/wjx/data/local/EnergyDataReader.kt",
        )
        val energyDataReaderText = energyDataReader.readText()
        listOf(
            "EnergyDataDiagnostics(currentSource(), databaseReader)",
            "databaseReader.readTripsSince(",
            "databaseReader.readTrips(",
            "availabilityCache.get(",
        ).firstOrNull { it !in energyDataReaderText }?.let { guard ->
            violations +=
                "${energyDataReader.relativeTo(rootDir)} is missing energy-data boundary: $guard"
        }
        listOf(
            "SQLiteDatabase",
            "rawQuery(",
            "FileInputStream",
            "FileOutputStream",
            "getSharedPreferences(",
            "SimpleDateFormat",
            "KNOWN_DATABASE_NAMES",
        ).firstOrNull(energyDataReaderText::contains)?.let { detail ->
            violations +=
                "${energyDataReader.relativeTo(rootDir)} owns extracted energy-data detail: $detail"
        }
        val energyDatabaseReader = file(
            "app/src/main/kotlin/com/byd/wjx/data/local/EnergyDatabaseReader.kt",
        )
        listOf("EnergyDataQueries.ALL_TRIPS", "EnergyDataQueries.TRIPS_SINCE", "readBydTripRecord()")
            .firstOrNull { it !in energyDatabaseReader.readText() }?.let { guard ->
                violations +=
                    "${energyDatabaseReader.relativeTo(rootDir)} is missing query boundary: $guard"
            }
        val energyDataSource = file(
            "app/src/main/kotlin/com/byd/wjx/data/local/EnergyDataSource.kt",
        )
        listOf("getSharedPreferences(", "EnergyDatabaseSnapshotCopier(context)", "findDatabaseFromListing(")
            .firstOrNull { it !in energyDataSource.readText() }?.let { guard ->
                violations +=
                    "${energyDataSource.relativeTo(rootDir)} is missing source boundary: $guard"
            }
        val energySnapshotCopier = file(
            "app/src/main/kotlin/com/byd/wjx/data/local/EnergyDatabaseSnapshotCopier.kt",
        )
        listOf("FileInputStream(", "output.fd.sync()", "SourceFingerprint.capture", "ATOMIC_MOVE")
            .firstOrNull { it !in energySnapshotCopier.readText() }?.let { guard ->
                violations +=
                    "${energySnapshotCopier.relativeTo(rootDir)} is missing snapshot guard: $guard"
            }
        val adbProtocolClient = file(
            "app/src/main/kotlin/com/byd/wjx/data/autoservice/AdbProtocolClient.kt",
        )
        val adbProtocolClientText = adbProtocolClient.readText()
        listOf("AdbSocketTransport(", "AdbAuth.signToken(", "AdbAuth.serializePublicKey(", "AdbWire.A_OPEN")
            .firstOrNull { it !in adbProtocolClientText }?.let { guard ->
                violations +=
                    "${adbProtocolClient.relativeTo(rootDir)} is missing ADB protocol boundary: $guard"
            }
        listOf("java.net.Socket", "ByteBuffer", "Base64", "Signature", "RSAPublicKey", "InputStream")
            .firstOrNull(adbProtocolClientText::contains)?.let { detail ->
                violations +=
                    "${adbProtocolClient.relativeTo(rootDir)} owns extracted ADB detail: $detail"
            }
        val adbOnDeviceClient = file(
            "app/src/main/kotlin/com/byd/wjx/data/autoservice/AdbOnDeviceClientImpl.kt",
        )
        val adbOnDeviceClientText = adbOnDeviceClient.readText()
        listOf("AdbConnectionManager(", "AdbShellCommands.getProperty(", "AdbShellCommands.spawnHelper(")
            .firstOrNull { it !in adbOnDeviceClientText }?.let { guard ->
                violations +=
                    "${adbOnDeviceClient.relativeTo(rootDir)} is missing on-device ADB boundary: $guard"
            }
        listOf("Mutex(", "protocol: AdbProtocol", "fun shellQuote(", "fun helperStartScript(", "Regex(")
            .firstOrNull(adbOnDeviceClientText::contains)?.let { detail ->
                violations +=
                    "${adbOnDeviceClient.relativeTo(rootDir)} owns extracted ADB coordination detail: $detail"
            }
        val adbPublicApi = file(
            "app/src/main/kotlin/com/byd/wjx/data/autoservice/AdbOnDeviceClient.kt",
        )
        if ("suspend fun exec(" in adbPublicApi.readText()) {
            violations += "${adbPublicApi.relativeTo(rootDir)} exposes a generic shell command API"
        }
        val historyImporter = file(
            "app/src/main/kotlin/com/byd/wjx/data/local/HistoryImporter.kt",
        )
        val historyImporterText = historyImporter.readText()
        listOf(
            "EnergyHistoryImporter(",
            "DiPlusHistoryImporter(",
            "HistoryMaintenanceCoordinator(",
            "energyDataReader.markSourceProcessed(context)",
            "diPlusDbReader.markSourceProcessed()",
        ).firstOrNull { it !in historyImporterText }?.let { guard ->
            violations +=
                "${historyImporter.relativeTo(rootDir)} is missing history-import boundary: $guard"
        }
        listOf(
            "TripEntity(",
            "IdleDrainEntity(",
            "getByStartTsRange(",
            "insertTrip(",
            "getTripsWithoutCost(",
            "detachOrphanPoints(",
            ".first()",
        ).firstOrNull(historyImporterText::contains)?.let { detail ->
            violations +=
                "${historyImporter.relativeTo(rootDir)} owns extracted history detail: $detail"
        }
        val energyHistoryImporter = file(
            "app/src/main/kotlin/com/byd/wjx/data/local/EnergyHistoryImporter.kt",
        )
        listOf("mapEnergyTrip(", "mergeEnergyRecord(", "setLastEnergyImportTs(")
            .firstOrNull { it !in energyHistoryImporter.readText() }?.let { guard ->
                violations +=
                    "${energyHistoryImporter.relativeTo(rootDir)} is missing energy import guard: $guard"
            }
        val historyModels = file(
            "app/src/main/kotlin/com/byd/wjx/data/local/HistoryImportModels.kt",
        )
        listOf("fun isHistoryTripWithinSession(", "fun mapEnergyTrip(", "fun mergeEnergyRecord(")
            .firstOrNull { it !in historyModels.readText() }?.let { guard ->
                violations +=
                    "${historyModels.relativeTo(rootDir)} is missing history mapping guard: $guard"
            }
        val dashboardViewModel =
            file("app/src/main/kotlin/com/byd/wjx/ui/dashboard/DashboardViewModel.kt")
        val dashboardViewModelText = dashboardViewModel.readText()
        listOf(
            "DashboardLiveStateMapper.map(",
            "DashboardTimeRanges.forPeriod(",
            "DashboardTimeRanges.today(",
        ).firstOrNull { it !in dashboardViewModelText }?.let { guard ->
            violations +=
                "${dashboardViewModel.relativeTo(rootDir)} is missing dashboard boundary: $guard"
        }
        listOf("data class DashboardUiState", "enum class DashboardPeriod", "Calendar.")
            .firstOrNull(dashboardViewModelText::contains)?.let { detail ->
                violations +=
                    "${dashboardViewModel.relativeTo(rootDir)} owns extracted dashboard detail: $detail"
            }
        val chargesViewModel =
            file("app/src/main/kotlin/com/byd/wjx/ui/charges/ChargesViewModel.kt")
        val chargesViewModelText = chargesViewModel.readText()
        listOf("ChargesTimeRanges.forPeriod(", "groupBuilder.build(")
            .firstOrNull { it !in chargesViewModelText }?.let { guard ->
                violations +=
                    "${chargesViewModel.relativeTo(rootDir)} is missing charges boundary: $guard"
            }
        listOf("Context", "SimpleDateFormat", "data class ChargesUiState", "enum class ChargesPeriod")
            .firstOrNull(chargesViewModelText::contains)?.let { detail ->
                violations +=
                    "${chargesViewModel.relativeTo(rootDir)} owns extracted charges detail: $detail"
            }
        val lyricRepository = file("app/src/main/kotlin/com/byd/wjx/lyrics/LyricRepository.kt")
        val lyricRepositoryText = lyricRepository.readText()
        listOf("LocalLyricFileSource()", "OnlineLyricSource(httpClient)", "LyricParsers.")
            .firstOrNull { it !in lyricRepositoryText }?.let { guard ->
                violations +=
                    "${lyricRepository.relativeTo(rootDir)} is missing lyric boundary: $guard"
            }
        listOf("Request.Builder", "JSONObject", "JSONArray", "Base64", "java.io.File", "android.net.Uri")
            .firstOrNull(lyricRepositoryText::contains)?.let { detail ->
                violations +=
                    "${lyricRepository.relativeTo(rootDir)} owns extracted lyric detail: $detail"
            }
        val musicPlaybackModels =
            file("app/src/main/kotlin/com/byd/wjx/lyrics/MusicPlaybackModels.kt")
        val musicPlaybackModelsText = musicPlaybackModels.readText()
        listOf(
            "data class MusicPlaybackSnapshot(",
            "enum class MusicPlaybackSource",
            "MusicIdentity.trackKey(",
            "fun projectedPositionMs(",
        ).firstOrNull { it !in musicPlaybackModelsText }?.let { guard ->
            violations +=
                "${musicPlaybackModels.relativeTo(rootDir)} is missing playback model guard: $guard"
        }
        listOf("MutableStateFlow", "SUPPORTED", "packageNames: Set<String>")
            .firstOrNull(musicPlaybackModelsText::contains)?.let { detail ->
                violations +=
                    "${musicPlaybackModels.relativeTo(rootDir)} owns extracted playback detail: $detail"
            }
        val musicIdentity = file("app/src/main/kotlin/com/byd/wjx/lyrics/MusicIdentity.kt")
        val musicIdentityText = musicIdentity.readText()
        listOf(
            "fun isSameSong(",
            "fun isSameTrack(",
            "DURATION_IDENTITY_TOLERANCE_MS",
            "placeholderTitles",
        ).firstOrNull { it !in musicIdentityText }?.let { guard ->
            violations +=
                "${musicIdentity.relativeTo(rootDir)} is missing music identity guard: $guard"
        }
        val musicPlaybackStore =
            file("app/src/main/kotlin/com/byd/wjx/lyrics/MusicPlaybackStore.kt")
        val musicPlaybackStoreText = musicPlaybackStore.readText()
        listOf(
            "private val _state = MutableStateFlow(MusicPlaybackSnapshot.EMPTY)",
            "snapshot.source == MusicPlaybackSource.AdbFallback",
            "snapshot.mergeMissingFrom(previous)",
            "PRIMARY_SOURCE_FRESH_MS",
        ).firstOrNull { it !in musicPlaybackStoreText }?.let { guard ->
            violations +=
                "${musicPlaybackStore.relativeTo(rootDir)} is missing playback store guard: $guard"
        }
        val supportedMusicPackages =
            file("app/src/main/kotlin/com/byd/wjx/lyrics/SupportedMusicPackages.kt")
        val supportedMusicPackagesText = supportedMusicPackages.readText()
        listOf(
            "object SupportedMusicPackages",
            "internal object QishuiMusic",
            "fun extractTrackId(vararg candidates: String?)",
            "com.luna.music.car",
        ).firstOrNull { it !in supportedMusicPackagesText }?.let { guard ->
            violations +=
                "${supportedMusicPackages.relativeTo(rootDir)} is missing package catalog guard: $guard"
        }
        val homeDetailScreens =
            file("app/src/main/kotlin/com/byd/wjx/ui/home/HomeDetailScreens.kt")
        val homeDetailScreensText = homeDetailScreens.readText()
        listOf("HomeDetailHeader(", "HomeDetailSection(", "HomeDetailMetricGrid(", "BatteryHealthSummary(")
            .firstOrNull { it !in homeDetailScreensText }?.let { guard ->
                violations +=
                    "${homeDetailScreens.relativeTo(rootDir)} is missing detail-screen boundary: $guard"
            }
        listOf("Card(", "IconButton(", "private data class HomeDetailMetric")
            .firstOrNull(homeDetailScreensText::contains)?.let { detail ->
                violations +=
                    "${homeDetailScreens.relativeTo(rootDir)} owns extracted detail component: $detail"
            }
        val proHomeHero = file("app/src/main/kotlin/com/byd/wjx/ui/home/ProHomeHero.kt")
        val proHomeHeroText = proHomeHero.readText()
        listOf("SocGauge(", "SpeedGauge(", "VehicleStage(")
            .firstOrNull { it !in proHomeHeroText }?.let { guard ->
                violations += "${proHomeHero.relativeTo(rootDir)} is missing hero boundary: $guard"
            }
        listOf("Canvas(", "pointerInput(", "PowertrainSpeedStrip(", "TireBadge(")
            .firstOrNull(proHomeHeroText::contains)?.let { detail ->
                violations += "${proHomeHero.relativeTo(rootDir)} owns extracted hero detail: $detail"
            }
        val lyricRuntime = file("app/src/main/kotlin/com/byd/wjx/lyrics/LyricRuntime.kt")
        val lyricRuntimeText = lyricRuntime.readText()
        listOf(
            "ensurePermissionsWithRetry()",
            "LyricPermissionRetryPolicy.delayAfterFailure(failedAttempts)",
            "resolveLyricOverlayTargets(",
            "val overlayPermissionGranted = Settings.canDrawOverlays(appContext)",
        ).firstOrNull { guard -> guard !in lyricRuntimeText }?.let { guard ->
            violations +=
                "${lyricRuntime.relativeTo(rootDir)} is missing lyric runtime guard: $guard"
        }
        val ledSignalSampling =
            file("app/src/main/kotlin/com/byd/wjx/led/LedVehicleSignalSampling.kt")
        val ledSignalSamplingText = ledSignalSampling.readText()
        listOf(
            "class LedVehicleSignalAccumulator",
            "fun updateCore(data: DiParsData)",
            "fun update(tier: LedSignalTier, data: DiParsData, sampledAt: Long)",
            "fun snapshot(now: Long): LedVehicleSignals",
            "const val MOTION_FRESH_MS = 350L",
            "const val BRAKE_FRESH_MS = 700L",
            "const val BODY_FRESH_MS = 1_500L",
            "const val ADAS_FRESH_MS = 2_500L",
            "delay((intervalMs - spent).coerceAtLeast(MIN_LOOP_DELAY_MS))",
        ).firstOrNull { guard -> guard !in ledSignalSamplingText }?.let { guard ->
            violations +=
                "${ledSignalSampling.relativeTo(rootDir)} is missing LED sampling guard: $guard"
        }
        listOf(
            file("app/src/main/kotlin/com/byd/wjx/led/ipixelfa/IpixelFaRuntimeCoordinator.kt"),
            file("app/src/main/kotlin/com/byd/wjx/led/mafu/MafuRuntimeCoordinator.kt"),
        ).forEach { source ->
            val sourceText = source.readText()
            listOf(
                "LedVehicleSignalAccumulator()",
                "runLedSignalLoop(",
                "signalAccumulator.snapshot(now)",
                "signalAccumulator.reset()",
            ).firstOrNull { guard -> guard !in sourceText }?.let { guard ->
                violations +=
                    "${source.relativeTo(rootDir)} is missing shared LED sampling guard: $guard"
            }
            listOf(
                "data class TimedSignals",
                "data class SignalLogKey",
                "fun LedVehicleSignals.mergeMotion",
                "fun freshSignals(",
            ).firstOrNull(sourceText::contains)?.let { duplicate ->
                violations +=
                    "${source.relativeTo(rootDir)} duplicates LED sampling logic: $duplicate"
            }
        }
        val maintenanceUiStateText =
            file("app/src/main/kotlin/com/byd/wjx/ui/settings/SettingsUiState.kt").readText()
        listOf(
            "val lockSound:",
            "val lockSoundChoices:",
            "val lockSoundSyncing:",
            "val lockSoundStatus:",
        ).firstOrNull(maintenanceUiStateText::contains)?.let { field ->
            violations += "SettingsUiState contains legacy vehicle-event sound field $field"
        }
        listOf(
            "updateStatus",
            "chainLog",
        ).firstOrNull(maintenanceUiStateText::contains)?.let { field ->
            violations +=
                "SettingsUiState contains removed dead maintenance field $field"
        }
        val legacyPaymentStateFields = listOf(
            "activationPaymentOrder",
            "activationPaymentLoading",
            "activationPaymentMessage",
        )
        mainSources.files.forEach { source ->
            val sourceText = source.readText()
            legacyPaymentStateFields.firstOrNull(sourceText::contains)?.let { field ->
                violations +=
                    "${source.relativeTo(rootDir)} uses legacy payment state field $field"
            }
        }

        val paymentWebViewOwner =
            file("app/src/main/kotlin/com/byd/wjx/ui/settings/PaymentCheckoutContent.kt")
        val paymentWebViewText = paymentWebViewOwner.readText()
        val requiredPaymentWebViewGuards = listOf(
            "allowFileAccess = false",
            "allowContentAccess = false",
            "mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW",
            "javaScriptCanOpenWindowsAutomatically = false",
            "setSupportMultipleWindows(false)",
            "setAcceptThirdPartyCookies(webView, false)",
            "handler.cancel()",
            "callback.backToSafety(true)",
        )
        requiredPaymentWebViewGuards.forEach { guard ->
            if (guard !in paymentWebViewText) {
                violations += "${paymentWebViewOwner.relativeTo(rootDir)} is missing payment guard: $guard"
            }
        }
        if ("addJavascriptInterface" in paymentWebViewText) {
            violations += "${paymentWebViewOwner.relativeTo(rootDir)} exposes a JavaScript bridge"
        }
        fileTree("app/src/main/kotlin/com/byd/wjx/ui/settings") { include("**/*.kt") }
            .files
            .filterNot { it == paymentWebViewOwner }
            .forEach { source ->
                if ("javaScriptEnabled = true" in source.readText()) {
                    violations +=
                        "${source.relativeTo(rootDir)} enables JavaScript outside the payment WebView owner"
                }
            }

        val gifAssets = fileTree("app/src/main/res") { include("**/*.gif") }.files
        val expectedFaPresetNames = (1..20)
            .map { number -> "ipixel_fa_preset_${number.toString().padStart(2, '0')}.gif" }
            .toSet()
        val faPresetAssets = gifAssets.filter { asset ->
            asset.parentFile.name == "drawable-nodpi" && asset.name in expectedFaPresetNames
        }
        val actualFaPresetNames = faPresetAssets.map(File::getName).toSet()
        if (actualFaPresetNames != expectedFaPresetNames) {
            violations +=
                "FA preset GIF set mismatch: expected=$expectedFaPresetNames actual=$actualFaPresetNames"
        }
        gifAssets.filterNot(faPresetAssets::contains).forEach { asset ->
            violations += "${asset.relativeTo(rootDir)} is an unapproved GIF resource"
        }
        faPresetAssets.forEach { asset ->
            val image = javax.imageio.ImageIO.read(asset)
            if (image == null || image.width != 96 || image.height != 16) {
                violations +=
                    "${asset.relativeTo(rootDir)} must be a readable 96x16 FA preset preview"
            }
        }
        val faPresetBytes = faPresetAssets.sumOf(File::length)
        if (faPresetBytes > 900_000L) {
            violations += "FA preset GIF resources use $faPresetBytes bytes; limit is 900000"
        }

        if (violations.isNotEmpty()) {
            throw GradleException(violations.joinToString(prefix = "Source health check failed:\n", separator = "\n"))
        }
    }
}
