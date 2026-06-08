package com.company.vehiclevoice.data.readonly

internal object VehicleInterfaceProto {
    fun decodeSpeed(payload: ByteArray): Float? = ProtoWire.decode(payload).lastFloat(1)

    fun decodeDcuInfo1(payload: ByteArray): DcuInfo1 {
        val fields = ProtoWire.decode(payload)
        return DcuInfo1(
            gear = fields.lastVarint(1)?.let(::gearName),
            parking = fields.lastVarint(3)?.let(::parkingName)
        )
    }

    fun decodeDcuInfo2(payload: ByteArray): VehicleDcuInfo2 = ProtoWire.decode(payload).let { fields ->
        VehicleDcuInfo2(
            autoDLimitInReason = fields.lastVarint(1)?.let(::autoDLimitInReasonName),
            emergencyStopReason = fields.lastVarint(2)?.let(::emergencyStopReasonName),
            lowVoltageFault = fields.lastVarint(3)?.let(::lowFaultName),
            takeoverRequest = fields.lastVarint(4)?.let(::takeoverName),
            driveMode = fields.lastVarint(5)?.let(::driveModeName),
            autoDOutReason = fields.lastVarint(6)?.let(::autoDOutReasonName),
            brakeSystemFault = fields.lastVarint(9)?.let(::brakeFaultName),
            brakeStatus = fields.lastVarint(10)?.let(::brakeStatusName),
            highVoltageFault = fields.lastVarint(11)?.let(::highFaultName)
        )
    }

    fun decodeBattery(payload: ByteArray): Battery = ProtoWire.decode(payload).let { fields ->
        Battery(socPercent = fields.lastFloat(3))
    }

    fun decodeRange(payload: ByteArray): Float? = ProtoWire.decode(payload).lastFloat(1)

    fun decodeL2Status(payload: ByteArray): VehicleL2Status = ProtoWire.decode(payload).let { fields ->
        VehicleL2Status(
            accStatus = fields.lastVarint(1)?.let(::accStatusName),
            accMode = fields.lastVarint(2)?.let(::accModeName),
            accFailReason = fields.lastVarint(3)?.let(::accFailReasonName),
            accQuitReason = fields.lastVarint(4)?.let(::accQuitReasonName),
            lkaStatus = fields.lastVarint(5)?.let(::lkaStatusName),
            lkaQuitReason = fields.lastVarint(6)?.let(::lkaQuitReasonName),
            lkaFailReason = fields.lastVarint(7)?.let(::lkaFailReasonName),
            l2ActiveMode = fields.lastVarint(9)?.let(::l2ActiveModeName)
        )
    }

    fun decodeAcTemperature(payload: ByteArray): AcTemperature = ProtoWire.decode(payload).let { fields ->
        AcTemperature(inCar = fields.lastFloat(1), outCar = fields.lastFloat(2))
    }

    fun decodeAcState(payload: ByteArray): AcState = ProtoWire.decode(payload).let { fields ->
        AcState(
            power = fields.lastVarint(1)?.let(::acPowerName),
            mode = fields.lastVarint(2)?.let(::acModeName),
            fanGear = fields.lastVarint(3)?.takeIf { it in 1..5 },
            setTemp = fields.lastFloat(4)
        )
    }

    fun decodeBodyState(payload: ByteArray): BodyState = ProtoWire.decode(payload).let { fields ->
        BodyState(
            frontDoor = fields.lastVarint(3)?.let(::frontDoorName),
            midDoor = fields.lastVarint(4)?.let(::midDoorName),
            horn = fields.lastVarint(8)?.let(::openCloseName),
            wiper = fields.lastVarint(23)?.let(::wiperName)
        )
    }

    fun decodeTpms(payload: ByteArray): VehicleTireStatus = ProtoWire.decode(payload).let { fields ->
        VehicleTireStatus(
            location = fields.lastVarint(1)?.let(::tireLocationName),
            pressureKpa = fields.lastVarint(2),
            temperatureCelsius = fields.lastFloat(3),
            highTempAlarm = fields.lastVarint(4)?.let(::tireHighTempAlarmName),
            leakAlarm = fields.lastVarint(5)?.let(::tireLeakAlarmName),
            lostAlarm = fields.lastVarint(6)?.let(::tireLostAlarmName),
            pressureAlarm = fields.lastVarint(7)?.let(::tirePressureAlarmName)
        )
    }

    fun decodeLocation(payload: ByteArray): VehicleLocation = ProtoWire.decode(payload).let { fields ->
        VehicleLocation(
            timestamp = fields.lastDouble(1),
            lon = fields.lastDouble(2),
            lat = fields.lastDouble(3),
            heading = fields.lastDouble(7),
            linearVelocity = fields.lastDouble(8)
        )
    }

    fun decodeTrafficLights(payload: ByteArray): VehicleTrafficLight? {
        val fields = ProtoWire.decode(payload)
        val count = fields.lastVarint(2)
        val first = fields.messages(3).firstOrNull()?.let { ProtoWire.decode(it) }
        return VehicleTrafficLight(
            color = first?.lastVarint(1)?.let(::trafficLightColorName),
            confidence = first?.lastDouble(2),
            count = count
        )
    }

    fun decodeObstacles(payload: ByteArray): VehicleObstacle? {
        val first = ProtoWire.decode(payload).messages(3).firstOrNull()?.let { ProtoWire.decode(it) } ?: return null
        return VehicleObstacle(
            id = first.lastVarint(1),
            vehicleX = first.lastDouble(2),
            vehicleY = first.lastDouble(3),
            velocity = first.lastDouble(8),
            type = first.lastVarint(15)?.let(::obstacleTypeName),
            confidence = first.lastDouble(16)
        )
    }

    fun decodeMainObstacle(payload: ByteArray): PfcMainObstacleState = ProtoWire.decode(payload).let { fields ->
        PfcMainObstacleState(
            obstacle = VehicleObstacle(
                id = null,
                type = fields.lastVarint(1)?.let(::obstacleTypeName),
                vehicleX = fields.lastFloat(2)?.toDouble(),
                vehicleY = fields.lastFloat(3)?.toDouble(),
                velocity = fields.lastFloat(4)?.toDouble(),
                confidence = null
            ),
            faults = VehiclePerceptionFaults(
                cameraFault = fields.lastVarint(6)?.let(::cameraFaultName),
                radarFault = fields.lastVarint(7)?.let(::radarFaultName),
                vehicleConnectFault = fields.lastVarint(8)?.let(::vehicleConnectFaultName),
                fusionFault = fields.lastVarint(9)?.let(::fusionFaultName)
            )
        )
    }

    fun decodeSam(payload: ByteArray): VehicleCooperativeState = ProtoWire.decode(payload).let { fields ->
        VehicleCooperativeState(
            timestamp = fields.lastDouble(1),
            sceneId = fields.lastVarint(2),
            sceneName = fields.lastVarint(2)?.let(::samSceneName),
            vehicleId = fields.lastVarint(3),
            vehicleNumber = fields.lastString(4),
            autoLevel = fields.lastString(5),
            speedMps = fields.lastDouble(10),
            collaborativeVehicleCount = fields.lastVarint(11),
            drivingIntention = fields.lastVarint(13)?.let(::cooperativeBehaviorName),
            intentReason = fields.lastVarint(14)?.let(::intentReasonName),
            guideDecision = fields.lastVarint(15)?.let(::guideDecisionName),
            feedbackResult = fields.lastVarint(16)?.let(::feedbackResultName),
            coordinateBehavior = fields.lastVarint(17)?.let(::cooperativeBehaviorName),
            eventType = fields.lastVarint(18)?.let(::samEventTypeName),
            checkCount = fields.lastVarint(21)
        )
    }

    data class DcuInfo1(val gear: String?, val parking: String?)
    data class Battery(val socPercent: Float?)
    data class AcTemperature(val inCar: Float?, val outCar: Float?)
    data class AcState(val power: String?, val mode: String?, val fanGear: Int?, val setTemp: Float?)
    data class BodyState(val frontDoor: String?, val midDoor: String?, val horn: String?, val wiper: String?)
    data class PfcMainObstacleState(val obstacle: VehicleObstacle?, val faults: VehiclePerceptionFaults?)

    private fun named(value: Int, names: Map<Int, String>, prefix: String): String = names[value] ?: "$prefix($value)"

    private fun gearName(value: Int): String = named(value, mapOf(0 to "无效", 1 to "R", 2 to "N", 3 to "D"), "未知档位")
    private fun parkingName(value: Int): String = named(value, mapOf(0 to "释放", 1 to "驻车"), "未知驻车状态")

    private fun autoDLimitInReasonName(value: Int): String = named(value, autoDLimitReasons, "未知限制进入原因")
    private fun emergencyStopReasonName(value: Int): String = named(value, emergencyStopReasons, "未知急停原因")
    private fun lowFaultName(value: Int): String = named(value, faultLevels("低压"), "未知低压故障")
    private fun takeoverName(value: Int): String = named(value, mapOf(0 to "未激活", 1 to "激活提醒", 2 to "预留", 3 to "预留"), "未知接管提醒")
    private fun driveModeName(value: Int): String = named(value, mapOf(0 to "无", 1 to "紧急制动模式", 2 to "手动模式", 3 to "预留", 4 to "自动驾驶模式", 5 to "缓刹模式", 6 to "自检模式", 7 to "远程驾驶模式"), "未知驾驶模式")
    private fun autoDOutReasonName(value: Int): String = named(value, autoDOutReasons, "未知退出自动驾驶原因")
    private fun brakeFaultName(value: Int): String = named(value, mapOf(0 to "无故障", 1 to "一级故障", 2 to "二级故障", 3 to "三级故障"), "未知制动故障")
    private fun brakeStatusName(value: Int): String = named(value, mapOf(0 to "未工作", 1 to "工作中", 2 to "无效", 3 to "无效"), "未知制动状态")
    private fun highFaultName(value: Int): String = named(value, mapOf(0 to "无故障", 1 to "一级故障", 2 to "二级故障", 3 to "三级故障", 4 to "四级故障", 5 to "无效", 6 to "无效", 7 to "无效"), "未知高压故障")

    private fun accStatusName(value: Int): String = named(value, mapOf(0 to "初始化", 1 to "关闭", 2 to "开启", 3 to "待机", 4 to "激活", 5 to "超越控制", 6 to "抑制", 7 to "故障"), "未知ACC状态")
    private fun accModeName(value: Int): String = named(value, mapOf(0 to "无", 1 to "定速控制", 2 to "跟随控制", 3 to "停车控制", 4 to "启动控制", 5 to "无效", 6 to "无效", 7 to "无效"), "未知ACC模式")
    private fun accFailReasonName(value: Int): String = named(value, mapOf(0 to "无不满足条件", 1 to "档位不满足", 2 to "手刹未松开", 3 to "刹车未松开", 4 to "油门开度大于60%", 5 to "车辆模式不满足", 6 to "ACC系统自检错误", 7 to "按键关闭", 8 to "雨刮开启中高速档", 9 to "车道宽度不满足", 10 to "车道线置信度不满足", 11 to "道路曲率半径不满足"), "未知ACC启动失败原因")
    private fun accQuitReasonName(value: Int): String = named(value, mapOf(0 to "无不满足条件", 1 to "ACC自检未通过", 2 to "手刹异常拉起", 3 to "车辆模式不满足", 4 to "档位不处于前进挡", 5 to "ACC取消", 6 to "雨刮开启中高档", 7 to "车道宽不满足", 8 to "车道线置信度不满足", 9 to "道路曲率半径不满足", 10 to "油门开度大于阈值", 11 to "驾驶员踩制动", 12 to "车速低于ACC最低允许车速", 13 to "协同控制器失效"), "未知ACC退出原因")
    private fun lkaStatusName(value: Int): String = named(value, mapOf(0 to "无效", 1 to "关闭", 2 to "待机", 3 to "激活", 4 to "退出", 5 to "故障", 6 to "无效", 7 to "无效"), "未知LKA状态")
    private fun lkaQuitReasonName(value: Int): String = named(value, mapOf(0 to "无不满足条件", 1 to "车辆模式不满足", 2 to "方向盘扭矩大于阈值", 3 to "CMS功能激活", 4 to "ACC功能退出", 5 to "转向灯开启", 6 to "驾驶员满油门输出", 7 to "深踩制动", 8 to "司机离座超时", 9 to "雨刮开启中高档", 10 to "车道宽无效", 11 to "车道线置信度不满足", 12 to "道路曲率半径小于阈值", 13 to "车速超过最高限速", 14 to "加速度超过安全阈值", 15 to "按键关闭", 16 to "协同控制器失效"), "未知LKA退出原因")
    private fun lkaFailReasonName(value: Int): String = named(value, mapOf(0 to "无不满足条件", 1 to "车辆档位不满足", 2 to "方向盘扭矩不满足", 3 to "车门未关", 4 to "方向盘角度不满足", 5 to "ACC未激活", 6 to "转向灯开启", 7 to "油门踏板不满足", 8 to "制动踏板不满足", 9 to "司机离座", 10 to "雨刮开启中高速档", 11 to "车道宽度不满足", 12 to "车道线置信度不满足", 13 to "道路曲率半径不满足", 14 to "车速不满足", 15 to "横纵向加速度不满足", 16 to "手刹未释放", 17 to "车辆模式不满足"), "未知LKA启动失败原因")
    private fun l2ActiveModeName(value: Int): String = named(value, mapOf(0 to "未激活", 1 to "LKA横向控制激活", 2 to "ACC纵向控制激活", 3 to "L2功能激活", 4 to "CMS/AEB功能激活", 5 to "预留", 6 to "预留", 7 to "预留"), "未知L2模式")

    private fun acPowerName(value: Int): String = named(value, mapOf(0 to "关闭", 1 to "开启", 3 to "不可用"), "未知空调状态")
    private fun acModeName(value: Int): String = named(value, mapOf(1 to "自动制冷", 2 to "ECO", 3 to "通风", 4 to "制冷", 5 to "制热", 6 to "自动制热", 7 to "不可用"), "保留模式")
    private fun frontDoorName(value: Int): String = named(value, doorStates, "未知前门状态")
    private fun midDoorName(value: Int): String = named(value, doorStates, "未知中门状态")
    private fun openCloseName(value: Int): String = named(value, mapOf(0 to "关闭", 1 to "开启"), "未知开关")
    private fun wiperName(value: Int): String = named(value, mapOf(0 to "关闭", 1 to "间歇", 2 to "低速", 3 to "高速"), "未知雨刮")

    private fun tireLocationName(value: Int): String = named(value, mapOf(0 to "轴1胎1", 1 to "轴1胎2", 16 to "轴2胎1", 17 to "轴2胎2", 18 to "轴2胎3", 19 to "轴2胎4", 32 to "轴3胎1", 33 to "轴3胎2", 96 to "备胎1", 97 to "备胎2"), "未知轮胎")
    private fun tireHighTempAlarmName(value: Int): String = named(value, mapOf(0 to "正常", 1 to "高温报警", 2 to "保留", 3 to "保留"), "未知高温报警")
    private fun tireLeakAlarmName(value: Int): String = named(value, mapOf(0 to "正常", 1 to "轮胎漏气", 2 to "保留", 3 to "保留"), "未知漏气报警")
    private fun tireLostAlarmName(value: Int): String = named(value, mapOf(0 to "正常", 1 to "传感器故障报警", 2 to "保留", 3 to "保留"), "未知信号报警")
    private fun tirePressureAlarmName(value: Int): String = named(value, mapOf(0 to "超出极限压力", 1 to "过压", 2 to "没有压力报警", 3 to "压力过低", 4 to "低于低压极限", 5 to "无效值", 6 to "指示器错误", 7 to "保留"), "未知压力报警")

    private fun trafficLightColorName(value: Int): String = named(value, mapOf(1 to "红灯", 2 to "黄灯", 3 to "绿灯"), "颜色类型")
    private fun obstacleTypeName(value: Int): String = named(value, mapOf(0 to "无效", 1 to "行人", 2 to "车辆"), "障碍物类型")
    private fun cameraFaultName(value: Int): String = named(value, mapOf(0 to "无故障", 1 to "摄像头连接故障"), "未知摄像头故障")
    private fun radarFaultName(value: Int): String = named(value, mapOf(0 to "无故障", 1 to "雷达连接故障", 2 to "雷达硬件故障", 3 to "雷达堵塞故障", 4 to "雷达标定故障", 5 to "雷达不工作"), "未知雷达故障")
    private fun vehicleConnectFaultName(value: Int): String = named(value, mapOf(0 to "无故障", 1 to "车辆连接故障"), "未知车辆连接故障")
    private fun fusionFaultName(value: Int): String = named(value, mapOf(0 to "无故障", 1 to "感知融合故障"), "未知融合故障")

    private fun samSceneName(value: Int): String = named(value, mapOf(
        0 to "无协作场景",
        1 to "V2V协作式变道",
        2 to "V2V协同自适应巡航",
        3 to "V2V协作式车道汇入",
        4 to "V2V编队行驶",
        5 to "V2V合作式借道",
        6 to "V2V无信号匝道转向或交替通行",
        7 to "V2V路口对向车辆无保护左转",
        8 to "V2I协作式变道",
        9 to "V2I协同自适应巡航",
        10 to "V2I协作式车道汇入",
        11 to "V2I动态车速限制",
        12 to "V2I编队行驶",
        13 to "V2I远程控制驾驶"
    ), "未知协作场景")

    private fun samEventTypeName(value: Int): String = named(value, mapOf(0 to "未知事件", 1 to "开始", 2 to "进行中", 3 to "结束"), "未知事件类型")
    private fun cooperativeBehaviorName(value: Int): String = named(value, mapOf(0 to "无", 1 to "变道", 2 to "跟车巡航", 3 to "汇入", 4 to "编队", 5 to "借道", 6 to "交替通行", 7 to "左转", 8 to "远程控制场景"), "协作行为")
    private fun intentReasonName(value: Int): String = named(value, mapOf(0 to "无", 1 to "道路条件", 2 to "交通参与者", 3 to "交通规则", 4 to "协同引导", 5 to "安全距离"), "意图原因")
    private fun guideDecisionName(value: Int): String = named(value, mapOf(0 to "无决策", 1 to "允许", 2 to "拒绝", 3 to "等待", 4 to "调整速度", 5 to "调整车道"), "引导决策")
    private fun feedbackResultName(value: Int): String = named(value, mapOf(0 to "无反馈", 1 to "接受", 2 to "拒绝", 3 to "执行中", 4 to "完成", 5 to "失败"), "协作反馈")

    private val doorStates = mapOf(0 to "无效", 1 to "已打开", 2 to "已关闭", 3 to "正在打开", 4 to "正在关闭")

    private fun faultLevels(name: String): Map<Int, String> = mapOf(
        0 to "无故障",
        1 to "${name}一级故障",
        2 to "${name}二级故障",
        3 to "${name}三级故障",
        4 to "预留",
        5 to "预留",
        6 to "预留",
        7 to "预留"
    )

    private val autoDLimitReasons = mapOf(
        0 to "无",
        1 to "急停按键触发",
        2 to "远程停车按键激活",
        3 to "前碰撞触发",
        4 to "后碰撞触发",
        5 to "缓刹按键触发",
        6 to "智能驾驶按键未切换",
        7 to "智能驾驶按键未允许",
        8 to "机械档位不满足",
        9 to "制动踏板不满足",
        10 to "油门踏板不满足",
        11 to "转向系统模式不满足",
        12 to "方向盘角度不满足",
        13 to "车辆驻车状态不满足",
        14 to "驻车按键触发",
        15 to "车速不满足",
        16 to "高压未上电成功",
        17 to "自检不满足",
        18 to "低压系统故障",
        19 to "缓刹手柄不满足",
        20 to "遥控手柄接管",
        21 to "智能系统掉线",
        22 to "档位请求不满足",
        23 to "制动请求不满足",
        24 to "油门请求不满足",
        25 to "方向盘角度请求不满足",
        26 to "智能驾驶指令未切换",
        27 to "智能系统未启动",
        28 to "智能系统启动间隔不满足",
        29 to "智能停车不允许接管",
        30 to "安全带未系",
        31 to "司机离座",
        32 to "门未关闭"
    )

    private val autoDOutReasons = mapOf(
        0 to "无",
        1 to "急停按键触发",
        2 to "远程停车按键激活",
        3 to "前碰撞触发",
        4 to "后碰撞触发",
        5 to "缓刹按键触发",
        6 to "智能驾驶按键未允许",
        7 to "司机操作机械档位",
        8 to "司机踩制动踏板",
        9 to "司机踩油门踏板",
        10 to "司机接管方向盘",
        11 to "司机操作驻车按键",
        12 to "高压下电且停车",
        13 to "低压严重故障且停车",
        14 to "司机操作缓刹手柄",
        15 to "手柄握手接管",
        16 to "智能系统掉线",
        17 to "智能系统退出",
        18 to "转向系统模式不满足"
    )

    private val emergencyStopReasons = mapOf(
        0 to "无",
        1 to "紧急停车开关",
        2 to "远程急停",
        3 to "急停开关3",
        4 to "急停开关4",
        5 to "前碰撞触发",
        6 to "后碰撞触发",
        7 to "预留"
    )
}
