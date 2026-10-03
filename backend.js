/* ISAI local-first backend
 * IndexedDB persistence for library state and imported audio.
 * No account or remote database is required.
 */
(() => {
  "use strict";

  const DB_NAME = "isai-backend";
  const DB_VERSION = 1;
  const STATE_STORE = "state";
  const TRACK_STORE = "tracks";
  const STATE_KEY = "app";

  function open() {
    return new Promise((resolve, reject) => {
      const request = indexedDB.open(DB_NAME, DB_VERSION);
      request.onupgradeneeded = () => {
        const db = request.result;
        if (!db.objectStoreNames.contains(STATE_STORE)) {
          db.createObjectStore(STATE_STORE);
        }
        if (!db.objectStoreNames.contains(TRACK_STORE)) {
          db.createObjectStore(TRACK_STORE, { keyPath: "id" });
        }
      };
      request.onsuccess = () => resolve(request.result);
      request.onerror = () => reject(request.error || new Error("IndexedDB unavailable"));
    });
  }

  function tx(store, mode, action) {
    return open().then(db => new Promise((resolve, reject) => {
      const request = action(db.transaction(store, mode).objectStore(store));
      request.onsuccess = () => resolve(request.result);
      request.onerror = () => reject(request.error);
    }));
  }

  async function getState() {
    try {
      const value = await tx(STATE_STORE, "readonly", s => s.get(STATE_KEY));
      return value || null;
    } catch (_) {
      return null;
    }
  }

  async function saveState(state) {
    try {
      await tx(STATE_STORE, "readwrite", s => s.put({
        ...state,
        favorites: Array.from(state.favorites || [])
      }, STATE_KEY));
    } catch (_) {
      // localStorage remains the synchronous fallback.
    }
  }

  async function saveImportedTrack(meta, blob) {
    if (!blob) return;
    try {
      await tx(TRACK_STORE, "readwrite", s => s.put({
        id: String(meta.id),
        title: meta.title || "Unknown track",
        artist: meta.artist || "Local file",
        album: meta.album || "Imported",
        genre: meta.genre || "Local",
        duration: Number(meta.duration || 0),
        displayName: meta.displayName || "track.audio",
        blob
      }));
    } catch (_) {}
  }

  async function restoreImportedTracks() {
    try {
      const rows = await tx(TRACK_STORE, "readonly", s => s.getAll());
      return (rows || []).map(row => ({
        id: row.id,
        title: row.title,
        artist: row.artist,
        album: row.album,
        genre: row.genre,
        duration: row.duration,
        displayName: row.displayName,
        src: URL.createObjectURL(row.blob),
        cover: "",
        color: "linear-gradient(145deg,#171713,#b48b32)"
      }));
    } catch (_) {
      return [];
    }
  }

  async function removeImportedTrack(id) {
    try {
      await tx(TRACK_STORE, "readwrite", s => s.delete(String(id)));
    } catch (_) {}
  }

  async function clearImportedTracks() {
    try {
      await tx(TRACK_STORE, "readwrite", s => s.clear());
    } catch (_) {}
  }

  window.ISAIBackend = Object.freeze({
    getState,
    saveState,
    saveImportedTrack,
    restoreImportedTracks,
    removeImportedTrack,
    clearImportedTracks
  });
})();
