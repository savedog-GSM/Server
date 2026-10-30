// 지켜멍 센서 (ESP32 + 초음파 센서 HC-SR04 + 온습도 센서 DHT11/DHT22)
// 1초마다 전방 거리와 최근 온습도를 서버의 아두이노 전용 API(POST /api/arduino/sensors)로 보낸다.
// 측정은 정확히 1초마다 하고, 전송은 다른 코어의 작업이 맡는다. 전송이 1초보다 오래 걸리면(재연결 등)
// 밀린 값은 버리고 가장 최근 값을 보낸다. 그래서 측정이 밀리지 않고, 서버에는 늘 최신 값이 간다.
//
// 배선
//   HC-SR04 VCC  → ESP32 5V(VIN)       DHT VCC  → ESP32 3.3V
//   HC-SR04 GND  → ESP32 GND           DHT GND  → ESP32 GND
//   HC-SR04 TRIG → P23                 DHT DATA → P33
//   HC-SR04 ECHO → P22
//   ※ DHT는 P34~P39에 꽂으면 안 된다. 입력 전용 핀이라 측정 시작 신호를 못 보내서 온습도를 읽지 못한다.
//   ※ ECHO는 5V 신호라 그대로 꽂으면 ESP32 핀이 망가질 수 있다.
//     저항 두 개(예: 1kΩ, 2kΩ)로 나눠 3.3V로 낮춰서 연결하거나, 3.3V용 HC-SR04P를 쓴다.
//   ※ DHT 센서만 있는 부품(다리 4개)이면 DATA와 VCC 사이에 10kΩ 저항을 단다. 모듈형(다리 3개)은 이미 달려 있다.
//
// 준비
//   1) 보드 매니저에서 "esp32"를 설치하고 ESP32 Dev Module을 고른다.
//   2) 라이브러리 매니저에서 "DHT sensor library"(Adafruit)를 설치한다. 같이 설치하라는 Adafruit Unified Sensor도 설치한다.
//   3) 이 폴더의 secrets.example.h를 secrets.h로 복사하고 Wi-Fi·토큰 값을 채운다. (secrets.h는 git에 올라가지 않는다)
//   4) 시리얼 모니터(9600)에서 "거리 ...cm, 온도 ...°C, 습도 ...% 보냄"이 1초마다 찍히면 정상이다.

#include <WiFi.h>
#include <WiFiClientSecure.h>
#include <HTTPClient.h>
#include <DHT.h>
#include "secrets.h"  


const char* SERVER_URL = "https://savedog.https.gsmsv.site/api/arduino/sensors";


const int TRIG_PIN = 23;
const int ECHO_PIN = 22;
const int DHT_PIN = 33;
#define DHT_TYPE DHT11  // 파란색 센서는 DHT11, 흰색 센서는 DHT22로 바꾼다.
const unsigned long SEND_INTERVAL_MS = 1000;  // 거리를 보내는 간격
// ────────────────────────

const float MAX_DISTANCE_CM = 400.0;           // 서버가 받는 최대 거리. HC-SR04의 측정 한계와 같다.
const unsigned long ECHO_TIMEOUT_US = 30000;   // 이만큼 기다려도 반사가 없으면 4m 안에 장애물이 없는 것으로 본다.
const float CM_PER_MICROSECOND = 0.0343;       // 소리가 1마이크로초에 가는 거리
const int SAMPLES = 3;                         // 튀는 값을 줄이려고 세 번 재서 가운데 값을 쓴다.
const unsigned long RECONNECT_INTERVAL_MS = 5000;
const int HTTP_TIMEOUT_MS = 3000;
// DHT 센서는 2초에 한 번보다 자주 읽으면 값이 안 나온다. 그 사이에는 마지막 값을 보낸다.
const unsigned long CLIMATE_INTERVAL_MS = 2000;
// 이보다 오래 못 읽었으면 센서가 빠진 것으로 보고 온습도를 빼고 보낸다.
const unsigned long CLIMATE_STALE_MS = 10000;
// 서버가 받는 범위. 벗어난 값은 센서 오류로 보고 보내지 않는다. (보내면 거리까지 같이 거절된다)
const float MIN_TEMPERATURE = -40.0;
const float MAX_TEMPERATURE = 85.0;

// HTTPS 연결을 매번 새로 맺으면 1초 안에 못 보낼 수 있어서 연결을 계속 쓴다. (전송 작업에서만 쓴다)
WiFiClientSecure secureClient;
WiFiClient plainClient;
HTTPClient http;
DHT dht(DHT_PIN, DHT_TYPE);
bool useHttps = false;

// 측정 → 전송 작업으로 넘기는 값. 줄은 한 칸이라 새 값이 오면 아직 못 보낸 이전 값을 덮어쓴다.
struct Reading {
  float distance;
  bool withClimate;
  float temperature;
  float humidity;
};
QueueHandle_t readings;
volatile unsigned long skippedReadings = 0;  // 전송이 밀려 덮어쓴(못 보낸) 값의 수

// Arduino IDE가 함수 선언을 자동으로 위에 끼워 넣으면 Reading보다 앞에 놓여 오류가 날 수 있어 직접 적는다.
void sendTask(void*);
void sendReadings(const Reading& reading);

unsigned long nextMeasureAt = 0;
unsigned long lastReconnectAt = 0;
unsigned long lastClimateReadAt = 0;
unsigned long lastClimateOkAt = 0;
bool hasClimate = false;
float temperature = 0;
float humidity = 0;

void setup() {
  Serial.begin(9600);
  pinMode(TRIG_PIN, OUTPUT);
  pinMode(ECHO_PIN, INPUT);
  digitalWrite(TRIG_PIN, LOW);
  dht.begin();
  if (DHT_PIN >= 34 && DHT_PIN <= 39) {
    Serial.println("주의: DHT_PIN이 입력 전용 핀(34~39)이라 온습도를 읽을 수 없습니다. P4·P25·P26·P27·P32·P33 중 하나로 옮기세요.");
  }

  useHttps = strncmp(SERVER_URL, "https://", 8) == 0;
  // 프로토타입이라 서버 인증서를 확인하지 않는다. (암호화는 된다)
  // 실제로 쓸 때는 setCACert()로 인증서를 넣어 가짜 서버에 토큰을 보내지 않게 한다.
  secureClient.setInsecure();
  http.setReuse(true);
  http.setConnectTimeout(HTTP_TIMEOUT_MS);
  http.setTimeout(HTTP_TIMEOUT_MS);

  WiFi.mode(WIFI_STA);
  WiFi.begin(WIFI_SSID, WIFI_PASSWORD);
  Serial.println("Wi-Fi에 연결하는 중...");

  readings = xQueueCreate(1, sizeof(Reading));
  // 전송은 0번 코어에서 따로 돈다. (loop()는 1번 코어)
  xTaskCreatePinnedToCore(sendTask, "send", 8192, nullptr, 1, nullptr, 0);
  nextMeasureAt = millis();
}

void loop() {
  unsigned long now = millis();
  if (now - lastClimateReadAt >= CLIMATE_INTERVAL_MS) {
    lastClimateReadAt = now;
    readClimate(now);
  }
  // 정해진 시각에 맞춰 잰다. 측정이 조금 늦어도 다음 측정 시각은 밀리지 않는다.
  if ((long) (now - nextMeasureAt) >= 0) {
    nextMeasureAt += SEND_INTERVAL_MS;
    if ((long) (now - nextMeasureAt) >= 0) {
      nextMeasureAt = now + SEND_INTERVAL_MS;  // 크게 밀렸으면(Wi-Fi 재연결 등) 따라잡지 않고 지금부터 다시 센다.
    }
    measure(now);
  }
  delay(5);
}

void measure(unsigned long now) {
  if (WiFi.status() != WL_CONNECTED) {
    // 연결을 시도하는 도중에 다시 부르면 처음부터 다시 하게 되므로 몇 초에 한 번만 다시 붙는다.
    if (now - lastReconnectAt >= RECONNECT_INTERVAL_MS) {
      lastReconnectAt = now;
      Serial.println("Wi-Fi가 끊겨 있어 다시 연결합니다.");
      WiFi.reconnect();
    }
    return;
  }
  Reading reading;
  reading.distance = measureDistance();
  reading.withClimate = hasClimate && now - lastClimateOkAt <= CLIMATE_STALE_MS;
  reading.temperature = temperature;
  reading.humidity = humidity;
  // 아직 못 보낸 값이 줄에 남아 있으면 그 값은 버리고 새 값으로 바꾼다.
  if (uxQueueMessagesWaiting(readings) > 0) {
    skippedReadings++;
  }
  xQueueOverwrite(readings, &reading);
}

// 줄에 들어온 값을 받는 대로 보낸다.
void sendTask(void*) {
  Reading reading;
  unsigned long lastSkipReportAt = 0;
  for (;;) {
    if (xQueueReceive(readings, &reading, portMAX_DELAY) != pdTRUE) {
      continue;
    }
    unsigned long started = millis();
    sendReadings(reading);
    unsigned long elapsed = millis() - started;
    if (elapsed > SEND_INTERVAL_MS) {
      Serial.printf("전송에 %lums 걸렸습니다. 1초보다 오래 걸려 그동안 잰 값은 건너뜁니다.\n", elapsed);
    }
    // 건너뛴 값이 있으면 10초에 한 번만 알린다.
    if (skippedReadings > 0 && millis() - lastSkipReportAt >= 10000) {
      lastSkipReportAt = millis();
      Serial.printf("최근 전송이 느려 %lu개 값을 건너뛰었습니다. 계속되면 HTTP 주소(service.gsmsv.site:22107)를 써 보세요.\n",
                    (unsigned long) skippedReadings);
      skippedReadings = 0;
    }
  }
}

// 온습도를 읽어 둔다. 실패하면(배선 불량, 너무 자주 읽음 등) 이전 값을 그대로 둔다.
void readClimate(unsigned long now) {
  float t = dht.readTemperature();
  float h = dht.readHumidity();
  if (isnan(t) || isnan(h)) {
    Serial.printf("온습도 센서를 읽지 못했습니다. DATA 핀(P%d)과 DHT_TYPE을 확인하세요.\n", DHT_PIN);
    return;
  }
  if (t < MIN_TEMPERATURE || t > MAX_TEMPERATURE || h < 0 || h > 100) {
    Serial.printf("온습도 값이 이상합니다.: %.1f°C, %.1f%%\n", t, h);
    return;
  }
  temperature = t;
  humidity = h;
  hasClimate = true;
  lastClimateOkAt = now;
}

// 한 번 잰 전방 거리(cm). 반사가 돌아오지 않으면 최대 거리로 본다.
float measureOnce() {
  digitalWrite(TRIG_PIN, LOW);
  delayMicroseconds(2);
  digitalWrite(TRIG_PIN, HIGH);
  delayMicroseconds(10);
  digitalWrite(TRIG_PIN, LOW);

  unsigned long duration = pulseIn(ECHO_PIN, HIGH, ECHO_TIMEOUT_US);
  if (duration == 0) {
    return MAX_DISTANCE_CM;
  }
  // 소리가 갔다가 돌아온 시간이므로 2로 나눈다.
  float distance = duration * CM_PER_MICROSECOND / 2.0;
  return distance > MAX_DISTANCE_CM ? MAX_DISTANCE_CM : distance;
}

// 세 번 재서 가운데 값을 돌려준다.
float measureDistance() {
  float values[SAMPLES];
  for (int i = 0; i < SAMPLES; i++) {
    values[i] = measureOnce();
    delay(10);  // 앞 측정의 반사음이 사라질 때까지 기다린다.
  }
  for (int i = 0; i < SAMPLES - 1; i++) {
    for (int j = 0; j < SAMPLES - 1 - i; j++) {
      if (values[j] > values[j + 1]) {
        float swap = values[j];
        values[j] = values[j + 1];
        values[j + 1] = swap;
      }
    }
  }
  return values[SAMPLES / 2];
}

void sendReadings(const Reading& reading) {
  float distance = reading.distance;
  bool withClimate = reading.withClimate;
  float temperature = reading.temperature;
  float humidity = reading.humidity;
  bool started = useHttps ? http.begin(secureClient, SERVER_URL) : http.begin(plainClient, SERVER_URL);
  if (!started) {
    Serial.println("서버 주소(SERVER_URL)를 확인하세요.");
    return;
  }
  http.addHeader("Content-Type", "application/json");
  if (strlen(DEVICE_TOKEN) > 0) {
    http.addHeader("X-Device-Token", DEVICE_TOKEN);
  }

  // 온습도를 못 읽고 있으면 빼고 거리만 보낸다. (서버는 온습도가 없어도 받는다)
  char body[96];
  if (withClimate) {
    snprintf(body, sizeof(body), "{\"distance\":%.1f,\"temperature\":%.1f,\"humidity\":%.1f}",
             distance, temperature, humidity);
  } else {
    snprintf(body, sizeof(body), "{\"distance\":%.1f}", distance);
  }
  int status = http.POST(body);

  if (status == 201) {
    if (withClimate) {
      Serial.printf("거리 %.1fcm, 온도 %.1f°C, 습도 %.1f%% 보냄\n", distance, temperature, humidity);
    } else {
      Serial.printf("거리 %.1fcm 보냄 (온습도 없음)\n", distance);
    }
  } else if (status > 0) {
    // 400(값 오류), 401(토큰 오류) 등. 서버가 이유를 JSON으로 알려 준다.
    Serial.printf("서버가 받지 않았습니다 (%d): %s\n", status, http.getString().c_str());
  } else {
    Serial.printf("서버에 보내지 못했습니다: %s\n", http.errorToString(status).c_str());
  }
  http.end();
}
