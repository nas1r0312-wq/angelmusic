const { contextBridge, ipcRenderer } = require("electron");

// The only surface the page can reach in the main process.
contextBridge.exposeInMainWorld("angel", {
  search: (query) => ipcRenderer.invoke("search", query),
  stream: (videoId, force) => ipcRenderer.invoke("stream", videoId, !!force),
  lyrics: (artist, title, duration) => ipcRenderer.invoke("lyrics", artist, title, duration),
  download: (song) => ipcRenderer.invoke("download", song),
  reveal: (file) => ipcRenderer.invoke("reveal", file),
  store: {
    get: (key) => ipcRenderer.invoke("store:get", key),
    set: (key, value) => ipcRenderer.invoke("store:set", key, value),
  },
});
