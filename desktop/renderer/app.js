const $ = (id) => document.getElementById(id);
const audio = $("audio");
const load = (k, d) => { try { return JSON.parse(localStorage.getItem("angel:" + k)) ?? d; } catch { return d; } };
const save = (k, v) => { try { localStorage.setItem("angel:" + k, JSON.stringify(v)); } catch {} };

const state = {
  tab: "search",
  results: [],
  queue: [],
  index: -1,
  lines: [],
  lastLine: -1,
  liked: load("liked", []),
  playlists: load("playlists", {}),
  thumb: "",
};
let playToken = 0;
let lyricsToken = 0;

const PLAY_SVG = "▶";
const PAUSE_SVG = "❚❚";

function toast(msg) {
  const t = $("toast");
  t.textContent = msg;
  t.classList.add("show");
  clearTimeout(toast.timer);
  toast.timer = setTimeout(() => t.classList.remove("show"), 2800);
}

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

function isLiked(song) { return state.liked.some((x) => x.videoId === song.videoId); }

function toggleLike(song) {
  state.liked = isLiked(song) ? state.liked.filter((x) => x.videoId !== song.videoId) : [...state.liked, song];
  save("liked", state.liked);
  updateLikeButton();
}

function updateLikeButton() {
  const song = state.queue[state.index];
  const liked = !!song && isLiked(song);
  const btn = $("likeBtn");
  btn.textContent = liked ? "♥ Beğenildi" : "♡ Beğen";
  btn.classList.toggle("on", liked);
}

function addToPlaylist(song) {
  const name = "Listem";
  const items = state.playlists[name] || (state.playlists[name] = []);
  if (items.some((x) => x.videoId === song.videoId)) return toast("Zaten Listem'de");
  items.push(song);
  save("playlists", state.playlists);
  toast("Listem'e eklendi");
}

function miniBtn(label, title, onClick) {
  const b = document.createElement("button");
  b.className = "mini-btn";
  b.textContent = label;
  b.title = title;
  b.addEventListener("click", (e) => { e.stopPropagation(); onClick(); });
  return b;
}

function row(song, onPlay, buttons) {
  const el = document.createElement("div");
  el.className = "row";
  const img = document.createElement("img");
  img.src = song.thumb || "";
  img.alt = "";
  img.loading = "lazy";
  const info = document.createElement("div");
  info.className = "info";
  const t = document.createElement("div");
  t.className = "t";
  t.textContent = song.title;
  const a = document.createElement("div");
  a.className = "a";
  a.textContent = song.artist || "";
  info.append(t, a);
  const dur = document.createElement("span");
  dur.className = "dur";
  dur.textContent = song.duration ? fmt(song.duration) : "";
  el.append(img, info, dur, ...buttons);
  el.addEventListener("click", onPlay);
  return el;
}

function note(view, text) {
  const p = document.createElement("p");
  p.className = "note";
  p.textContent = text;
  view.append(p);
}

function list(view, songs, onPlay) {
  songs.forEach((song, i) => {
    view.append(row(song, () => onPlay(i), [
      miniBtn(isLiked(song) ? "♥" : "♡", "Beğen", () => { toggleLike(song); renderView(); }),
      miniBtn("+", "Listem'e ekle", () => addToPlaylist(song)),
    ]));
  });
}

function renderPlaylists(view) {
  const title = document.createElement("h2");
  title.className = "section-title";
  title.textContent = "Listeler";
  view.append(title);

  const form = document.createElement("form");
  form.className = "newlist";
  const input = document.createElement("input");
  input.placeholder = "Yeni liste adı";
  const button = document.createElement("button");
  button.type = "submit";
  button.className = "btn";
  button.textContent = "Oluştur";
  form.append(input, button);
  form.addEventListener("submit", (e) => {
    e.preventDefault();
    const name = input.value.trim();
    if (!name || state.playlists[name]) return;
    state.playlists[name] = [];
    save("playlists", state.playlists);
    renderView();
  });
  view.append(form);

  const names = Object.keys(state.playlists);
  if (!names.length) note(view, "Henüz liste yok. Bir şarkıdaki + düğmesiyle Listem oluşur.");
  for (const name of names) {
    const h = document.createElement("h4");
    const label = document.createElement("span");
    label.textContent = name;
    const count = document.createElement("span");
    count.className = "count";
    count.textContent = `${state.playlists[name].length} şarkı`;
    h.append(label, count, miniBtn("Sil", "Listeyi sil", () => {
      delete state.playlists[name];
      save("playlists", state.playlists);
      renderView();
    }));
    view.append(h);
    const songs = state.playlists[name];
    if (songs.length) list(view, songs, (i) => setQueue(songs, i));
  }
}

function renderQueue(view) {
  const title = document.createElement("h2");
  title.className = "section-title";
  title.textContent = "Sıra";
  view.append(title);
  if (!state.queue.length) return note(view, "Sıra boş.");
  list(view, state.queue, (i) => playAt(i));
}

function renderLiked(view) {
  const title = document.createElement("h2");
  title.className = "section-title";
  title.textContent = "Beğendiklerim";
  view.append(title);
  if (!state.liked.length) return note(view, "Henüz beğenilen şarkı yok.");
  list(view, state.liked, (i) => setQueue(state.liked, i));
}

function renderView() {
  const view = $("view");
  view.replaceChildren();
  if (state.tab === "search") {
    if (!state.results.length) note(view, "Bir şarkı veya sanatçı arayın.");
    else list(view, state.results, (i) => setQueue(state.results, i));
  } else if (state.tab === "queue") {
    renderQueue(view);
  } else if (state.tab === "liked") {
    renderLiked(view);
  } else if (state.tab === "playlists") {
    renderPlaylists(view);
  }
}

function show(tab) {
  state.tab = tab;
  document.querySelectorAll(".rail button").forEach((b) => b.classList.toggle("on", b.dataset.tab === tab));
  renderView();
}

function setQueue(songs, i) {
  state.queue = songs.slice();
  playAt(i);
}

function openFull() { $("full").classList.remove("hidden"); }
function closeFull() { $("full").classList.add("hidden"); }

function renderLyrics() {
  const body = $("lyricsBody");
  body.replaceChildren();
  state.lastLine = -1;
  if (!state.lines.length) {
    body.textContent = "Senkronize söz bulunamadı";
    return;
  }
  state.lines.forEach((line, i) => {
    const p = document.createElement("p");
    p.className = "ln";
    p.dataset.i = String(i);
    p.textContent = line.text || "♪";
    p.addEventListener("click", () => { if (audio.duration) audio.currentTime = line.t; });
    body.append(p);
  });
}

async function loadLyrics(song) {
  const token = ++lyricsToken;
  const synced = await window.angel.lyrics(song.artist, song.title, song.duration).catch(() => null);
  if (token !== lyricsToken) return;
  state.lines = parseLrc(synced);
  renderLyrics();
}

function highlightLyrics(t) {
  let idx = -1;
  for (let i = 0; i < state.lines.length; i++) {
    if (state.lines[i].t <= t) idx = i;
    else break;
  }
  if (idx === state.lastLine) return;
  state.lastLine = idx;
  document.querySelectorAll(".ln.on").forEach((el) => el.classList.remove("on"));
  if (idx >= 0) {
    const el = document.querySelector(`.ln[data-i="${idx}"]`);
    if (el) {
      el.classList.add("on");
      el.scrollIntoView({ block: "center", behavior: "smooth" });
    }
  }
}

function setThumb(url) {
  const src = url || "";
  $("m-thumb").src = src;
  $("f-art").src = src;
  if (src !== state.thumb) {
    state.thumb = src;
    $("bg").style.backgroundImage = src ? `url("${src.replace(/"/g, "%22")}")` : "";
  }
}

async function playAt(i) {
  if (i < 0 || i >= state.queue.length) {
    audio.pause();
    return;
  }
  const token = ++playToken;
  const song = state.queue[i];
  state.index = i;
  $("m-title").textContent = song.title;
  $("m-artist").textContent = song.artist || "";
  $("f-title").textContent = song.title;
  $("f-artist").textContent = song.artist || "";
  setThumb(song.thumb);
  $("lyricsBody").textContent = "Sözler yükleniyor…";
  updateLikeButton();
  loadLyrics(song);
  try {
    const stream = await window.angel.stream(song.videoId);
    if (token !== playToken) return;
    audio.src = stream.url;
    await audio.play();
  } catch (e) {
    if (token !== playToken) return;
    toast("Çalınamadı: " + e.message);
    playAt(i + 1);
  }
}

function togglePlay() {
  if (!audio.src) return;
  if (audio.paused) audio.play().catch(() => {});
  else audio.pause();
}

function prev() {
  if (audio.currentTime > 3) audio.currentTime = 0;
  else playAt(state.index - 1);
}

audio.volume = 0.8;
audio.addEventListener("timeupdate", () => {
  const d = audio.duration || 0;
  const ratio = d ? audio.currentTime / d : 0;
  $("seek").value = ratio * 1000;
  $("m-fill").style.width = ratio * 100 + "%";
  $("cur").textContent = fmt(audio.currentTime);
  highlightLyrics(audio.currentTime);
});
audio.addEventListener("loadedmetadata", () => { $("dur").textContent = fmt(audio.duration); });
audio.addEventListener("play", () => {
  $("m-toggle").textContent = PAUSE_SVG;
  $("f-toggle").textContent = PAUSE_SVG;
});
audio.addEventListener("pause", () => {
  $("m-toggle").textContent = PLAY_SVG;
  $("f-toggle").textContent = PLAY_SVG;
});
audio.addEventListener("ended", () => playAt(state.index + 1));

$("seek").addEventListener("input", (e) => {
  if (audio.duration) audio.currentTime = (e.target.value / 1000) * audio.duration;
});
$("m-toggle").addEventListener("click", (e) => { e.stopPropagation(); togglePlay(); });
$("f-toggle").addEventListener("click", togglePlay);
$("m-prev").addEventListener("click", (e) => { e.stopPropagation(); prev(); });
$("f-prev").addEventListener("click", prev);
$("m-next").addEventListener("click", (e) => { e.stopPropagation(); playAt(state.index + 1); });
$("f-next").addEventListener("click", () => playAt(state.index + 1));
$("vol").addEventListener("input", (e) => { audio.volume = Number(e.target.value); });
$("likeBtn").addEventListener("click", () => {
  const song = state.queue[state.index];
  if (song) { toggleLike(song); renderView(); }
});
$("addList").addEventListener("click", () => {
  const song = state.queue[state.index];
  if (song) addToPlaylist(song);
});

$("mini").addEventListener("click", openFull);
$("closeFull").addEventListener("click", closeFull);
document.addEventListener("keydown", (e) => {
  if (e.target.tagName === "INPUT" && e.target.type !== "range") return;
  if (e.key === "Escape") closeFull();
  else if (e.key === " ") { e.preventDefault(); togglePlay(); }
});

document.querySelectorAll(".rail button").forEach((b) => b.addEventListener("click", () => show(b.dataset.tab)));

$("searchForm").addEventListener("submit", async (e) => {
  e.preventDefault();
  const query = $("q").value.trim();
  if (!query) return;
  show("search");
  const view = $("view");
  view.replaceChildren();
  note(view, "Aranıyor…");
  try {
    state.results = await window.angel.search(query);
  } catch (err) {
    state.results = [];
    toast("Arama başarısız: " + err.message);
  }
  renderView();
});

show("search");
