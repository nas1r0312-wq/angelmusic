const { app, BrowserWindow, ipcMain, shell } = require("electron");
const path = require("path");
const fs = require("fs");
const { Readable } = require("stream");
const { pipeline } = require("stream/promises");

app.setAppUserModelId("com.angelmusic.desktop");

// InnerTube endpoints and client identities, the same ones the Android app uses.
const API = "https://music.youtube.com/youtubei/v1/";
const SONG_FILTER = "EgWKAQIIAWoKEAkQBRAKEAMQBA==";
const ARTIST_FILTER = "EgWKAQIgAWoKEAkQBRAKEAMQBA==";
const UA_APP = "AngelMusic desktop (https://github.com/nas1r0312-wq/angelmusic)";
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
const PLAYER_CLIENTS = [ANDROID_VR, IOS];
const VIDEO_ID = /^[A-Za-z0-9_-]{11}$/;

function checkVideoId(id) {
  const value = String(id ?? "");
  if (!VIDEO_ID.test(value)) throw new Error("Geçersiz şarkı kimliği");
  return value;
}

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
    signal: AbortSignal.timeout(15000),
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

// Upgrades a small YouTube thumbnail URL to a sharper size for the large artwork.
function sharpThumb(url) {
  return url ? url.replace(/=w\d+-h\d+[^ ]*$/, "=w544-h544-l90-rj") : "";
}

// Collects every object stored under `key`, wherever it sits in the JSON tree.
// YouTube Music moves its renderers between layouts, so walking is more robust than fixed paths.
function findAll(node, key, out = []) {
  if (Array.isArray(node)) {
    for (const item of node) findAll(item, key, out);
  } else if (node && typeof node === "object") {
    if (key in node) out.push(node[key]);
    for (const value of Object.values(node)) findAll(value, key, out);
  }
  return out;
}

const ITEM_RENDERERS = ["musicResponsiveListItemRenderer", "musicTwoRowItemRenderer"];
const rendererItems = (json) => ITEM_RENDERERS.flatMap((key) => findAll(json, key));

function videoIdOf(r) {
  return (
    r.playlistItemData?.videoId ??
    r.overlay?.musicItemThumbnailOverlayRenderer?.content?.musicPlayButtonRenderer?.playNavigationEndpoint?.watchEndpoint?.videoId ??
    r.navigationEndpoint?.watchEndpoint?.videoId ??
    null
  );
}

function browseIdOf(r) {
  return findAll(r, "browseId").find((id) => typeof id === "string" && id.startsWith("UC")) ?? null;
}

function titleOf(r) {
  if (r.flexColumns) return textOf(r.flexColumns[0]?.musicResponsiveListItemFlexColumnRenderer?.text?.runs);
  return textOf(r.title?.runs);
}

function subtitleParts(r) {
  const runs = r.flexColumns
    ? r.flexColumns[1]?.musicResponsiveListItemFlexColumnRenderer?.text?.runs
    : r.subtitle?.runs;
  return (runs ?? []).map((x) => x.text).filter((t) => t.trim() && t.trim() !== "•");
}

function durationOf(r) {
  return r.fixedColumns?.[0]?.musicResponsiveListItemFixedColumnRenderer?.text?.runs?.[0]?.text ?? "";
}

function thumbOf(node) {
  const lists = findAll(node, "thumbnails").filter((t) => Array.isArray(t) && t.length);
  return sharpThumb(lists[0]?.at(-1)?.url ?? "");
}

function parseSongs(json) {
  const songs = [];
  const seen = new Set();
  for (const r of rendererItems(json)) {
    const videoId = videoIdOf(r);
    if (!videoId || seen.has(videoId)) continue;
    seen.add(videoId);
    songs.push({
      videoId,
      title: titleOf(r) || "Adsız",
      artist: subtitleParts(r)[0] ?? "",
      duration: parseDuration(durationOf(r)),
      thumb: thumbOf(r),
    });
  }
  return songs;
}

function parseArtists(json) {
  const artists = [];
  const seen = new Set();
  for (const r of rendererItems(json)) {
    const browseId = browseIdOf(r);
    if (!browseId || seen.has(browseId)) continue;
    seen.add(browseId);
    artists.push({ browseId, name: titleOf(r) || "Sanatçı", thumb: thumbOf(r) });
  }
  return artists;
}

async function artistPage(browseId) {
  const id = String(browseId ?? "");
  if (!/^UC[\w-]{10,}$/.test(id)) throw new Error("Geçersiz sanatçı kimliği");
  const json = await innertube("browse", { browseId: id }, WEB_REMIX);
  const header =
    findAll(json, "musicImmersiveHeaderRenderer")[0] ?? findAll(json, "musicVisualHeaderRenderer")[0] ?? {};
  return {
    browseId: id,
    name: textOf(header.title?.runs) || "Sanatçı",
    thumb: thumbOf(header),
    songs: parseSongs(json),
  };
}

// Signed stream URLs expire; keep them until shortly before the expire timestamp.
function expiryOf(url) {
  const m = url.match(/[?&]expire=(\d+)/);
  return m ? Number(m[1]) * 1000 - 5 * 60 * 1000 : Date.now() + 20 * 60 * 1000;
}

const streamCache = new Map();

async function resolveStream(videoId) {
  let reason = "Bu şarkı şu an çalınamıyor";
  for (const client of PLAYER_CLIENTS) {
    try {
      const json = await innertube("player", { videoId, contentCheckOk: true, racyCheckOk: true }, client);
      const formats = (json?.streamingData?.adaptiveFormats ?? []).filter((f) => f.mimeType?.startsWith("audio/") && f.url);
      if (formats.length) {
        formats.sort((a, b) => (b.bitrate ?? 0) - (a.bitrate ?? 0));
        const best = formats.find((f) => f.mimeType.includes("mp4")) ?? formats[0];
        return { url: best.url, mime: best.mimeType, ua: client.userAgent, exp: expiryOf(best.url) };
      }
      reason = json?.playabilityStatus?.reason ?? reason;
    } catch (err) {
      reason = err.message;
    }
  }
  throw new Error(reason);
}

async function getStream(videoId, force) {
  const hit = streamCache.get(videoId);
  if (!force && hit && hit.exp > Date.now()) return hit;
  const found = await resolveStream(videoId);
  streamCache.set(videoId, found);
  return found;
}

async function lrclib(endpoint, params) {
  const res = await fetch(`https://lrclib.net/api/${endpoint}?${new URLSearchParams(params)}`, {
    headers: { "User-Agent": UA_APP },
    signal: AbortSignal.timeout(10000),
  });
  if (!res.ok) return null;
  return res.json();
}

const lyricsCache = new Map();

async function lyricsFor(artist, title, duration) {
  const key = [artist, title, Math.round(duration || 0)].join("|");
  if (lyricsCache.has(key)) return lyricsCache.get(key);
  let text = null;
  try {
    const exact = await lrclib("get", {
      artist_name: artist || "",
      track_name: title || "",
      duration: String(Math.round(duration || 0)),
    });
    text = exact?.syncedLyrics ?? null;
  } catch {}
  if (!text) {
    try {
      const results = await lrclib("search", { track_name: title || "", artist_name: artist || "" });
      text = results?.find((x) => x.syncedLyrics)?.syncedLyrics ?? null;
    } catch {}
  }
  if (lyricsCache.size > 200) lyricsCache.clear();
  lyricsCache.set(key, text);
  return text;
}

// ---- downloads ----
const downloadedFiles = new Set();

function downloadDir() {
  try {
    return app.getPath("music");
  } catch {
    return app.getPath("downloads");
  }
}

function safeName(value) {
  return String(value).replace(/[\\/:*?"<>|\x00-\x1f]/g, "_").replace(/\s+/g, " ").trim().slice(0, 150);
}

async function downloadSong(song) {
  const videoId = checkVideoId(song?.videoId);
  const stream = await getStream(videoId, false);
  const res = await fetch(stream.url, {
    headers: { "User-Agent": stream.ua },
    signal: AbortSignal.timeout(10 * 60 * 1000),
  });
  if (!res.ok || !res.body) throw new Error(`Ses dosyası indirilemedi (${res.status})`);

  const dir = downloadDir();
  fs.mkdirSync(dir, { recursive: true });
  const ext = stream.mime.includes("mp4") ? "m4a" : "webm";
  const base = safeName(song.artist ? `${song.artist} - ${song.title}` : song.title) || videoId;
  let file = path.join(dir, `${base}.${ext}`);
  for (let n = 1; fs.existsSync(file); n++) file = path.join(dir, `${base} (${n}).${ext}`);

  await pipeline(Readable.fromWeb(res.body), fs.createWriteStream(file));
  downloadedFiles.add(file);
  return file;
}

// ---- local library (liked songs, playlists, settings) ----
const LIBRARY_KEYS = new Set(["liked", "playlists", "settings"]);
let library = null;

const libraryPath = () => path.join(app.getPath("userData"), "library.json");

function loadLibrary() {
  if (!library) {
    try {
      library = JSON.parse(fs.readFileSync(libraryPath(), "utf8")) ?? {};
    } catch {
      library = {};
    }
  }
  return library;
}

function saveLibrary() {
  const file = libraryPath();
  fs.mkdirSync(path.dirname(file), { recursive: true });
  const tmp = file + ".tmp";
  fs.writeFileSync(tmp, JSON.stringify(loadLibrary()));
  fs.renameSync(tmp, file);
}

function checkKey(key) {
  if (!LIBRARY_KEYS.has(key)) throw new Error("Bilinmeyen veri anahtarı");
  return key;
}

ipcMain.handle("search", async (_e, query) => {
  const q = String(query ?? "").trim().slice(0, 200);
  if (!q) return { songs: [], artists: [] };
  const [songsJson, artistsJson] = await Promise.all([
    innertube("search", { query: q, params: SONG_FILTER }, WEB_REMIX),
    innertube("search", { query: q, params: ARTIST_FILTER }, WEB_REMIX).catch(() => null),
  ]);
  return {
    songs: parseSongs(songsJson),
    artists: artistsJson ? parseArtists(artistsJson).slice(0, 12) : [],
  };
});
ipcMain.handle("artist", (_e, browseId) => artistPage(browseId));
ipcMain.handle("stream", async (_e, videoId, force) => {
  const stream = await getStream(checkVideoId(videoId), !!force);
  return { url: stream.url, mime: stream.mime };
});
ipcMain.handle("lyrics", (_e, artist, title, duration) =>
  lyricsFor(String(artist ?? "").slice(0, 200), String(title ?? "").slice(0, 200), Number(duration) || 0)
);
ipcMain.handle("download", (_e, song) => downloadSong(song));
ipcMain.handle("reveal", (_e, file) => {
  if (!downloadedFiles.has(file)) throw new Error("Bu dosya AngelMusic tarafından indirilmedi");
  shell.showItemInFolder(file);
  return true;
});
ipcMain.handle("store:get", (_e, key) => loadLibrary()[checkKey(key)] ?? null);
ipcMain.handle("store:set", (_e, key, value) => {
  loadLibrary()[checkKey(key)] = value;
  saveLibrary();
  return true;
});

let win = null;

function createWindow() {
  win = new BrowserWindow({
    width: 1280,
    height: 820,
    minWidth: 900,
    minHeight: 600,
    backgroundColor: "#0a0406",
    title: "AngelMusic",
    icon: path.join(__dirname, "renderer", "logo.png"),
    autoHideMenuBar: true,
    webPreferences: {
      preload: path.join(__dirname, "preload.js"),
      contextIsolation: true,
      nodeIntegration: false,
      sandbox: true,
      spellcheck: false,
    },
  });
  win.setMenuBarVisibility(false);
  win.loadFile(path.join(__dirname, "renderer", "index.html"));
  win.webContents.setWindowOpenHandler(({ url }) => {
    if (/^https:\/\//.test(url)) shell.openExternal(url);
    return { action: "deny" };
  });
  win.webContents.on("will-navigate", (event, url) => {
    if (!url.startsWith("file://")) event.preventDefault();
  });
  win.on("closed", () => {
    win = null;
  });
}

if (!app.requestSingleInstanceLock()) {
  app.quit();
} else {
  app.on("second-instance", () => {
    if (!win) return;
    if (win.isMinimized()) win.restore();
    win.focus();
  });
  app.whenReady().then(createWindow);
  app.on("activate", () => {
    if (!win) createWindow();
  });
  app.on("window-all-closed", () => app.quit());
}
