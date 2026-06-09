# Karoo Forumslader Extension

⚠️ **This project is currently WIP and not ready** ⚠️s

This project is a [Hammerhead Karoo](https://www.hammerhead.io/) extension that integrates the **Forumslader** (a versatile bike dynamo charger and battery system) into your Karoo cycling computer.

## Purpose

The extension allows Karoo users to monitor real-time data from their Forumslader device directly on their head unit. It handles device discovery, establishes a secure Bluetooth Low Energy (BLE) connection, and streams live sensor data.

## Key Features

- **Device Discovery**: Seamlessly scan and connect to your Forumslader device within the Karoo sensors menu.
- **Real-time Data Streaming**:
    - **Battery Level (%)**: Monitor the current charge state of your Forumslader battery.
    - **Speed (km/h)**: View accurate speed data derived directly from the dynamo.
    - **Voltage (V)**: Keep an eye on the system voltage for health monitoring.
- **Background Operation**: The extension runs as a background service, ensuring data is available even when the main companion app is not in the foreground.

## Architecture

Following the project's [Agents Guidelines](agents.md), the codebase is organized using a clean architecture approach:
- **Domain**: Pure Kotlin logic for parsing the Forumslader data protocol.
- **Adapters/Framework**: BLE connection management and integration with the Hammerhead Karoo SDK.

## Requirements

- Hammerhead Karoo (Karoo 2 or Karoo 3)
- Forumslader with Bluetooth (BLE) support
- Android 8.0 (API 26) or higher (as per Karoo SDK)

## Acknowledgments

This project is heavily influenced by and takes inspiration from [cyberman54/Forumslader-Companion](https://github.com/cyberman54/Forumslader-Companion). I'm grateful for the excellent work on the Forumslader communication protocol implementation, which served as a blueprint for my Bluetooth Low Energy communication approach in this extension. Special thanks for paving the way!

## Links

- [Hammerhead Karoo SDK Documentation](https://hammerheadnav.github.io/karoo-ext/index.html)
- [Forumslader Official Website](http://www.forumslader.de/)
- [Forumslader-Companion (Inspiration)](https://github.com/cyberman54/Forumslader-Companion)
