const { app, BrowserWindow, ipcMain, shell } = require("electron");
const path = require("path");

// Same InnerTube endpoints and client identities the Android app uses.
const API = "https://music.youtube.com/youtubei/v1/";
const SONG_FILTER = "EgWKAQIIAWoKEAkQBRAKEAMQBA==";
const UA_WEB = "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:140.0) Gecko/20100101 Firefox/140.0";
const WEB_REMIX = { clientName: "WEB_REMIX", clientVersion: "1.20260213.01.00", clientId: "67", userAgent: UA_WEB };
const ANDROID_VR = {
  clientName: "ANDROID_VR",
  clientVersion: "1.61.48",
  clientId: "28",
  userAgent: "com.google.android.apps.youtube.vr.oculus/1.61.48 (Linux; U; Android 12; en_US; Quest 3; Build/SQ3A.220605.009.A1; Cronet/132.0.6808.3)",
  extra: { deviceMake: "Oculus", deviceModel: "Quest 3", osName: "Android", osVersion: "12", androidSdkVersion: "32" },
};
const IOS = {
  clientName: "IOS",
  clientVersion: "21.03.1",
  clientId: "5",
  userAgent: "com.google.ios.youtube/21.03.1 (iPhone16,2; U; CPU iOS 18_2 like Mac OS X;)",
  extra: { osName: "iPhone", osVersion: "18.2.22C152" },
};

async function innertube(endpoint, body, client) {
  const res = await fetch(API + endpoint + "?prettyPrint=false", {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      "User-Agent": client.userAgent,
      "X-YouTube-Client-Name": client.clientId,
      "X-YouTube-Client-Version": client.clientVersion,
      "X-Origin": "https://music.youtube.com",
      Referer: "https://music.youtube.com/",
    },
    body: JSON.stringify({
      context: {
        client: { clientName: client.clientName, clientVersion: client.clientVersion, hl: "tr", gl: "TR", ...(client.extra || {}) },
      },
      ...body,
    }),
  });
  if (!res.ok) throw new Error(`YouTube Music yanıt vermedi (${res.status})`);
  return res.json();
}

const textOf = (runs) => (runs || []).map((r) => r.text).join("");

function parseDuration(text) {
  const parts = String(text || "").split(":").map(Number);
  if (!parts.length || parts.some(Number.isNaN)) return 0;
  return parts.reduce((total, v) => total * 60 + v, 0);
}

function parseSongs(json) {
  const sections = json?.contents?.tabbedSearchResultsRenderer?.tabs?.[0]?.tabRenderer?.content?.sectionListRenderer?.contents ?? [];
  const songs = [];
  for (const section of sections) {
    for (const row of section.musicShelfRenderer?.contents ?? []) {
      const r = row.musicResponsiveListItemRenderer;
      if (!r) continue;
      const videoId =
        r.playlistItemData?.videoId ??
        r.overlay?.musicItemThumbnailOverlayRenderer?.content?.musicPlayButtonRenderer?.playNavigationEndpoint?.watchEndpoint?.videoId;
      if (!videoId) continue;
      const cols = r.flexColumns ?? [];
      const title = textOf(cols[0]?.musicResponsiveListItemFlexColumnRenderer?.text?.runs);
      const second = (cols[1]?.musicResponsiveListItemFlexColumnRenderer?.text?.runs ?? []).filter((x) => x.text.trim() !== "•");
      const durationText = r.fixedColumns?.[0]?.musicResponsiveListItemFixedColumnRenderer?.text?.runs?.[0]?.text;
      const thumbs = r.thumbnail?.musicThumbnailRenderer?.thumbnail?.thumbnails ?? [];
      songs.push({
        videoId,
        title: title || "Adsız",
        artist: second[0]?.text ?? "",
        duration: parseDuration(durationText),
        thumb: thumbs.at(-1)?.url ?? "",
      });
    }
  }
  return songs;
}

async function streamFor(videoId) {
  let reason = "Bu şarkı çalınamadı";
  for (const client of [ANDROID_VR, IOS]) {
    const json = await innertube("player", { videoId, contentCheckOk: true, racyCheckOk: true }, client);
    const formats = (json?.streamingData?.adaptiveFormats ?? []).filter((f) => f.mimeType?.startsWith("audio/") && f.url);
    if (formats.length) {
      formats.sort((a, b) => (b.bitrate ?? 0) - (a.bitrate ?? 0));
      const best = formats.find((f) => f.mimeType.includes("mp4")) ?? formats[0];
      return { url: best.url };
    }
    reason = json?.playabilityStatus?.reason ?? reason;
  }
  throw new Error(reason);
}

async function lyricsFor(artist, title, duration) {
  const query = new URLSearchParams({ artist_name: artist || "", track_name: title || "", duration: String(Math.round(duration || 0)) });
  const res = await fetch("https://lrclib.net/api/get?" + query, {
    headers: { "User-Agent": "AngelMusic desktop (https://github.com/nas1r0312-wq/angelmusic)" },
  });
  if (!res.ok) return null;
  const json = await res.json();
  return json.syncedLyrics ?? null;
}

ipcMain.handle("search", async (_e, query) => parseSongs(await innertube("search", { query: String(query), params: SONG_FILTER }, WEB_REMIX)));
ipcMain.handle("stream", (_e, videoId) => streamFor(String(videoId)));
ipcMain.handle("lyrics", (_e, artist, title, duration) => lyricsFor(artist, title, duration));

function createWindow() {
  const win = new BrowserWindow({
    width: 1200,
    height: 800,
    minWidth: 900,
    minHeight: 600,
    backgroundColor: "#0b0507",
    title: "AngelMusic",
    webPreferences: {
      preload: path.join(__dirname, "preload.js"),
      contextIsolation: true,
      nodeIntegration: false,
      sandbox: true,
    },
  });
  win.setMenuBarVisibility(false);
  win.loadFile(path.join(__dirname, "renderer", "index.html"));
  win.webContents.setWindowOpenHandler(({ url }) => {
    shell.openExternal(url);
    return { action: "deny" };
  });
}

app.whenReady().then(createWindow);
app.on("window-all-closed", () => app.quit());
