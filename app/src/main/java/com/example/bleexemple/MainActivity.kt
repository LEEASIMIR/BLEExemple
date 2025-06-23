package com.example.bleexemple

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.MacAddress
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.provider.Settings
import android.util.Log
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.annotation.RequiresApi
import androidx.annotation.RequiresPermission
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatButton
import androidx.core.app.ActivityCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.bleexemple.ble.BluetoothService
import com.example.bleexemple.ble.BluetoothServiceImpl
import com.example.bleexemple.ble.device.Device
import com.example.bleexemple.ble.device.things.ThermoHygrometer
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import java.util.UUID

class MainActivity : AppCompatActivity() {

    lateinit var textView: TextView

    @RequiresApi(Build.VERSION_CODES.S)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        textView = findViewById(R.id.temperature_humidity)
        val bleStartButton = findViewById<AppCompatButton>(R.id.start_ble)

        bleStartButton.setOnClickListener {

            ActivityCompat.requestPermissions(this, arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.CAMERA
            ), 1)

            val locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
            val isLocationEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                    locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
            Log.d("BLE", "위치 서비스 상태: $isLocationEnabled")

            val options = ScanOptions()
            options.setPrompt("QR 코드를 스캔하세요")
            options.setBeepEnabled(true)
            options.setOrientationLocked(false)
            options.setCameraId(0) // 후면 카메라
            qrLauncher.launch(options)

        }

    }

    @SuppressLint("NewApi")
    private val qrLauncher = registerForActivityResult(ScanContract()) { result ->
        if (result.contents != null) {

            val contents = result.contents.split(",")

            if(contents.size != 2) {
                Toast.makeText(this, "QR 코드가 인식되지 않습니다. $contents", Toast.LENGTH_LONG).show()
                return@registerForActivityResult
            }

            val name = contents[0]
            val mac = contents[1]
            Log.d("QR", "스캔된 MAC 주소: $name, $mac")
            Toast.makeText(this, "스캔 결과:$name, $mac", Toast.LENGTH_SHORT).show()

            if(name == "ThermoHygrometer") {
                val device = ThermoHygrometer(name, MacAddress.fromString(mac))
                device.callback = object: BluetoothGattCallback() {

                    //연결 상태 변경 시 호출
                    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
                    override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
                        if (newState == BluetoothProfile.STATE_CONNECTED) {
                            Log.d("BLE", "✅ 연결 성공: ${gatt.device.address}")
                            runOnUiThread {
                                textView.text = "BLE 연결 성공"
                            }
                            gatt.discoverServices() // onServicesDiscovered 콜백 호출
                        } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                            Log.d("BLE", "❌ 연결 해제됨: ${gatt.device.address}")
                            runOnUiThread {
                                textView.text = "BLE 연결 해제됨"
                            }
                        }
                    }

                    @SuppressLint("MissingPermission")//여기 까지 왔는데.. 이미 권한 있음
                    override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
                        if (status == BluetoothGatt.GATT_SUCCESS) {//동작 중 인 서비스? 가 있다면
                            Log.d("BLE", "📡 서비스 발견됨:")
                            gatt.services.forEach { service ->
                                Log.d("BLE", "UUID: ${service.uuid}")

                                service.characteristics.forEach { characteristic ->
                                    Log.d("BLE", "   ↪ 특성 UUID: ${characteristic.uuid} (properties: ${characteristic.properties})")

                                    val isNotifiable = (characteristic.properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY) != 0

                                    if (characteristic != null && isNotifiable) {
                                        val isSuccess = gatt.setCharacteristicNotification(characteristic, true)

                                        // CCCD 설정 (필수)
                                        if(isSuccess) {
                                            //getDescriptor 의 UUID 는 Bluetooth SIG 에서 정한 국제 표준 UUID
//                                            BLE에서 장치가 앱에 데이터를 알림(notify) 또는 응답(indicate) 방식으로 보내려면,
//                                            클라이언트(앱)가 CCCD에 설정 값을 써서 허가해야 합니다.
                                            val descriptor = characteristic.getDescriptor(UUID.fromString("00002902-0000-1000-8000-00805f9b34fb"))
                                            if(descriptor != null) {
                                                gatt.writeDescriptor(descriptor, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
                                            } else {
                                                Log.d("BLE", "descriptor 값 없음")
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    override fun onCharacteristicChanged (
                        gatt: BluetoothGatt,
                        characteristic: BluetoothGattCharacteristic,
                        value: ByteArray
                    ) {
                        val stringValue = value.toString(Charsets.UTF_8)
                        Log.d("BLE", "📥 수신된 값: $stringValue")
                        val res = stringValue.split(",")
                        val temperature = res[0]
                        val humidity = res[1]
                        runOnUiThread {
                            textView.text = "온도 $temperature°C 습도 $humidity%"
                        }
                    }
                }
                textView.text = "BLE 연결 시작"
                val bluetoothService: BluetoothService = BluetoothServiceImpl(device, this)
                bluetoothService.connect()
            }

        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == 1 &&
            grantResults.all { it == PackageManager.PERMISSION_GRANTED }
        ) {
            Log.d("BLE", "권한 허용됨")
        } else {
            Log.e("BLE", "권한 거부됨")
            AlertDialog.Builder(this)
                .setTitle("권한 필요")
                .setMessage("BLE 기능을 사용하려면 권한이 필요합니다.\n앱 설정 화면으로 이동하시겠습니까?")
                .setPositiveButton("설정으로 이동") { _, _ ->
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.fromParts("package", packageName, null)
                    }
                    startActivity(intent)
                }
                .setNegativeButton("취소", null)
                .show()
        }
    }
}