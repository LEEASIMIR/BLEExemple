package com.example.bleexemple.ble.device

import android.bluetooth.BluetoothGattCallback
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.net.MacAddress
import android.util.Log
import com.example.bleexemple.ble.device.things.ThermoHygrometer

open class Device (val name: String, val macAddress: MacAddress) {
    var callback: BluetoothGattCallback? = null

    //GATT 구조
//    GATT 서버
//    └ 서비스 (Service)
//      └ 특성 (Characteristic)
//          └ 값(Read/Write/Notify)

    init {
    }
}