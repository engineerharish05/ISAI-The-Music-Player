import {
  createAudioPlayer,
  setAudioModeAsync,
  type AudioPlayer
} from "expo-audio";
import type { Song } from "./types";

let player: AudioPlayer | null = null;
let activeId: string | null = null;

export async function configureAudio() {
  await setAudioModeAsync({
    playsInSilentMode: true,
    shouldPlayInBackground: true,
    interruptionMode: "doNotMix"
  });
}

export async function playSong(song: Song, volume = 1) {
  if (player) {
    try { player.pause(); } catch {}
    try { player.release(); } catch {}
  }

  player = createAudioPlayer(song.uri, { updateInterval: 500 });
  activeId = song.id;
  player.volume = volume;
  player.setActiveForLockScreen(true, {
    title: song.title,
    artist: song.artist,
    albumTitle: song.album
  });
  player.play();
  return player;
}

export function getPlayer() {
  return player;
}

export function getActiveId() {
  return activeId;
}

export async function stopPlayer() {
  if (!player) return;
  player.pause();
  player.setActiveForLockScreen(false);
  player.release();
  player = null;
  activeId = null;
}
