package com.example.bleexemple.ble

import android.app.Activity
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.le.ScanCallback
import com.example.bleexemple.ble.device.Device

interface BluetoothService {
    fun connect()
}