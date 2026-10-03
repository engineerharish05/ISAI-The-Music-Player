import AsyncStorage from "@react-native-async-storage/async-storage";
import type { PlayerState } from "./types";

const KEY = "isai-player-state-v2";

export const defaultState: PlayerState = {
  currentId: null,
  queue: [],
  playing: false,
  shuffle: false,
  repeat: "all",
  favorites: [],
  history: [],
  playlists: { "Liked Songs": [] }
};

export async function loadState(): Promise<PlayerState> {
  try {
    const raw = await AsyncStorage.getItem(KEY);
    if (!raw) return defaultState;
    return { ...defaultState, ...JSON.parse(raw) };
  } catch {
    return defaultState;
  }
}

export async function saveState(state: PlayerState) {
  await AsyncStorage.setItem(KEY, JSON.stringify(state));
}
