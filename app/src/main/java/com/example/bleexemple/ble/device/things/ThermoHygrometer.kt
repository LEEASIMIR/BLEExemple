package com.example.bleexemple.ble.device.things

import android.net.MacAddress
import com.example.bleexemple.ble.device.Device

class ThermoHygrometer(name: String, macAddress: MacAddress): Device(name, macAddress) {

    private var _temperature: Float = 0.0f
    val temperature: Float get() = _temperature

    private var _humidity: Float = 0.0f
    val humidity: Float get() = _humidity

    fun update(temperature: Float, humidity: Float) {
        _temperature = temperature
        _humidity = humidity
    }

}