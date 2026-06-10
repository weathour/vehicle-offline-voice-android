package com.company.vehiclevoice.data.readonly

object VehicleSnapshotStateMapper {
    fun toStateMap(snapshot: VehicleReadOnlySnapshot): Map<String, String> = buildMap {
        put("vehicle.snapshot.source", snapshot.sourceName)
        put("vehicle.snapshot.connected", snapshot.diagnostics.connected.toString())
        put("vehicle.snapshot.decoded_keys", snapshot.keyStatuses.values.count { it.decoded }.toString())
        put("vehicle.snapshot.missing_keys", snapshot.keyStatuses.values.count { !it.present }.toString())
        put("vehicle.snapshot.decode_errors", snapshot.keyStatuses.values.count { it.present && !it.decoded }.toString())
        put("vehicle.snapshot.timestamp_only_keys", timestampOnlyKeys(snapshot).joinToString(","))
        put("vehicle.snapshot.detail", snapshot.diagnostics.detail)
        snapshot.speedKmh?.let { put("vehicle.speed_kmh", oneDecimal(it)) }
        snapshot.speedSource?.let { put("vehicle.speed_source", it) }
        snapshot.gear?.let { put("vehicle.gear", it) }
        snapshot.parking?.let { put("vehicle.parking", it) }
        snapshot.batterySocPercent?.let { put("vehicle.battery_soc_percent", oneDecimal(it)) }
        snapshot.batteryVoltageVolts?.let { put("vehicle.battery_voltage_v", oneDecimal(it)) }
        snapshot.batteryCurrentAmps?.let { put("vehicle.battery_current_a", oneDecimal(it)) }
        snapshot.remainingRangeKm?.let { put("vehicle.remaining_range_km", oneDecimal(it)) }
        snapshot.acPower?.let { put("vehicle.ac_power", it) }
        snapshot.acMode?.let { put("vehicle.ac_mode", it) }
        snapshot.acFanGear?.let { put("vehicle.ac_fan_gear", it.toString()) }
        snapshot.acSetTempCelsius?.let { put("vehicle.ac_set_temp_c", oneDecimal(it)) }
        snapshot.inCarTempCelsius?.let { put("vehicle.in_car_temp_c", oneDecimal(it)) }
        snapshot.outCarTempCelsius?.let { put("vehicle.out_car_temp_c", oneDecimal(it)) }
        snapshot.frontDoor?.let { put("vehicle.front_door", it) }
        snapshot.midDoor?.let { put("vehicle.mid_door", it) }
        snapshot.horn?.let { put("vehicle.horn", it) }
        snapshot.wiper?.let { put("vehicle.wiper", it) }
        snapshot.tireStatus?.let { tire ->
            tire.location?.let { put("vehicle.tire.location", it) }
            tire.pressureKpa?.let { put("vehicle.tire.pressure_kpa", it.toString()) }
            tire.temperatureCelsius?.let { put("vehicle.tire.temperature_c", oneDecimal(it)) }
            put("vehicle.tire.normal", tire.normal.toString())
            put("vehicle.tire.alarm_summary", tire.alarmSummary)
            tire.highTempAlarm?.let { put("vehicle.tire.high_temp_alarm", it) }
            tire.leakAlarm?.let { put("vehicle.tire.leak_alarm", it) }
            tire.lostAlarm?.let { put("vehicle.tire.lost_alarm", it) }
            tire.pressureAlarm?.let { put("vehicle.tire.pressure_alarm", it) }
        }
        snapshot.dcuInfo2?.let { dcu ->
            dcu.autoDLimitInReason?.let { put("vehicle.autod.limit_reason", it) }
            dcu.emergencyStopReason?.let { put("vehicle.emergency_stop_reason", it) }
            dcu.lowVoltageFault?.let { put("vehicle.low_voltage_fault", it) }
            dcu.takeoverRequest?.let { put("vehicle.takeover_request", it) }
            dcu.driveMode?.let { put("vehicle.drive_mode", it) }
            dcu.autoDOutReason?.let { put("vehicle.autod.out_reason", it) }
            dcu.brakeSystemFault?.let { put("vehicle.brake_system_fault", it) }
            dcu.brakeStatus?.let { put("vehicle.brake_status", it) }
            dcu.highVoltageFault?.let { put("vehicle.high_voltage_fault", it) }
            put("vehicle.warning_summary", dcu.warningSummary)
        }
        snapshot.l2Status?.let { l2 ->
            l2.accStatus?.let { put("vehicle.acc.status", it) }
            l2.accMode?.let { put("vehicle.acc.mode", it) }
            l2.accFailReason?.let { put("vehicle.acc.fail_reason", it) }
            l2.accQuitReason?.let { put("vehicle.acc.quit_reason", it) }
            l2.lkaStatus?.let { put("vehicle.lka.status", it) }
            l2.lkaFailReason?.let { put("vehicle.lka.fail_reason", it) }
            l2.lkaQuitReason?.let { put("vehicle.lka.quit_reason", it) }
            l2.l2ActiveMode?.let { put("vehicle.l2.mode", it) }
            put("vehicle.l2.summary", l2.summary)
        }
        snapshot.location?.let { location ->
            location.timestamp?.let { put("vehicle.location_timestamp", noDecimalIfWhole(it)) }
            location.lon?.let { put("vehicle.location_lon", sixDecimals(it)) }
            location.lat?.let { put("vehicle.location_lat", sixDecimals(it)) }
            location.height?.let { put("vehicle.location_height_m", oneDecimal(it)) }
            location.pitch?.let { put("vehicle.location_pitch", threeDecimals(it)) }
            location.roll?.let { put("vehicle.location_roll", threeDecimals(it)) }
            location.heading?.let { put("vehicle.heading_deg", oneDecimal(it)) }
            location.linearVelocity?.let { put("vehicle.linear_velocity_mps", oneDecimal(it)) }
            location.velocityX?.let { put("vehicle.velocity_x_mps", oneDecimal(it)) }
            location.velocityY?.let { put("vehicle.velocity_y_mps", oneDecimal(it)) }
            location.velocityZ?.let { put("vehicle.velocity_z_mps", oneDecimal(it)) }
            location.linearAcceleration?.let { put("vehicle.linear_acceleration_mps2", oneDecimal(it)) }
            location.angularVelocity?.let { put("vehicle.angular_velocity_radps", threeDecimals(it)) }
            location.utmPositionX?.let { put("vehicle.utm_x", oneDecimal(it)) }
            location.utmPositionY?.let { put("vehicle.utm_y", oneDecimal(it)) }
            location.utmPositionZ?.let { put("vehicle.utm_z", oneDecimal(it)) }
            location.rtkFlag?.let { put("vehicle.rtk_flag", it.toString()) }
        }
        snapshot.obstacleCount?.let { put("vehicle.obstacle_count", it.toString()) }
        snapshot.nearestObstacle?.let { obstacle ->
            obstacle.id?.let { put("vehicle.nearest_obstacle_id", it.toString()) }
            obstacle.type?.let { put("vehicle.nearest_obstacle_type", it) }
            obstacle.vehicleX?.let { put("vehicle.nearest_obstacle_x_m", oneDecimal(it)) }
            obstacle.vehicleY?.let { put("vehicle.nearest_obstacle_y_m", oneDecimal(it)) }
            obstacle.vehicleZ?.let { put("vehicle.nearest_obstacle_z_m", oneDecimal(it)) }
            obstacle.distanceXY?.let { put("vehicle.nearest_obstacle_distance_m", oneDecimal(it)) }
            obstacle.velocity?.let { put("vehicle.nearest_obstacle_velocity_mps", oneDecimal(it)) }
            obstacle.confidence?.let { put("vehicle.nearest_obstacle_confidence", twoDecimals(it)) }
            obstacle.length?.let { put("vehicle.nearest_obstacle_length_m", oneDecimal(it)) }
            obstacle.width?.let { put("vehicle.nearest_obstacle_width_m", oneDecimal(it)) }
            obstacle.height?.let { put("vehicle.nearest_obstacle_height_m", oneDecimal(it)) }
            obstacle.lon?.let { put("vehicle.nearest_obstacle_lon", sixDecimals(it)) }
            obstacle.lat?.let { put("vehicle.nearest_obstacle_lat", sixDecimals(it)) }
        }
        snapshot.perceptionFaults?.let { faults ->
            faults.cameraFault?.let { put("vehicle.perception.camera_fault", it) }
            faults.radarFault?.let { put("vehicle.perception.radar_fault", it) }
            faults.vehicleConnectFault?.let { put("vehicle.perception.vehicle_connect_fault", it) }
            faults.fusionFault?.let { put("vehicle.perception.fusion_fault", it) }
            put("vehicle.perception.fault_summary", faults.summary)
        }
        snapshot.trafficLight?.let { light ->
            light.timestamp?.let { put("vehicle.traffic_light_timestamp", noDecimalIfWhole(it)) }
            light.color?.let { put("vehicle.traffic_light", it) }
            light.confidence?.let { put("vehicle.traffic_light_confidence", twoDecimals(it)) }
            light.count?.let { put("vehicle.traffic_light_count", it.toString()) }
            light.intersectionId?.let { put("vehicle.traffic_light_intersection_id", it.toString()) }
            light.phaseId?.let { put("vehicle.traffic_light_phase_id", it.toString()) }
            light.remainingTimeSeconds?.let { put("vehicle.traffic_light_remaining_s", oneDecimal(it)) }
            put("vehicle.traffic_light.business_data", light.hasBusinessData.toString())
        }
        snapshot.laneStatus?.let { lane ->
            lane.timestamp?.let { put("vehicle.lane_timestamp", noDecimalIfWhole(it)) }
            lane.laneCount?.let { put("vehicle.lane_count", it.toString()) }
            put("vehicle.lane_line_count", lane.lineCount.toString())
            lane.bestConfidence?.let { put("vehicle.lane_confidence", twoDecimals(it)) }
            put("vehicle.lane.business_data", lane.hasBusinessData.toString())
        }
        snapshot.plannedTrajectory?.let { trajectory ->
            put("vehicle.trajectory.point_count", trajectory.pointCount.toString())
            trajectory.firstPoint?.let { put("vehicle.trajectory.first_point", pointText(it)) }
            trajectory.lastPoint?.let { put("vehicle.trajectory.last_point", pointText(it)) }
            trajectory.approximateLengthMeters?.let { put("vehicle.trajectory.length_m", oneDecimal(it)) }
        }
        snapshot.cooperativeState?.let { sam ->
            sam.sceneId?.let { put("vehicle.cooperation.scene_id", it.toString()) }
            sam.sceneName?.let { put("vehicle.cooperation.scene", it) }
            sam.v2xType?.let { put("vehicle.cooperation.v2x_type", it) }
            sam.eventType?.let { put("vehicle.cooperation.event", it) }
            sam.autoLevel?.let { put("vehicle.cooperation.auto_level", it) }
            sam.drivingModeFeedback?.let { put("vehicle.cooperation.driving_mode_fd", it.toString()) }
            sam.gearLocationFeedback?.let { put("vehicle.cooperation.gear_location_fd", it.toString()) }
            sam.steeringValueFeedback?.let { put("vehicle.cooperation.steering_value_fd", oneDecimal(it)) }
            sam.accelerationCommand?.let { put("vehicle.cooperation.acceleration_cmd", oneDecimal(it)) }
            sam.collaborativeVehicleCount?.let { put("vehicle.cooperation.collaborative_vehicle_count", it.toString()) }
            sam.drivingIntention?.let { put("vehicle.cooperation.driving_intention", it) }
            sam.intentReason?.let { put("vehicle.cooperation.intent_reason", it) }
            sam.guideDecision?.let { put("vehicle.cooperation.guide_decision", it) }
            sam.feedbackResult?.let { put("vehicle.cooperation.feedback_result", it) }
            sam.coordinateBehavior?.let { put("vehicle.cooperation.coordinate_behavior", it) }
            sam.speedMps?.let { put("vehicle.cooperation.speed_mps", oneDecimal(it)) }
            put("vehicle.cooperation.summary", sam.summary)
        }

        put("vehicle.summary.basic", basicSummary(this))
        put("vehicle.summary.warning", warningSummary(this))
        put("vehicle.summary.cooperation", cooperationSummary(this))

        snapshot.keyStatuses.values.filter { !it.decoded }.take(5).forEachIndexed { index, status ->
            put("vehicle.snapshot.error_$index", "${status.key}:${status.error ?: "not_decoded"}")
        }
    }

    private fun basicSummary(state: Map<String, String>): String = listOfNotNull(
        state["vehicle.speed_kmh"]?.let { "车速${it}km/h" },
        state["vehicle.gear"]?.let { "档位$it" },
        state["vehicle.battery_soc_percent"]?.let { "电量$it%" },
        state["vehicle.remaining_range_km"]?.let { "续航${it}km" },
        state["vehicle.obstacle_count"]?.let { "障碍物${it}个" },
        state["vehicle.traffic_light"]
    ).ifEmpty { listOf("暂无基础车况") }.joinToString("，")

    private fun warningSummary(state: Map<String, String>): String = listOfNotNull(
        state["vehicle.warning_summary"],
        state["vehicle.l2.summary"]?.let { "L2：$it" },
        state["vehicle.tire.alarm_summary"]?.let { "胎压：$it" },
        state["vehicle.perception.fault_summary"]?.let { "感知：$it" }
    ).ifEmpty { listOf("暂无告警信息") }.joinToString("；")

    private fun cooperationSummary(state: Map<String, String>): String =
        state["vehicle.cooperation.summary"] ?: "暂无协作信息"

    private fun oneDecimal(value: Float): String = String.format(java.util.Locale.US, "%.1f", value)
    private fun oneDecimal(value: Double): String = String.format(java.util.Locale.US, "%.1f", value)
    private fun twoDecimals(value: Double): String = String.format(java.util.Locale.US, "%.2f", value)
    private fun threeDecimals(value: Double): String = String.format(java.util.Locale.US, "%.3f", value)
    private fun sixDecimals(value: Double): String = String.format(java.util.Locale.US, "%.6f", value)
    private fun pointText(point: Pair<Double, Double>): String = "${oneDecimal(point.first)},${oneDecimal(point.second)}"
    private fun noDecimalIfWhole(value: Double): String =
        if (value % 1.0 == 0.0) String.format(java.util.Locale.US, "%.0f", value) else threeDecimals(value)

    private fun timestampOnlyKeys(snapshot: VehicleReadOnlySnapshot): List<String> = listOfNotNull(
        snapshot.trafficLight?.takeIf { !it.hasBusinessData && it.timestamp != null }?.let { VehicleRedisKeys.TRAFFIC_LIGHTS },
        snapshot.laneStatus?.takeIf { !it.hasBusinessData && it.timestamp != null }?.let { VehicleRedisKeys.LANES }
    )
}
