#include <WiFi.h>
#include <esp_now.h>
#include <esp_wifi.h>
#include <Wire.h>
#include <vl53l4cd_class.h>

VL53L4CD sensor(&Wire, 0x29);

typedef struct {
  int distance;
} Data;

Data data;

uint8_t broadcastAddress[] = {0xFF,0xFF,0xFF,0xFF,0xFF,0xFF};

uint8_t pairedMAC[6];
bool paired = false;

// timing
unsigned long lastSendTime = 0;
const unsigned long sendInterval = 3000;

unsigned long lastPeerCheck = 0;
const unsigned long peerCheckInterval = 5000;

// fail-safe
int failCount = 0;
const int maxFails = 10;

// peer memory
#define MAX_PEERS 5
uint8_t peerList[MAX_PEERS][6];
int peerCount = 0;

// FIFO peer management
void addPeerFIFO(uint8_t *newMAC) {
  for (int i = 0; i < peerCount; i++) {
    if (memcmp(peerList[i], newMAC, 6) == 0) return;
  }

  if (peerCount == MAX_PEERS) {
    esp_now_del_peer(peerList[0]);
    for (int i = 1; i < MAX_PEERS; i++) {
      memcpy(peerList[i - 1], peerList[i], 6);
    }
    peerCount--;
  }

  memcpy(peerList[peerCount], newMAC, 6);
  peerCount++;

  esp_now_peer_info_t peerInfo = {};
  memcpy(peerInfo.peer_addr, newMAC, 6);
  peerInfo.channel = 1;
  peerInfo.encrypt = false;

  esp_now_add_peer(&peerInfo);
}

// receive (pairing)
void onReceive(const esp_now_recv_info *info, const uint8_t *incomingData, int len) {

  if (!paired) {
    memcpy(pairedMAC, info->src_addr, 6);
    paired = true;

    Serial.println("⚡ Connected!");
    addPeerFIFO((uint8_t*)info->src_addr);
  }

  failCount = 0;
}

// send status
void onSent(const wifi_tx_info_t *info, esp_now_send_status_t status) {
  if (status == ESP_NOW_SEND_SUCCESS) {
    failCount = 0;
  } else {
    failCount++;
  }
}

void setup() {
  Serial.begin(115200);
  delay(1500);

  Wire.begin(6, 7);

  sensor.begin();
  sensor.InitSensor();
  sensor.VL53L4CD_StartRanging();

  // 🔥 FIXED CHANNEL SYNC
  WiFi.mode(WIFI_STA);
  WiFi.disconnect(true);
  delay(1000);

  esp_wifi_set_promiscuous(true);
  esp_wifi_set_channel(1, WIFI_SECOND_CHAN_NONE);
  esp_wifi_set_promiscuous(false);
  delay(500);

  if (esp_now_init() != ESP_OK) {
    Serial.println("ESP-NOW init failed");
    return;
  }

  // broadcast peer
  esp_now_peer_info_t peerInfo = {};
  memcpy(peerInfo.peer_addr, broadcastAddress, 6);
  peerInfo.channel = 1;
  peerInfo.encrypt = false;
  esp_now_add_peer(&peerInfo);

  esp_now_register_recv_cb(onReceive);
  esp_now_register_send_cb(onSent);

  Serial.println("🚀 Sender Ready");
}

void loop() {

  // 🔴 NOT CONNECTED
  if (!paired) {

    // check peer list every 5 sec
    if (millis() - lastPeerCheck >= peerCheckInterval) {

      if (peerCount > 0) {
        Serial.println("🔍 Checking peer list...");

        for (int i = 0; i < peerCount; i++) {
          data.distance = 999;
          esp_now_send(peerList[i], (uint8_t *)&data, sizeof(data));
          delay(50);
        }
      }

      lastPeerCheck = millis();
    }

    // broadcast
    data.distance = 999;

    for (int i = 0; i < 10; i++) {
      esp_now_send(broadcastAddress, (uint8_t *)&data, sizeof(data));
      delayMicroseconds(1000);
    }

    Serial.println("📢 Broadcasting...");
    delay(50);

    return;
  }

  // 🟢 CONNECTED MODE
  if (millis() - lastSendTime >= sendInterval) {

    uint8_t ready = 0;
    sensor.VL53L4CD_CheckForDataReady(&ready);

    if (ready) {
      VL53L4CD_Result_t results;
      sensor.VL53L4CD_GetResult(&results);

      int dist = results.distance_mm;

      if (dist > 10 && dist < 1300) {
        data.distance = dist;

        esp_now_send(pairedMAC, (uint8_t *)&data, sizeof(data));

        Serial.print("📡 Distance: ");
        Serial.println(data.distance);
      } else {
        Serial.println("⚠️ Invalid reading ignored");
      }

      sensor.VL53L4CD_ClearInterrupt();
      lastSendTime = millis();
    }
  }

  // reconnect
  if (paired && failCount > maxFails) {
    Serial.println("🔄 Restarting connection");

    esp_now_del_peer(pairedMAC);
    paired = false;
    failCount = 0;
  }

  delay(50);
}
