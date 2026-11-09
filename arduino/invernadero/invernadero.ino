// Invernadero IoT - Arduino + DHT + HC-05 + relé
// Cableado: DHT data -> D2 | HC-05 TX -> D10 | HC-05 RX -> D11 (con divisor de voltaje 5V->3.3V) | Relé IN -> D7
#include <SoftwareSerial.h>
#include <DHT.h>

#define DHTPIN 2
#define DHTTYPE DHT11        // cambia a DHT22 si corresponde
#define RELAY 7

SoftwareSerial bt(10, 11);   // RX, TX
DHT dht(DHTPIN, DHTTYPE);
unsigned long last = 0;

void setup() {
  pinMode(RELAY, OUTPUT);
  digitalWrite(RELAY, LOW);
  bt.begin(9600);            // velocidad por defecto del HC-05
  dht.begin();
}

void loop() {
  // Envía una lectura cada 2 s con formato  T:25.3;H:60.1
  if (millis() - last >= 2000) {
    last = millis();
    float h = dht.readHumidity();
    float t = dht.readTemperature();
    if (!isnan(h) && !isnan(t)) {
      bt.print("T:"); bt.print(t, 1);
      bt.print(";H:"); bt.println(h, 1);
    }
  }
  // Recibe comandos FAN:1 / FAN:0
  if (bt.available()) {
    String c = bt.readStringUntil('\n');
    c.trim();
    if (c == "FAN:1") digitalWrite(RELAY, HIGH);
    else if (c == "FAN:0") digitalWrite(RELAY, LOW);
  }
}
