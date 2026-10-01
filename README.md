# Kynox

<p align="center">
  <strong>Kynox — Android System Utility & Performance Monitor</strong>
</p>

<p align="center">
  Aplikasi utilitas Android untuk memantau perangkat, performa sistem, jaringan, thermal, refresh rate, dan berbagai informasi sistem lainnya.
</p>

<p align="center">
  <img src="https://img.shields.io/badge/version-2.0.0-00BFA6?style=for-the-badge">
  <img src="https://img.shields.io/badge/platform-Android-3DDC84?style=for-the-badge&logo=android&logoColor=white">
  <img src="https://img.shields.io/badge/language-Kotlin-7F52FF?style=for-the-badge&logo=kotlin&logoColor=white">
  <img src="https://img.shields.io/badge/UI-Jetpack%20Compose-4285F4?style=for-the-badge">
</p>

---

## 📱 Tentang Kynox

**Kynox** adalah aplikasi utilitas dan monitoring sistem Android yang dirancang untuk memberikan informasi perangkat secara real-time dalam satu tempat.

Kynox berfokus pada:

- Monitoring performa perangkat
- Monitoring CPU, GPU, RAM, baterai dan thermal
- Monitoring jaringan
- Monitoring refresh rate
- Informasi perangkat
- Diagnostik sistem
- Monitoring SELinux
- Root utilities
- Pencatatan sesi performa
- Snapshot kondisi perangkat

Kynox menggunakan antarmuka modern berbasis **Jetpack Compose** dengan desain dark, minimal, dan berorientasi pada informasi.

---

## ✨ Fitur

### 🏠 Beranda

- Temperatur perangkat
- CPU usage
- GPU usage
- RAM usage
- Baterai
- Thermal status
- GPU telemetry
- Root status

### ⚡ Performa

- CPU monitoring
- GPU monitoring
- RAM monitoring
- Battery monitoring
- Thermal monitoring
- Refresh rate monitoring
- Refresh rate per aplikasi
- Performance information

### 📱 Perangkat

- Device information
- Hardware information
- Android information
- Display information
- Rekam Sesi
- Kynox Snapshot
- Session Compare

### 🧰 Lainnya

- Automation Rules
- Network Monitor
- Network Activity History
- SELinux Monitor
- Command Console
- Kynox Diagnostics

---

## 🔐 Root

Kynox dapat memanfaatkan akses root untuk fitur tertentu.

Root **tidak wajib** untuk menjalankan fitur dasar aplikasi.

> ⚠️ Jangan menjalankan command yang tidak dipahami. Penggunaan akses root dapat menyebabkan perubahan pada sistem Android.

---

## 🛠️ Tech Stack

| Teknologi | Penggunaan |
|---|---|
| Kotlin | Bahasa pemrograman |
| Jetpack Compose | UI |
| Material 3 | Design system |
| Gradle | Build system |
| Android SDK | Platform |
| Coroutines | Asynchronous processing |
| Android Services | Background monitoring |
| ADB / Shell | System interaction |
| Root | Optional system-level access |

---

## 📦 Struktur Project

```text
Kynox/
├── app/
│   ├── src/
│   │   └── main/
│   │       ├── java/
│   │       │   └── com/kynox/gaming/
│   │       └── res/
│   └── build.gradle.kts
├── gradle/
├── build.gradle.kts
├── settings.gradle.kts
├── gradle.properties
└── README.md
```

---