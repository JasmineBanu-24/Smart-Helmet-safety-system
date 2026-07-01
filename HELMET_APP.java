import 'dart:async';
import 'dart:convert';
import 'package:flutter/material.dart';
import 'package:flutter_blue_plus/flutter_blue_plus.dart';
import 'package:permission_handler/permission_handler.dart';

final serviceUUID = Guid("12345678-1234-1234-1234-1234567890ab");
final charUUID = Guid("abcd1234-5678-90ab-cdef-1234567890ab");

void main() {
  runApp(const HelmetApp());
}

class HelmetApp extends StatelessWidget {
  const HelmetApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      debugShowCheckedModeBanner: false,
      theme: ThemeData(
        primaryColor: Colors.deepPurple,
        colorScheme: ColorScheme.fromSeed(seedColor: Colors.deepPurple),
      ),
      home: const SplashPage(),
    );
  }
}

Widget violetBackground({required Widget child}) {
  return Stack(
    children: [
      Positioned.fill(
        child: Image.asset("assets/images/bg.jpeg", fit: BoxFit.cover),
      ),
      child,
    ],
  );
}

Widget splashBackground({required Widget child}) {
  return Stack(
    children: [
      Positioned.fill(
        child: Image.asset("assets/images/splash.jpeg", fit: BoxFit.cover),
      ),
      child,
    ],
  );
}

//////////////////////////////////////////////////////////////

class SplashPage extends StatefulWidget {
  const SplashPage({super.key});

  @override
  State<SplashPage> createState() => _SplashPageState();
}

class _SplashPageState extends State<SplashPage> {
  @override
  void initState() {
    super.initState();

    Future.delayed(const Duration(seconds: 3), () {
      Navigator.pushReplacement(
        context,
        MaterialPageRoute(builder: (_) => const BluetoothPage()),
      );
    });
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      body: splashBackground(
        child: const Center(
          child: Text(
            "தலைக்கவசம்\nஉயிர்க்கவசம்",
            textAlign: TextAlign.center,
            style: TextStyle(
              fontSize: 30,
              fontWeight: FontWeight.bold,
              color: Colors.white,
            ),
          ),
        ),
      ),
    );
  }
}

//////////////////////////////////////////////////////////////

class BluetoothPage extends StatefulWidget {
  const BluetoothPage({super.key});

  @override
  State<BluetoothPage> createState() => _BluetoothPageState();
}

class _BluetoothPageState extends State<BluetoothPage> {
  bool bluetoothOn = false;

  @override
  void initState() {
    super.initState();

    FlutterBluePlus.adapterState.listen((state) {
      setState(() => bluetoothOn = state == BluetoothAdapterState.on);
    });
  }

  Future<void> requestPermissions() async {
    await [
      Permission.bluetoothScan,
      Permission.bluetoothConnect,
      Permission.location,
    ].request();
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      body: violetBackground(
        child: Center(
          child: Card(
            shape: RoundedRectangleBorder(
              borderRadius: BorderRadius.circular(25),
            ),
            child: Padding(
              padding: const EdgeInsets.all(30),
              child: Column(
                mainAxisSize: MainAxisSize.min,
                children: [
                  Icon(
                    Icons.bluetooth,
                    size: 110,
                    color: bluetoothOn ? Colors.green : Colors.red,
                  ),
                  const SizedBox(height: 20),
                  ElevatedButton(
                    onPressed: () async {
                      await FlutterBluePlus.turnOn();
                    },
                    child: const Text("TURN ON BLUETOOTH"),
                  ),
                  const SizedBox(height: 10),
                  ElevatedButton(
                    onPressed: bluetoothOn
                        ? () async {
                            await requestPermissions();
                            Navigator.pushReplacement(
                              context,
                              MaterialPageRoute(
                                builder: (_) => const ConnectionPage(),
                              ),
                            );
                          }
                        : null,
                    child: const Text("CONTINUE"),
                  ),
                ],
              ),
            ),
          ),
        ),
      ),
    );
  }
}

//////////////////////////////////////////////////////////////

class ConnectionPage extends StatefulWidget {
  const ConnectionPage({super.key});

  @override
  State<ConnectionPage> createState() => _ConnectionPageState();
}

class _ConnectionPageState extends State<ConnectionPage> {
  List<ScanResult> devices = [];
  List<ScanResult> filteredDevices = [];

  BluetoothDevice? helmetDevice;
  BluetoothDevice? bikeDevice;

  String search = "";

  @override
  void initState() {
    super.initState();
    startScan();
  }

  ////////////////////////////////////////////////////////////

  void startScan() {
    FlutterBluePlus.startScan(timeout: const Duration(seconds: 5));

    FlutterBluePlus.scanResults.listen((results) {
      setState(() {
        devices = results;

        filteredDevices = devices.where((d) {
          final name = d.device.platformName.toLowerCase();

          return name.contains("helmet_esp") || name.contains("bike_esp");
        }).toList();
      });
    });
  }

  ////////////////////////////////////////////////////////////

  Future connectDevice(BluetoothDevice device, bool helmet) async {
    if (device == helmetDevice || device == bikeDevice) {
      showMsg("This device already connected");
      return;
    }

    await device.connect();

    setState(() {
      if (helmet) {
        helmetDevice = device;
      } else {
        bikeDevice = device;
      }
    });
  }

  ////////////////////////////////////////////////////////////

  void showMsg(String msg) {
    ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(msg)));
  }

  ////////////////////////////////////////////////////////////

  Widget deviceTile(ScanResult r, bool helmet) {
    final name = r.device.platformName.isEmpty
        ? "Unknown Device"
        : r.device.platformName;

    if (helmet && !name.toLowerCase().contains("helmet_esp")) {
      return const SizedBox();
    }

    if (!helmet && !name.toLowerCase().contains("bike_esp")) {
      return const SizedBox();
    }

    return Card(
      child: ListTile(
        title: Text(name),
        subtitle: Text(r.device.remoteId.toString()),
        trailing: ElevatedButton(
          child: const Text("CONNECT"),
          onPressed: () => connectDevice(r.device, helmet),
        ),
      ),
    );
  }

  ////////////////////////////////////////////////////////////

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: Column(
          children: [
            const Text("Helmet & Bike Connection"),
            Text(
              "Helmet: ${helmetDevice?.platformName ?? "Not connected"} | "
              "Bike: ${bikeDevice?.platformName ?? "Not connected"}",
              style: const TextStyle(fontSize: 12),
            ),
          ],
        ),
      ),
      body: violetBackground(
        child: Column(
          children: [
            const SizedBox(height: 10),

            Padding(
              padding: const EdgeInsets.all(12),
              child: TextField(
                decoration: const InputDecoration(
                  hintText: "Search device...",
                  border: OutlineInputBorder(),
                  prefixIcon: Icon(Icons.search),
                ),
                onChanged: (v) {
                  setState(() {
                    search = v;

                    filteredDevices = devices.where((d) {
                      final name = d.device.platformName.toLowerCase();

                      return (name.contains("helmet_esp") ||
                              name.contains("bike_esp")) &&
                          name.contains(v.toLowerCase());
                    }).toList();
                  });
                },
              ),
            ),

            Expanded(
              child: SingleChildScrollView(
                child: Column(
                  children: [
                    const Text(
                      "HELMET CONNECTION",
                      style: TextStyle(fontWeight: FontWeight.bold),
                    ),
                    ...filteredDevices.map((r) => deviceTile(r, true)),

                    const Divider(),

                    const Text(
                      "BIKE CONNECTION",
                      style: TextStyle(fontWeight: FontWeight.bold),
                    ),
                    ...filteredDevices.map((r) => deviceTile(r, false)),

                    const SizedBox(height: 20),
                  ],
                ),
              ),
            ),

            Padding(
              padding: const EdgeInsets.all(12),
              child: ElevatedButton(
                onPressed: helmetDevice != null
                    ? () {
                        Navigator.push(
                          context,
                          MaterialPageRoute(
                            builder: (_) => MonitoringPage(
                              helmet: helmetDevice,
                              bike: bikeDevice,
                            ),
                          ),
                        );
                      }
                    : null,
                child: const Text("CONTINUE"),
              ),
            ),
          ],
        ),
      ),
      floatingActionButton: FloatingActionButton(
        onPressed: startScan,
        child: const Icon(Icons.refresh),
      ),
    );
  }
}

//////////////////////////////////////////////////////////////

class MonitoringPage extends StatefulWidget {
  final BluetoothDevice? helmet;
  final BluetoothDevice? bike;

  const MonitoringPage({super.key, this.helmet, this.bike});

  @override
  State<MonitoringPage> createState() => _MonitoringPageState();
}

class _MonitoringPageState extends State<MonitoringPage> {
  int battery = 0;
  String esp = "-";
  String sensors = "-";
  String service = "-";

  bool showHelmet = true;

  @override
  void initState() {
    super.initState();
    if (widget.helmet != null) {
      listenHelmet();
    }
  }

  void listenHelmet() async {
    List<BluetoothService> services = await widget.helmet!.discoverServices();

    for (var s in services) {
      if (s.uuid == serviceUUID) {
        for (var c in s.characteristics) {
          if (c.uuid == charUUID) {
            await c.setNotifyValue(true);

            c.value.listen((data) {
              try {
                Map jsonData = jsonDecode(utf8.decode(data));

                setState(() {
                  battery = jsonData["battery"];
                  esp = jsonData["esp"];
                  sensors = jsonData["sensors"];
                  service = jsonData["service"];
                });
              } catch (_) {}
            });
          }
        }
      }
    }
  }

  Widget row(String a, String b) {
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 8),
      child: Row(
        mainAxisAlignment: MainAxisAlignment.spaceBetween,
        children: [
          Text(a, style: const TextStyle(fontSize: 18)),
          Text(b, style: const TextStyle(fontSize: 18)),
        ],
      ),
    );
  }

  Widget helmetUI() {
    return Column(
      children: [
        row("Battery", "$battery %"),
        row("ESP32", esp),
        row("Sensors", sensors),
        row("Service Required", service),
      ],
    );
  }

  Widget bikeUI() {
    if (widget.bike == null) {
      return const Text(
        "Bike device not connected.\nPlease go back and connect a bike device.",
        textAlign: TextAlign.center,
      );
    }

    return const Text("Bike monitoring coming soon");
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text("System Monitoring")),
      body: violetBackground(
        child: Center(
          child: Card(
            shape: RoundedRectangleBorder(
              borderRadius: BorderRadius.circular(20),
            ),
            child: Padding(
              padding: const EdgeInsets.all(30),
              child: Column(
                mainAxisSize: MainAxisSize.min,
                children: [
                  Row(
                    mainAxisAlignment: MainAxisAlignment.spaceEvenly,
                    children: [
                      ElevatedButton(
                        onPressed: () => setState(() => showHelmet = true),
                        child: const Text("Helmet Status"),
                      ),
                      ElevatedButton(
                        onPressed: () => setState(() => showHelmet = false),
                        child: const Text("Bike Status"),
                      ),
                    ],
                  ),
                  const SizedBox(height: 20),
                  showHelmet ? helmetUI() : bikeUI(),
                ],
              ),
            ),
          ),
        ),
      ),
    );
  }
}

