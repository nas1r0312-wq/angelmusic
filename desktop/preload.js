const { contextBridge, ipcRenderer } = require("electron");

contextBridge.exposeInMainWorld("angel", {
  search: (query) => ipcRenderer.invoke("search", query),
  stream: (videoId) => ipcRenderer.invoke("stream", videoId),
  lyrics: (artist, title, duration) => ipcRenderer.invoke("lyrics", artist, title, duration),
});
