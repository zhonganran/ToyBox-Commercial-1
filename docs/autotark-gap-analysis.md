# 当前项目与 AutoTark 3.7.2 差距及优化决策

对比基线：`AutoTark-v3.7.2.apk` 的 Manifest、反编译代码、资源和运行时解密后的 FID 表。
本项目暂不借鉴“魔迪”。本文件只记录可验证的差距，不表示要复制竞品实现。

## 结论

当前技术栈不需要更换。Kotlin 2.1、JVM 17、Compose Material 3、Hilt/KSP、Room、
Coroutines/Flow、WorkManager 和 OkHttp 足以支撑现有功能，能力上没有落后 AutoTark。
主要问题是单模块内文件过大、状态与生命周期所有权不够集中，以及部分热路径读取过多。

适合当前项目的方向是“分层单体”：先稳定 `UI -> ViewModel/Controller -> Repository ->
Gateway -> Helper Binder` 的单向链路，再按稳定边界拆模块。现在直接改成多 Gradle 模块或
替换地图、数据库、依赖注入框架，会扩大回归面且不会自动提升车机运行速度。

## FID 对比

- AutoTark 读取表：97 条。
- AutoTark 中 90 条字段与官方 Settings feature catalog 的语义直接一致。
- 7 条虽然 FID 本身可能存在，但 AutoTark 的字段名/用途与官方语义冲突，不能照搬。
- 当前可选择的车辆读取目录为 114 条：70 条常规 full/profile 字段，加上 44 条只由自动化、
  首页安全遥测、车辆数据诊断页或 LED 消费者启用的按需字段；另有 11 条电池/充电专用地址，
  总计实际接入 124 个不同 FID 地址。

### AutoTark 的 7 条冲突映射

| 字段 | AutoTark 3.7.2 | 当前采用的官方值 | 原因 |
|---|---:|---:|---|
| `powerState` | dev1023 `0x12D00020` | dev1001 `0x12D0002A` | 前者是 `SET_VEHICLE_STATE`，后者才是 `BODYWORK_POWER_LEVEL` |
| `chargingStatus` | dev1009 `0x49601010` | dev1009 `0x34400018` | 前者是充电功能启停，后者是实时 BMS 充放电状态 |
| `windowRR` | dev1001 `0x38800030` | dev1001 `0x4B900018` | 前者不在官方目录，后者被官方设置页实际读取 |
| `fuelCap` | dev1007 `0x3D902022` | dev1009 `0x40700028` | 前者是仪表“充电口未关”故障提示，后者是左侧盖板状态 |
| `seatbeltFR` | dev1038 `0x1FF0502E` | dev1042 `0x31200008` | 前者是 ADAS 安全带响应功能，后者是副驾物理扣合状态 |
| `seatbeltRL` | dev1042 `0x000B8915` | dev1042 `0x3120000A` | 前者不在官方 feature catalog，后者是后排左安全带状态 |
| `seatbeltRR` | dev1042 `0x4750001A` | dev1042 `0x3120000C` | 前者是后排右安全带命令区域，后者是物理扣合状态 |

项目内部不再把这些官方枚举改回旧的 0/1/2 编码。`DiParsData`、自动化、行程、LED 和首页
统一使用官方原始值，并通过 `BydSignalSemantics` 共享判断逻辑：电源为
`0=OFF/1=ACC/2=ON/3=OK/4=FAKE_OK`，车锁为 `1=解锁/2=上锁`，盖板为
`1=关闭/2=打开`，后备箱为 `1=打开/2=关闭/3=中间或暂停`。

### 当前项目同时补齐的官方项

- 天窗百分比：dev1001 `0x41A00008`，官方设置页按 0–100 读取。
- 充电电池电压：dev1009 `0x44400008`，替换无官方目录依据的 `0xAA000055`。
- 充电电流：dev1009 `0x44400018`，显示在实时充电卡片；仅页面可见且确认 AC/DC 充电时读取。
- 充电功率：dev1009 `0x32300018`，Float；与电流使用相同的页面级条件读取，不进入车辆常规轮询。
- 最大允许充/放电功率：dev1014 `0x34500018` / `0x44400038`，仅实时充电卡片读取。
- 电池绝缘电阻：dev1039 `0x43A00018`；充电停止 SOC：dev1009 `0x43800028`，同属条件诊断读取。
- 智驾安全状态：AEB、AVH、BSD、ESP、ACC/NOA 灰态、MNOA 和实际执行速度共 8 条；首页可见时
  每 2 秒串行读取，原始官方枚举直接进入展示与自动化，不做旧值重映射。
- 电驱诊断：前后电机转速、温度、电流，以及发动机转速和冷却液温度共 8 条；只在车辆数据页
  可见或自动化明确引用时读取。
- 轮胎状态：四轮状态与四轮胎温组成 8 条首页轮胎 profile，每 10 秒读取；未知车型枚举保留
  原值，非零状态只作醒目标记，不在应用内部臆造故障码含义。

AutoTark 多出的 29 条不能直接全部加入高频轮询。

### 重复或别名行程字段（17 条）

这些值与当前里程、行程或能耗链路重叠，加入 300 ms/1 s 热路径只会增加 autoservice
事务数，应在明确产品用途和数据一致性策略后按低频或事件读取：

```text
tripMileage1Km
tripMileage2Km
totalEvMileageKm
totalHevMileageKm
instantFuelCon
instantFuelConUnit
instrumentJourneyMileageKm
instrumentJourneyTimeHours
currentTripMileageKmInt
currentTripMileageKmFloat
tripMileage1Float
currentTripTravelTimeHours
currentTripTravelTimeHourPart
currentTripTravelTimeMinFloat
currentTripTravelTimeMinPart
currentTripDrivingTimeMin
currentTripDrivingTimeHours
```

### 旧兼容字段（1 条）

```text
chargingCapLegacy  dev=1009 fid=603979834 tx=5
```

当前已有正式充电盖字段。没有车型证据前，不增加第二状态源。

### 官方目录确认、仍无产品消费者的可选读取（3 条）

```text
driveModeIndicator   1007 710934584  5 INT_ENUM
energyState          1006 873463832  5 INT_ENUM
accSpeed             1007 597688328  5 INT_RAW
```

这 3 条的地址和字段语义同时得到 AutoTark 与官方 feature catalog 支持，已写入候选目录。
它们与当前驾驶模式、工作模式或 ACC 速度高度重叠，因此没有塞入生产轮询。

按需目录现有 44 条，全部从常规 full profile 排除：20 条空调/座椅/灯光/驾驶与舒适字段、
8 条轮胎字段、8 条智驾字段和 8 条电驱诊断字段。首页只串行运行智驾 2 秒档与轮胎 10 秒档；
车辆数据页只运行电驱 2 秒档；实际刹车灯/倒车灯仅 LED 连接时进入 100 ms 快速档位；启用
自动化时只把规则明确引用的单字段加入 action batch。

### CPU

官方目录存在 dev1030 / `75503`（`CPU_TEMPRATURE`），但官方 SDK 暴露的是车机向 MCU 上报
温度的写侧接口，设置 APK 没有读取它，AutoTark 也没有把它放进 97 条车辆 FID。AutoTark 的
CPU 占用、频率和温度来自 `/proc`、`/sys/devices/system/cpu` 与进程统计。当前只把 75503
登记为 deferred 候选，真车 `getInt` 成功前不加入生产读取。

## 写动作对比

- 当前 `vehicle-actions.json`：123 个动作。
- AutoTark `competitor-actions.json`：123 个动作。
- 动作名集合、device/fid/value payload 完全一致，没有写能力缺口。
- 同 payload 的不同动作名属于命令兼容别名，不作为“重复代码”删除。

## 功能和数据差距

AutoTark 额外存在以下 Room 数据能力：

- `AiInsightDao / AiInsightRecordEntity`：持久化 AI insight 历史。
- `FuelRefuelDao / FuelRefuelEntity`：加油记录。
- `TripSupplementDao / TripSupplementEntity`：行程补充字段。

当前项目已有本地洞察计算，但没有持久化 AI insight 历史。上述内容属于产品功能，不应
塞入车辆高频读取或 `last_state`；确认需要后应以独立 repository、表和低频 worker 实现。

AutoTark 还包含高德原生 SDK、WebRTC、`RemoteBackupWorker`、屏幕录制投屏服务和独立
`LockSoundKeepAliveService`。当前项目使用 osmdroid + 高德瓦片，并把后台启动和锁车音效
集中到现有 watchdog/helper 链路。除非新增导航、远程视频或云备份产品需求，否则引入这些
组件会增加 APK、内存、权限和后台服务数量，不是性能优化。

## 当前项目已确认的问题

1. 车辆常规全量 70 FID 曾在驾驶 full tick 全部读取；现改为首帧 70、核心 12 按 cadence、
   详情 58 每 3 秒到期刷新。SOH 随首次共享全量恢复，不再由 Dashboard 单独抢跑读取。
   新增的 44 条功能字段属于 on-demand catalog，不改变这 70 条常规读取数量。
2. `last_state` 曾随 full tick 重复写 Room；现将它定义为恢复检查点，重要变化立即写，
   其余最多 5 秒一次。
3. 服务重启可能出现短暂双轮询；现由 `SharedAdaptiveLoop` 的唯一所有权锁串行化。
4. Helper 启动与健康检查存在重复 Binder 探活；现合并同轮探活并限制单一运行检查。
5. `SentryInformReceiver` 曾手动创建 `LockSoundConfigSync`，产生第二 mutex/缓存；现统一
   使用 Hilt application singleton。
6. 网络切换时旧探测任务可能清除新探测的 pending 状态；现使用代次令牌保持最新任务所有权。
7. osmdroid 曾在每次 Application 冷启动初始化；现延迟到首次打开地图。
8. `AppModule` 曾同时承担 Room、DAO、接口绑定和运行时组装；现拆为
   `DatabaseModule`、`AppBindingsModule` 和显式运行时 provider。
9. `SoundFloatingService` 曾手动创建标记为 `@Singleton` 的 `SoundLibrary`；现改为注入。
10. `AutomationScreen` 中存在一套已不再调用的旧 toolbar/rule-card UI；现删除死代码，并把
    当前概览与规则场景卡拆到 `AutomationOverview.kt`，规则编辑器 API 保持不变。
11. 仪表投射配置曾由设置 UI、无障碍服务、运行协调器和投射管理器分别直接解释相同的
    SharedPreferences key；现由 `ClusterProjectionPreferences` 统一默认值、快照和组合写入。
12. 自动化规则编辑器已继续按职责拆成 `AutomationTriggerEditor`、
    `AutomationActionEditor`、`AutomationJournalDialog`；`AutomationScreen` 只保留页面入口、
    编辑器壳层和少量共享控件，由最初 3762 行降至约 822 行。
13. `HelperDaemon` 的 Shell 执行、任务投射、VIN 反射、辅助日志和平台适配已移入
    `HelperDaemonPlatform.kt`；Binder 主入口与锁车音频运行时不再和平台工具混在同一文件。
14. 自动化动作编辑器继续拆为核心动作、媒体动作和系统动作三个文件；媒体文件选择、通知、
    电话、导航和 URL 对话框不再堆叠在一个千行文件中。
15. Helper PCM 缓存解码与 8/16/24/32-bit、float 到 PCM16 的格式转换已移入
    `HelperPcmDecoder.kt`，并增加帧对齐、8-bit 转换和缓存元数据测试。
16. Helper 进程入口与锁车/哨兵音频运行时彻底分文件：`HelperDaemon.kt` 仅保留约 394 行
    进程启动、Binder 分发和协议处理，播放状态机进入 `HelperLockSoundRuntime.kt`。
17. 设置页已按显示、快捷键、授权、车辆音效和应用维护拆成独立 section；主页面由约 2950 行
    降至 682 行。`SettingsUiState` 与日志录制也已移出 ViewModel，日志进程在 ViewModel 销毁、
    两小时时限或 8 MB 文件上限时统一停止。
18. `SoundScreen` 已拆成页面壳、本地音效和在线音效三个文件；主文件由 1925 行降至 740 行。
19. 实时喊话的 Service 只保留 Android 生命周期和通知，采集/播放状态机进入
    `RealtimeHailingEngine`，PCM 处理进入 `HailingAudioProcessor`；Service 约 324 行。
20. 桌面组件页已拆成预览控件、设置面板和状态/副作用编排；主文件由 2121 行降至 669 行，
    音效、歌词、电量和自动门的权限/启停回调不再在每个面板重复实现。
21. 锁车、解锁和哨兵仍保留各自独立触发状态机与音频文件；只把通道 A/B 的 FID 路由顺序
    收口到 `ExteriorRoutePlan`，应用侧与 Helper 侧分别执行同一纯路由计划。
22. SharedPreferences 已建立 `FILE_POLICIES` 清单，逐文件记录唯一 owner、备份策略和设备保护
    范围。桌面面板、自动化迁移标记与二维码缓存均改为类型化 owner，不再由 UI 解释 key。
23. 启动诊断统一由 `BootDiagnosticsStore` 使用设备保护存储。修复了 BootReceiver 写设备保护目录、
    设置页却读普通目录的问题，并把链路日志的读改写串行化。
24. 项目最低系统版本为 Android 10（API 29）；已删除 API 29 以下永远不可达的通知、悬浮窗、
    音频焦点和存储兼容分支。Lint 从 105 条降到 34 条，剩余项仅为固定依赖、车机私有 API、
    特殊存储路径和两个显式缩放的无密度位图。
25. 当前 Kotlin 生产代码约 5.6 万行，已无超过 1000 行的文件。900 行级文件仅保留自动化引擎、
    自动化 ViewModel、桌面预览控件和设置编排等高内聚边界，不再按行数机械拆分。

## 当前保留边界

- 暂不把单一 `:app` 强拆为多 Gradle 模块。包边界已经稳定，但模块化本身不会降低 Binder
  事务、车机 CPU 或运行时内存；应在下一次独立版本中处理构建隔离。
- LED 的 100 ms FID profile 只在启用且已连接时运行。没有真车延迟数据前不降低频率，避免
  牺牲灯效响应。
- 仍无消费者的 3 条重叠候选 FID 不进入常规轮询；新增智驾、电驱和轮胎状态按需读取仍需
  在目标车型验证返回值、哨兵和原始枚举语义。
- 私有 API 反射、`/sdcard`/车机固定路径和当前依赖组合是已知兼容边界，未为追求零警告盲改。

## 后续只需真车验证

1. 记录 core/detail/action/LED batch 延迟、失败率、CPU 与内存，验证分级读取的实际收益。
2. 按车型验证新增智驾、电驱和轮胎状态 FID，并记录不支持车型的哨兵与刷新稳定性。
3. 若以后新增 AI 历史、加油记录、远程视频或云备份，再按独立 repository/worker 增加产品能力。
