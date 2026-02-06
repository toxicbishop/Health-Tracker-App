# Google Sheets Integration Setup Instructions

## Current Status ✅
The Android app has been fixed with:
- ✅ Proper API endpoint configuration
- ✅ Enhanced error handling and debugging
- ✅ Network permissions configured
- ✅ Offline support (data saves locally if API fails)
- ✅ Test functionality included

## Required Google Apps Script Setup

### Step 1: Create Google Apps Script
1. Go to [script.google.com](https://script.google.com)
2. Click "New Project"
3. Copy the code from `google_apps_script_template.gs`
4. Replace `YOUR_SPREADSHEET_ID` with your actual Google Sheet ID
5. Save the script

### Step 2: Create Google Sheet
1. Go to [sheets.google.com](https://sheets.google.com)
2. Create a new spreadsheet named "Health Track Data"
3. Create a sheet named "HealthData"
4. Add headers: `Timestamp`, `Email`, `Type`, `Value1`, `Value2`
5. Get the spreadsheet ID from the URL (between `/d/` and `/edit`)

### Step 3: Deploy the Script
1. In Google Apps Script, click "Deploy" > "New Deployment"
2. Select type: "Web app"
3. Configuration:
   - Description: "Health Track API"
   - Execute as: "Me"
   - Who has access: "Anyone" (or "Anyone with Google account")
4. Click "Deploy"
5. Copy the Web app URL
6. Update `SCRIPT_URL` in MainActivity.kt with your new URL

### Step 4: Update Android App
1. In `MainActivity.kt`, line ~149:
   ```kotlin
   private const val SCRIPT_URL = "YOUR_GOOGLE_APPS_SCRIPT_URL"
   ```
2. Replace with your deployed script URL

## Testing the Integration

### Using the Test Button
1. Open the app
2. On the Home screen, click "Test API" button
3. Check the result:
   - ✅ Success: API is working
   - ❌ Failed: Check your Google Apps Script setup

### Testing Data Flow
1. Log weight or blood pressure data
2. Check your Google Sheet for new entries
3. Check Android logcat for DEBUG messages

## Common Issues & Solutions

### Issue: "API Connection Failed"
- Check if Google Apps Script is deployed correctly
- Verify the URL in MainActivity.kt
- Check internet permissions in AndroidManifest.xml ✅
- Make sure the script is shared with "Anyone"

### Issue: "No data in Google Sheet"
- Check if spreadsheet ID is correct
- Verify the sheet name is "HealthData"
- Check script execution logs in Google Apps Script
- Ensure API is deployed as Web app

### Issue: "Network errors"
- Check network connectivity
- Verify `usesCleartextTraffic="true"` in AndroidManifest.xml ✅
- Check if VPN or firewall is blocking requests

## Debug Information

The app now includes comprehensive debugging:
- Network requests are logged in Logcat
- API responses are logged
- Test functionality is available
- Offline mode saves data locally

## Security Notes

⚠️ **Important Security Considerations:**
- Anyone with the script URL can send data to your sheet
- Consider adding authentication in the Google Apps Script
- Regularly check your Google Sheet for unauthorized entries
- Consider limiting access to specific Google accounts

## Alternative: Use Firebase

If Google Sheets integration is too complex, consider using Firebase:
1. Create Firebase project
2. Add Firebase SDK to Android app
3. Use Firestore for data storage
4. More secure and scalable than Google Sheets