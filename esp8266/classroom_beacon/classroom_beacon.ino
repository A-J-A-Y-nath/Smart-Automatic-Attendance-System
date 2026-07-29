#include <ESP8266WiFi.h>
#include <ESP8266mDNS.h>

const char* ssid = "CAMPUS_WIFI_SSID";
const char* password = "CAMPUS_WIFI_PASSWORD";

void setup() {
  Serial.begin(115200);
  WiFi.mode(WIFI_STA);
  WiFi.begin(ssid, password);

  while (WiFi.status() != WL_CONNECTED) {
    delay(500);
    Serial.print(".");
  }
  Serial.println("");
  Serial.println("WiFi connected");
  Serial.println("IP address: ");
  Serial.println(WiFi.localIP());

  if (MDNS.begin("esp8266-mca101")) {
    MDNS.addService("attendance", "tcp", 80);
    Serial.println("mDNS responder started: _attendance._tcp");
  } else {
    Serial.println("Error setting up MDNS responder!");
  }
}

void loop() {
  MDNS.update();
}