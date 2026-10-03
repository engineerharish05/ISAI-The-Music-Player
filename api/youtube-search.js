// Vercel serverless endpoint for ISAI online discovery.
// Requires YOUTUBE_API_KEY in Vercel Project Environment Variables.
export default async function handler(req, res) {
  res.setHeader("Access-Control-Allow-Origin", "*");
  res.setHeader("Access-Control-Allow-Methods", "GET, OPTIONS");
  res.setHeader("Access-Control-Allow-Headers", "Content-Type");
  if (req.method === "OPTIONS") return res.status(204).end();

  const q = String(req.query?.q || "").trim();
  if (!q) return res.status(400).json({ error: "Missing q" });

  const key = process.env.YOUTUBE_API_KEY;
  if (!key) {
    return res.status(503).json({
      error: "Online search is not configured",
      setup: "Add YOUTUBE_API_KEY to the Vercel project environment variables."
    });
  }

  const url = new URL("https://www.googleapis.com/youtube/v3/search");
  url.searchParams.set("part", "snippet");
  url.searchParams.set("q", q);
  url.searchParams.set("type", "video");
  url.searchParams.set("videoCategoryId", "10");
  url.searchParams.set("maxResults", "20");
  url.searchParams.set("safeSearch", "moderate");
  url.searchParams.set("key", key);

  try {
    const response = await fetch(url);
    const data = await response.json();
    if (!response.ok) {
      return res.status(response.status).json({
        error: data?.error?.message || "YouTube API request failed"
      });
    }

    const items = (data.items || []).map(item => ({
      id: item.id?.videoId,
      title: item.snippet?.title || "Unknown title",
      artist: item.snippet?.channelTitle || "Unknown artist",
      album: "YouTube",
      thumbnail: item.snippet?.thumbnails?.high?.url ||
                 item.snippet?.thumbnails?.medium?.url ||
                 item.snippet?.thumbnails?.default?.url || "",
      publishedAt: item.snippet?.publishedAt || "",
      description: item.snippet?.description || ""
    })).filter(x => x.id);

    res.setHeader("Cache-Control", "s-maxage=60, stale-while-revalidate=300");
    return res.status(200).json({ items });
  } catch (error) {
    return res.status(500).json({ error: "Online search failed" });
  }
}
