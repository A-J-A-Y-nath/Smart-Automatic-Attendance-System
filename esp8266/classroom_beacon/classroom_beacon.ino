#include <ESP8266WiFi.h>

// Classroom Details
const char* ssid = "MCA_ROOM_101";
const char* password = "attendance123";

void setup() {

  Serial.begin(115200);

  Serial.println();
  Serial.println("================================");
  Serial.println(" Classroom Beacon Starting...");
  Serial.println("================================");

  // ESP works only as Access Point
  WiFi.mode(WIFI_AP);

  bool result = WiFi.softAP(
      ssid,
      password,
      6,      // Wi-Fi Channel
      false,  // Hidden SSID? false = visible
      50      // Maximum connected devices
  );

  if(result)
  {
      Serial.println("Access Point Started Successfully!");
  }
  else
  {
      Serial.println("Failed to Start AP");
  }

  Serial.print("SSID : ");
  Serial.println(ssid);

  Serial.print("Password : ");
  Serial.println(password);

  Serial.print("IP Address : ");
  Serial.println(WiFi.softAPIP());

  Serial.print("Channel : ");
  Serial.println(WiFi.channel());

  Serial.println("--------------------------------");
}

void loop()
{
    delay(5000);

    Serial.println("Beacon Running...");

    Serial.print("Connected Devices : ");
    Serial.println(WiFi.softAPgetStationNum());
}