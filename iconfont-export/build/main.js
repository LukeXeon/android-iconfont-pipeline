const {app, BrowserWindow, ipcMain} = require('electron');
const path = require('path');
const fs = require('fs');

async function createWindow() {
    const window = new BrowserWindow({
        width: 800,
        height: 600,
        webPreferences: {
            nodeIntegration: true,
            enableRemoteModule: true
        }
    });
    if (process.argv.findIndex(e => e !== "--fastboot") !== -1) {
        await window.loadURL("http://localhost:3000");
    } else {
        const url = 'file://' + path.join(__dirname, 'app', 'index.html');
        await window.loadURL(url);
    }
    ipcMain.on("devToolsSwitch", (e, a) => {
        if (a) {
            window.webContents.openDevTools();
        } else {
            window.webContents.closeDevTools();
        }
    });
}

app.whenReady().then(createWindow);

app.on('window-all-closed', () => {
    if (process.platform !== 'darwin') {
        app.quit()
    }
});

app.on('activate', async () => {
    if (BrowserWindow.getAllWindows().length === 0) {
        await createWindow()
    }
});



