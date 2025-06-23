package com.example.bleexemple.ble

import android.annotation.SuppressLint
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothManager
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Intent
import android.os.Handler
import android.util.Log
import androidx.appcompat.app.AppCompatActivity.BLUETOOTH_SERVICE
import com.example.bleexemple.ble.device.Device

@SuppressLint("MissingPermission")
class BluetoothServiceImpl(
    private val device: Device,
    private val activity: Activity
) : BluetoothService {

    private var bluetoothAdapter: BluetoothAdapter
    private var scanner: BluetoothLeScanner? = null

    init {
        val bluetoothManager = activity.getSystemService(BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothAdapter = bluetoothManager.adapter

        if (!bluetoothAdapter.isEnabled) {
            // 블루투스 활성화 요청 필요
            val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            activity.startActivityForResult(enableBtIntent, 2)
        } else {
            Log.d("BLE", "스캐너 초기화")
            scanner = bluetoothAdapter.bluetoothLeScanner
        }
    }

    private var isStartSearch = false
    override fun connect() {

        //중복하면 scanner.startScan 콜백에서 에러코드를 주긴 하나, 보기 싫음
        if(isStartSearch) return
        isStartSearch = true

        val filter = ScanFilter.Builder()
            .setDeviceAddress(this.device.macAddress.toString().uppercase())
            .build()

        val filters = listOf(filter)
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY) // 빠른 반응 속도
            .build()

        scanner?.startScan(filters, settings, scanCallback)

        //검색 시작 후 5초 뒤 종료, 5초 동안 못 찾으면 블루투스 전원을 안켰거나 다른 디바이스에 물려있는거임
        Handler().postDelayed({
            Log.d("BLE", "일정 시간 경과, 검색 종료")
            stopBleScan()
        }, 5000)

    }

    private fun connectToDevice(device: BluetoothDevice) {
        device.connectGatt(activity, false, this.device.callback)
    }

    private fun stopBleScan() {
        Log.d("BLE", "스캔 종료")
        scanner?.stopScan(scanCallback)
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult?) {
            val device = result?.device
            if (device != null) {
                Log.d("BLE", "발견된 디바이스: ${device.name ?: "이름 없음"} - ${device.address}")
                connectToDevice(device)
                stopBleScan()
            }
        }

        override fun onScanFailed(errorCode: Int) {
            Log.e("BLE", "스캔 실패: $errorCode")
        }
    }

}