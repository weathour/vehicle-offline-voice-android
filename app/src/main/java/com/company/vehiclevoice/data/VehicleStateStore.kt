package com.company.vehiclevoice.data

interface VehicleStateStore {
    fun put(key: String, value: String)
    fun get(key: String): String?
    fun snapshot(): Map<String, String>
    fun remove(key: String)
    fun clear()
}
