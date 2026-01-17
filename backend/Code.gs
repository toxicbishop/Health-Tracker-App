/**
 * Mom's Health Tracker - API Layer
 * This script handles POST requests from the mobile app and appends data to a Google Sheet.
 */

const SHEETS = {
  LOGS: 'HealthLogs',
  MEDS: 'Medications',
  GOALS: 'HealthGoals',
  USER: 'UserSettings',
  MOOD: 'MoodSymptomLogs'
};

// --- CONFIGURATION ---
const RECIPIENT_EMAIL = 'reachshwet@gmail.com'; // Update this to your or Mom's email
const PATIENT_NAME = "Mom";

function doPost(e) {
  try {
    const data = JSON.parse(e.postData.contents);
    const ss = SpreadsheetApp.getActiveSpreadsheet();
    
    // Deletion Support
    if (data.action === 'DELETE_LOG') {
      const sheet = ss.getSheetByName(SHEETS[data.type] || SHEETS.LOGS);
      if (!sheet) {
        return ContentService.createTextOutput(JSON.stringify({ status: 'error', message: 'Sheet not found' })).setMimeType(ContentService.MimeType.JSON);
      }
      const rows = sheet.getDataRange().getValues();
      // Find the row by timestamp ID (assuming timestamp is the last column)
      // Note: This assumes unique timestamps for deletion.
      const index = rows.findIndex(row => row[row.length - 1] === data.id); 
      if (index > -1) {
        sheet.deleteRow(index + 1); // +1 because sheet rows are 1-indexed and header is row 1
        return ContentService.createTextOutput(JSON.stringify({ status: 'success' })).setMimeType(ContentService.MimeType.JSON);
      } else {
        return ContentService.createTextOutput(JSON.stringify({ status: 'error', message: 'Log not found' })).setMimeType(ContentService.MimeType.JSON);
      }
    }

    // Clear Data Support
    if (data.action === 'CLEAR_DATA') {
      const sheet = ss.getSheetByName(SHEETS[data.type]);
      if (sheet) {
        sheet.clearContents();
        // Re-add headers if it's a known sheet type
        if (data.type === 'LOGS') {
          sheet.appendRow(['Date', 'Time', 'Type', 'Weight', 'BP Systolic', 'BP Diastolic', 'Timestamp']);
        } else if (data.type === 'MEDS') {
          sheet.appendRow(['Date', 'Time', 'Med Name', 'Status', 'Timestamp']);
        } else if (data.type === 'GOALS') {
          sheet.appendRow(['Type', 'Value', 'Timestamp']);
        } else if (data.type === 'MOOD') {
          sheet.appendRow(['Date', 'Time', 'Mood', 'Symptoms', 'Notes', 'Timestamp']);
        }
        return ContentService.createTextOutput(JSON.stringify({ status: 'success' })).setMimeType(ContentService.MimeType.JSON);
      } else {
        return ContentService.createTextOutput(JSON.stringify({ status: 'error', message: 'Sheet not found for clearing' })).setMimeType(ContentService.MimeType.JSON);
      }
    }
    
    // Auth Action
    if (data.action === 'CHECK_INIT') {
       const sheet = ss.getSheetByName(SHEETS.USER) || ss.insertSheet(SHEETS.USER);
       return ContentService.createTextOutput(JSON.stringify({ 
         initialized: sheet.getLastRow() > 0 
       })).setMimeType(ContentService.MimeType.JSON);
    }

    if (data.action === 'INITIALIZE_USER') {
       const sheet = ss.getSheetByName(SHEETS.USER) || ss.insertSheet(SHEETS.USER);
       sheet.clear();
       sheet.appendRow(['Key', 'Value']);
       sheet.appendRow(['PIN', data.pin]);
       sheet.appendRow(['Q1', data.q1]);
       sheet.appendRow(['A1', data.a1.toLowerCase().trim()]);
       sheet.appendRow(['Q2', data.q2]);
       sheet.appendRow(['A2', data.a2.toLowerCase().trim()]);
       return ContentService.createTextOutput(JSON.stringify({ status: 'success' }))
         .setMimeType(ContentService.MimeType.JSON);
    }

    if (data.action === 'VERIFY_PIN') {
       const sheet = ss.getSheetByName(SHEETS.USER);
       const userData = sheet.getDataRange().getValues();
       const pinRow = userData.find(row => row[0] === 'PIN');
       const savedPin = pinRow ? pinRow[1].toString() : null;
       
       return ContentService.createTextOutput(JSON.stringify({ 
         status: data.pin === savedPin ? 'success' : 'error'
       })).setMimeType(ContentService.MimeType.JSON);
    }

    if (data.action === 'RESET_PIN') {
       const sheet = ss.getSheetByName(SHEETS.USER);
       const userData = sheet.getDataRange().getValues();
       const a1 = userData.find(row => row[0] === 'A1')[1];
       const a2 = userData.find(row => row[0] === 'A2')[1];

       if (data.a1.toLowerCase().trim() === a1 && data.a2.toLowerCase().trim() === a2) {
         // Update PIN
         const pinIndex = userData.findIndex(row => row[0] === 'PIN');
         sheet.getRange(pinIndex + 1, 2).setValue(data.newPin);
         return ContentService.createTextOutput(JSON.stringify({ status: 'success' }))
           .setMimeType(ContentService.MimeType.JSON);
       }
       return ContentService.createTextOutput(JSON.stringify({ status: 'error', message: 'Answers incorrect' }))
         .setMimeType(ContentService.MimeType.JSON);
    }

    if (data.action === 'GET_QUESTIONS') {
       const sheet = ss.getSheetByName(SHEETS.USER);
       const userData = sheet.getDataRange().getValues();
       return ContentService.createTextOutput(JSON.stringify({ 
         q1: userData.find(row => row[0] === 'Q1')[1],
         q2: userData.find(row => row[0] === 'Q2')[1]
       })).setMimeType(ContentService.MimeType.JSON);
    }

    let sheetName = SHEETS.LOGS;
    if (data.action === 'LOG_MED') sheetName = SHEETS.MEDS;
    if (data.action === 'SAVE_GOAL') sheetName = SHEETS.GOALS;
    if (data.action === 'LOG_MOOD') sheetName = SHEETS.MOOD;

    const sheet = ss.getSheetByName(sheetName) || ss.insertSheet(sheetName);

    // Header logic
    if (sheet.getLastRow() === 0) {
      if (sheetName === SHEETS.LOGS) {
        sheet.appendRow(['Date', 'Time', 'Type', 'Weight', 'BP Systolic', 'BP Diastolic', 'Timestamp']);
      } else if (sheetName === SHEETS.MEDS) {
        sheet.appendRow(['Date', 'Time', 'Med Name', 'Status', 'Timestamp']);
      } else if (sheetName === SHEETS.GOALS) {
        sheet.appendRow(['Type', 'Value', 'Timestamp']);
      } else if (sheetName === SHEETS.MOOD) {
        sheet.appendRow(['Date', 'Time', 'Mood', 'Symptoms', 'Notes', 'Timestamp']);
      }
    }

    const timestamp = new Date();
    const dateStr = timestamp.toLocaleDateString();
    const timeStr = timestamp.toLocaleTimeString();

    if (data.action === 'LOG_MED') {
      sheet.appendRow([dateStr, timeStr, data.name, 'Taken', data.timestamp]);
    } else if (data.action === 'LOG_MOOD') {
      sheet.appendRow([dateStr, timeStr, data.mood, data.symptoms, data.notes, data.timestamp]);
    } else if (data.action === 'SAVE_GOAL') {
      const existingData = sheet.getDataRange().getValues();
      for (let i = existingData.length - 1; i >= 1; i--) {
        if (existingData[i][0] === data.type) {
          sheet.deleteRow(i + 1);
        }
      }
      sheet.appendRow([data.type, data.value, data.timestamp]);
    } else {
      sheet.appendRow([
        dateStr,
        timeStr,
        data.type || "",
        data.weight != null ? data.weight : "",
        data.bp_sys != null ? data.bp_sys : "",
        data.bp_dia != null ? data.bp_dia : "",
        data.timestamp
      ]);
    }

    return ContentService.createTextOutput(JSON.stringify({ status: 'success' }))
      .setMimeType(ContentService.MimeType.JSON);

  } catch (error) {
    return ContentService.createTextOutput(JSON.stringify({ status: 'error', message: error.toString() }))
      .setMimeType(ContentService.MimeType.JSON);
  }
}

function doGet(e) {
  try {
    const ss = SpreadsheetApp.getActiveSpreadsheet();
    const type = e.parameter.type || 'LOGS';
    const sheetName = SHEETS[type] || SHEETS.LOGS;
    const sheet = ss.getSheetByName(sheetName);
    
    if (!sheet) return ContentService.createTextOutput("[]").setMimeType(ContentService.MimeType.JSON);
    
    const data = sheet.getDataRange().getValues();
    const headers = data[0];
    const rows = data.slice(1);
    
    const jsonData = rows.map(row => {
      let obj = {};
      headers.forEach((header, index) => {
        obj[header.toLowerCase().replace(/ /g, '_')] = row[index];
      });
      return obj;
    });

    if (type !== 'GOALS') jsonData.reverse();
    
    return ContentService.createTextOutput(JSON.stringify(jsonData.slice(0, 50)))
      .setMimeType(ContentService.MimeType.JSON);
      
  } catch (error) {
    return ContentService.createTextOutput(JSON.stringify({ error: error.toString() }))
      .setMimeType(ContentService.MimeType.JSON);
  }
}

/**
 * Weekly Summary Trigger
 * This sends an email summary of the week's logs.
 */
function sendWeeklySummary() {
  const ss = SpreadsheetApp.getActiveSpreadsheet();
  const logSheet = ss.getSheetByName(SHEETS.LOGS);
  if (!logSheet) return;

  const data = logSheet.getDataRange().getValues();
  const headers = data[0];
  const rows = data.slice(1);

  const oneWeekAgo = new Date();
  oneWeekAgo.setDate(oneWeekAgo.getDate() - 7);

  let weeklyLogs = rows.filter(row => {
    const timestamp = new Date(row[6]); // Timestamp column
    return timestamp >= oneWeekAgo;
  });

  if (weeklyLogs.length === 0) return;

  // Calculate averages
  let totalWeight = 0, weightCount = 0;
  let totalSys = 0, totalDia = 0, bpCount = 0;
  let highBPAlerts = 0;

  weeklyLogs.forEach(row => {
    if (row[3]) { totalWeight += row[3]; weightCount++; }
    if (row[4] && row[5]) {
      totalSys += row[4];
      totalDia += row[5];
      bpCount++;
      if (row[4] >= 140) highBPAlerts++;
    }
  });

  const avgWeight = weightCount ? (totalWeight / weightCount).toFixed(1) : "N/A";
  const avgBP = bpCount ? `${(totalSys / bpCount).toFixed(0)}/${(totalDia / bpCount).toFixed(0)}` : "N/A";

  const subject = `Weekly Health Summary: ${PATIENT_NAME}`;
  const body = `
    <h2>Weekly Health Summary for ${PATIENT_NAME}</h2>
    <p>Here is the summary of the logs from the last 7 days:</p>
    <ul>
      <li><b>Average Weight:</b> ${avgWeight} kg</li>
      <li><b>Average BP:</b> ${avgBP} mmHg</li>
      <li><b>High BP Warnings:</b> ${highBPAlerts} log(s)</li>
      <li><b>Total Logs:</b> ${weeklyLogs.length}</li>
    </ul>
    <p>Please check the Google Sheet for full details.</p>
    <br/>
    <p><i>Generated by Mom's Health Tracker API</i></p>
  `;

  MailApp.sendEmail({
    to: RECIPIENT_EMAIL,
    subject: subject,
    htmlBody: body
  });
}
