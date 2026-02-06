// Google Apps Script for Health Track App
// Deploy this script to Google Apps Script and update URL in MainActivity.kt

function doPost(e) {
  try {
    // Parse the incoming data from the Android app
    const data = JSON.parse(e.postData.contents);
    const sheet = SpreadsheetApp.openById('YOUR_SPREADSHEET_ID').getSheetByName('HealthData');
    
    // Add headers if sheet is empty (first time setup)
    if (sheet.getLastRow() === 0) {
      sheet.appendRow(['Timestamp', 'Email', 'Type', 'Value1', 'Value2', 'Status']);
      // Format headers to make them stand out
      sheet.getRange('A1:F1').setFontWeight('bold');
      sheet.getRange('A1:F1').setBackground('#4285F4');
      sheet.getRange('A1:F1').setFontColor('#FFFFFF');
    }
    
    // Determine status based on data type
    let status = '';
    switch(data.type) {
      case 'WEIGHT':
        status = 'Logged';
        break;
      case 'BLOOD_PRESSURE':
        status = 'Logged';
        break;
      case 'MEDICATION':
        status = 'Added';
        break;
      case 'MEDICATION_TAKEN':
        status = 'Taken';
        break;
      case 'MEDICATION_REMINDER':
        status = 'Scheduled';
        break;
      case 'MOOD':
        status = 'Logged';
        break;
      default:
        status = 'Unknown';
    }
    
    // Append the new data row
    sheet.appendRow([
      data.timestamp,
      data.email,
      data.type,
      data.value1,
      data.value2 || '',
      status
    ]);
    
    // Auto-resize columns for better readability
    sheet.autoResizeColumns(1, 6);
    
    // Add color coding based on data type for easy visualization
    const lastRow = sheet.getLastRow();
    const range = sheet.getRange(lastRow, 1, 1, 6);
    
    switch(data.type) {
      case 'WEIGHT':
        range.setBackground('#E3F2FD'); // Light blue
        break;
      case 'BLOOD_PRESSURE':
        range.setBackground('#FFEBEE'); // Light red
        break;
      case 'MEDICATION':
      case 'MEDICATION_TAKEN':
        range.setBackground('#E8F5E8'); // Light green
        break;
      case 'MEDICATION_REMINDER':
        range.setBackground('#FFF3E0'); // Light orange
        break;
      case 'MOOD':
        range.setBackground('#F3E5F5'); // Light purple
        break;
    }
    
    // Return success response to Android app
    return ContentService.createTextOutput(JSON.stringify({
      status: 'success',
      message: `${data.type} data saved successfully`
    })).setMimeType(ContentService.MimeType.JSON);
    
  } catch (error) {
    // Log the error for debugging
    Logger.log('Error in doPost: ' + error.toString());
    
    // Return error response to Android app
    return ContentService.createTextOutput(JSON.stringify({
      status: 'error',
      message: error.toString()
    })).setMimeType(ContentService.MimeType.JSON);
  }
}

// Function to handle GET requests (for testing)
function doGet() {
  return ContentService.createTextOutput('Health Track API is running').setMimeType(ContentService.MimeType.TEXT);
}

// Setup function - run this once to create the spreadsheet
function setup() {
  const spreadsheet = SpreadsheetApp.create('Health Track Data');
  const sheet = spreadsheet.getSheets()[0];
  sheet.setName('HealthData');
  
  // Add headers
  sheet.appendRow(['Timestamp', 'Email', 'Type', 'Value1', 'Value2', 'Status']);
  
  // Format the sheet
  sheet.autoResizeColumns(1, 6);
  sheet.getRange('A1:F1').setFontWeight('bold');
  sheet.getRange('A1:F1').setBackground('#4285F4');
  sheet.getRange('A1:F1').setFontColor('#FFFFFF');
  
  // Log the spreadsheet ID and URL for easy access
  Logger.log('Spreadsheet created with ID: ' + spreadsheet.getId());
  Logger.log('Spreadsheet URL: ' + spreadsheet.getUrl());
  
  return spreadsheet.getId();
}