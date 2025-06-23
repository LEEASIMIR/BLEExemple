#include <AM2302-Sensor.h>
#define SENSOR_PIN 2

// BLE 모듈 설정
#include <SoftwareSerial.h>
const int HM10_VCC_PIN = 12; // HM-10 전원 제어 핀
// 아두이노 RX:5, TX:4
#define RX_PIN 5
#define TX_PIN 4
SoftwareSerial HM10(TX_PIN, RX_PIN);

#include <avr/sleep.h>
#include <avr/wdt.h> // 와치독 타이머 라이브러리

AM2302::AM2302_Sensor am2302{SENSOR_PIN};
const int GOAL_SLEEP_CYCLES = 3; //슬립 반복 횟수 , 최대 8초만 슬립가능하여..
volatile int sleep_cycle_count = 0; // 슬립 사이클 카운터

// 와치독 인터럽트가 발생했을 때 호출될 함수 (비어 있어도 됨)
ISR(WDT_vect) {
  // 이 함수가 있어야 와치독 인터럽트가 정상 동작함
  sleep_cycle_count++;
}

void setup() {
  Serial.begin(9600);
  pinMode(HM10_VCC_PIN, OUTPUT);
  digitalWrite(HM10_VCC_PIN, HIGH);

  // 와치독 타이머 설정
  setup_watchdog(1); // 9 = 8초, 8 = 4초, 7 = 2초, 6 = 1초 ...
}

void loop() {

  if (sleep_cycle_count >= GOAL_SLEEP_CYCLES) {
    measure();
    sleep_cycle_count = 0;
  } else {
    Serial.print("..");
    Serial.println(sleep_cycle_count);
    Serial.flush();
  }
  // 시스템을 저전력 모드로 전환
  enter_sleep(); 
}

void setup_watchdog(int prescaler) {
  // 와치독 타이머의 프리스케일러(시간 간격) 설정
  byte new_wdtcsr = prescaler & 0x08 ? _BV(WDP3) : 0x00;
  new_wdtcsr |= prescaler & 0x07;

  MCUSR &= ~_BV(WDRF); // Watchdog 리셋 플래그 클리어
  WDTCSR = _BV(WDCE) | _BV(WDE); // 변경 허용
  WDTCSR = new_wdtcsr; // 새로운 프리스케일러 설정
  WDTCSR |= _BV(WDIE); // 와치독 타임아웃 인터럽트 활성화
}

void enter_sleep(void) {
  set_sleep_mode(SLEEP_MODE_PWR_DOWN); // 가장 깊은 절전 모드 설정
  sleep_enable();                      // 슬립 모드 활성화
  sleep_mode();                        // 슬립 모드 진입!
  
  // ------- 여기서 잠이 듦 -------
  // ... 인터럽트가 발생하면 여기서부터 코드가 다시 실행됨 ...
  
  sleep_disable();                     // 슬립 모드 비활성화
}

void measure() {
  delay(1000);
  am2302.begin();
  HM10.begin(9600); // SoftwareSerial 포트 시작
  delay(2500);
  auto status = am2302.read();

  if (status == 0) {
    float temp = am2302.get_Temperature();
    float humidity = am2302.get_Humidity();

    // --- 3. 데이터 포맷팅 및 전송 ---
    // 안드로이드 앱에서 파싱하기 쉬운 형태로 데이터를 만듦
    String dataToSend = "T" + String(temp, 2) + ",H" + String(humidity, 2);
    
    // BLE 모듈을 통해 데이터 전송
    HM10.println(dataToSend);
    
    // PC 디버깅용으로도 데이터 출력
    Serial.println("Data sent via BLE: " + dataToSend);
    
  } else {
    Serial.println("Failed to read from sensor.");
    HM10.println("Sensor Error");
  }

  // --- 4. HM-10 모듈 전원 끄기 ---
  // 데이터 전송이 완료될 시간을 잠시 줌
  delay(1000);
  HM10.end(); // SoftwareSerial 포트 종료
  Serial.println("Going back to sleep...");
  Serial.flush();
}
