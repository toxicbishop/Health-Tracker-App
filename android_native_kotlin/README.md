# 🏥 Health Track

A comprehensive health tracking Android application built with **Kotlin** and **Jetpack Compose**. Track your vitals, medications, mood, and health trends all in one beautiful, modern interface.

![Android](https://img.shields.io/badge/Android-3DDC84?style=for-the-badge&logo=android&logoColor=white)
![Kotlin](https://img.shields.io/badge/Kotlin-7F52FF?style=for-the-badge&logo=kotlin&logoColor=white)
![Jetpack Compose](https://img.shields.io/badge/Jetpack%20Compose-4285F4?style=for-the-badge&logo=jetpackcompose&logoColor=white)

## ✨ Features

### 📊 Health Dashboard
- Real-time date and time display
- Quick overview of daily health metrics
- Mood logging with emoji-based selection
- Active prescription tracking
- Clinical reports generation

### 💊 Medication Management
- Track active prescriptions
- Set medication reminders with custom times
- Daily/Weekly/Custom frequency scheduling
- Toggle reminders on/off
- Progress tracking for medication adherence

### 📈 Vitals & Trends
- Weight tracking with trend analysis
- Blood pressure monitoring
- Interactive charts and graphs
- Date range filtering (7 days, 30 days, 3 months, 1 year)
- Health insights and patterns

### 📅 Track Screen
- Log daily health entries
- Quick action buttons for common logs
- Month/date navigation
- Search and filter capabilities
- Floating action button for quick entry

### 👤 User Profile
- Persistent login (remembers user)
- Dark/Light theme toggle
- Personal information management
- Health goals tracking
- Secure sign-out

## 🛠️ Tech Stack

- **Language:** Kotlin
- **UI Framework:** Jetpack Compose
- **Architecture:** Single Activity with Compose Navigation
- **State Management:** Compose State & Remember
- **Persistence:** SharedPreferences
- **Styling:** Material 3 Design with custom theming

## 🚀 Getting Started

### Prerequisites

- Android Studio Arctic Fox or later
- JDK 17 or later
- Android SDK 34

### Installation

1. **Clone the repository**

   ```bash
   git clone https://github.com/toxicbishop/Health-Tracker-App.git
   cd Health-Tracker-App
   ```

2. **Open in Android Studio**
   - Open Android Studio
   - Select "Open an existing project"
   - Navigate to the cloned directory

3. **Build the project**
   - Wait for Gradle sync to complete
   - Build → Build Bundle(s) / APK(s) → Build APK(s)

4. **Run on device/emulator**
   - Connect an Android device or start an emulator
   - Click the Run button (▶)

### Building with Docker

```bash
# Build the Docker image
docker build -t health-track .

# Run the container to build APK
docker run --rm -v $(pwd)/output:/app/app/build/outputs health-track
```

## 📁 Project Structure

```text
Health-Tracker-App/
├── app/
│   ├── src/
│   │   └── main/
│   │       ├── java/com/first/mynew_application/
│   │       │   └── MainActivity.kt    # Main app code
│   │       └── res/
│   │           └── values/
│   │               └── strings.xml     # App strings
│   └── build.gradle.kts
├── gradle/
├── .gitignore
├── .dockerignore
├── Dockerfile
├── build.gradle.kts
├── settings.gradle.kts
└── README.md
```

## 🎨 Design Features

- **Dark & Light Theme:** Full support for both themes with persistent preference
- **Modern UI:** Glassmorphism, smooth gradients, and micro-animations
- **Responsive Layout:** Adapts to different screen sizes
- **Material 3:** Following latest Material Design guidelines

## 🔐 Security Features

- Input validation on all forms
- Session persistence with SharedPreferences
- Secure sign-out functionality
- Password field masking

## 📋 Future Enhancements

- [ ] Cloud sync with Firebase
- [ ] Biometric authentication
- [ ] Export data to PDF/CSV
- [ ] Doctor appointment scheduling
- [ ] Push notifications for reminders
- [ ] Wearable device integration
- [ ] Multi-language support

## 🤝 Contributing

Contributions are welcome! Please feel free to submit a Pull Request.

1. Fork the project
2. Create your feature branch (`git checkout -b feature/AmazingFeature`)
3. Commit your changes (`git commit -m 'Add some AmazingFeature'`)
4. Push to the branch (`git push origin feature/AmazingFeature`)
5. Open a Pull Request

## 📄 License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

## 👨‍💻 Author

**toxicbishop**

- GitHub: [@toxicbishop](https://github.com/toxicbishop)

## 🙏 Acknowledgments

- [Jetpack Compose](https://developer.android.com/jetpack/compose) for the modern UI toolkit
- [Material Design 3](https://m3.material.io/) for design guidelines
- Android community for inspiration and support

---

<p align="center">
  Made with ❤️ for better health tracking
</p>