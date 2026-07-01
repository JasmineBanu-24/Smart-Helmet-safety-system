#include <WiFi.h>
#include <esp_now.h>
#include <esp_wifi.h>

typedef struct {
  int distance;
} Data;

Data incomingData;

// receive callback
void onReceive(const esp_now_recv_info *info, const uint8_t *data, int len) {

  memcpy(&incomingData, data, sizeof(incomingData));

  Serial.print("📥 Distance: ");
  Serial.println(incomingData.distance);

  // ensure peer exists
  if (!esp_now_is_peer_exist(info->src_addr)) {

    esp_now_peer_info_t peerInfo = {};
    memcpy(peerInfo.peer_addr, info->src_addr, 6);
    peerInfo.channel = 1;
    peerInfo.encrypt = false;

    if (esp_now_add_peer(&peerInfo) == ESP_OK) {
      Serial.println("✅ Peer added");
    } else {
      Serial.println("❌ Peer add failed");
      return;
    }
  }

  // send ACK
  esp_now_send(info->src_addr, (uint8_t *)&incomingData, sizeof(incomingData));
}

void setup() {
  Serial.begin(115200);
  delay(1000);

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

  esp_now_register_recv_cb(onReceive);

  Serial.println("⚡ Receiver Ready");
}

void loop() {}
