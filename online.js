/* ISAI Online Music layer
 * Uses the official YouTube Data API for discovery and the official YouTube
 * embedded player for playback. No scraped audio URLs are used.
 */
(function(){
  "use strict";

  const ONLINE_KEY = "isai-online-state-v1";
  const state = (() => {
    try { return JSON.parse(localStorage.getItem(ONLINE_KEY) || "{}") || {}; }
    catch (_) { return {}; }
  })();
  state.favorites = Array.isArray(state.favorites) ? state.favorites : [];
  state.history = Array.isArray(state.history) ? state.history : [];
  state.queue = Array.isArray(state.queue) ? state.queue : [];
  state.volume = Number.isFinite(state.volume) ? state.volume : 80;
  let results = [];
  let current = null;
  let player = null;
  let playerReady = false;
  let apiPromise = null;

  const esc = s => String(s ?? "").replace(/[&<>"']/g, c =>
    ({ "&":"&amp;", "<":"&lt;", ">":"&gt;", '"':"&quot;", "'":"&#39;" }[c]));

  function save(){ localStorage.setItem(ONLINE_KEY, JSON.stringify(state)); }
  function apiBase(){
    const configured=window.ISAI_API_BASE;
    if(configured) return String(configured).replace(/\\/$/,"");
    return window.Capacitor?.isNativePlatform?.()
      ? "https://isai-the-music-player.vercel.app/api"
      : "/api";
  }
  function toast(text){
    const el=document.querySelector("#toast");
    if(!el) return;
    el.textContent=text; el.classList.add("show");
    clearTimeout(window.__isaiOnlineToast);
    window.__isaiOnlineToast=setTimeout(()=>el.classList.remove("show"),1800);
  }

  function ensureStyle(){
    if(document.getElementById("isai-online-style")) return;
    const style=document.createElement("style");
    style.id="isai-online-style";
    style.textContent=`
      .online-wrap{margin-top:8px}
      .online-search{display:flex;gap:8px;margin:12px 0}
      .online-search input{flex:1;min-width:0;height:52px;border-radius:18px;border:1px solid var(--line);background:var(--surface);padding:0 16px;color:var(--text);outline:0}
      .online-search button{height:52px;padding:0 17px;border-radius:18px;background:var(--black);color:#fff;font-weight:800}
      body.dark .online-search button{background:#f5f1e6;color:#111}
      .online-card{display:flex;align-items:center;gap:11px;padding:9px;border-radius:17px;background:none;border:0;width:100%;text-align:left;color:inherit}
      .online-card:hover{background:var(--gold-soft)}
      .online-thumb{width:64px;height:48px;border-radius:11px;object-fit:cover;background:#222;flex:none}
      .online-meta{min-width:0;flex:1}
      .online-meta b,.online-meta span{display:block;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}
      .online-meta b{font-size:13px}.online-meta span{font-size:11px;color:var(--muted);margin-top:4px}
      .online-action{width:38px;height:38px;border-radius:50%;background:var(--surface);border:1px solid var(--line);color:inherit;flex:none}
      .online-player{position:fixed;inset:0;z-index:90;background:rgba(0,0,0,.72);display:none;align-items:flex-end}
      .online-player.open{display:flex}
      .online-sheet{width:min(900px,100%);background:var(--solid);border-radius:28px 28px 0 0;padding:18px;max-height:92vh;overflow:auto}
      .online-head{display:flex;justify-content:space-between;align-items:center;margin-bottom:12px}
      .online-video{width:100%;aspect-ratio:16/9;border-radius:20px;overflow:hidden;background:#000}
      .online-video iframe{width:100%;height:100%;border:0}
      .online-now{display:flex;align-items:center;gap:12px;margin:15px 0}
      .online-now img{width:58px;height:42px;object-fit:cover;border-radius:10px}
      .online-now b,.online-now span{display:block}.online-now span{font-size:11px;color:var(--muted);margin-top:4px}
      .online-actions{display:flex;gap:8px;flex-wrap:wrap}
      .online-actions button{padding:11px 15px;border-radius:999px;border:1px solid var(--line);background:var(--surface);color:inherit}
    `;
    document.head.appendChild(style);
  }

  function ensurePlayerShell(){
    if(document.getElementById("isaiOnlinePlayer")) return;
    const shell=document.createElement("div");
    shell.id="isaiOnlinePlayer";
    shell.className="online-player";
    shell.innerHTML=`
      <div class="online-sheet">
        <div class="online-head"><b>Now playing</b><button class="close" id="isaiOnlineClose">×</button></div>
        <div class="online-video" id="isaiYoutubeFrame"></div>
        <div class="online-now"><img id="isaiOnlineCover" alt=""><div><b id="isaiOnlineTitle"></b><span id="isaiOnlineArtist"></span></div></div>
        <div class="online-actions">
          <button id="isaiOnlineFav">♡ Like</button>
          <button id="isaiOnlineQueue">＋ Queue</button>
          <button id="isaiOnlineOpen">Open on YouTube</button>
        </div>
      </div>`;
    document.body.appendChild(shell);
    document.getElementById("isaiOnlineClose").onclick=closePlayer;
    document.getElementById("isaiOnlineFav").onclick=toggleFavorite;
    document.getElementById("isaiOnlineQueue").onclick=queueCurrent;
    document.getElementById("isaiOnlineOpen").onclick=()=>{
      if(current?.id) window.open("https://www.youtube.com/watch?v="+encodeURIComponent(current.id),"_blank","noopener");
    };
  }

  function loadYoutubeAPI(){
    if(playerReady) return Promise.resolve();
    if(apiPromise) return apiPromise;
    apiPromise=new Promise((resolve,reject)=>{
      if(window.YT?.Player){playerReady=true;resolve();return;}
      const old=window.onYouTubeIframeAPIReady;
      window.onYouTubeIframeAPIReady=()=>{
        try{old?.();}catch(_){}
        playerReady=true;resolve();
      };
      const s=document.createElement("script");
      s.src="https://www.youtube.com/iframe_api";
      s.async=true;
      s.onerror=()=>reject(new Error("YouTube player unavailable"));
      document.head.appendChild(s);
    });
    return apiPromise;
  }

  async function play(item){
    current=item;
    state.history=[item.id,...state.history.filter(id=>id!==item.id)].slice(0,100);
    save();
    ensurePlayerShell();
    ensureStyle();
    document.getElementById("isaiOnlinePlayer").classList.add("open");
    document.getElementById("isaiOnlineTitle").textContent=item.title;
    document.getElementById("isaiOnlineArtist").textContent=item.artist;
    document.getElementById("isaiOnlineCover").src=item.thumbnail || "";
    const host=document.getElementById("isaiYoutubeFrame");
    host.innerHTML="";
    try{
      await loadYoutubeAPI();
      player=new YT.Player(host,{
        videoId:item.id,
        playerVars:{autoplay:1,playsinline:1,rel:0,modestbranding:1},
        events:{onReady:e=>e.target.setVolume(state.volume)}
      });
    }catch(error){
      host.innerHTML='<div style="display:grid;place-items:center;height:100%;color:#fff;padding:30px;text-align:center">YouTube playback could not be initialized on this device.</div>';
    }
    updateFavoriteButton();
  }

  function closePlayer(){
    const shell=document.getElementById("isaiOnlinePlayer");
    if(shell) shell.classList.remove("open");
    try{player?.pauseVideo?.();}catch(_){}
  }

  function updateFavoriteButton(){
    const b=document.getElementById("isaiOnlineFav");
    if(!b || !current) return;
    b.textContent=state.favorites.includes(current.id)?"♥ Liked":"♡ Like";
  }

  function toggleFavorite(){
    if(!current)return;
    const i=state.favorites.indexOf(current.id);
    if(i>=0) state.favorites.splice(i,1); else state.favorites.push(current.id);
    save(); updateFavoriteButton(); toast(i>=0?"Removed from liked":"Added to liked");
  }

  function queueCurrent(){
    if(!current)return;
    if(!state.queue.includes(current.id)) state.queue.push(current.id);
    save(); toast("Added to online queue");
  }

  async function search(q,container){
    if(!q)return;
    container.innerHTML='<div class="empty">Searching YouTube…</div>';
    try{
      const r=await fetch(apiBase()+"/youtube-search?q="+encodeURIComponent(q),{headers:{"Accept":"application/json"}});
      const data=await r.json();
      if(!r.ok) throw new Error(data.error||"Search failed");
      results=data.items||[];
      renderResults(container);
    }catch(error){
      container.innerHTML='<div class="empty">'+esc(error.message||"Online search unavailable")+'</div>';
    }
  }

  function renderResults(container){
    if(!results.length){container.innerHTML='<div class="empty">No online results found.</div>';return;}
    container.innerHTML=results.map((x,i)=>`
      <div class="online-card">
        <img class="online-thumb" src="${esc(x.thumbnail)}" alt="">
        <div class="online-meta"><b>${esc(x.title)}</b><span>${esc(x.artist)}</span></div>
        <button class="online-action" data-online-play="${i}">▶</button>
      </div>`).join("");
    container.querySelectorAll("[data-online-play]").forEach(b=>b.onclick=()=>play(results[Number(b.dataset.onlinePlay)]));
  }

  function mountExplore(){
    ensureStyle();
    const view=document.querySelector("#view");
    if(!view || !location) return;
    const obs=new MutationObserver(()=>{
      if(document.querySelector("#isaiOnlineSearch")) return;
      const search=document.querySelector("#search");
      const resultsBox=document.querySelector("#results");
      if(!search || !resultsBox) return;
      const wrap=document.createElement("div");
      wrap.className="online-wrap";
      wrap.id="isaiOnlineSearch";
      wrap.innerHTML=`
        <div class="eyebrow">Online music</div>
        <div class="online-search">
          <input id="isaiOnlineInput" placeholder="Search YouTube…" autocomplete="off">
          <button id="isaiOnlineButton">Search</button>
        </div>
        <div id="isaiOnlineResults" class="card list"><div class="empty">Search for an artist, song or album.</div></div>`;
      resultsBox.parentNode.insertBefore(wrap,resultsBox);
      const input=document.getElementById("isaiOnlineInput");
      const button=document.getElementById("isaiOnlineButton");
      const run=()=>search(input.value.trim(),document.getElementById("isaiOnlineResults"));
      button.onclick=run;
      input.addEventListener("keydown",e=>{if(e.key==="Enter")run();});
    });
    obs.observe(view,{childList:true,subtree:true});
  }

  function init(){
    ensureStyle();
    ensurePlayerShell();
    mountExplore();
  }
  if(document.readyState==="loading") document.addEventListener("DOMContentLoaded",init);
  else init();
})();