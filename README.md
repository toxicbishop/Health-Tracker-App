# Health Tracker

A secure, performance-oriented mobile application for vital health metrics monitoring. Built on a modular 3-tier architecture for reliability and data integrity.

## 🏗️ Architecture

This application implements a robust 3-tier architecture to ensure clear separation of concerns:

### 1. Presentation Layer (Mobile Client)
- **Role**: React Native application.
- **Responsibility**: User interface management, local state persistence, and communication with the Application Layer via structured JSON payloads.
- **Key Features**: Offline data queuing, secure PIN authentication, and visual trend analysis.

### 2. Application Layer (Backend Logic)
- **Role**: Google Apps Script (Serverless).
- **Responsibility**: Input validation, business logic enforcement, and secure data routing. Acts as a middleware between the client and the persistence layer.

### 3. Data Layer (Persistence)
- **Role**: Google Sheets (Cloud Storage).
- **Responsibility**: Long-term data durability and structured storage.

---

## 📂 Project Structure

```text
Mom-health-tracker/
├── backend/            # Backend integration logic (Google Apps Script)
├── mobile/             # Cross-platform mobile application source
│   ├── assets/         # System assets and branding
│   ├── App.js          # Core application logic and routing
│   ├── Constants.js    # System configuration
│   ├── app.json        # Manifest configuration
│   └── package.json    # Dependency management
├── Dockerfile          # Web environment configuration
├── LICENSE             # MIT License
└── README.md           # Technical documentation
```

---

## Features

### 🔐 Authentication & Security
- **Secure Sign In/Up**: Dark-themed login screens with modern aesthetics.
- **PIN Protection**: Quick access via PIN verification.
- **Dark/Light Mode**: Full theme support with a toggle in Profile settings.

### 📊 Health Dashboard
- **Vitals Tracking**: Log Weight and Blood Pressure with ease.
- **Progress Indicators**: Visual trackers for health goals.
- **Quick Logging**: Easy access buttons for daily logs.

### 💊 Medication Management
- **Meds Tab**: dedicated screen for managing prescriptions (e.g., Metformin).
- **Adherence Tracking**: "Today's Progress" bars and completion stats.
- **Reminders**: Custom schedules (Daily/Weekly) with alarm toggles.

### 📈 Analytics & History
- **Trends**: Visual weight graphs and blood pressure analysis.
- **Insights**: Auto-generated health insights based on your data patterns.
- **Calendar/History**: Detailed log history grouped by date (Today/Yesterday).

### ⚙️ Profile
- **Personal Settings**: manage account info.
- **Theme Toggle**: Switch between Professional Light and Slate Dark modes.

## Tech Stack
- **Language**: Kotlin
- **UI Framework**: Jetpack Compose (Material3)
- **Navigation**: Jetpack Navigation Compose
- **Networking**: Retrofit + Gson
- **Architecture**: MVVM (Model-View-ViewModel)

## How to Run

1. **Prerequisites**: Android Studio Hedgehog or newer.
2. **Open Project**: Open the android project folder in Android Studio.
3. **Sync Gradle**: Ensure all dependencies (`retrofit`, `navigation-compose`, etc.) are downloaded.
4. **Run**: Select an emulator or physical device and click **Run**.

## Project Structure
- `MainActivity.kt`: Contains the entry point and all Composable screens (Login, Home, Trends, Meds, Profile).
- `Theme Engine`: Custom `AppTheme` with swappable `LightPalette` and `DarkPalette`.

---

## �️ Technical Setup

### 1. Persistence Initialization
1.  Initialize a new Google Sheet.
2.  Note the primary sheet identifier.

### 2. Backend Deployment
1.  Access the Apps Script editor from the Google Sheet.
2.  Deploy the code provided in `backend/Code.gs`.
3.  Configure as a Web App with public access (for secure relay from the mobile client).
4.  Retain the generated deployment URL.

### 3. Client Configuration
1.  Update `mobile/Constants.js`.
2.  Configure the `API_URL` with the deployment endpoint from the previous step.

---

## 📱 Application Execution

1.  Navigate to the `mobile` directory.
2.  Install required dependencies:
    ```bash
    npm install
    ```
3.  Initialize the Expo development environment:
    ```bash
    npx expo start
    ```
4.  Deploy to a physical device via Expo Go or an emulator.

---

## 🐳 Containerized Deployment

To execute the web-compatible version within a containerized environment:
```bash
docker build -t health-tracker .
docker run -p 8080:80 health-tracker
```
Access the application at `http://localhost:8080`.

---

## Key Functionalities
- **Secure Authentication**: End-to-end PIN-based access control.
- **Smart Data Synchronization**: Intelligent offline queuing system for unreliable network conditions.
- **Automated Reporting**: Integrated PDF generation for historical data analysis.
- **Performance Visualization**: Local charting of health trends and progress metrics.
- **Responsive Layout**: Standardized UI components across all application states.

---

## 📜 License

Distributed under the MIT License. See [LICENSE](./LICENSE) for full details.
