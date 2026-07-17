#include <ESP8266WiFi.h>

// Change these to your classroom details
const char* ssid = "MCA_ROOM_101";
const char* password = "attendance123";

void setup() {
  Serial.begin(115200);

  Serial.println();
  Serial.println("Starting ESP-12E...");

  // Start ESP as Wi-Fi Access Point
  WiFi.mode(WIFI_AP);
  WiFi.softAP(ssid, password);

  Serial.println("Wi-Fi Access Point Started");
  Serial.print("SSID: ");
  Serial.println(ssid);

  Serial.print("Password: ");
  Serial.println(password);

  Serial.print("IP Address: ");
  Serial.println(WiFi.softAPIP());
}

void loop() {
  // Print number of connected devices every 5 seconds
  Serial.print("Connected Devices: ");
  Serial.println(WiFi.softAPgetStationNum());

  delay(5000);
}