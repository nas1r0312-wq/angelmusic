"use strict";

const $ = (id) => document.getElementById(id);
const audio = $("audio");
const angel = window.angel;

// ---------- icons ----------
// [path markup, filled?]
const ICONS = {
  search: ['<circle cx="11" cy="11" r="6.5"/><path d="M16 16l4.5 4.5"/>', false],
  queue: ['<path d="M4 6h12M4 12h12M4 18h8M18 15v6"/>', false],
  heart: ['<path d="M12 20s-7-4.4-7-10a4 4 0 0 1 7-2.6A4 4 0 0 1 19 10c0 5.6-7 10-7 10z"/>', false],
  library: ['<rect x="4" y="4" width="16" height="16" rx="3"/><path d="M9 9h6M9 13h6M9 17h3"/>', false],
  play: ['<path d="M8 5.5v13a1 1 0 0 0 1.5.86l10.5-6.5a1 1 0 0 0 0-1.72L9.5 4.64A1 1 0 0 0 8 5.5z"/>', true],
  pause: ['<rect x="6" y="5" width="4" height="14" rx="1.2"/><rect x="14" y="5" width="4" height="14" rx="1.2"/>', true],
  prev: ['<path d="M6 6h2.2v12H6zM18 6.2v11.6c0 .6-.7 1-1.2.6L9 12.6a.7.7 0 0 1 0-1.2l7.8-5.8c.5-.4 1.2 0 1.2.6z"/>', true],
  next: ['<path d="M16 6h2.2v12H16zM6 6.2v11.6c0 .6.7 1 1.2.6L15 12.6a.7.7 0 0 0 0-1.2L7.2 5.6C6.7 5.2 6 5.6 6 6.2z"/>', true],
  shuffle: ['<path d="M4 7h3.2c4.8 0 5.6 10 10.4 10H20M4 17h3.2c1.8 0 2.6-1.3 3.4-3M13.6 10c.8-1.9 1.6-3 3.2-3H20M17 4l3 3-3 3M17 14l3 3-3 3"/>', false],
  repeat: ['<path d="M4 11V9a3 3 0 0 1 3-3h13M17 3l3 3-3 3M20 13v2a3 3 0 0 1-3 3H4M7 21l-3-3 3-3"/>', false],
  download: ['<path d="M12 4v11M7 10.5l5 5 5-5M5 20h14"/>', false],
  plus: ['<path d="M12 5v14M5 12h14"/>', false],
  volume: ['<path d="M4 9.5h3.5L12 5.5v13l-4.5-4H4z"/><path d="M15.5 9a4 4 0 0 1 0 6M18 6.5a7.5 7.5 0 0 1 0 11"/>', false],
  chevron: ['<path d="M6 9l6 6 6-6"/>', false],
  back: ['<path d="M15 6l-6 6 6 6"/>', false],
  x: ['<path d="M6 6l12 12M18 6L6 18"/>', false],
  trash: ['<path d="M4 7h16M9 7V4h6v3M6 7l1 13h10l1-13"/>', false],
};

function svgFor(name, extra = "") {
  const [body, filled] = ICONS[name];
  return `<svg viewBox="0 0 24 24" aria-hidden="true" fill="${filled ? "currentColor" : "none"}" stroke="${filled ? "none" : "currentColor"}" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round">${body}${extra}</svg>`;
}

function setIcon(el, name, extra = "") {
  el.innerHTML = svgFor(name, extra);
}

// ---------- DOM helper (never builds HTML from song data) ----------
function h(tag, attrs, ...kids) {
  const el = document.createElement(tag);
  for (const [k, v] of Object.entries(attrs || {})) {
    if (v == null || v === false) continue;
    if (k === "class") el.className = v;
    else if (k === "text") el.textContent = v;
    else if (k.startsWith("on")) el.addEventListener(k.slice(2), v);
    else el.setAttribute(k, v === true ? "" : String(v));
  }
  for (const kid of kids.flat()) {
    if (kid == null || kid === false) continue;
    el.append(kid instanceof Node ? kid : document.createTextNode(String(kid)));
  }
  return el;
}

const clamp = (v, lo, hi) => Math.min(hi, Math.max(lo, v));

function fmt(seconds) {
  const s = Math.max(0, Math.floor(seconds || 0));
  return Math.floor(s / 60) + ":" + String(s % 60).padStart(2, "0");
}

function parseLrc(text) {
  if (!text) return [];
  return text
    .split("\n")
    .map((line) => {
      const m = line.match(/^\[(\d+):(\d+(?:\.\d+)?)\](.*)$/);
      return m ? { t: Number(m[1]) * 60 + Number(m[2]), text: m[3].trim() } : null;
    })
    .filter(Boolean);
}

// ---------- state ----------
const CHIPS = ["Türkçe pop", "Lo-fi", "Rock", "Rap", "Klasik", "Yeni çıkanlar"];

const state = {
  tab: "search",
  openPlaylist: null,
  search: { status: "idle", query: "", results: [], artists: [], error: "" },
  artist: null,
  artistToken: 0,
  searchToken: 0,
  queue: [],
  index: -1,
  current: null,
  liked: [],
  playlists: {},
  volume: 0.8,
  shuffle: false,
  repeat: "off", // off | all | one
  lines: [],
  lastLine: -1,
  seeking: false,
  retried: false,
  failStreak: 0,
};
let playToken = 0;
let lyricsToken = 0;

// ---------- persistence ----------
function saveKey(key, value) {
  angel.store.set(key, value).catch(() => {});
}
const saveLiked = () => saveKey("liked", state.liked);
const savePlaylists = () => saveKey("playlists", state.playlists);
const saveSettings = () => saveKey("settings", { volume: state.volume, shuffle: state.shuffle, repeat: state.repeat });

async function loadLibrary() {
  const [liked, playlists, settings] = await Promise.all(
    ["liked", "playlists", "settings"].map((k) => angel.store.get(k).catch(() => null))
  );
  if (Array.isArray(liked)) state.liked = liked;
  if (playlists && typeof playlists === "object" && !Array.isArray(playlists)) state.playlists = playlists;
  if (settings) {
    state.volume = clamp(Number(settings.volume) || 0.8, 0, 1);
    state.shuffle = !!settings.shuffle;
    state.repeat = ["off", "all", "one"].includes(settings.repeat) ? settings.repeat : "off";
  }
}

// ---------- song helpers ----------
const toSong = (s) => ({ videoId: s.videoId, title: s.title, artist: s.artist || "", duration: s.duration || 0, thumb: s.thumb || "" });
const isLiked = (song) => state.liked.some((x) => x.videoId === song.videoId);

function toggleLike(song) {
  state.liked = isLiked(song) ? state.liked.filter((x) => x.videoId !== song.videoId) : [...state.liked, toSong(song)];
  saveLiked();
  updateLikeButton();
  renderView();
}

function addToPlaylist(name, song) {
  const list = state.playlists[name] || (state.playlists[name] = []);
  if (list.some((x) => x.videoId === song.videoId)) return toast(`“${name}” listesinde zaten var`);
  list.push(toSong(song));
  savePlaylists();
  toast(`“${name}” listesine eklendi`);
  if (state.tab === "playlists") renderView();
}

function removeFromPlaylist(name, song) {
  state.playlists[name] = (state.playlists[name] || []).filter((x) => x.videoId !== song.videoId);
  savePlaylists();
  renderView();
}

function deletePlaylist(name) {
  if (!confirm(`“${name}” listesi silinsin mi?`)) return;
  delete state.playlists[name];
  savePlaylists();
  state.openPlaylist = null;
  renderView();
}

function playlistMenuItems(song) {
  const names = ["Listem", ...Object.keys(state.playlists).filter((n) => n !== "Listem")];
  return [
    ...names.map((name) => ({ label: name, onClick: () => addToPlaylist(name, song) })),
    { label: "+ Yeni liste oluştur", onClick: () => show("playlists") },
  ];
}

// ---------- toast & menu ----------
let toastTimer = 0;
function toast(msg, action) {
  const kids = [h("span", { text: msg })];
  if (action) {
    kids.push(h("button", { class: "toast-btn", type: "button", onclick: () => { hideToast(); action.run(); } }, action.label));
  }
  $("toast").replaceChildren(...kids);
  $("toast").classList.add("show");
  clearTimeout(toastTimer);
  toastTimer = setTimeout(hideToast, action ? 6000 : 2800);
}
function hideToast() {
  $("toast").classList.remove("show");
}

function openMenu(anchor, items) {
  const menu = $("menu");
  menu.replaceChildren(
    ...items.map((it) => h("button", { class: "menu-item", type: "button", role: "menuitem", onclick: () => { closeMenu(); it.onClick(); } }, it.label))
  );
  menu.classList.remove("hidden");
  const r = anchor.getBoundingClientRect();
  const left = clamp(r.right - menu.offsetWidth, 8, innerWidth - menu.offsetWidth - 8);
  let top = r.bottom + 6;
  if (top + menu.offsetHeight > innerHeight - 8) top = Math.max(8, r.top - menu.offsetHeight - 6);
  menu.style.left = left + "px";
  menu.style.top = top + "px";
}

function closeMenu() {
  $("menu").classList.add("hidden");
}

// ---------- rows ----------
function iconBtn(name, title, onClick, cls = "") {
  const b = h("button", {
    class: "mini-btn " + cls,
    type: "button",
    title,
    "aria-label": title,
    onclick: (e) => { e.stopPropagation(); onClick(e, b); },
  });
  setIcon(b, name);
  return b;
}

function songRow(song, onPlay, extra = []) {
  const playing = !!state.current && state.current.videoId === song.videoId;
  const liked = isLiked(song);
  return h(
    "div",
    {
      class: "row" + (playing ? " now" : ""),
      role: "button",
      tabindex: "0",
      onclick: onPlay,
      onkeydown: (e) => { if (e.key === "Enter") onPlay(); },
    },
    h("div", { class: "thumb" },
      h("img", { src: song.thumb || null, alt: "", loading: "lazy" }),
      playing ? h("span", { class: "eq" }, h("i"), h("i"), h("i")) : null),
    h("div", { class: "info" },
      h("div", { class: "t", text: song.title }),
      h("div", { class: "a", text: song.artist || "" })),
    h("span", { class: "dur", text: song.duration ? fmt(song.duration) : "" }),
    h("div", { class: "row-actions" },
      iconBtn("heart", liked ? "Beğenmekten vazgeç" : "Beğen", () => toggleLike(song), liked ? "is-on" : ""),
      iconBtn("plus", "Listeye ekle", (e) => openMenu(e.currentTarget, playlistMenuItems(song))),
      iconBtn("download", "İndir", () => downloadSong(song)),
      ...extra)
  );
}

function songList(songs, onPlayAt, extraFor) {
  return h("div", { class: "list" }, songs.map((song, i) =>
    songRow(song, () => onPlayAt(i), extraFor ? extraFor(song, i) : [])));
}

const note = (text) => h("p", { class: "note", text });
const textBtn = (label, onClick, cls = "") => h("button", { class: "pill " + cls, type: "button", onclick: onClick }, label);
const sectionHead = (title, sub, ...actions) =>
  h("div", { class: "head" },
    h("div", null, h("h2", { text: title }), sub ? h("p", { class: "sub", text: sub }) : null),
    h("div", { class: "head-actions" }, ...actions));

// ---------- views ----------
function renderSearch(view) {
  const s = state.search;
  if (s.status === "idle") {
    view.append(h("div", { class: "hero" },
      h("h2", { text: "Ne dinlemek istersin?" }),
      h("p", { text: "Şarkı veya sanatçı ara." }),
      h("div", { class: "chips" }, CHIPS.map((c) =>
        h("button", { class: "chip", type: "button", onclick: () => { $("q").value = c; runSearch(c); } }, c)))));
    return;
  }
  if (s.status === "loading") return view.append(note("Aranıyor…"));
  if (s.status === "error") return view.append(note(s.error));
  if (!s.results.length && !s.artists.length) {
    view.append(sectionHead(`“${s.query}”`, null));
    return view.append(note("Sonuç bulunamadı."));
  }
  view.append(sectionHead(`“${s.query}”`, null));
  if (s.artists.length) {
    view.append(h("h3", { class: "section-h", text: "Sanatçılar" }));
    view.append(h("div", { class: "artist-row" }, s.artists.map(artistCard)));
  }
  if (s.results.length) {
    view.append(h("h3", { class: "section-h" },
      "Şarkılar",
      textBtn("▶ Hepsini çal", () => playList(s.results, 0), "small")));
    view.append(songList(s.results, (i) => playList(s.results, i)));
  }
}

function artistCard(a) {
  const open = () => openArtist(a.browseId, a.name, a.thumb);
  return h("div", { class: "acard", role: "button", tabindex: "0", onclick: open, onkeydown: (e) => { if (e.key === "Enter") open(); } },
    h("div", { class: "acover" }, a.thumb ? h("img", { src: a.thumb, alt: "", loading: "lazy" }) : null),
    h("div", { class: "card-t", text: a.name }));
}

function renderArtist(view) {
  view.append(h("button", { class: "back", type: "button", onclick: () => show("search") },
    h("span", { class: "ico", "data-icon": "back" }), "Aramaya dön"));
  const a = state.artist;
  if (!a) return;
  if (a.status === "loading") return view.append(note("Yükleniyor…"));
  if (a.status === "error") return view.append(note(a.error));
  view.append(h("div", { class: "artist-hero" },
    h("div", { class: "ahero-img" }, a.thumb ? h("img", { src: a.thumb, alt: "" }) : null),
    h("div", { class: "ahero-info" },
      h("h2", { text: a.name }),
      h("p", { class: "sub", text: `${a.songs.length} şarkı` }),
      a.songs.length ? textBtn("▶ Çal", () => playList(a.songs, 0)) : null)));
  if (!a.songs.length) return view.append(note("Bu sanatçının şarkısı bulunamadı."));
  view.append(songList(a.songs, (i) => playList(a.songs, i)));
}

function renderQueue(view) {
  view.append(sectionHead("Sıra", state.queue.length ? `${state.queue.length} şarkı` : null,
    state.queue.length ? textBtn("Sırayı temizle", clearQueue) : null));
  if (!state.queue.length) return view.append(note("Sıra boş. Bir şarkı çalın veya listeden ekleyin."));
  view.append(songList(state.queue, (i) => playAt(i)));
}

function clearQueue() {
  const keep = state.index >= 0 ? state.queue[state.index] : null;
  state.queue = keep ? [keep] : [];
  state.index = keep ? 0 : -1;
  renderView();
}

function renderLiked(view) {
  view.append(sectionHead("Beğendiklerim", state.liked.length ? `${state.liked.length} şarkı` : null,
    state.liked.length ? textBtn("▶ Hepsini çal", () => playList(state.liked, 0)) : null));
  if (!state.liked.length) return view.append(note("Henüz beğenilen şarkı yok. Bir şarkıdaki kalp simgesine dokunun."));
  view.append(songList(state.liked, (i) => playList(state.liked, i)));
}

function renderPlaylists(view) {
  if (state.openPlaylist && state.playlists[state.openPlaylist]) return renderPlaylistDetail(view, state.openPlaylist);
  state.openPlaylist = null;
  view.append(sectionHead("Listeler", null));
  view.append(h("form", {
    class: "newlist",
    onsubmit: (e) => {
      e.preventDefault();
      const input = e.currentTarget.elements.name;
      const name = input.value.trim().slice(0, 60);
      if (!name) return;
      if (state.playlists[name]) return toast("Bu isimde bir liste var");
      state.playlists[name] = [];
      savePlaylists();
      input.value = "";
      renderView();
    },
  },
    h("input", { name: "name", placeholder: "Yeni liste adı", maxlength: "60", autocomplete: "off" }),
    h("button", { class: "btn", type: "submit" }, "Oluştur")));
  const names = Object.keys(state.playlists);
  if (!names.length) return view.append(note("Henüz liste yok. Bir şarkıdaki + düğmesiyle Listem oluşur."));
  view.append(h("div", { class: "cards" }, names.map(playlistCard)));
}

function playlistCard(name) {
  const songs = state.playlists[name];
  const cover = songs[0]?.thumb || "";
  const open = () => { state.openPlaylist = name; renderView(); };
  return h("div", { class: "card", role: "button", tabindex: "0", onclick: open, onkeydown: (e) => { if (e.key === "Enter") open(); } },
    h("div", { class: "cover" }, cover ? h("img", { src: cover, alt: "", loading: "lazy" }) : null),
    h("div", { class: "card-t", text: name }),
    h("div", { class: "card-s", text: `${songs.length} şarkı` }));
}

function renderPlaylistDetail(view, name) {
  const songs = state.playlists[name];
  view.append(h("button", { class: "back", type: "button", onclick: () => { state.openPlaylist = null; renderView(); } },
    h("span", { class: "ico", "data-icon": "back" }), "Listeler"));
  view.append(sectionHead(name, `${songs.length} şarkı`,
    songs.length ? textBtn("▶ Çal", () => playList(songs, 0)) : null,
    textBtn("Listeyi sil", () => deletePlaylist(name), "danger")));
  if (!songs.length) return view.append(note("Bu liste boş. Şarkılardaki + düğmesiyle ekleyin."));
  view.append(songList(songs, (i) => playList(songs, i),
    (song) => [iconBtn("x", "Listeden çıkar", () => removeFromPlaylist(name, song))]));
}

function renderView() {
  const view = $("view");
  view.replaceChildren();
  if (state.tab === "search") renderSearch(view);
  else if (state.tab === "queue") renderQueue(view);
  else if (state.tab === "liked") renderLiked(view);
  else if (state.tab === "playlists") renderPlaylists(view);
  else if (state.tab === "artist") renderArtist(view);
  view.querySelectorAll("[data-icon]").forEach((el) => setIcon(el, el.dataset.icon));
}

function show(tab) {
  state.tab = tab;
  state.openPlaylist = null;
  document.querySelectorAll(".rail button").forEach((b) => b.classList.toggle("on", b.dataset.tab === tab));
  renderView();
}

async function runSearch(query) {
  const q = query.trim();
  if (!q) return;
  state.tab = "search";
  state.openPlaylist = null;
  document.querySelectorAll(".rail button").forEach((b) => b.classList.toggle("on", b.dataset.tab === "search"));
  const token = ++state.searchToken;
  Object.assign(state.search, { status: "loading", query: q, results: [], artists: [], error: "" });
  renderView();
  try {
    const { songs, artists } = await angel.search(q);
    if (token !== state.searchToken) return;
    Object.assign(state.search, { status: "done", results: songs, artists });
  } catch (err) {
    if (token !== state.searchToken) return;
    Object.assign(state.search, { status: "error", error: "Arama başarısız: " + err.message });
  }
  renderView();
}

async function openArtist(browseId, name, thumb) {
  const token = ++state.artistToken;
  state.artist = { status: "loading", name, thumb, songs: [], error: "" };
  state.tab = "artist";
  document.querySelectorAll(".rail button").forEach((b) => b.classList.remove("on"));
  renderView();
  try {
    const data = await angel.artist(browseId);
    if (token !== state.artistToken) return;
    state.artist = { status: "done", name: data.name || name, thumb: data.thumb || thumb, songs: data.songs, error: "" };
  } catch (err) {
    if (token !== state.artistToken) return;
    state.artist = { status: "error", name, thumb, songs: [], error: "Sanatçı açılamadı: " + err.message };
  }
  renderView();
}

async function downloadSong(song) {
  toast(`İndiriliyor: ${song.title}`);
  try {
    const file = await angel.download(toSong(song));
    toast("Şarkı indirildi", { label: "Klasörde göster", run: () => angel.reveal(file).catch(() => {}) });
  } catch (err) {
    toast("İndirilemedi: " + err.message);
  }
}

// ---------- playback ----------
function playList(songs, i) {
  state.queue = songs.slice();
  return playAt(i);
}

function nextIndex() {
  const n = state.queue.length;
  if (!n) return -1;
  if (state.repeat === "one") return state.index;
  if (state.shuffle && n > 1) {
    let r;
    do { r = Math.floor(Math.random() * n); } while (r === state.index);
    return r;
  }
  if (state.index + 1 < n) return state.index + 1;
  return state.repeat === "all" ? 0 : -1;
}

function prevIndex() {
  const n = state.queue.length;
  if (!n) return -1;
  if (state.index > 0) return state.index - 1;
  return state.repeat === "all" ? n - 1 : 0;
}

function setArt(img, url) {
  if (url) img.src = url;
  else img.removeAttribute("src");
}

function paintSong(song) {
  $("m-title").textContent = song.title;
  $("m-artist").textContent = song.artist;
  $("f-title").textContent = song.title;
  $("f-artist").textContent = song.artist;
  setArt($("m-thumb"), song.thumb);
  setArt($("f-art"), song.thumb);
  $("bg").style.backgroundImage = song.thumb ? `url(${JSON.stringify(song.thumb)})` : "";
  $("dur").textContent = song.duration ? fmt(song.duration) : "0:00";
  $("cur").textContent = "0:00";
  $("seek").value = 0;
  $("m-fill").style.width = "0%";
  document.title = `${song.title} · AngelMusic`;
  if ("mediaSession" in navigator) {
    try {
      navigator.mediaSession.metadata = new MediaMetadata({
        title: song.title,
        artist: song.artist,
        album: "AngelMusic",
        artwork: song.thumb ? [{ src: song.thumb, sizes: "544x544", type: "image/jpeg" }] : [],
      });
    } catch {}
  }
}

async function playAt(i, retry = false) {
  if (i < 0 || i >= state.queue.length) {
    audio.pause();
    return;
  }
  const token = ++playToken;
  const song = state.queue[i];
  state.index = i;
  state.current = song;
  state.retried = retry;
  paintSong(song);
  updateLikeButton();
  loadLyrics(song);
  renderView();
  try {
    const { url } = await angel.stream(song.videoId, retry);
    if (token !== playToken) return;
    audio.src = url;
    await audio.play();
    state.failStreak = 0;
  } catch (err) {
    if (token !== playToken || err?.name === "AbortError") return;
    state.failStreak++;
    toast("Çalınamadı: " + (err?.message || "bilinmeyen hata"));
    skipAfterFailure();
  }
}

function skipAfterFailure() {
  if (state.failStreak >= 3) {
    state.failStreak = 0;
    audio.pause();
    return;
  }
  const n = nextIndex();
  if (n >= 0) playAt(n);
  else audio.pause();
}

function togglePlay() {
  if (!state.current) return;
  if (audio.paused) audio.play().catch(() => {});
  else audio.pause();
}

function prev() {
  if (audio.currentTime > 3) {
    audio.currentTime = 0;
    return;
  }
  const p = prevIndex();
  if (p >= 0) playAt(p);
}

function skipNext() {
  const n = nextIndex();
  if (n >= 0) playAt(n);
}

function paintTransport() {
  const icon = audio.paused ? "play" : "pause";
  setIcon($("m-toggle"), icon);
  setIcon($("f-toggle"), icon);
}

function paintModes() {
  $("shuffle").classList.toggle("is-on", state.shuffle);
  $("shuffle").title = state.shuffle ? "Karışık çalma: açık" : "Karışık çal";
  const repeatOn = state.repeat !== "off";
  $("repeat").classList.toggle("is-on", repeatOn);
  const oneMark = state.repeat === "one" ? '<text x="12" y="14.2" font-size="7.5" font-weight="700" text-anchor="middle" fill="currentColor" stroke="none">1</text>' : "";
  setIcon($("repeat"), "repeat", oneMark);
  $("repeat").title = { off: "Tekrarla: kapalı", all: "Tekrarla: tümü", one: "Tekrarla: tek şarkı" }[state.repeat];
}

function updateLikeButton() {
  const btn = $("likeBtn");
  const liked = !!state.current && isLiked(state.current);
  btn.classList.toggle("is-on", liked);
  btn.innerHTML = svgFor("heart") + (liked ? " Beğenildi" : " Beğen");
}

// ---------- lyrics ----------
async function loadLyrics(song) {
  const token = ++lyricsToken;
  state.lines = [];
  state.lastLine = -1;
  $("lyricsBody").replaceChildren(h("div", { class: "lyrics-empty", text: "Sözler yükleniyor…" }));
  let text = null;
  try {
    text = await angel.lyrics(song.artist, song.title, song.duration);
  } catch {}
  if (token !== lyricsToken) return;
  state.lines = parseLrc(text);
  renderLyrics();
}

function renderLyrics() {
  const body = $("lyricsBody");
  if (!state.lines.length) {
    body.replaceChildren(h("div", { class: "lyrics-empty", text: "Senkronize söz bulunamadı" }));
    return;
  }
  body.replaceChildren(...state.lines.map((line, i) =>
    h("p", {
      class: "ln",
      "data-i": String(i),
      onclick: () => { if (audio.duration) audio.currentTime = line.t; },
    }, line.text || "♪")));
}

function highlightLyrics(t) {
  if (!state.lines.length) return;
  let idx = -1;
  for (let i = 0; i < state.lines.length; i++) {
    if (state.lines[i].t <= t) idx = i;
    else break;
  }
  if (idx === state.lastLine) return;
  state.lastLine = idx;
  const body = $("lyricsBody");
  body.querySelectorAll(".ln.on").forEach((el) => el.classList.remove("on"));
  if (idx < 0) return;
  const el = body.querySelector(`.ln[data-i="${idx}"]`);
  if (!el) return;
  el.classList.add("on");
  body.scrollTo({ top: el.offsetTop - body.clientHeight / 2 + el.offsetHeight / 2, behavior: "smooth" });
}

// ---------- full player & mini player ----------
function openFull() {
  $("full").classList.remove("hidden");
  $("full").setAttribute("aria-hidden", "false");
}
function closeFull() {
  $("full").classList.add("hidden");
  $("full").setAttribute("aria-hidden", "true");
}
const fullOpen = () => !$("full").classList.contains("hidden");

// ---------- audio events ----------
audio.volume = state.volume;
audio.addEventListener("play", paintTransport);
audio.addEventListener("pause", paintTransport);
audio.addEventListener("loadedmetadata", () => { $("dur").textContent = fmt(audio.duration); });
audio.addEventListener("timeupdate", () => {
  const d = audio.duration || 0;
  const ratio = d ? audio.currentTime / d : 0;
  if (!state.seeking) $("seek").value = ratio * 1000;
  $("m-fill").style.width = (ratio * 100).toFixed(2) + "%";
  $("cur").textContent = fmt(audio.currentTime);
  highlightLyrics(audio.currentTime);
});
audio.addEventListener("ended", () => {
  if (state.repeat === "one") {
    audio.currentTime = 0;
    audio.play().catch(() => {});
    return;
  }
  skipNext();
});
audio.addEventListener("error", () => {
  if (!state.current || !audio.getAttribute("src")) return;
  if (!state.retried) {
    playAt(state.index, true);
  } else {
    toast("Ses akışı durdu, sonraki şarkıya geçiliyor");
    state.failStreak++;
    skipAfterFailure();
  }
});

// ---------- controls ----------
function bindControls() {
  $("mini").addEventListener("click", openFull);
  $("mini").addEventListener("keydown", (e) => { if (e.key === "Enter") openFull(); });
  for (const id of ["m-prev", "m-toggle", "m-next"]) $(id).addEventListener("click", (e) => e.stopPropagation());
  $("m-prev").addEventListener("click", prev);
  $("m-toggle").addEventListener("click", togglePlay);
  $("m-next").addEventListener("click", skipNext);

  $("closeFull").addEventListener("click", closeFull);
  $("f-prev").addEventListener("click", prev);
  $("f-toggle").addEventListener("click", togglePlay);
  $("f-next").addEventListener("click", skipNext);

  $("shuffle").addEventListener("click", () => {
    state.shuffle = !state.shuffle;
    paintModes();
    saveSettings();
  });
  $("repeat").addEventListener("click", () => {
    state.repeat = { off: "all", all: "one", one: "off" }[state.repeat];
    paintModes();
    saveSettings();
  });

  $("likeBtn").addEventListener("click", () => { if (state.current) toggleLike(state.current); });
  $("addList").addEventListener("click", (e) => { if (state.current) openMenu(e.currentTarget, playlistMenuItems(state.current)); });
  $("dlBtn").addEventListener("click", () => { if (state.current) downloadSong(state.current); });

  $("seek").addEventListener("pointerdown", () => { state.seeking = true; });
  $("seek").addEventListener("input", (e) => {
    if (audio.duration) audio.currentTime = (e.target.value / 1000) * audio.duration;
  });
  $("seek").addEventListener("change", () => { state.seeking = false; });

  $("vol").addEventListener("input", (e) => {
    state.volume = Number(e.target.value);
    audio.volume = state.volume;
  });
  $("vol").addEventListener("change", saveSettings);

  document.querySelectorAll(".rail button").forEach((b) => b.addEventListener("click", () => show(b.dataset.tab)));

  $("searchForm").addEventListener("submit", (e) => {
    e.preventDefault();
    runSearch($("q").value);
  });

  document.addEventListener("mousedown", (e) => {
    if (!$("menu").classList.contains("hidden") && !$("menu").contains(e.target)) closeMenu();
  });

  document.addEventListener("keydown", (e) => {
    if (e.key === "Escape") {
      if (!$("menu").classList.contains("hidden")) closeMenu();
      else if (fullOpen()) closeFull();
      return;
    }
    const typing = e.target.matches("input[type=text], input[type=search], input:not([type]), textarea");
    if (typing) return;
    if (e.key === " " && !e.target.matches("button, [role=button]")) {
      e.preventDefault();
      togglePlay();
    } else if (e.key === "ArrowRight" && audio.duration) {
      audio.currentTime = Math.min(audio.duration, audio.currentTime + 5);
    } else if (e.key === "ArrowLeft" && audio.duration) {
      audio.currentTime = Math.max(0, audio.currentTime - 5);
    }
  });

  if ("mediaSession" in navigator) {
    const ms = navigator.mediaSession;
    const actions = {
      play: () => audio.play().catch(() => {}),
      pause: () => audio.pause(),
      previoustrack: prev,
      nexttrack: skipNext,
      seekto: (d) => { if (d.seekTime != null) audio.currentTime = d.seekTime; },
    };
    for (const [name, fn] of Object.entries(actions)) {
      try { ms.setActionHandler(name, fn); } catch {}
    }
  }
}

// ---------- boot ----------
function boot() {
  document.querySelectorAll("[data-icon]").forEach((el) => setIcon(el, el.dataset.icon));
  bindControls();
  paintTransport();
  paintModes();
  updateLikeButton();
  renderView();
  loadLibrary().then(() => {
    audio.volume = state.volume;
    $("vol").value = state.volume;
    paintModes();
    updateLikeButton();
    renderView();
  }).catch(() => {});
}

boot();
