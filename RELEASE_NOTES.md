# 🏥 Health Track v2.4.0 Release Notes

**Release Date:** January 30, 2026

---

## ✨ What's New

### 🔐 Persistent User Login

- **Stay logged in** — Users now remain logged in across app restarts
- Session credentials securely stored using SharedPreferences
- Automatic redirect to home screen for returning users
- Clean sign-out functionality that properly clears session data

### 🎨 Enhanced UI/UX

- **Improved Sign-In Experience** — Added comprehensive form validation with real-time error messages
- Clear error indicators for empty fields and invalid email formats
- Password minimum length requirement (4 characters)
- Loading spinner during sign-in process
- **Better Visual Feedback** — Error messages now display in styled containers with proper theming

### 💊 Medication Management Improvements

- **Interactive Reminder Toggles** — Enable/disable individual medication reminders with a single tap
- **Add New Reminder Times** — New time picker dialog to add custom medication schedules
- **Frequency Selection** — Daily, Weekly, and Custom scheduling options with segmented control
- **Progress Tracking** — Visual progress bar showing daily medication completion status
- **Edit Reminders** — Tap to edit existing reminder details

### 📅 Track Screen Enhancements

- **Floating Action Button (FAB)** — Quick access to add new health entries
- **Add Entry Dialog** — Log Weight, Blood Pressure, Mood, or Medication entries on the fly
- **Month/Date Navigation** — Interactive date picker for browsing health history
- **Search & Filter** — Quick access to search and filter functionality (icons)
- **Quick Actions** — Convenient "Log Weight" and "Log BP" buttons

### 📈 Clinical Reports

- **Monthly Health Summary** — View comprehensive health overview including:
  - Weight and Blood Pressure averages
  - Active medications count
  - Medication adherence percentage
  - AI-generated health insights
- **Export Options** — PDF Report, CSV Data, and Share with Doctor functionality

### 🌗 Theme Improvements

- **Persistent Theme Preference** — Dark/Light mode choice saved and restored on app launch
- Improved color consistency across all screens in both themes
- Fixed text visibility issues in dark mode input fields

### ⚡ Performance & Navigation

- **Removed Navigation Transitions** — Instant screen changes for snappier experience
- Optimized navigation flow with proper back stack handling
- Clean logout that clears navigation history

---

## 🐛 Bug Fixes

- Fixed sign-in validation not working properly for empty fields
- Fixed text not visible in dark mode on certain input fields
- Fixed theme toggle not persisting after app restart
- Fixed navigation back stack issues on logout
- Fixed reminder toggle state not updating correctly

---

## 🛠️ Technical Details

- **Target SDK:** 34
- **Language:** Kotlin
- **UI Framework:** Jetpack Compose with Material 3
- **Architecture:** Single Activity with Compose Navigation
- **State Management:** Compose State & Remember
- **Persistence:** SharedPreferences

---

## 📋 Known Issues

- Push notifications for medication reminders are not yet functional (coming in v2.5.0)
- Export to PDF/CSV generates placeholder data
- Wearable device integration not yet available

---

## 🔜 Coming in v2.5.0

- [ ] Firebase Cloud Sync
- [ ] Biometric Authentication (Fingerprint/Face ID)
- [ ] Push Notifications for Medication Reminders
- [ ] Data Export to PDF/CSV
- [ ] Doctor Appointment Scheduling

---

## 📦 Installation

Download the latest APK from the [Releases](https://github.com/toxicbishop/Health-Tracker-App/releases) page.

### Requirements

- Android 7.0 (API 24) or higher
- ~15 MB storage space

---

## 🙏 Acknowledgments

Thank you to all our beta testers for their valuable feedback!

---

**Full Changelog:** [v2.3.0...v2.4.0](https://github.com/toxicbishop/Health-Tracker-App/compare/v2.3.0...v2.4.0)
