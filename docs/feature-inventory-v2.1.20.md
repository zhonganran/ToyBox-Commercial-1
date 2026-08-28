# v2.1.20 功能清单与整理边界

> 基线：`ed3c2d0c57169edb46806b19b39d1853b4fb7bfc`（`2.1.20`）。
>
> 本清单用于逐项确认功能去留。当前项目按“全新安装、不兼容旧版本数据”处理；在用户确认前，不删除任何用户可见功能，也不删除可能被多个功能共享的底层能力。

## 1. 结论先行

当前项目不是“完全不可救”的屎山，但已经明显超过了一个轻量工具应有的复杂度：

- **功能面过宽**：车辆遥测、行程/充电、音效、歌词、悬浮卡片、仪表投射、蓝牙 LED、自动化和商业授权同时存在。
- **边界已经开始形成**：最近的导航、设置、后台运行时拆分已经降低了继续整理的风险。
- **主要问题不在单个类，而在产品边界**：多个独立产品级能力共用同一套后台服务、权限、偏好备份、车辆信号和授权判断。
- **本轮已完成产品减法**：手机投屏、A950 LED 协议、电子后视镜、在线音效库、常用地点和前照灯模式控制已彻底删除；仪表卡片不再跟随系统夜间模式切换字体颜色。
- **新安装行为已明确**：智能联动不再自动创建预设；“我的音效”只读取 WJX 目录并保留左滑确认删除；永久授权由服务器按 VIN 直接恢复。
- **暂时不能凭感觉删除**：车辆数据首页、行程/充电、基础本地音效、仪表/桌面组件、智能联动、授权激活。这些构成当前产品主线，且被多个功能复用。

## 2. 状态定义

| 状态 | 含义 |
| --- | --- |
| `核心保留` | 当前产品主线，除非用户明确否决，否则继续整理和稳定化。 |
| `待确认` | 有明确入口和实现，但需要确认真实使用价值、车型覆盖或商业定位。 |
| `删除候选` | 边界独立、收益较低或硬件/平台依赖明显，适合优先做删除评审。 |
| `基础设施` | 不直接作为产品功能展示，但被其他功能共享；必须等依赖清空后再删。 |
| `后置清理` | 全新安装前提下可统一移除的旧迁移、旧备份或兼容逻辑。 |

## 3. 用户功能清单

| 编号 | 功能 | 用户入口 | 主要 UI | 核心实现 | 组件/权限/依赖 | 数据、后台与影响 | 初始状态 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| F01 | 应用启动、安全声明、自启动与后台保活 | 首次启动；系统启动；设置引导 | `ui/navigation/AppNavigation.kt`、`ui/legal/DisclaimerScreen.kt` | `MainActivity`、`KeepAliveService`、`BootReceiver`、`ProcessRevivalReceiver`、`KeepAliveAccessibilityService` | `RECEIVE_BOOT_COMPLETED`、前台服务、无障碍、自启动/电池优化 | 影响全局后台能力；不能在删除单项功能时直接移除 | 核心保留 |
| F02 | 授权激活、VIN 绑定、试用、支付与服务二维码 | 设置 → 授权激活；服务与授权 | `SettingsAuthorizationSection.kt`、`SettingsServiceAuthorizationSection.kt` | `activation/ActivationManager.kt`、`ServiceQrRepository.kt`、`SettingsMaintenanceCoordinator.kt` | 网络、车辆 VIN、真实车型系统属性、ZXing；`ACTIVATION_API_URL`、`SERVICE_QR_API_URL` | 激活码或支付首次绑定 VIN；重装、清数据或恢复车机后由服务器按 VIN 直接恢复，不使用设备令牌或离线许可证；服务器保存车辆主动上报的车型配置供后台查看 | 核心保留 |
| F03 | 首页实时车辆数据与车辆图片 | 首页 | `ui/home/ProHomeScreen.kt`、`ProHomeHero.kt`、`ProHomePanels.kt` | `DashboardViewModel`、`VehicleStateRepository`、`SharedAdaptiveLoop`、`VehicleGateway` | BYD 权限、Helper Binder、车辆信号 | 共享车辆快照；首页、卡片、自动化和多个详情页依赖 | 核心保留 |
| F04 | 能源、电池健康、车身环境、车机系统详情 | 首页分析卡片 | `HomeOperationalScreens.kt`、`HomeDetailScreens.kt`、`DashboardDetails.kt` | `BatteryHealthRepository`、`SystemMetricsReader`、`BatteryStateRepository`、车辆读取层 | BYD 读取权限、Helper/Native Pars | 使用 `BatterySnapshotEntity`、`IdleDrainEntity` 等；删除需区分单页与底层遥测 | 核心保留 |
| F05 | 行程记录、能耗分析与 GPS 轨迹 | 首页 → 行程与轨迹 | `ui/map/MapScreen.kt`、`MapViewModel.kt` | `TripTracker`、`TripRecorder`、`DriveSessionCoordinator`、`HistoryImporter` | 定位、后台定位、osmdroid、高德瓦片 | `TripEntity`、`TripPointEntity`、`OdometerSampleEntity`；行程记录、能耗统计和 GPS 轨迹均为核心能力 | 核心保留 |
| F06 | 充电实时状态、充电记录与统计 | 首页 → 充电记录 | `ui/charges/ChargesScreen.kt`、`ChargesViewModel.kt` | `AutoserviceChargingDetector`、`ChargingCatchUpCoordinator`、`ChargeRepository` | 车辆充电信号、ADB/Autoservice、WorkManager/后台轮询 | `ChargeEntity`；后台服务和首页统计依赖 | 核心保留 |
| F07 | 常用地点管理 | 已删除 | 已删除 | 已删除 | 已删除 | 已移除地点页面、地点 Room 表、地点仓库、地点触发器和相关自动化编辑控件；GPS 行程轨迹保留 | 已删除 |
| F08 | 本地音效库与车外播放 | 个性音效 → 我的音效；桌面卡片；车辆事件音效 | `ui/sound/SoundLocalTab.kt`、`SoundScreen.kt` | `SoundLibrary`、`WjxOutsidePlayService`、Helper 音频路由 | 存储读写、音频、BYD 音频权限、前台媒体服务 | “我的音效”不提供导入入口，只读取本地存储根目录 `/storage/emulated/0/WJX`；保留排序、试听、左滑删除与确认弹窗；车辆事件音效仍可独立导入/删除 | 核心保留 |
| F09 | 在线音效库与下载 | 已删除 | 已删除 | 已删除 | 已删除 | 已移除在线 Tab、远程仓库、下载缓存、客户端配置、服务端音效表、管理页和 API | 已删除 |
| F10 | 实时喊话 | 个性音效麦克风；方向盘快捷键 | `SoundScreen.kt`、`SettingsShortcutSection.kt` | `WjxRealtimeHailingService`、`RealtimeHailingEngine`、`ExteriorHailingSettings` | `RECORD_AUDIO`、麦克风前台服务、音频路由 | 与本地车外播放共用音频通道；属于确认保留的核心音频能力，后续整理不得删除其服务、权限、快捷键和自动化调用 | 核心保留 |
| F11 | 桌面歌词与仪表盘歌词 | 桌面组件 → 歌词组件 | `ui/lyrics/LyricDisplayScreen.kt` | `LyricRuntime`、`MusicNotificationListenerService`、歌词 overlay/controller | 通知访问、悬浮窗、`SYSTEM_ALERT_WINDOW` | `lyrics`、`lyric_display_controls` 偏好；仪表歌词依赖投射/悬浮层 | 核心保留 |
| F12 | 桌面卡片悬浮窗 | 桌面组件 → 桌面卡片 | `DesktopQuickCardSettingsContent.kt` | `DesktopQuickCardRuntime`、`DesktopQuickCardController` | 悬浮窗、车辆写入、音频 | `wjx_desktop_cards` 偏好；依赖 F03/F08 和车辆控制白名单；与 F10/F11/F13 共享入口和运行时 | 核心保留 |
| F13 | 仪表卡片与仪表卡片 2 | 桌面组件 → 仪表卡片 | `clustercard/ClusterCardSettingsContent.kt` | `ClusterCardRuntime`、`ClusterCardRepository` | 仪表投射、悬浮窗/媒体投射链路 | `wjx_cluster_card` 偏好；两张卡片独立配置指标、尺寸、位置和布局；字体跟随车机昼夜模式，白天黑字、夜晚白字 | 核心保留 |
| F14 | 方向盘快捷键 | 设置 → 按键/显示/车外通道 | `SettingsShortcutSection.kt` | `SteeringWheelKeyService`、`SteeringWheelRuntimeCoordinator`、`KeyShortcutSettings` | 无障碍服务、事件监听、音频/车辆写入 | `key_shortcuts` 偏好；当前动作范围为本地音效和实时喊话；与 F15 共用按键服务但生命周期可独立 | 核心保留 |
| F15 | 仪表盘投射/镜像 | 设置 → 按键/显示/车外通道 | `SettingsDisplaySection.kt`、`ClusterMirrorConsentActivity` | `ClusterMirrorService`、`ClusterProjectionManager`、`ClusterEntryPoint` | `FOREGROUND_SERVICE_MEDIA_PROJECTION`、媒体投射授权、悬浮窗 | `cluster_projection` 偏好；F11/F13 依赖；保留整屏镜像、投射入口和底层投射生命周期 | 核心保留 |
| F16 | 车辆控制 | 桌面卡片；智能联动 | `DesktopQuickCardSettingsContent.kt`、自动化动作编辑器 | `VehicleApi`、`VehicleGateway`、`WriteAllowlist` | BYD BODYWORK 权限；`vehicle-actions.json` | 自动化动作和桌面卡片共用；前照灯模式控制已删除，车辆状态只读展示不受影响 | 核心保留 |
| F17 | 智能联动规则 | 智能联动 | `ui/automation/*` | `AutomationEngine`、`AutomationTriggerEvaluator`、`ActionDispatcher`、`RoomAutomationConfigRepository` | 后台车辆快照、定位、网络、精确闹钟、媒体服务 | 新安装从空规则列表开始，不自动创建四条预设；`RuleEntity`、`RuleLogEntity` 可调用 F05/F08/F10/F12/F16/F18 | 核心保留 |
| F18 | LED 蓝牙屏联动 | 设置 → LED 联动；智能联动动作 | `LedLinkSettingsSection.kt`、`MafuSettingsPane.kt`、`LedLinkageRulesEditor.kt` | `led/ipixelfa/*`、`led/mafu/*` | 蓝牙扫描/连接、两套私有协议；FA 标准与马夫 LED 均为确认保留的核心硬件能力 | `wjx_led_ipixel_fa`、`wjx_led_mafu`；后续只整理生命周期、协议边界和自动化耦合，不删除任一协议 | 核心保留 |
| F19 | A950 LED 协议及自定义 GIF | 已删除 | 已删除 | 已删除 | 已删除 | 已移除 A950 协议、运行时、偏好、测试和 20 个 GIF 资源 | 已删除 |
| F20 | 电子后视镜/AVM 摄像头画中画 | 已删除 | 已删除 | 已删除 | 已移除 `CAMERA` 权限和摄像头硬件声明；保留歌词使用的前台摄像头状态监测 | 已移除电子后视镜包、设置、运行时、测试和专属 Helper Binder 操作 | 已删除 |
| F21 | 手机投屏/RTSP 播放 | 已删除 | 已删除 | 已删除 | 已移除 Media3 RTSP 依赖和 Manifest Activity | 已移除 `phone_cast` 偏好、设置入口和专属测试；仪表盘投射保留 | 已删除 |
| F22 | 车辆事件音效 | 设置 → 车辆事件音效 | `SettingsVehicleSoundSection.kt` | `VehicleEventSoundCoordinator`、`HelperLockSoundRuntime`、`SentryInformReceiver` | 音频、BYD 车身、后台 receiver | `lock_sound` 偏好；保留锁车/解锁、哨兵等事件音效；依赖本地音效和后台保活 | 核心保留 |
| F23 | 车辆容量、价格、能耗阈值 | 设置 → 车辆费用与能耗 | `SettingsVehicleSection.kt` | `SettingsViewModel`、`SettingsRepository`、计算器/分析引擎 | Room/SharedPreferences | 影响 F04/F05/F06 首页统计；独立于已删除的常用地点 | 核心保留 |
| F24 | 主题、壁纸、使用说明、法律声明 | 设置 → 数据与更新；首次启动 | `SettingsAppSection.kt`、`UsageGuideScreen.kt`、`DisclaimerScreen.kt` | `SettingsRepository`、主题与壁纸、说明目录 | 本地资源、偏好 | 删除任意功能时必须同步更新使用说明、权限说明和声明 | 核心保留 |
| F25 | 备份与恢复 | 设置 → 数据与更新 | `SettingsAppSection.kt`、`BackupManager.kt` | `BackupManager`、`BackupPreferencesRegistry` | 存储、Room 数据库、ZIP | 保留当前版本有效配置与数据库备份；仅恢复精确匹配的当前 schema 和备份格式，不兼容旧版本迁移 | 核心保留 |
| F26 | 自动更新与日志上传 | 设置 → 数据与更新；启动自动检查 | `UpdateDialog.kt`、`SettingsLogUploader.kt` | `UpdateChecker`、`SettingsMaintenanceCoordinator` | 网络、未知来源安装、文件读取；更新/日志 API | 保留更新检查、安装流程、日志上传和服务端接口；与车辆核心运行时解耦 | 核心保留 |

## 4. 共享基础设施清单

这些不是应该直接展示给用户的功能，但多个功能依赖它们。删除功能时必须先确认引用已经归零。

| 基础设施 | 主要文件/包 | 当前服务对象 | 整理原则 |
| --- | --- | --- | --- |
| 车辆读取与快照 | `data/nativestack/*`、`data/vehicle/*`、`SharedAdaptiveLoop` | 首页、行程、充电、自动化、卡片、LED 联动 | 保留稳定接口，禁止 UI 直接接触 Helper/ADB/VehicleGateway。 |
| 后台运行时 | `TrackingService`、`TrackingFeatureRuntimes`、`KeepAliveService` | 车辆读取、歌词、卡片、投射、LED、摄像头 | 删除功能时同步移除 runtime 注册、启动条件和 stop 清理。 |
| 音频通道 | `audio/*`、`helper/*` | 本地音效、喊话、事件音效、自动化 | 先区分车内/车外/麦克风所有权，再决定删除。 |
| Room 数据库 | `data/local/database`、`entity`、`dao` | 行程、充电、规则、地点、设置、采样 | 功能删除后再删实体、DAO、Repository 和 schema。 |
| 偏好与备份 | `BackupPreferencesRegistry`、各功能 Preferences | 几乎所有设置 | 每删一个功能，移除 owner、文件登记和备份测试。 |
| ADB 身份 | `AdbKeyStore`、`AdbIdentityBackupStore`、`AdbConnectionManager` | Helper 启动、系统能力自动授权 | 原始 RSA 私钥只保存在应用内部；认证后仅将 AES-GCM 加密、设备与正式签名绑定的备份写入系统设置，重装时先恢复再连接。恢复出厂、系统清理或签名变化后安全降级为重新授权。 |
| 授权能力 | `ActivationManager`、`PremiumFeature` | LED、投射、车辆控制、商业服务 | 永久授权唯一恢复键为 VIN；客户端不保存设备令牌和离线许可证，服务端启用的 VIN 授权记录为准。 |
| 服务端平台 | `server/platform/app.py`、`platform_license.py`、`platform_payment.py` | 激活、支付、二维码、更新、日志 | 客户端功能删完后再删端点、后台页面和安全测试。 |

## 5. 当前可确认的整理顺序

### 第一阶段：已完成的独立删除

手机投屏、A950 LED 协议和电子后视镜已经完成完整删除：UI 入口、源码、Manifest 组件、专属权限、Gradle 依赖、资源、偏好登记和测试均已同步清理。

仪表卡片不再提供手动字体颜色设置，字体颜色跟随车机 `sys_day_or_night` 自动切换为白天黑字、夜晚白字；仪表卡片、仪表投射和歌词的其他功能保持不变。

### F21 手机投屏删除记录

手机投屏原本是一个相对干净的独立功能块，已按确认结果移除：

- **已删除源码**：`ui/settings/PhoneCastSettingsCard.kt`、`phonecast/*`。
- **已删除组件与依赖**：`.phonecast.PhoneCastActivity`、Media3 ExoPlayer/RTSP/UI 三项依赖。
- **已删除数据登记**：`phone_cast` 偏好和备份注册、专属测试。
- **保留边界**：`SettingsDisplaySection` 中的仪表盘投射/镜像设置和 `mediaProjection` 权限仍属于其他功能。

### F09 在线音效库删除记录

在线音效库已按全新安装策略完整移除：

- **客户端**：删除在线标签页、搜索/分类/试听/下载状态、远程仓库、下载缓存和 `SOUND_API_BASE_URL`。
- **数据与备份**：删除 `wjx_sound_downloads` 偏好登记及对应测试；本地 `sound_library` 保留。
- **服务端**：删除音效管理页、音效上传目录、`sounds` 表、在线音效配置项、列表/分类/下载/试听 API 和安全测试。
- **保留边界**：“我的音效”继续保留 WJX 目录读取、排序、试听、左滑确认删除和车外播放；车辆事件音效保留自己的导入/删除；智能联动继续使用本地音效库。

### 第二阶段：核心能力稳定化

当前用户功能去留已经全部确认，后续工作转入核心能力稳定化、模块边界整理和真机验证。

### F05/F07 当前评估结论

- **F05 全部保留**：`TripEntity`、`TripRecorder`、`DriveSessionCoordinator`、`TripTracker`、`TripPointEntity`、地图页面、osmdroid 和 `HistoryImporter.attachGpsPoints()` 都属于当前行程与能耗产品能力，后续只做稳定性和模块边界整理。
- **GPS 不再作为删除候选**：`TrackingService` 当前把 `TripTracker.state` 作为驾驶会话的辅助输入，任何解耦都必须以保留完整行程记录、能耗统计和轨迹能力为前提。
- **F07 已完成删除**：常用地点设置、`PlaceEntity`、地点 DAO/仓库、地点几何判断、`place_enter` / `place_exit` 触发器及自动化编辑控件均已移除；导航动作改为直接填写经纬度。

### 第三阶段：核心能力稳定化

1. 首页车辆快照与详情
2. 充电记录
3. 本地音效和车外播放
4. 智能联动
5. 授权激活与商业后台

## 6. 每次删除的验收清单

- [ ] 用户入口和导航目的地已删除。
- [ ] UI、ViewModel、Repository、Runtime、Service、Receiver、Activity 已删除或不再被引用。
- [ ] Manifest 组件和相关权限已删除。
- [ ] 专属 Gradle 依赖已删除。
- [ ] 专属资源、assets、默认数据已删除。
- [ ] SharedPreferences owner、`BackupPreferencesRegistry` 和备份测试已清理。
- [ ] Room Entity、DAO、Repository、迁移和 schema 已清理（若该功能使用数据库）。
- [ ] 智能联动动作/触发器/快捷键绑定已清理（若该功能被调用）。
- [ ] 服务端 API、后台页面、上传目录和安全测试已清理（若该功能有后台）。
- [ ] 使用说明、权限文案、更新日志和 FAQ 已同步更新。
- [ ] 单元测试、Lint、Debug/Release 构建和 `verifySourceHealth` 通过。
- [ ] 真机验证删除后的后台启动、车辆读取和剩余功能没有回归。

## 7. 全新安装前的后置清理

本轮已完成 Room 旧迁移链清理：`MIGRATION_20_21`、数据库构建器的旧迁移注册，以及仅用于验证旧 v20 数据的仪器测试已移除。新版本只创建当前 schema 21，旧数据库按项目约定不再兼容。

本轮同时完成了旧偏好清理：两套 LED 只使用独立的 `wjx_led_ipixel_fa` 与 `wjx_led_mafu` 文件，删除旧的混合配置文件引用和 FA 的无效 `rules_customized` 键；相关兼容性测试、备份断言和死状态均已移除。

在所有功能去留确定后，才统一处理以下内容：

- 其他 Room 历史 schema 兼容代码（如后续新增）。
- 仍未归属到当前功能的旧偏好键、旧备份格式和旧配置文件兼容。
- 旧数据库导入、旧车辆历史兼容和废弃数据清理。
- 不再使用的 `HistoryImporter` 分支、旧 API 兼容端点和旧后台页面。

这样做可以避免在功能尚未定型时反复改数据库和备份格式。
