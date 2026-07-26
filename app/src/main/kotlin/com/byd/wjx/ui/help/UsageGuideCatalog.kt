package com.byd.wjx.ui.help

internal enum class UsageGuideCategory(val label: String) {
    GettingStarted("快速上手"),
    Vehicle("车辆数据"),
    Records("行程与充电"),
    Sound("音效"),
    Components("桌面组件"),
    Automation("智能联动"),
    Settings("设置"),
    Troubleshooting("常见问题"),
}

internal data class UsageGuideSection(
    val id: String,
    val category: UsageGuideCategory,
    val title: String,
    val summary: String,
    val steps: List<String>,
    val notes: List<String> = emptyList(),
    val keywords: List<String> = emptyList(),
)

internal val usageGuideSections = listOf(
    UsageGuideSection(
        id = "first_start",
        category = UsageGuideCategory.GettingStarted,
        title = "首次安装与必要权限",
        summary = "安装后先完成自启动、车辆连接和按需授权。",
        steps = listOf(
            "首次打开应用，阅读安全声明后选择同意。驾驶中不要进行配置或预览操作。",
            "按自启动引导打开车机的自启动、后台运行或电池优化白名单，否则熄火或重启后联动、行程记录可能不会恢复。",
            "如果弹出 ADB 连接确认，选择始终允许这台设备后再确认。这是车辆数据、按键、仪表投射和部分车辆控制的底层连接。",
            "悬浮窗权限用于桌面歌词和桌面卡片；通知访问权限用于第三方音乐歌词。",
            "位置权限只在开启 GPS 行程轨迹和地点联动时需要；麦克风用于实时喊话；蓝牙用于 LED 设备。",
            "应用内更新安装时，系统可能要求允许安装未知应用，只需对超级玩具箱开启。",
        ),
        notes = listOf(
            "不使用的功能无需提前授权，在首次打开该功能时再允许即可。",
            "不同 DiLink 代际和车型的底层能力不同，以实车预览和读取结果为准。",
        ),
        keywords = listOf("ADB", "权限", "自启动", "悬浮窗", "通知访问"),
    ),
    UsageGuideSection(
        id = "activation",
        category = UsageGuideCategory.GettingStarted,
        title = "试用、激活与车辆绑定",
        summary = "查看 VIN、试用期和永久授权，在设置内完成激活。",
        steps = listOf(
            "进入设置 → 授权激活，等待应用读取 17 位车架号（VIN）。",
            "新车辆首次成功连接服务器后进入试用。试用中可使用高级功能，到期后受限功能会自动关闭或限制数量。",
            "获得激活码后，在输入框粘贴并点击立即激活。激活码会与当前 VIN 绑定，不能直接换车使用。",
            "服务与授权页可查看官方客服、付款二维码和售后说明，也可复制 VIN 发给客服核对。",
            "授权状态包含已激活、试用和已到期。重装后应用会使用已签名的本地授权恢复设备凭证。",
        ),
        notes = listOf("车辆必须能读到真实 VIN 才能激活，不要手工伪造或修改车架号。"),
        keywords = listOf("VIN", "激活码", "授权", "试用", "到期"),
    ),
    UsageGuideSection(
        id = "home_dashboard",
        category = UsageGuideCategory.Vehicle,
        title = "首页实时车辆数据",
        summary = "查看电量、车速、续航、功率、里程和动力系统。",
        steps = listOf(
            "首页大卡片显示真实车型、SOC、当前车速和车辆连接状态。",
            "数据条包含预估纯电续航、预估燃油续航、预估总续航、油量、实时功率和总里程。",
            "动力区域显示发动机、前电机和后电机状态。某些车型没有后电机或不开放数据时会显示空值。",
            "点击车辆图片区可从相册上传自己的车辆照片，长按该区域可删除自定义图片并恢复默认画面。",
            "数据会跟随车辆读取周期刷新。熄火后底层数据停止，界面可保留最后一次有效值。",
        ),
        notes = listOf("续航和剩余油量会使用设置中的电池容量、油箱容量和本地行程数据进行估算。"),
        keywords = listOf("SOC", "续航", "油量", "功率", "车辆图片"),
    ),
    UsageGuideSection(
        id = "vehicle_details",
        category = UsageGuideCategory.Vehicle,
        title = "能量回收、电池、车身与车机监控",
        summary = "点击首页分析卡片进入更详细的车辆状态页。",
        steps = listOf(
            "点击本次能量回收，查看当前行程回收电量、发动机发电量以及累计回收数据。",
            "点击车机系统，查看 CPU 总占用、核心使用率、内存、存储、GPU、温度和当前进程。",
            "点击电池健康，查看 SoH、总电压、单体最高/最低电压、压差、电池温度和 12V 蓄电池。",
            "点击车身环境，查看车门、车窗、空调、轮胎、灯光、安全带与驾驶辅助状态。",
            "本地分析卡会基于已记录的行程给出能耗、费用和变化趋势，分析在车机本地完成。",
        ),
        notes = listOf("显示 -- 或暂不可读通常代表当前车型没有该传感器，或车辆处于熄火状态。"),
        keywords = listOf("SoH", "电芯", "轮胎", "CPU", "能量回收"),
    ),
    UsageGuideSection(
        id = "trip_routes",
        category = UsageGuideCategory.Records,
        title = "行程记录与 GPS 轨迹",
        summary = "自动记录行驶时间、里程、能耗、费用和可选 GPS 路线。",
        steps = listOf(
            "车辆上电并开始行驶后，应用根据电源、档位、车速和里程变化自动建立行程，停车熄火后结束。",
            "从首页点击行程与轨迹，可按日、周、月、年或全部时间查看行程。",
            "每条行程显示开始/结束时间、时长、里程、用电、用油、电费和油费。",
            "在设置 → 数据与更新中开启记录行程 GPS 轨迹，并允许位置权限。开启后地图显示起点、终点和行驶路线。",
            "选中单条或多条行程可在地图上定位；点击适配视图可将全部选中轨迹缩放到可见区域。",
        ),
        notes = listOf(
            "不开启 GPS 也会记录行程和能耗，只是没有地图路线。",
            "电费和油费使用设置中的充电电价、油价和行程费率计算。",
        ),
        keywords = listOf("GPS", "轨迹", "行程", "地图", "电费", "油费"),
    ),
    UsageGuideSection(
        id = "charging_records",
        category = UsageGuideCategory.Records,
        title = "实时充电与充电记录",
        summary = "识别慢充/快充，查看实时参数、费用和长期健康趋势。",
        steps = listOf(
            "插枪并开始充电后，充电页会显示慢充或快充、功率、电流、电压、本次充入电量和停止 SOC 等信息。",
            "充电结束后应用会生成历史记录，可按日、周、月、年、全部以及慢充/快充筛选。",
            "点击月份和日期可展开详细记录；长按单条充电记录可编辑或删除。",
            "点击新增充电可手工补录开始/结束时间、SOC、充电类型、充入电量和费用。",
            "右侧统计区显示充电次数、总电量、总费用、平均单价、SoH、电芯压差和温度趋势。",
        ),
        notes = listOf("自动充电记录依赖充电枪、BMS 充电状态和电量变化；数据不完整时可手工编辑。"),
        keywords = listOf("充电记录", "慢充", "快充", "BMS", "SoH"),
    ),
    UsageGuideSection(
        id = "places",
        category = UsageGuideCategory.Records,
        title = "常用地点",
        summary = "管理家、公司和自定义地点，用于行程标注和进入/离开联动。",
        steps = listOf(
            "进入设置 → 车辆费用与地点 → 常用地点管理。",
            "新建地点时设置名称、经纬度和判定半径。半径过小容易受 GPS 漂移影响，可根据停车场大小调整。",
            "行程记录可使用常用地点识别起点或终点。",
            "智能联动可选择进入地点或离开地点作为触发条件。",
        ),
        notes = listOf("删除已被联动引用的地点后，对应规则会提示地点已删除，需要重新选择。"),
        keywords = listOf("地点", "围栏", "进入", "离开", "家", "公司"),
    ),
    UsageGuideSection(
        id = "local_sounds",
        category = UsageGuideCategory.Sound,
        title = "我的音效与车外播放",
        summary = "导入、排序、试听和删除本地音效，通过车外通道播放。",
        steps = listOf(
            "进入个性音效 → 我的音效，点击导入音效选择 MP3、WAV、AAC、M4A、OGG 或 FLAC 文件。",
            "也可直接将音频放入 /sdcard/WJX 目录，回到音效页点击刷新。",
            "点击音效卡片开始车外播放，再次点击当前音效停止。播放中会显示当前状态。",
            "长按并拖动音效卡片可调整顺序；打开卡片菜单可删除不再使用的音效。",
            "点击声音调节，分别设置音效播放音量、实时喊话音量和喊话降噪强度。",
            "如果车外没声音，到设置 → 按键/显示/车外通道，先选自动，不正常时再测试 A 或 B 通道。",
        ),
        notes = listOf(
            "车外音效不会主动暂停、压低或调节车内音乐，音效音量只作用于车外播放器。",
            "播放音效前请确认周围环境，不要使用过高音量影响他人或道路安全。",
        ),
        keywords = listOf("音效", "WJX", "车外播放", "音量", "A通道", "B通道"),
    ),
    UsageGuideSection(
        id = "online_sound_hailing",
        category = UsageGuideCategory.Sound,
        title = "在线音效库与实时喊话",
        summary = "搜索下载在线资源，或将麦克风声音实时输出到车外。",
        steps = listOf(
            "在个性音效页切换到在线音效库，可按分类浏览、输入关键字搜索或刷新列表。",
            "点击在线音效的下载按钮，完成后文件会进入我的音效和 WJX 音效库。",
            "点击麦克风图标启动实时喊话，首次使用需要允许麦克风权限。",
            "喊话运行时再次点击麦克风图标停止。可在声音调节中修改喊话音量、降噪和自动麦克风灵敏度。",
        ),
        notes = listOf("实时喊话会占用麦克风和车外音频通道，使用完毕请及时停止。"),
        keywords = listOf("在线音效", "下载", "喊话", "麦克风", "降噪"),
    ),
    UsageGuideSection(
        id = "lyrics",
        category = UsageGuideCategory.Components,
        title = "桌面歌词与仪表盘歌词",
        summary = "中控和仪表盘分别开启、分别调节，参数互不影响。",
        steps = listOf(
            "进入桌面组件 → 歌词组件，分别开启桌面歌词和仪表盘歌词。",
            "第三方音乐软件需开启系统通知访问权限，应用通过媒体会话和播放通知读取歌曲、进度和歌词。",
            "桌面歌词可设置是否允许显示在其他应用上层，以及是否开启触摸穿透。触摸穿透开启后，点击会交给下方应用。",
            "在调节对象中选择桌面歌词或仪表盘歌词，再调整行数、字号、颜色、当前行高亮、字体风格、滚动动效和歌词同步微调。",
            "仪表盘歌词额外支持水平、垂直位置和仪表字号调节。桌面与仪表的全部参数独立保存。",
            "音乐切歌后歌词会自动更新；当前行按播放进度滚动。如只识别歌名但歌词不走动，通常是播放器没有持续上报进度。",
        ),
        notes = listOf(
            "仪表盘歌词依赖车型的仪表投射能力。",
            "原车 360 的显示优先级与歌词独立，歌词不会因此强制隐藏。",
        ),
        keywords = listOf("歌词", "媒体会话", "通知访问", "仪表盘", "触摸穿透"),
    ),
    UsageGuideSection(
        id = "quick_card",
        category = UsageGuideCategory.Components,
        title = "桌面卡片悬浮窗",
        summary = "在其他应用上层常驻显示车辆控制和车外音效。",
        steps = listOf(
            "进入桌面组件 → 桌面卡片，先打开悬浮窗权限，再开启车辆控制与音效卡片。",
            "在车辆控制中添加需要的控制项，例如左右电滑门、车窗或空调。",
            "在车外音效中从 WJX 音效库添加常用声音，点击卡片播放，再次点击同一音效停止。",
            "可选择内置悬浮图标，也可导入自定义图片或 GIF。GIF 会按原始动画循环显示。",
            "悬浮卡片显示后可直接拖动到不遮挡驾驶信息的位置，最后位置会保存。",
            "车辆控制会复用智能联动的安全写入链路；点击后如果车辆未连接，会显示失败而不会盲目下发。",
        ),
        notes = listOf("自定义 GIF 不建议尺寸过大或帧数过多，否则会额外占用车机内存。"),
        keywords = listOf("桌面卡片", "悬浮窗", "GIF", "滑门", "拖动"),
    ),
    UsageGuideSection(
        id = "automation",
        category = UsageGuideCategory.Automation,
        title = "智能联动规则",
        summary = "用车辆数据、时间、地点和系统事件自动执行动作。",
        steps = listOf(
            "应用预置了插枪播放车外音效、拔枪播放车外音效、倒车播放车外音效和自动泊车播放车外音效四条规则，默认全部关闭。",
            "使用预置前先编辑规则、选择实际音效，保存后再手动开启。",
            "点击新建联动，填写名称并添加触发条件。多个条件可选 AND（全部满足）或 OR（任一满足）。",
            "车辆参数触发支持车速、档位、SOC、充电枪、电源、温度、车门车窗、空调座椅、轮胎、灯光、智驾等类别。",
            "其他触发类型包含进入/离开常用地点、指定时刻、时间段、周期间隔、服务启动、网络恢复和上车开门。",
            "进入动作在条件从不满足变为满足时执行；退出动作在条件从满足变为不满足时执行。",
            "动作支持车窗、空调、座椅、电滑门、通知、启动应用、拨号、高德导航、链接、HTTP、车外音效、音乐/视频、哨兵模式和返回桌面等。",
            "冷却时间用于防止信号抖动重复触发；重复间隔用于条件持续满足时定时重复执行，0 代表关闭重复。",
            "主页可启用/停用、编辑、复制或删除规则，最近触发记录可查看触发时间、条件和执行动作。",
        ),
        notes = listOf(
            "新规则请先在停车状态下测试，并从低风险动作开始。",
            "未激活状态的规则数量和启用数量会受限制。",
        ),
        keywords = listOf("智能联动", "预设", "插枪", "拔枪", "AND", "OR", "触发记录"),
    ),
    UsageGuideSection(
        id = "led_linkage",
        category = UsageGuideCategory.Settings,
        title = "LED 蓝牙屏与联动规则",
        summary = "扫描并连接 LED 设备，调节亮度，设置车辆事件画面序列。",
        steps = listOf(
            "进入设置 → LED 联动，允许蓝牙扫描和连接权限。",
            "开始扫描后，在发现的设备中选择目标 LED 屏。连接成功会显示已就绪和当前信号强度。",
            "使用屏幕亮度滑块调整 LED 亮度，点击恢复微笑画面可立即显示默认待机画面。",
            "内置联动画面测试可单独发送已知画面，用于确认屏幕通信和显示是否正常。",
            "在智能联动规则中新建规则，选择触发类型和条件，再添加一个或多个画面步骤。",
            "每个画面步骤可选画面和持续时间，多个步骤按顺序播放。保存后需手动开启该规则。",
        ),
        notes = listOf("连接不稳定时将 LED 设备靠近车机，确认设备没有被其他手机或车机占用。"),
        keywords = listOf("LED", "蓝牙", "亮度", "微笑", "画面序列"),
    ),
    UsageGuideSection(
        id = "vehicle_event_sounds",
        category = UsageGuideCategory.Settings,
        title = "锁车、解锁与哨兵事件音效",
        summary = "在后台检测车辆事件，通过车外通道播放指定音效。",
        steps = listOf(
            "进入设置 → 车辆事件音效。如果 WJX 目录没有音效，先点击导入音效到 WJX。",
            "锁车音效在车辆锁止并伴随闪灯确认后播放，用于过滤普通的门锁数值抖动。",
            "解锁音效在车辆解锁并伴随闪灯确认后播放。",
            "哨兵音效在车机收到原车哨兵报警事件后播放。",
            "每个事件分别选择音效并打开开关。只选音效但不开启开关不会播放。",
        ),
        notes = listOf("这三项与智能联动中的插枪、拔枪、倒车音效是两套独立配置。"),
        keywords = listOf("锁车", "解锁", "哨兵", "闪灯", "事件音效"),
    ),
    UsageGuideSection(
        id = "shortcuts_projection",
        category = UsageGuideCategory.Settings,
        title = "方向盘快捷键与仪表盘投射",
        summary = "学习实车按键，快速播放音效、喊话或显示仪表画面。",
        steps = listOf(
            "进入设置 → 按键/显示/车外通道 → 方向盘快捷键，点击添加。",
            "选择动作：播放本地音效或实时喊话。",
            "选择按一下、按两下或长按，再点击识别并按下实车方向盘按键，保存后开启绑定。",
            "开启仪表盘映射后，点击显示按键学习实车触发键。",
            "独立投射模式可选择要在仪表显示的应用，并调节宽度、高度、水平位置、垂直位置和缩放。",
            "复制投射模式将中控当前画面复制到仪表，不需要选择目标应用，并按全屏方式显示。",
            "如果同一按键同时绑定了快捷动作和仪表投射，快捷动作优先执行。",
        ),
        notes = listOf("某些车型只在原车仪表支持的显示区域内有效，需通过位置和缩放调整。"),
        keywords = listOf("方向盘", "快捷键", "仪表盘", "投射", "复制投射"),
    ),
    UsageGuideSection(
        id = "vehicle_cost_settings",
        category = UsageGuideCategory.Settings,
        title = "电池容量、油箱、电价、油价与能耗区间",
        summary = "为续航估算、行程费用和充电统计提供计算基准。",
        steps = listOf(
            "进入设置 → 车辆费用与地点，填写车辆标称电池容量和油箱容量后点击保存。",
            "填写家用慢充电价、直流快充电价和每升油价。",
            "行程费率可选慢充、快充或自定义电价，新行程按该费率计算电费。",
            "修改费率后可点击重新计算全部行程，确认后所有历史行程费用会按新费率更新。",
            "优秀能耗和偏高能耗阈值用于首页本地分析的颜色和评价区间。",
        ),
        notes = listOf("电池容量建议填标称可用容量，不要填瞬时充入电量。"),
        keywords = listOf("电池容量", "油箱", "电价", "油价", "重新计算"),
    ),
    UsageGuideSection(
        id = "data_update_logs",
        category = UsageGuideCategory.Settings,
        title = "主题、备份恢复、更新与日志上传",
        summary = "管理界面外观、用户配置、应用更新和故障日志。",
        steps = listOf(
            "进入设置 → 数据与更新，主题可选白天、黑暗或跟随系统，背景壁纸会同步应用到所有主要页面。",
            "开启记录行程 GPS 轨迹后，新行程会保存位置点；关闭不会删除已经记录的轨迹。",
            "导出完整配置会将用户数据库和可备份设置生成 ZIP 文件保存到下载目录。",
            "恢复配置会覆盖当前用户数据，确认后选择之前导出的 ZIP。恢复完成后按界面提示重启或刷新。",
            "开启自动检查更新后，应用启动时会按限流规则查询服务器；也可点击检查更新手动查询。",
            "发现新版本后点击下载并安装。如系统拦截，对超级玩具箱允许安装未知应用后重试。",
            "遇到问题时点击上传日志，应用会将 /sdcard/WJX/wjx.log 的当前完整原始内容上传到授权后台，界面会显示上传中、上传完成或失败原因。",
        ),
        notes = listOf(
            "备份不包含设备凭证和车辆本地身份缓存，防止将一辆车的身份恢复到另一辆车。",
            "日志可能包含 VIN、车辆信号和错误堆栈，只上传到官方后台用于排查。",
        ),
        keywords = listOf("主题", "壁纸", "备份", "恢复", "更新", "日志上传", "wjx.log"),
    ),
    UsageGuideSection(
        id = "status_troubleshooting",
        category = UsageGuideCategory.Troubleshooting,
        title = "状态含义与通用排查",
        summary = "理解在线、休眠、离线和常见功能异常的处理顺序。",
        steps = listOf(
            "服务器显示在线：最近 2 分钟内收到车辆上报，且最后电源状态不是 OFF。",
            "显示休眠：最后上报 powerState=0，代表车辆熄火/下电。熄火后最多保留休眠标记 10 分钟。",
            "显示离线：超过 10 分钟没有新上报，或应用、网络、自启动服务没有运行。",
            "车辆一直显示离线：先打开应用停留一分钟，确认车机能上网、VIN 已读取、授权状态已刷新，再检查 ADB/自启动。",
            "车辆已启动却仍长时间休眠：说明 powerState 还是旧值或心跳没更新，点击上传日志后记录当时的车辆状态。",
            "悬浮窗不显示：检查悬浮窗权限、对应功能开关和授权状态，然后重新开关一次。",
            "歌词有歌名但不滚动：确认音乐应用的播放通知正常、通知访问权限未被关闭，并检查歌词同步微调。",
            "仍无法解决时，在问题发生后尽快进入设置 → 数据与更新 → 上传日志，并将 VIN、车型、操作时间和现象发给客服。",
        ),
        notes = listOf("上传日志不会开始录制，它直接上传 WJX 目录中现有的 wjx.log。"),
        keywords = listOf("在线", "休眠", "离线", "无画面", "不滚动", "故障"),
    ),
)

internal fun filterUsageGuideSections(
    query: String,
    category: UsageGuideCategory?,
    sections: List<UsageGuideSection> = usageGuideSections,
): List<UsageGuideSection> {
    val normalizedQuery = query.trim().lowercase()
    return sections.filter { section ->
        val categoryMatches = category == null || section.category == category
        val queryMatches = normalizedQuery.isBlank() || buildList {
            add(section.category.label)
            add(section.title)
            add(section.summary)
            addAll(section.steps)
            addAll(section.notes)
            addAll(section.keywords)
        }.any { it.lowercase().contains(normalizedQuery) }
        categoryMatches && queryMatches
    }
}
