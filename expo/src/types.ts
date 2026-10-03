export type Song = {
  id: string;
  title: string;
  artist: string;
  album: string;
  duration: number;
  uri: string;
  filename: string;
  genre?: string;
  dateAdded?: number;
};

export type PlayerState = {
  currentId: string | null;
  queue: string[];
  playing: boolean;
  shuffle: boolean;
  repeat: "off" | "all" | "one";
  favorites: string[];
  history: string[];
  playlists: Record<string, string[]>;
};
