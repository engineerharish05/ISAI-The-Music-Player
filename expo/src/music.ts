import * as MediaLibrary from "expo-media-library";
import type { Song } from "./types";

export async function requestMusicPermission() {
  const current = await MediaLibrary.getPermissionsAsync(false, ["audio"]);
  if (current.granted || current.accessPrivileges === "limited") return current;
  return MediaLibrary.requestPermissionsAsync(false, ["audio"]);
}

export async function scanDeviceMusic(): Promise<Song[]> {
  const permission = await requestMusicPermission();
  if (!permission.granted && permission.accessPrivileges !== "limited") {
    throw new Error("Music permission was not granted");
  }

  const result = await MediaLibrary.getAssetsAsync({
    mediaType: MediaLibrary.MediaType.audio,
    first: 10000,
    sortBy: [[MediaLibrary.SortBy.modificationTime, false]]
  });

  return result.assets
    .map((asset) => ({
      id: asset.id,
      title: asset.filename.replace(/\\.[^.]+$/, ""),
      artist: "Unknown artist",
      album: "Unknown album",
      duration: Math.max(0, asset.duration || 0),
      uri: asset.uri,
      filename: asset.filename,
      dateAdded: asset.modificationTime || asset.creationTime || undefined
    }))
    .filter((song) => !!song.uri);
}
