package com.byd.wjx.ui.automation

import android.content.Context
import android.widget.Toast
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.byd.wjx.activation.ActivationManager
import com.byd.wjx.activation.PremiumFeature
import com.byd.wjx.data.automation.AutomationPreferences
import com.byd.wjx.data.local.dao.RuleDao
import com.byd.wjx.data.local.dao.RuleLogDao
import com.byd.wjx.data.local.entity.ActionDef
import com.byd.wjx.data.local.entity.PlaceEntity
import com.byd.wjx.data.local.entity.RuleEntity
import com.byd.wjx.data.local.entity.RuleLogEntity
import com.byd.wjx.data.local.entity.TriggerDef
import com.byd.wjx.data.repository.PlaceRepository
import com.byd.wjx.data.vehicle.VehicleApi
import com.byd.wjx.storage.SoundLibrary
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONObject
import javax.inject.Inject

private val seatComfortLevels = listOf(
    "0" to "关闭",
    "1" to "1档",
    "2" to "2档",
    "3" to "3档",
    "4" to "4档",
    "5" to "5档",
)

val TRIGGER_PARAMS = listOf(
    TriggerParamOption("Speed", "车速", "车速", "车速", "公里/小时", "行驶", "行驶", "行驶"),
    TriggerParamOption("Gear", "档位", "档位", "档位", "", "行驶", "行驶", "行驶",
        enumValues = listOf("1" to "驻车挡", "2" to "倒车挡", "3" to "空挡", "4" to "前进挡")),
    TriggerParamOption("AdasApaParkingStatus", "自动泊车", "自动泊车", "自动泊车", "", "行驶", "行驶", "行驶",
        enumValues = listOf(
            "0" to "未开启",
            "1" to "搜索车位",
            "2" to "泊车准备",
            "3" to "待泊出",
            "4" to "泊车执行中",
            "5" to "完成",
            "6" to "泊车完成",
            "7" to "选车位"
        )),
    TriggerParamOption("DriveMode", "整车运行模式", "整车运行模式", "整车运行模式", "", "行驶", "行驶", "行驶",
        enumValues = listOf(
            "1" to "标准",
            "2" to "节能",
            "3" to "运动",
            "4" to "雪地",
            "5" to "泥地",
            "6" to "沙地",
        )),
    TriggerParamOption("FrontMotorSpeed", "前电机转速", "前电机转速", "前电机转速", "rpm", "电驱", "电驱", "电驱"),
    TriggerParamOption("RearMotorSpeed", "后电机转速", "后电机转速", "后电机转速", "rpm", "电驱", "电驱", "电驱"),
    TriggerParamOption("FrontMotorTemp", "前电机温度", "前电机温度", "前电机温度", "摄氏度", "电驱", "电驱", "电驱"),
    TriggerParamOption("RearMotorTemp", "后电机温度", "后电机温度", "后电机温度", "摄氏度", "电驱", "电驱", "电驱"),
    TriggerParamOption("FrontMotorCurrent", "前电机电流", "前电机电流", "前电机电流", "安", "电驱", "电驱", "电驱"),
    TriggerParamOption("RearMotorCurrent", "后电机电流", "后电机电流", "后电机电流", "安", "电驱", "电驱", "电驱"),
    TriggerParamOption("EngineRPM", "发动机转速", "发动机转速", "发动机转速", "rpm", "电驱", "电驱", "电驱"),
    TriggerParamOption("WaterTemp", "冷却液温度", "冷却液温度", "冷却液温度", "摄氏度", "电驱", "电驱", "电驱"),
    TriggerParamOption("SOC", "电量百分比", "电量百分比", "电量百分比", "%", "能源", "能源", "能源"),
    TriggerParamOption("ChargingStatus", "充电状态", "充电状态", "充电状态", "", "能源", "能源", "能源",
        enumValues = listOf(
            "0" to "就绪",
            "1" to "充电中",
            "2" to "充电完成",
            "3" to "放电中",
            "4" to "充电终止",
            "5" to "C10 故障",
            "6" to "充电枪故障",
            "7" to "充电机故障",
            "8" to "交流充电故障",
            "9" to "预约等待",
            "10" to "车身放电中",
            "11" to "充电超时",
            "12" to "放电完成",
            "13" to "充电暂停",
        )),
    TriggerParamOption("ChargeGunState", "充电枪", "充电枪", "充电枪", "", "能源", "能源", "能源",
        enumValues = listOf("1" to "未插枪", "2" to "交流慢充", "3" to "直流快充", "4" to "交直流", "5" to "外放电")),
    TriggerParamOption("PowerState", "电源状态", "电源状态", "电源状态", "", "能源", "能源", "能源",
        enumValues = listOf(
            "0" to "OFF",
            "1" to "ACC",
            "2" to "ON",
            "3" to "OK",
            "4" to "FAKE OK",
        )),
    TriggerParamOption("WorkMode", "整车工作模式", "整车工作模式", "整车工作模式", "", "能源", "能源", "能源",
        enumValues = listOf(
            "0" to "停止",
            "1" to "纯电",
            "2" to "强制纯电",
            "3" to "混动",
            "4" to "燃油",
            "5" to "保持",
        )),
    TriggerParamOption("EnergyState", "能量流状态", "能量流状态", "能量流状态", "", "能源", "能源", "能源",
        enumValues = listOf(
            "0" to "无能量回收",
            "4" to "纯电四驱回收",
            "5" to "纯电前驱回收",
            "6" to "纯电后驱回收",
            "11" to "发动机发电 11",
            "12" to "发动机发电 12",
            "13" to "发动机发电 13",
            "14" to "混动回收 14",
            "15" to "混动回收 15",
            "16" to "混动回收 16",
            "18" to "发动机发电 18",
            "20" to "发动机发电 20",
            "21" to "发动机发电 21",
            "22" to "发动机发电 22",
            "27" to "辅助驱动回收 27",
            "28" to "辅助驱动回收 28",
            "29" to "辅助驱动回收 29",
        )),
    TriggerParamOption("Voltage12V", "蓄电池电压", "蓄电池电压", "蓄电池电压", "伏", "能源", "能源", "能源"),
    TriggerParamOption("MinCellVoltage", "单体最低电压", "单体最低电压", "单体最低电压", "伏", "能源", "能源", "能源"),
    TriggerParamOption("MaxCellVoltage", "单体最高电压", "单体最高电压", "单体最高电压", "伏", "能源", "能源", "能源"),
    TriggerParamOption("ExtTemp", "车外温度", "车外温度", "车外温度", "摄氏度", "温度", "温度", "温度"),
    TriggerParamOption("InsideTemp", "车内温度", "车内温度", "车内温度", "摄氏度", "温度", "温度", "温度"),
    TriggerParamOption("AvgBatTemp", "平均电池温度", "平均电池温度", "平均电池温度", "摄氏度", "温度", "温度", "温度"),
    TriggerParamOption("WindowFL", "主驾车窗打开百分比", "主驾车窗打开百分比", "主驾车窗打开百分比", "%", "车身", "车身", "车身"),
    TriggerParamOption("WindowFR", "副驾车窗打开百分比", "副驾车窗打开百分比", "副驾车窗打开百分比", "%", "车身", "车身", "车身"),
    TriggerParamOption("WindowRL", "左后车窗打开百分比", "左后车窗打开百分比", "左后车窗打开百分比", "%", "车身", "车身", "车身"),
    TriggerParamOption("WindowRR", "右后车窗打开百分比", "右后车窗打开百分比", "右后车窗打开百分比", "%", "车身", "车身", "车身"),
    TriggerParamOption("Sunroof", "天窗打开百分比", "天窗打开百分比", "天窗打开百分比", "%", "车身", "车身", "车身"),
    TriggerParamOption("DoorFL", "主驾车门", "主驾车门", "主驾车门", "", "车身", "车身", "车身",
        enumValues = listOf("0" to "关闭", "1" to "打开")),
    TriggerParamOption("DoorFR", "副驾车门", "副驾车门", "副驾车门", "", "车身", "车身", "车身",
        enumValues = listOf("0" to "关闭", "1" to "打开")),
    TriggerParamOption("DoorRL", "左后车门", "左后车门", "左后车门", "", "车身", "车身", "车身",
        enumValues = listOf("0" to "关闭", "1" to "打开")),
    TriggerParamOption("DoorRR", "右后车门", "右后车门", "右后车门", "", "车身", "车身", "车身",
        enumValues = listOf("0" to "关闭", "1" to "打开")),
    TriggerParamOption("Hood", "引擎盖", "引擎盖", "引擎盖", "", "车身", "车身", "车身",
        enumValues = listOf("0" to "关闭", "1" to "打开")),
    TriggerParamOption("LockFL", "主驾车门锁", "主驾车门锁", "主驾车门锁", "", "车身", "车身", "车身",
        enumValues = listOf("1" to "解锁", "2" to "上锁")),
    TriggerParamOption("Trunk", "后备箱门", "后备箱门", "后备箱门", "", "车身", "车身", "车身",
        enumValues = listOf("1" to "打开", "2" to "关闭", "3" to "中间/暂停")),
    TriggerParamOption("FuelCap", "油箱盖", "油箱盖", "油箱盖", "", "车身", "车身", "车身",
        enumValues = listOf("1" to "关闭", "2" to "打开")),
    TriggerParamOption("ChargingCap", "充电盖", "充电盖", "充电盖", "", "车身", "车身", "车身",
        enumValues = listOf("1" to "关闭", "2" to "打开")),
    TriggerParamOption("ACStatus", "空调状态", "空调状态", "空调状态", "", "空调", "空调", "空调"),
    TriggerParamOption("ACCirc", "空调循环方式", "空调循环方式", "空调循环方式", "", "空调", "空调", "空调",
        enumValues = listOf("0" to "外循环", "1" to "内循环")),
    TriggerParamOption("ACTemp", "主驾驶空调温度", "主驾驶空调温度", "主驾驶空调温度", "摄氏度", "空调", "空调", "空调"),
    TriggerParamOption("FanLevel", "风量档位", "风量档位", "风量档位", "", "空调", "空调", "空调"),
    TriggerParamOption("ACCtrlMode", "空调控制模式", "空调控制模式", "空调控制模式", "", "空调", "空调", "空调",
        enumValues = listOf("0" to "自动", "1" to "手动")),
    TriggerParamOption("ACDefrostFront", "前挡除霜", "前挡除霜", "前挡除霜", "", "空调", "空调", "空调",
        enumValues = listOf("0" to "关闭", "1" to "开启")),
    TriggerParamOption("ACWindMode", "空调出风模式", "空调出风模式", "空调出风模式", "原始值", "空调", "空调", "空调"),
    TriggerParamOption("SeatHeatD", "主驾座椅加热档位", "主驾座椅加热档位", "主驾座椅加热档位", "档", "座椅", "座椅", "座椅",
        enumValues = seatComfortLevels),
    TriggerParamOption("SeatVentD", "主驾座椅通风档位", "主驾座椅通风档位", "主驾座椅通风档位", "档", "座椅", "座椅", "座椅",
        enumValues = seatComfortLevels),
    TriggerParamOption("SeatHeatP", "副驾座椅加热档位", "副驾座椅加热档位", "副驾座椅加热档位", "档", "座椅", "座椅", "座椅",
        enumValues = seatComfortLevels),
    TriggerParamOption("SeatVentP", "副驾座椅通风档位", "副驾座椅通风档位", "副驾座椅通风档位", "档", "座椅", "座椅", "座椅",
        enumValues = seatComfortLevels),
    TriggerParamOption("ACDefrostRear", "后挡除霜", "后挡除霜", "后挡除霜", "", "空调", "空调", "空调",
        enumValues = listOf("0" to "关闭", "1" to "开启")),
    TriggerParamOption("SteeringHeat", "方向盘加热", "方向盘加热", "方向盘加热", "", "座椅", "座椅", "座椅",
        enumValues = listOf("1" to "关闭", "2" to "开启")),
    TriggerParamOption("SunshadePos", "遮阳帘位置", "遮阳帘位置", "遮阳帘位置", "%", "车身", "车身", "车身"),
    TriggerParamOption("MirrorFoldState", "后视镜折叠状态", "后视镜折叠状态", "后视镜折叠状态", "原始值", "车身", "车身", "车身"),
    TriggerParamOption("SeatbeltFL", "主驾驶安全带状态", "主驾驶安全带状态", "主驾驶安全带状态", "", "安全", "安全", "安全",
        enumValues = listOf("0" to "未系", "1" to "已系")),
    TriggerParamOption("TirePressFL", "左前轮气压", "左前轮气压", "左前轮气压", "千帕", "安全", "安全", "安全"),
    TriggerParamOption("TirePressFR", "右前轮气压", "右前轮气压", "右前轮气压", "千帕", "安全", "安全", "安全"),
    TriggerParamOption("TirePressRL", "左后轮气压", "左后轮气压", "左后轮气压", "千帕", "安全", "安全", "安全"),
    TriggerParamOption("TirePressRR", "右后轮气压", "右后轮气压", "右后轮气压", "千帕", "安全", "安全", "安全"),
    TriggerParamOption("TireTempFL", "左前轮胎温度", "左前轮胎温度", "左前轮胎温度", "摄氏度", "安全", "安全", "安全"),
    TriggerParamOption("TireTempFR", "右前轮胎温度", "右前轮胎温度", "右前轮胎温度", "摄氏度", "安全", "安全", "安全"),
    TriggerParamOption("TireTempRL", "左后轮胎温度", "左后轮胎温度", "左后轮胎温度", "摄氏度", "安全", "安全", "安全"),
    TriggerParamOption("TireTempRR", "右后轮胎温度", "右后轮胎温度", "右后轮胎温度", "摄氏度", "安全", "安全", "安全"),
    TriggerParamOption("TireStateFL", "左前轮胎状态", "左前轮胎状态", "左前轮胎状态", "原始值", "安全", "安全", "安全"),
    TriggerParamOption("TireStateFR", "右前轮胎状态", "右前轮胎状态", "右前轮胎状态", "原始值", "安全", "安全", "安全"),
    TriggerParamOption("TireStateRL", "左后轮胎状态", "左后轮胎状态", "左后轮胎状态", "原始值", "安全", "安全", "安全"),
    TriggerParamOption("TireStateRR", "右后轮胎状态", "右后轮胎状态", "右后轮胎状态", "原始值", "安全", "安全", "安全"),
    TriggerParamOption("LightLow", "近光灯", "近光灯", "近光灯", "", "灯光", "灯光", "灯光",
        enumValues = listOf("0" to "关", "1" to "开")),
    TriggerParamOption("LightSide", "位置灯", "位置灯", "位置灯", "", "灯光", "灯光", "灯光",
        enumValues = listOf("0" to "关", "1" to "开")),
    TriggerParamOption("LightFrontFog", "前雾灯", "前雾灯", "前雾灯", "", "灯光", "灯光", "灯光",
        enumValues = listOf("0" to "关", "1" to "开")),
    TriggerParamOption("LightRearFog", "后雾灯", "后雾灯", "后雾灯", "", "灯光", "灯光", "灯光",
        enumValues = listOf("0" to "关", "1" to "开")),
    TriggerParamOption("LightBrake", "刹车灯", "刹车灯", "刹车灯", "", "灯光", "灯光", "灯光",
        enumValues = listOf("0" to "关", "1" to "开")),
    TriggerParamOption("LightReversing", "倒车灯", "倒车灯", "倒车灯", "", "灯光", "灯光", "灯光",
        enumValues = listOf("0" to "关", "1" to "开")),
    TriggerParamOption("DRL", "日行灯", "日行灯", "日行灯", "", "灯光", "灯光", "灯光",
        enumValues = listOf("0" to "无", "1" to "开", "2" to "关")),
    TriggerParamOption("LightHigh", "远光灯", "远光灯", "远光灯", "", "灯光", "灯光", "灯光",
        enumValues = listOf("0" to "关", "1" to "开")),
    TriggerParamOption("TurnSignalFlash", "转向灯", "转向灯", "转向灯", "", "灯光", "灯光", "灯光",
        enumValues = listOf("1" to "关闭", "2" to "左转", "4" to "右转", "6" to "双闪")),
    TriggerParamOption("AdasAccMode", "智驾状态", "智驾状态", "智驾状态", "", "智驾", "智驾", "智驾",
        enumValues = listOf("0" to "待命", "1" to "激活", "2" to "接管")),
    TriggerParamOption("AdasLccNcaStatus", "领航辅助状态", "领航辅助状态", "领航辅助状态", "", "智驾", "智驾", "智驾"),
    TriggerParamOption("AdasTjaState", "交通拥堵辅助", "交通拥堵辅助", "交通拥堵辅助", "", "智驾", "智驾", "智驾"),
    TriggerParamOption("LaneKeepState", "车道保持状态", "车道保持状态", "车道保持状态", "", "智驾", "智驾", "智驾",
        enumValues = listOf("0" to "关闭", "1" to "待激活", "2" to "激活", "3" to "强激活", "4" to "故障")),
    TriggerParamOption("AccCruiseState", "ACC巡航状态", "ACC巡航状态", "ACC巡航状态", "", "智驾", "智驾", "智驾",
        enumValues = listOf("0" to "关闭", "1" to "开启", "2" to "激活")),
    TriggerParamOption("AccCruisingSpeed", "ACC设定速度", "ACC设定速度", "ACC设定速度", "公里/小时", "智驾", "智驾", "智驾"),
    TriggerParamOption("AdasAebState", "AEB状态", "AEB状态", "AEB状态", "原始值", "智驾", "智驾", "智驾"),
    TriggerParamOption("AdasAvhState", "AVH状态", "AVH状态", "AVH状态", "原始值", "智驾", "智驾", "智驾"),
    TriggerParamOption("AdasBsdState", "BSD状态", "BSD状态", "BSD状态", "原始值", "智驾", "智驾", "智驾"),
    TriggerParamOption("AdasEspState", "ESP状态", "ESP状态", "ESP状态", "原始值", "智驾", "智驾", "智驾"),
    TriggerParamOption("AdasAccGrayState", "ACC灰态", "ACC灰态", "ACC灰态", "原始值", "智驾", "智驾", "智驾"),
    TriggerParamOption("AdasNoaGrayState", "NOA灰态", "NOA灰态", "NOA灰态", "原始值", "智驾", "智驾", "智驾"),
    TriggerParamOption("AdasMnoaStatus", "MNOA状态", "MNOA状态", "MNOA状态", "原始值", "智驾", "智驾", "智驾"),
    TriggerParamOption("AdasExecSpeed", "智驾执行速度", "智驾执行速度", "智驾执行速度", "公里/小时", "智驾", "智驾", "智驾"),
    TriggerParamOption("AutoPark", "自动驻车", "自动驻车", "自动驻车", "", "智驾", "智驾", "智驾",
        enumValues = listOf("0" to "关闭", "1" to "待激活", "2" to "激活")),
    TriggerParamOption("BrakePedal", "刹车深度", "刹车深度", "刹车深度", "%", "行驶", "行驶", "行驶"),
    TriggerParamOption("AccelPedal", "加速踏板深度", "加速踏板深度", "加速踏板深度", "%", "行驶", "行驶", "行驶"),
    TriggerParamOption("EpbState", "电子手刹状态", "电子手刹状态", "电子手刹状态", "原始值", "行驶", "行驶", "行驶"),
    TriggerParamOption("BrakeState", "制动状态", "制动状态", "制动状态", "原始值", "行驶", "行驶", "行驶"),
    TriggerParamOption("EpedalMode", "单踏板模式", "单踏板模式", "单踏板模式", "原始值", "行驶", "行驶", "行驶"),
)

val ACTION_COMMANDS = listOf(
    ActionOption("车窗通风", "车窗通风", "车窗通风", "车窗通风", "车窗", "车窗", "车窗"),
    ActionOption("车窗关闭", "关闭所有车窗", "关闭所有车窗", "关闭所有车窗", "车窗", "车窗", "车窗"),
    ActionOption("车窗全开", "打开所有车窗", "打开所有车窗", "打开所有车窗", "车窗", "车窗", "车窗"),
    ActionOption("车窗半开", "所有车窗半开", "所有车窗半开", "所有车窗半开", "车窗", "车窗", "车窗"),
    ActionOption("前排车窗关闭", "关闭前排车窗", "关闭前排车窗", "关闭前排车窗", "车窗", "车窗", "车窗"),
    ActionOption("后排车窗关闭", "关闭后排车窗", "关闭后排车窗", "关闭后排车窗", "车窗", "车窗", "车窗"),
    ActionOption("前排车窗全开", "打开前排车窗", "打开前排车窗", "打开前排车窗", "车窗", "车窗", "车窗"),
    ActionOption("后排车窗全开", "打开后排车窗", "打开后排车窗", "打开后排车窗", "车窗", "车窗", "车窗"),
    ActionOption("主驾打开100", "打开主驾车窗", "打开主驾车窗", "打开主驾车窗", "车窗", "车窗", "车窗"),
    ActionOption("主驾打开0", "关闭主驾车窗", "关闭主驾车窗", "关闭主驾车窗", "车窗", "车窗", "车窗"),
    ActionOption("副驾打开100", "打开副驾车窗", "打开副驾车窗", "打开副驾车窗", "车窗", "车窗", "车窗"),
    ActionOption("副驾打开0", "关闭副驾车窗", "关闭副驾车窗", "关闭副驾车窗", "车窗", "车窗", "车窗"),
    ActionOption("主驾打开50", "主驾车窗位置", "主驾车窗位置", "主驾车窗位置", "车窗", "车窗", "车窗"),
    ActionOption("副驾打开50", "副驾车窗位置", "副驾车窗位置", "副驾车窗位置", "车窗", "车窗", "车窗"),
    ActionOption("后左打开50", "后左车窗位置", "后左车窗位置", "后左车窗位置", "车窗", "车窗", "车窗"),
    ActionOption("后右打开50", "后右车窗位置", "后右车窗位置", "后右车窗位置", "车窗", "车窗", "车窗"),
    ActionOption("车窗打开50", "全部车窗位置", "全部车窗位置", "全部车窗位置", "车窗", "车窗", "车窗"),
    ActionOption("前排车窗打开50", "前排车窗位置", "前排车窗位置", "前排车窗位置", "车窗", "车窗", "车窗"),
    ActionOption("后排车窗打开50", "后排车窗位置", "后排车窗位置", "后排车窗位置", "车窗", "车窗", "车窗"),
    ActionOption("打开空调", "打开空调", "打开空调", "打开空调", "空调", "空调", "空调"),
    ActionOption("关闭空调", "关闭空调", "关闭空调", "关闭空调", "空调", "空调", "空调"),
    ActionOption("自动空调", "自动空调", "自动空调", "自动空调", "空调", "空调", "空调"),
    ActionOption("打开空调通风", "通风（不开制冷）", "通风（不开制冷）", "通风（不开制冷）", "空调", "空调", "空调"),
    ActionOption("关闭空调通风", "关闭只通风", "关闭只通风", "关闭只通风", "空调", "空调", "空调"),
    ActionOption("设置温度22", "主驾温度", "主驾温度", "主驾温度", "空调", "空调", "空调"),
    ActionOption("副驾温度22", "副驾温度", "副驾温度", "副驾温度", "空调", "空调", "空调"),
    ActionOption("风量3档", "风量档位", "风量档位", "风量档位", "空调", "空调", "空调"),
    ActionOption("主副驾温度同步", "主副驾温度同步", "主副驾温度同步", "主副驾温度同步", "空调", "空调", "空调"),
    ActionOption("主副驾温度分区", "主副驾温度分区", "主副驾温度分区", "主副驾温度分区", "空调", "空调", "空调"),
    ActionOption("内循环", "内循环", "内循环", "内循环", "空调", "空调", "空调"),
    ActionOption("外循环", "外循环", "外循环", "外循环", "空调", "空调", "空调"),
    ActionOption("出风吹脸", "出风：吹脸", "出风：吹脸", "出风：吹脸", "空调", "空调", "空调"),
    ActionOption("出风吹脸吹脚", "出风：吹脸＋吹脚", "出风：吹脸＋吹脚", "出风：吹脸＋吹脚", "空调", "空调", "空调"),
    ActionOption("出风吹脚", "出风：吹脚", "出风：吹脚", "出风：吹脚", "空调", "空调", "空调"),
    ActionOption("出风吹脚除霜", "出风：吹脚＋除霜", "出风：吹脚＋除霜", "出风：吹脚＋除霜", "空调", "空调", "空调"),
    ActionOption("出风除霜", "出风：除霜", "出风：除霜", "出风：除霜", "空调", "空调", "空调"),
    ActionOption("出风吹脸吹脚除霜", "出风：吹脸＋吹脚＋除霜", "出风：吹脸＋吹脚＋除霜", "出风：吹脸＋吹脚＋除霜", "空调", "空调", "空调"),
    ActionOption("出风吹脸除霜", "出风：吹脸＋除霜", "出风：吹脸＋除霜", "出风：吹脸＋除霜", "空调", "空调", "空调"),
    ActionOption("吹前挡", "前挡风除雾开", "前挡风除雾开", "前挡风除雾开", "空调", "空调", "空调"),
    ActionOption("关闭吹前挡", "前挡风除雾关", "前挡风除雾关", "前挡风除雾关", "空调", "空调", "空调"),
    ActionOption("主驾座椅加热1档", "主驾座椅加热1档", "主驾座椅加热1档", "主驾座椅加热1档", "座椅", "座椅", "座椅"),
    ActionOption("主驾座椅加热2档", "主驾座椅加热2档", "主驾座椅加热2档", "主驾座椅加热2档", "座椅", "座椅", "座椅"),
    ActionOption("主驾座椅加热3档", "主驾座椅加热3档", "主驾座椅加热3档", "主驾座椅加热3档", "座椅", "座椅", "座椅"),
    ActionOption("主驾座椅加热4档", "主驾座椅加热4档", "主驾座椅加热4档", "主驾座椅加热4档", "座椅", "座椅", "座椅"),
    ActionOption("主驾座椅加热5档", "主驾座椅加热5档", "主驾座椅加热5档", "主驾座椅加热5档", "座椅", "座椅", "座椅"),
    ActionOption("主驾座椅加热关闭", "主驾座椅加热关", "主驾座椅加热关", "主驾座椅加热关", "座椅", "座椅", "座椅"),
    ActionOption("副驾座椅加热1档", "副驾座椅加热1档", "副驾座椅加热1档", "副驾座椅加热1档", "座椅", "座椅", "座椅"),
    ActionOption("副驾座椅加热2档", "副驾座椅加热2档", "副驾座椅加热2档", "副驾座椅加热2档", "座椅", "座椅", "座椅"),
    ActionOption("副驾座椅加热3档", "副驾座椅加热3档", "副驾座椅加热3档", "副驾座椅加热3档", "座椅", "座椅", "座椅"),
    ActionOption("副驾座椅加热4档", "副驾座椅加热4档", "副驾座椅加热4档", "副驾座椅加热4档", "座椅", "座椅", "座椅"),
    ActionOption("副驾座椅加热5档", "副驾座椅加热5档", "副驾座椅加热5档", "副驾座椅加热5档", "座椅", "座椅", "座椅"),
    ActionOption("副驾座椅加热关闭", "副驾座椅加热关", "副驾座椅加热关", "副驾座椅加热关", "座椅", "座椅", "座椅"),
    ActionOption("主驾座椅通风1档", "主驾座椅通风1档", "主驾座椅通风1档", "主驾座椅通风1档", "座椅", "座椅", "座椅"),
    ActionOption("主驾座椅通风2档", "主驾座椅通风2档", "主驾座椅通风2档", "主驾座椅通风2档", "座椅", "座椅", "座椅"),
    ActionOption("主驾座椅通风3档", "主驾座椅通风3档", "主驾座椅通风3档", "主驾座椅通风3档", "座椅", "座椅", "座椅"),
    ActionOption("主驾座椅通风4档", "主驾座椅通风4档", "主驾座椅通风4档", "主驾座椅通风4档", "座椅", "座椅", "座椅"),
    ActionOption("主驾座椅通风5档", "主驾座椅通风5档", "主驾座椅通风5档", "主驾座椅通风5档", "座椅", "座椅", "座椅"),
    ActionOption("主驾座椅通风关闭", "主驾座椅通风关", "主驾座椅通风关", "主驾座椅通风关", "座椅", "座椅", "座椅"),
    ActionOption("副驾座椅通风1档", "副驾座椅通风1档", "副驾座椅通风1档", "副驾座椅通风1档", "座椅", "座椅", "座椅"),
    ActionOption("副驾座椅通风2档", "副驾座椅通风2档", "副驾座椅通风2档", "副驾座椅通风2档", "座椅", "座椅", "座椅"),
    ActionOption("副驾座椅通风3档", "副驾座椅通风3档", "副驾座椅通风3档", "副驾座椅通风3档", "座椅", "座椅", "座椅"),
    ActionOption("副驾座椅通风4档", "副驾座椅通风4档", "副驾座椅通风4档", "副驾座椅通风4档", "座椅", "座椅", "座椅"),
    ActionOption("副驾座椅通风5档", "副驾座椅通风5档", "副驾座椅通风5档", "副驾座椅通风5档", "座椅", "座椅", "座椅"),
    ActionOption("副驾座椅通风关闭", "副驾座椅通风关", "副驾座椅通风关", "副驾座椅通风关", "座椅", "座椅", "座椅"),
    ActionOption("方向盘加热", "方向盘加热开", "方向盘加热开", "方向盘加热开", "座椅", "座椅", "座椅"),
    ActionOption("关闭方向盘加热", "方向盘加热关", "方向盘加热关", "方向盘加热关", "座椅", "座椅", "座椅"),
    ActionOption("主驾按摩波浪1档", "主驾按摩：波浪", "主驾按摩：波浪", "主驾按摩：波浪", "座椅", "座椅", "座椅"),
    ActionOption("主驾按摩脉冲1档", "主驾按摩：脉冲", "主驾按摩：脉冲", "主驾按摩：脉冲", "座椅", "座椅", "座椅"),
    ActionOption("主驾按摩拉伸1档", "主驾按摩：拉伸", "主驾按摩：拉伸", "主驾按摩：拉伸", "座椅", "座椅", "座椅"),
    ActionOption("主驾按摩腰部1档", "主驾按摩：腰部", "主驾按摩：腰部", "主驾按摩：腰部", "座椅", "座椅", "座椅"),
    ActionOption("主驾按摩肩部1档", "主驾按摩：肩部", "主驾按摩：肩部", "主驾按摩：肩部", "座椅", "座椅", "座椅"),
    ActionOption("主驾按摩关闭", "主驾按摩关闭", "主驾按摩关闭", "主驾按摩关闭", "座椅", "座椅", "座椅"),
    ActionOption("副驾按摩波浪1档", "副驾按摩：波浪", "副驾按摩：波浪", "副驾按摩：波浪", "座椅", "座椅", "座椅"),
    ActionOption("副驾按摩脉冲1档", "副驾按摩：脉冲", "副驾按摩：脉冲", "副驾按摩：脉冲", "座椅", "座椅", "座椅"),
    ActionOption("副驾按摩拉伸1档", "副驾按摩：拉伸", "副驾按摩：拉伸", "副驾按摩：拉伸", "座椅", "座椅", "座椅"),
    ActionOption("副驾按摩腰部1档", "副驾按摩：腰部", "副驾按摩：腰部", "副驾按摩：腰部", "座椅", "座椅", "座椅"),
    ActionOption("副驾按摩肩部1档", "副驾按摩：肩部", "副驾按摩：肩部", "副驾按摩：肩部", "座椅", "座椅", "座椅"),
    ActionOption("副驾按摩关闭", "副驾按摩关闭", "副驾按摩关闭", "副驾按摩关闭", "座椅", "座椅", "座椅"),
    ActionOption("后视镜加热", "后视镜加热开", "后视镜加热开", "后视镜加热开", "后视镜", "后视镜", "后视镜"),
    ActionOption("关闭后视镜加热", "后视镜加热关", "后视镜加热关", "后视镜加热关", "后视镜", "后视镜", "后视镜"),
    ActionOption("氛围灯打开", "氛围灯开", "氛围灯开", "氛围灯开", "灯光", "灯光", "灯光"),
    ActionOption("氛围灯关闭", "氛围灯关", "氛围灯关", "氛围灯关", "灯光", "灯光", "灯光"),
    ActionOption("氛围灯亮度2档", "氛围灯亮度", "氛围灯亮度", "氛围灯亮度", "灯光", "灯光", "灯光"),
    ActionOption("打开日行灯", "日行灯开", "日行灯开", "日行灯开", "灯光", "灯光", "灯光"),
    ActionOption("关闭日行灯", "日行灯关", "日行灯关", "日行灯关", "灯光", "灯光", "灯光"),
    ActionOption("打开车内灯", "车内灯开", "车内灯开", "车内灯开", "灯光", "灯光", "灯光"),
    ActionOption("关闭车内灯", "车内灯关", "车内灯关", "车内灯关", "灯光", "灯光", "灯光"),
    ActionOption("车门上锁", "车门上锁", "车门上锁", "车门上锁", "门锁", "门锁", "门锁"),
    ActionOption("车门解锁", "车门解锁", "车门解锁", "车门解锁", "门锁", "门锁", "门锁"),
    ActionOption("天窗打开100", "天窗全开", "天窗全开", "天窗全开", "天窗", "天窗", "天窗"),
    ActionOption("天窗打开50", "天窗半开", "天窗半开", "天窗半开", "天窗", "天窗", "天窗"),
    ActionOption("天窗打开0", "天窗关闭", "天窗关闭", "天窗关闭", "天窗", "天窗", "天窗"),
    ActionOption("天窗停止", "天窗停止", "天窗停止", "天窗停止", "天窗", "天窗", "天窗"),
    ActionOption("天窗通风", "天窗通风", "天窗通风", "天窗通风", "天窗", "天窗", "天窗"),
    ActionOption("天窗舒适位置", "天窗舒适位置", "天窗舒适位置", "天窗舒适位置", "天窗", "天窗", "天窗"),
    ActionOption("遮阳帘打开", "遮阳帘打开", "遮阳帘打开", "遮阳帘打开", "天窗", "天窗", "天窗"),
    ActionOption("遮阳帘关闭", "遮阳帘关闭", "遮阳帘关闭", "遮阳帘关闭", "天窗", "天窗", "天窗"),
    ActionOption("遮阳帘位置50", "遮阳帘位置", "遮阳帘位置", "遮阳帘位置", "天窗", "天窗", "天窗"),
    ActionOption("开后备箱", "打开后备箱", "打开后备箱", "打开后备箱", "车身", "车身", "车身"),
    ActionOption("关后备箱", "关闭后备箱", "关闭后备箱", "关闭后备箱", "车身", "车身", "车身")
)

val AUTO_DOOR_ACTIONS = listOf(
    AutoDoorActionOption("slide_l", 1, "左门开"),
    AutoDoorActionOption("slide_l", 2, "左门关"),
    AutoDoorActionOption("slide_r", 1, "右门开"),
    AutoDoorActionOption("slide_r", 2, "右门关"),
    AutoDoorActionOption("both", 3, "暂停"),
)

val OPERATORS = listOf(">", "<", ">=", "<=", "==", "!=")

@HiltViewModel
class AutomationViewModel @Inject constructor(
    private val ruleDao: RuleDao,
    private val ruleLogDao: RuleLogDao,
    private val placeRepository: PlaceRepository,
    private val vehicleApi: VehicleApi,
    private val activationManager: ActivationManager,
    private val automationPreferences: AutomationPreferences,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(AutomationUiState())
    val uiState: StateFlow<AutomationUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch { cleanupStarterTemplatesIfNeeded() }
        viewModelScope.launch {
            ruleDao.getAll().collect { rules ->
                _uiState.update { it.copy(rules = rules) }
            }
        }
        viewModelScope.launch {
            ruleLogDao.getRecent(100).collect { logs ->
                _uiState.update { it.copy(logs = logs) }
            }
        }
        viewModelScope.launch {
            placeRepository.getAll().collect { places ->
                _uiState.update { it.copy(places = places) }
            }
        }
    }

    fun setFilter(filter: RuleFilter) {
        _uiState.update { it.copy(filter = filter) }
    }

    fun toggleEnabled(rule: RuleEntity) {
        viewModelScope.launch {
            val enabling = !rule.enabled
            if (enabling && !activationManager.canUse(PremiumFeature.AUTOMATION_UNLIMITED)) {
                val enabledCount = _uiState.value.rules.count { it.enabled }
                if (enabledCount >= ActivationManager.FREE_AUTOMATION_RULE_LIMIT) {
                    Toast.makeText(context, "未激活最多启用 3 条智能联动", Toast.LENGTH_SHORT).show()
                    return@launch
                }
            }
            ruleDao.setEnabled(rule.id, enabling)
        }
    }

    /**
     * Execute one vehicle command immediately for live testing from the rule editor.
     */
    fun executeNow(command: String) {
        viewModelScope.launch {
            val result = vehicleApi.dispatch(command)
            val msg = if (result.isSuccess) "已发送"
                      else "错误：${result.exceptionOrNull()?.message ?: "不可用"}"
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
        }
    }

    // --- Editor ---

    fun openNewRule() {
        if (_uiState.value.rules.size >= AUTOMATION_RULE_LIMIT) {
            Toast.makeText(context, "智能联动最多创建 $AUTOMATION_RULE_LIMIT 条", Toast.LENGTH_SHORT).show()
            return
        }
        if (!activationManager.canCreateAutomation(_uiState.value.rules.size)) {
            _uiState.update {
                it.copy(editorError = "试用已结束，未激活最多创建 3 条智能联动")
            }
            Toast.makeText(context, "试用已结束，未激活最多创建 3 条智能联动", Toast.LENGTH_SHORT).show()
            return
        }
        _uiState.update {
            it.copy(
                showEditor = true,
                editing = EditingRule(
                    triggers = emptyList(),
                    actions = emptyList()
                )
            )
        }
    }

    fun openEditRule(rule: RuleEntity) {
        _uiState.update {
            it.copy(
                showEditor = true,
                editing = EditingRule(
                    id = rule.id,
                    name = rule.name,
                    triggerLogic = rule.triggerLogic,
                    triggers = TriggerDef.listFromJson(rule.triggers),
                    actions = ActionDef.listFromJson(rule.actions),
                    exitActions = ActionDef.listFromJson(rule.exitActions),
                    cooldownSeconds = rule.cooldownSeconds,
                    repeatIntervalSeconds = rule.repeatIntervalSeconds,
                    requirePark = rule.requirePark,
                    confirmBeforeExecute = rule.confirmBeforeExecute,
                    fireOncePerTrip = rule.fireOncePerTrip,
                    isNew = false
                )
            )
        }
    }

    fun closeEditor() {
        _uiState.update { it.copy(showEditor = false, editorError = null) }
    }

    fun updateEditing(transform: EditingRule.() -> EditingRule) {
        _uiState.update { it.copy(editing = it.editing.transform()) }
    }

    private fun validateActions(actions: List<ActionDef>): String? {
        actions.forEachIndexed { idx, a ->
            val n = idx + 1
            when (a.kind) {
                "param" -> {
                    if (a.command.isBlank()) return localized("动作 #$n：未指定命令", "动作 #$n：未指定命令", "动作 #$n：未指定命令", context)
                }
                "notification_silent", "notification_sound" -> {
                    if (a.notificationTitle().isBlank()) return localized("动作 #$n：通知标题为空", "动作 #$n：通知标题为空", "动作 #$n：通知标题为空", context)
                }
                "app_launch" -> {
                    if (a.appLaunchPackageName().isBlank()) return localized("动作 #$n：未选择应用", "动作 #$n：未选择应用", "动作 #$n：未选择应用", context)
                }
                "call" -> {
                    val phone = a.callPhone().trim()
                    if (phone.length !in 5..20) return localized("动作 #$n：电话号码不正确", "动作 #$n：电话号码不正确", "动作 #$n：电话号码不正确", context)
                }
                "navigate" -> {
                    val lat = a.navigateLat()
                    val lon = a.navigateLon()
                    if (lat == null || lon == null || (lat == 0.0 && lon == 0.0)) {
                        return localized("动作 #$n：未选择导航目的地", "动作 #$n：未选择导航目的地", "动作 #$n：未选择导航目的地", context)
                    }
                }
                "url" -> {
                    val u = a.urlString().trim()
                    if (u.isEmpty()) return localized("动作 #$n：链接为空", "动作 #$n：链接为空", "动作 #$n：链接为空", context)
                    // Allow any scheme: http(s)://, tel:, intent://, geo:, etc.
                    if (!u.matches(Regex("^[a-zA-Z][a-zA-Z0-9+.\\-]*:.+"))) {
                        return localized("动作 #$n：链接必须包含协议头（例如 http:// 或 tel:）", "动作 #$n：链接必须包含协议头（例如 http:// 或 tel:）", "动作 #$n：链接必须包含协议头（例如 http:// 或 tel:）", context)
                    }
                }
                "media_volume" -> {
                    val level = a.payload?.toIntOrNull()
                    if (level == null || level < 0) return "动作 #$n：未设置音量"
                }
                "outside_sound" -> {
                    if (a.outsideSoundPath().isBlank()) return "动作 #$n：未选择车外音效"
                }
                "play_music" -> {
                    if (a.automationMediaPath().isBlank()) return "动作 #$n：未选择音乐"
                }
                "play_video" -> {
                    if (a.automationMediaPath().isBlank()) return "动作 #$n：未选择视频"
                }
                "sentry_mode" -> {
                    if (a.payload !in setOf("on", "off")) return "动作 #$n：哨兵模式设置无效"
                }
                "http" -> {
                    if (a.httpUrl().isBlank()) return "动作 #$n：HTTP 地址为空"
                }
                "auto_door" -> {
                    if (a.autoDoorActionOption() == null) return "动作 #$n：未选择自动滑门动作"
                }
            }
        }
        return null
    }

    fun saveRule() {
        val e = _uiState.value.editing
        if (e.name.isBlank() || e.triggers.isEmpty() || (e.actions.isEmpty() && e.exitActions.isEmpty())) {
            _uiState.value = _uiState.value.copy(editorError =
                localized("名称和条件不能为空，并至少添加一个进入或退出动作", "名称和条件不能为空，并至少添加一个进入或退出动作", "名称和条件不能为空，并至少添加一个进入或退出动作", context)
            )
            return
        }
        val actionError = validateActions(e.actions)
        val exitActionError = validateActions(e.exitActions)?.let { "退出$it" }
        if (actionError != null || exitActionError != null) {
            _uiState.value = _uiState.value.copy(editorError = actionError ?: exitActionError)
            return
        }
        if (e.repeatIntervalSeconds != 0 && e.repeatIntervalSeconds !in AUTOMATION_REPEAT_MIN_SECONDS..AUTOMATION_REPEAT_MAX_SECONDS) {
            _uiState.value = _uiState.value.copy(
                editorError = "循环触发间隔必须为 0（关闭）或 $AUTOMATION_REPEAT_MIN_SECONDS-$AUTOMATION_REPEAT_MAX_SECONDS 秒"
            )
            return
        }
        // Clear previous error on success path
        _uiState.value = _uiState.value.copy(editorError = null)

        viewModelScope.launch {
            if (e.isNew && !activationManager.canCreateAutomation(ruleDao.getCount())) {
                _uiState.update { it.copy(editorError = "试用已结束，未激活最多创建 3 条智能联动") }
                return@launch
            }
            val entity = RuleEntity(
                id = if (e.isNew) 0 else e.id,
                name = e.name.trim(),
                triggerLogic = e.triggerLogic,
                triggers = TriggerDef.listToJson(e.triggers),
                actions = ActionDef.listToJson(e.actions),
                exitActions = ActionDef.listToJson(e.exitActions),
                cooldownSeconds = e.cooldownSeconds.coerceIn(AUTOMATION_COOLDOWN_MIN_SECONDS, AUTOMATION_COOLDOWN_MAX_SECONDS),
                repeatIntervalSeconds = e.repeatIntervalSeconds.coerceIn(0, AUTOMATION_REPEAT_MAX_SECONDS),
                requirePark = e.requirePark,
                confirmBeforeExecute = e.confirmBeforeExecute,
                fireOncePerTrip = e.fireOncePerTrip,
            )
            if (e.isNew) ruleDao.insert(entity)
            else ruleDao.update(entity)
            closeEditor()
        }
    }

    // --- Duplicate / Delete ---

    fun duplicateRule(rule: RuleEntity) {
        viewModelScope.launch {
            if (ruleDao.getCount() >= AUTOMATION_RULE_LIMIT) {
                Toast.makeText(context, "智能联动最多创建 $AUTOMATION_RULE_LIMIT 条", Toast.LENGTH_SHORT).show()
                return@launch
            }
            if (!activationManager.canCreateAutomation(ruleDao.getCount())) {
                Toast.makeText(context, "试用已结束，未激活最多创建 3 条智能联动", Toast.LENGTH_SHORT).show()
                return@launch
            }
            ruleDao.insert(
                rule.copy(
                    id = 0,
                    name = "${rule.name} (${localized("副本", "副本", "副本", context)})",
                    enabled = false,
                    lastTriggeredAt = null,
                    triggerCount = 0,
                    createdAt = System.currentTimeMillis()
                )
            )
        }
    }

    fun requestDelete(ruleId: Long) {
        _uiState.update { it.copy(showDeleteConfirm = ruleId) }
    }

    fun cancelDelete() {
        _uiState.update { it.copy(showDeleteConfirm = null) }
    }

    fun confirmDelete() {
        val ruleId = _uiState.value.showDeleteConfirm ?: return
        viewModelScope.launch {
            ruleDao.getById(ruleId)?.let { ruleDao.delete(it) }
        }
        _uiState.update { it.copy(showDeleteConfirm = null) }
    }

    // --- Journal ---

    fun showJournal() { _uiState.update { it.copy(showJournal = true) } }
    fun hideJournal() { _uiState.update { it.copy(showJournal = false) } }

    private suspend fun cleanupStarterTemplatesIfNeeded() {
        if (automationPreferences.isStarterTemplateCleanupComplete()) return
        ruleDao.deleteUnusedStarterTemplates(
            listOf("高速关窗", "冬季启动", "低电量节能", "夏季制冷", "行驶开遮阳帘", "充电时空调")
        )
        automationPreferences.markStarterTemplateCleanupComplete()
    }
}

// --- Action kind helpers (v2.3.0) ---

fun newNotificationAction(silent: Boolean, context: Context): ActionDef {
    val name = if (silent)
        localized("通知（静音）", "通知（静音）", "通知（静音）", context)
    else
        localized("通知（带声音）", "通知（带声音）", "通知（带声音）", context)
    return ActionDef(
        command = "",
        displayName = name,
        kind = if (silent) "notification_silent" else "notification_sound",
        payload = """{"title":"","text":""}"""
    )
}

fun ActionDef.notificationTitle(): String = try {
    org.json.JSONObject(payload ?: "{}").optString("title")
} catch (e: Exception) { "" }

fun ActionDef.notificationText(): String = try {
    org.json.JSONObject(payload ?: "{}").optString("text")
} catch (e: Exception) { "" }

fun ActionDef.withNotification(title: String, text: String): ActionDef = copy(
    payload = org.json.JSONObject().apply {
        put("title", title)
        put("text", text)
    }.toString()
)

// --- App launch helpers (v2.3.0) ---

fun newAppLaunchAction(context: Context): ActionDef = ActionDef(
    command = "",
    displayName = localized("启动应用", "启动应用", "启动应用", context),
    kind = "app_launch",
    payload = """{"packageName":"","appLabel":""}"""
)

fun ActionDef.appLaunchPackageName(): String = try {
    org.json.JSONObject(payload ?: "{}").optString("packageName")
} catch (e: Exception) { "" }

fun ActionDef.appLaunchLabel(): String = try {
    org.json.JSONObject(payload ?: "{}").optString("appLabel")
} catch (e: Exception) { "" }

fun ActionDef.appLaunchMinimize(): Boolean = try {
    org.json.JSONObject(payload ?: "{}").optBoolean("minimize", false)
} catch (e: Exception) { false }

fun ActionDef.withAppLaunch(packageName: String, appLabel: String, minimize: Boolean): ActionDef = copy(
    payload = org.json.JSONObject().apply {
        put("packageName", packageName)
        put("appLabel", appLabel)
        put("minimize", minimize)
    }.toString()
)

// --- Call helpers (v2.3.0) ---

fun newCallAction(context: Context): ActionDef = ActionDef(
    command = "",
    displayName = localized("拨打电话", "拨打电话", "拨打电话", context),
    kind = "call",
    payload = """{"phone":""}"""
)

fun ActionDef.callPhone(): String = try {
    org.json.JSONObject(payload ?: "{}").optString("phone")
} catch (e: Exception) { "" }

fun ActionDef.callName(): String = try {
    org.json.JSONObject(payload ?: "{}").optString("name")
} catch (e: Exception) { "" }

fun ActionDef.callAutoDial(): Boolean = try {
    org.json.JSONObject(payload ?: "{}").optBoolean("autoDial", false)
} catch (e: Exception) { false }

fun ActionDef.withCall(phone: String, name: String, autoDial: Boolean): ActionDef = copy(
    payload = org.json.JSONObject().apply {
        put("phone", phone)
        put("name", name)
        put("autoDial", autoDial)
    }.toString()
)

// --- Navigate helpers (v2.3.0) ---

fun newNavigateAction(context: Context): ActionDef = ActionDef(
    command = "",
    displayName = localized("高德地图路线", "高德地图路线", "高德地图路线", context),
    kind = "navigate",
    payload = """{"lat":0,"lon":0,"name":""}"""
)

fun ActionDef.navigateLat(): Double? = try {
    val d = org.json.JSONObject(payload ?: "{}").optDouble("lat", Double.NaN)
    if (d.isNaN()) null else d
} catch (e: Exception) { null }

fun ActionDef.navigateLon(): Double? = try {
    val d = org.json.JSONObject(payload ?: "{}").optDouble("lon", Double.NaN)
    if (d.isNaN()) null else d
} catch (e: Exception) { null }

fun ActionDef.navigateName(): String = try {
    org.json.JSONObject(payload ?: "{}").optString("name")
} catch (e: Exception) { "" }

fun ActionDef.withNavigate(lat: Double, lon: Double, name: String): ActionDef = copy(
    payload = org.json.JSONObject().apply {
        put("lat", lat)
        put("lon", lon)
        put("name", name)
    }.toString()
)

// --- 链接动作 ---

fun newUrlAction(context: Context): ActionDef = ActionDef(
    command = "",
    displayName = localized("打开链接", "打开链接", "打开链接", context),
    kind = "url",
    payload = """{"url":""}"""
)

fun ActionDef.urlString(): String = try {
    org.json.JSONObject(payload ?: "{}").optString("url")
} catch (e: Exception) { "" }

fun ActionDef.urlMinimize(): Boolean = try {
    org.json.JSONObject(payload ?: "{}").optBoolean("minimize", false)
} catch (e: Exception) { false }

fun ActionDef.withUrl(url: String, minimize: Boolean): ActionDef = copy(
    payload = org.json.JSONObject().apply {
        put("url", url)
        put("minimize", minimize)
    }.toString()
)

// --- 车外音效动作 ---

fun newOutsideSoundAction(context: Context): ActionDef {
    val firstSound = SoundLibrary(context.applicationContext).listSounds().firstOrNull()
    val name = firstSound?.name.orEmpty()
    val path = firstSound?.file?.absolutePath.orEmpty()
    return ActionDef(
        command = "outside_sound",
        displayName = if (name.isBlank()) "车外播放音效" else "车外播放：$name",
        kind = "outside_sound",
        payload = org.json.JSONObject().apply {
            put("path", path)
            put("name", name)
            put("loop", false)
        }.toString()
    )
}

fun ActionDef.outsideSoundPath(): String = try {
    org.json.JSONObject(payload ?: "{}").optString("path")
} catch (e: Exception) { "" }

fun ActionDef.outsideSoundName(): String = try {
    org.json.JSONObject(payload ?: "{}").optString("name")
} catch (e: Exception) { "" }

fun ActionDef.outsideSoundLoop(): Boolean = try {
    org.json.JSONObject(payload ?: "{}").optBoolean("loop", false)
} catch (e: Exception) { false }

fun ActionDef.withOutsideSound(sound: SoundLibrary.SoundItem): ActionDef = copy(
    displayName = outsideSoundDisplayName(sound.name, outsideSoundLoop()),
    payload = org.json.JSONObject().apply {
        put("path", sound.file.absolutePath)
        put("name", sound.name)
        put("loop", outsideSoundLoop())
    }.toString()
)

fun ActionDef.withOutsideSoundLoop(loop: Boolean): ActionDef = copy(
    displayName = outsideSoundDisplayName(outsideSoundName(), loop),
    payload = org.json.JSONObject(payload ?: "{}").apply {
        put("loop", loop)
    }.toString()
)

private fun outsideSoundDisplayName(name: String, loop: Boolean): String {
    val base = if (name.isBlank()) "车外播放音效" else "车外播放：$name"
    return if (loop) "$base（循环）" else base
}

// --- 自动滑门动作 ---

fun newAutoDoorAction(): ActionDef = ActionDef(
    command = "auto_door",
    displayName = AUTO_DOOR_ACTIONS.first().label,
    kind = "auto_door",
    payload = autoDoorPayload(AUTO_DOOR_ACTIONS.first())
)

fun newStopOutsideSoundAction(): ActionDef = ActionDef(
    command = "stop_outside_sound",
    displayName = "停止车外音效",
    kind = "stop_outside_sound",
)

fun newGoHomeAction(): ActionDef = ActionDef(
    command = "go_home",
    displayName = "返回桌面",
    kind = "go_home",
)

fun newHttpAction(): ActionDef = ActionDef(
    command = "http",
    displayName = "HTTP GET",
    kind = "http",
    payload = """{"method":"GET","url":"","body":"","contentType":"application/json"}""",
)

fun newPlayMusicAction(): ActionDef = ActionDef(
    command = "play_music",
    displayName = "播放音乐",
    kind = "play_music",
    payload = """{"path":"","fileName":"","volume":30,"loop":false}""",
)

fun newStopMusicAction(): ActionDef = ActionDef(
    command = "stop_music",
    displayName = "暂停播放音乐",
    kind = "stop_music",
)

fun newPlayVideoAction(): ActionDef = ActionDef(
    command = "play_video",
    displayName = "播放视频",
    kind = "play_video",
    payload = """{"path":"","fileName":"","volume":30}""",
)

fun newStopVideoAction(): ActionDef = ActionDef(
    command = "stop_video",
    displayName = "结束视频播放",
    kind = "stop_video",
)

fun newSentryModeAction(enabled: Boolean = true): ActionDef = ActionDef(
    command = if (enabled) "sentry_on" else "sentry_off",
    displayName = if (enabled) "开启哨兵模式" else "关闭哨兵模式",
    kind = "sentry_mode",
    payload = if (enabled) "on" else "off",
)

fun ActionDef.automationMediaPath(): String = try {
    JSONObject(payload ?: "{}").optString("path")
} catch (_: Exception) { "" }

fun ActionDef.automationMediaFileName(): String = try {
    JSONObject(payload ?: "{}").optString("fileName")
} catch (_: Exception) { "" }

fun ActionDef.automationMediaVolume(): Int = try {
    JSONObject(payload ?: "{}").optInt("volume", 30).coerceIn(0, 100)
} catch (_: Exception) { 30 }

fun ActionDef.automationMediaLoop(): Boolean = try {
    JSONObject(payload ?: "{}").optBoolean("loop", false)
} catch (_: Exception) { false }

fun ActionDef.withAutomationMedia(path: String, fileName: String): ActionDef = copy(
    displayName = when (kind) {
        "play_video" -> "播放视频：$fileName"
        else -> "播放音乐：$fileName"
    },
    payload = automationMediaPayload().apply {
        put("path", path)
        put("fileName", fileName)
        if (!has("volume")) put("volume", 30)
        if (kind == "play_music" && !has("loop")) put("loop", false)
    }.toString(),
)

fun ActionDef.withAutomationMediaVolume(volume: Int): ActionDef = copy(
    payload = automationMediaPayload().apply {
        put("volume", volume.coerceIn(0, 100))
    }.toString(),
)

fun ActionDef.withAutomationMediaLoop(loop: Boolean): ActionDef = copy(
    payload = automationMediaPayload().apply { put("loop", loop) }.toString(),
)

private fun ActionDef.automationMediaPayload(): JSONObject = try {
    JSONObject(payload ?: "{}")
} catch (_: Exception) {
    JSONObject()
}

fun ActionDef.withSentryMode(enabled: Boolean): ActionDef = copy(
    command = if (enabled) "sentry_on" else "sentry_off",
    displayName = if (enabled) "开启哨兵模式" else "关闭哨兵模式",
    payload = if (enabled) "on" else "off",
)

fun ActionDef.httpMethod(): String = try {
    JSONObject(payload ?: "{}").optString("method", "GET").uppercase()
} catch (_: Exception) { "GET" }

fun ActionDef.httpUrl(): String = try {
    JSONObject(payload ?: "{}").optString("url")
} catch (_: Exception) { "" }

fun ActionDef.withHttp(method: String, url: String): ActionDef = copy(
    displayName = "HTTP ${method.uppercase()}",
    payload = JSONObject(payload ?: "{}").apply {
        put("method", method.uppercase())
        put("url", url)
        if (!has("body")) put("body", "")
        if (!has("contentType")) put("contentType", "application/json")
    }.toString(),
)

fun ActionDef.autoDoorSide(): String = try {
    org.json.JSONObject(payload ?: "{}").optString("side")
} catch (e: Exception) { "" }

fun ActionDef.autoDoorCommandValue(): Int = try {
    org.json.JSONObject(payload ?: "{}").optInt("commandValue", -1)
} catch (e: Exception) { -1 }

fun ActionDef.autoDoorActionOption(): AutoDoorActionOption? =
    AUTO_DOOR_ACTIONS.firstOrNull {
        it.side == autoDoorSide() && it.commandValue == autoDoorCommandValue()
    }

fun ActionDef.withAutoDoorAction(option: AutoDoorActionOption): ActionDef = copy(
    command = "auto_door",
    displayName = option.label,
    kind = "auto_door",
    payload = autoDoorPayload(option)
)

private fun autoDoorPayload(option: AutoDoorActionOption): String =
    org.json.JSONObject().apply {
        put("side", option.side)
        put("commandValue", option.commandValue)
    }.toString()

/**
 * Pure list reorder used by the automation editor's up/down arrows: returns a new
 * list with the item at [index] swapped with its neighbour. No-op (returns the same
 * order) when [index] is at the boundary or out of range.
 */
internal fun <T> List<T>.moveItem(index: Int, up: Boolean): List<T> {
    val target = if (up) index - 1 else index + 1
    if (index !in indices || target !in indices) return this
    return toMutableList().apply {
        val tmp = this[index]; this[index] = this[target]; this[target] = tmp
    }
}
