import { createContext, useContext, useEffect, useMemo, useState } from "react";
import { loadState, saveState, defaultState } from "./storage";
import { scanDeviceMusic } from "./music";
import { playSong, stopPlayer } from "./player";
import type { PlayerState, Song } from "./types";

type Store = {
  songs: Song[];
  state: PlayerState;
  loading: boolean;
  error: string | null;
  scan: () => Promise<void>;
  play: (song: Song) => Promise<void>;
  togglePlay: () => void;
  next: () => Promise<void>;
  previous: () => Promise<void>;
  toggleFavorite: (id: string) => void;
  toggleShuffle: () => void;
  cycleRepeat: () => void;
  createPlaylist: (name: string) => void;
  addToPlaylist: (name: string, id: string) => void;
};

const Context = createContext<Store | null>(null);

export function StoreProvider({ children }: { children: React.ReactNode }) {
  const [songs, setSongs] = useState<Song[]>([]);
  const [state, setState] = useState<PlayerState>(defaultState);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    (async () => {
      const saved = await loadState();
      setState(saved);
      try {
        setSongs(await scanDeviceMusic());
      } catch (e) {
        setError(e instanceof Error ? e.message : "Music permission is required");
      } finally {
        setLoading(false);
      }
    })();
  }, []);

  useEffect(() => {
    if (!loading) saveState(state).catch(() => {});
  }, [state, loading]);

  const scan = async () => {
    setLoading(true);
    setError(null);
    try { setSongs(await scanDeviceMusic()); }
    catch (e) { setError(e instanceof Error ? e.message : "Unable to scan music"); }
    finally { setLoading(false); }
  };

  const play = async (song: Song) => {
    const queue = state.queue.length ? state.queue : songs.map(s => s.id);
    const nextState = {
      ...state,
      currentId: song.id,
      queue: queue.includes(song.id) ? queue : [...queue, song.id],
      playing: true,
      history: [song.id, ...state.history.filter(id => id !== song.id)].slice(0, 50)
    };
    setState(nextState);
    await playSong(song);
  };

  const togglePlay = () => {
    // The actual AudioPlayer is intentionally kept in the player module.
    // This state update keeps the UI synchronized; route-level playback can resume.
    setState(s => ({ ...s, playing: !s.playing }));
  };

  const next = async () => {
    if (!songs.length) return;
    const current = state.currentId;
    let candidate: Song | undefined;
    if (state.shuffle) {
      const pool = songs.filter(s => s.id !== current);
      candidate = pool[Math.floor(Math.random() * Math.max(1, pool.length))];
    } else {
      const queue = state.queue.length ? state.queue : songs.map(s => s.id);
      const index = queue.findIndex(id => id === current);
      candidate = songs.find(s => s.id === queue[(index + 1 + queue.length) % queue.length]);
    }
    if (candidate) await play(candidate);
  };

  const previous = async () => {
    if (!songs.length) return;
    const queue = state.queue.length ? state.queue : songs.map(s => s.id);
    const index = queue.findIndex(id => id === state.currentId);
    const candidate = songs.find(s => s.id === queue[(index - 1 + queue.length) % queue.length]);
    if (candidate) await play(candidate);
  };

  const toggleFavorite = (id: string) => {
    setState(s => {
      const favorites = s.favorites.includes(id)
        ? s.favorites.filter(x => x !== id)
        : [...s.favorites, id];
      return { ...s, favorites, playlists: { ...s.playlists, "Liked Songs": favorites } };
    });
  };

  const toggleShuffle = () => setState(s => ({ ...s, shuffle: !s.shuffle }));

  const cycleRepeat = () =>
    setState(s => ({ ...s, repeat: s.repeat === "off" ? "all" : s.repeat === "all" ? "one" : "off" }));

  const createPlaylist = (name: string) =>
    setState(s => ({ ...s, playlists: { ...s.playlists, [name]: [] } }));

  const addToPlaylist = (name: string, id: string) =>
    setState(s => ({
      ...s,
      playlists: {
        ...s.playlists,
        [name]: [...new Set([...(s.playlists[name] || []), id])]
      }
    }));

  useEffect(() => () => { stopPlayer().catch(() => {}); }, []);

  const value = useMemo(() => ({
    songs, state, loading, error, scan, play, togglePlay, next, previous,
    toggleFavorite, toggleShuffle, cycleRepeat, createPlaylist, addToPlaylist
  }), [songs, state, loading, error]);

  return <Context.Provider value={value}>{children}</Context.Provider>;
}

export function useStore() {
  const value = useContext(Context);
  if (!value) throw new Error("useStore must be used inside StoreProvider");
  return value;
}
