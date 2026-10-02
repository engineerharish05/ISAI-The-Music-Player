const seed=[
{id:1,title:"Midnight Architecture",artist:"ISAI Demo",album:"Digital Tamil",genre:"Electronic",duration:252,color:"linear-gradient(145deg,#161611,#b58b34)"},
{id:2,title:"Lavender Signal",artist:"ISAI Demo",album:"Digital Tamil",genre:"Ambient",duration:218,color:"linear-gradient(145deg,#30221b,#c79a45)"},
{id:3,title:"நிலா",artist:"ISAI Demo",album:"Tamil Geometry",genre:"Tamil",duration:236,color:"linear-gradient(145deg,#182b27,#a98236)"},
{id:4,title:"Zero Latency",artist:"ISAI Demo",album:"System",genre:"Electronic",duration:201,color:"linear-gradient(145deg,#251d14,#c1783f)"},
{id:5,title:"Purple Circuit",artist:"ISAI Demo",album:"System",genre:"Synthwave",duration:224,color:"linear-gradient(145deg,#211c2d,#9e7bba)"},
{id:6,title:"அலை",artist:"ISAI Demo",album:"Tamil Geometry",genre:"Tamil",duration:198,color:"linear-gradient(145deg,#132d28,#5da88e)"}];

let saved={};
try{saved=JSON.parse(localStorage.getItem("isai-state")||"{}")||{}}catch(error){console.warn("ISAI state reset:",error);localStorage.removeItem("isai-state")}
const state={page:"home",playerOrigin:"home",theme:saved.theme||"dark",current:null,playing:false,shuffle:!!saved.shuffle,repeat:saved.repeat||"all",haptics:saved.haptics!==false,gestures:saved.gestures!==false,favorites:new Set(saved.favorites||[]),history:saved.history||[],queue:saved.queue||[],speed:saved.speed||1,volume:saved.volume??1,sleepUntil:saved.sleepUntil||0,playlists:saved.playlists||{"Liked Songs":[],"Tamil Essentials":[3,6]}};
let songs=[...seed],urls=[];
const nativePlugins=window.Capacitor?.Plugins||{};
const mediaStore=nativePlugins.IsaiMediaStore||null;
const nativeAudio=nativePlugins.NativeAudio||null;
const haptics=nativePlugins.Haptics||null;
const isNative=!!window.Capacitor?.isNativePlatform?.();
let nativeLibraryReady=false;
let lastHapticAt=0;
async function haptic(style="LIGHT"){
  if(!state.haptics||!haptics?.impact)return;
  const now=Date.now();
  if(now-lastHapticAt<45)return;
  lastHapticAt=now;
  try{await haptics.impact({style})}catch(error){console.warn("ISAI haptics unavailable:",error)}
}
let nativeAssetId=null;

async function initNativeMusic(){
  if(!isNative)return;
  try{
    if(nativeAudio){
      await nativeAudio.configure({backgroundPlayback:true,background:true,focus:true,showNotification:true});
      nativeAudio.addListener?.("complete",handleEnded);
    }
    if(mediaStore){
      const result=await mediaStore.scan();
      const local=(result?.media||[]).map(m=>({
        id:"local-"+m.id,
        title:m.title||m.displayName||"Unknown track",
        artist:m.artist&&m.artist!=="<unknown>"?m.artist:"Unknown artist",
        album:m.album&&m.album!=="<unknown>"?m.album:"Unknown album",
        genre:m.genre||"Local",
        duration:Number(m.duration||0)/1000,
        src:m.uri,
        cover:"",
        displayName:m.displayName||"track.mp3",
        color:"linear-gradient(145deg,#171713,#b48b32)"
      })).filter(s=>s.src);
      if(local.length){
        songs=local;
        const validQueue=(state.queue||[]).filter(id=>local.some(s=>String(s.id)===String(id)));
        state.queue=validQueue.length?validQueue:local.map(s=>s.id);
        nativeLibraryReady=true;
        save();
        render();
        msg(local.length+" local songs ready");
      }else{
        nativeLibraryReady=false;
        msg("No local music found");
      }
    }
  }catch(error){
    nativeLibraryReady=false;
    console.warn("ISAI native music initialization failed:",error);
    msg("Local music scan unavailable");
  }
}
async function nativeLoad(s){
  if(!nativeAudio||!mediaStore||!s?.src)return false;
  try{
    if(nativeAssetId){
      try{await nativeAudio.stop({assetId:nativeAssetId})}catch(_){}
      try{await nativeAudio.unload({assetId:nativeAssetId})}catch(_){}
      nativeAssetId=null;
    }
    const prepared=await mediaStore.prepare({uri:s.src,displayName:s.displayName||"track.mp3"});
    if(nativePlugins.IsaiPlaybackService?.start)await nativePlugins.IsaiPlaybackService.start();
    const assetId="isai-"+String(s.id).replace(/[^a-zA-Z0-9_-]/g,"_");
    await nativeAudio.preload({
      assetId,
      assetPath:prepared.uri,
      isUrl:true,
      volume:state.volume,
      notificationMetadata:{title:s.title,artist:s.artist,album:s.album}
    });
    nativeAssetId=assetId;
    await nativeAudio.play({assetId:nativeAssetId,volume:state.volume});
    state.playing=true;
    updateMini();
    return true;
  }catch(error){
    console.warn("ISAI native playback failed; using Web Audio fallback:",error);
    nativeAssetId=null;
    return false;
  }
}
const view=document.querySelector("#view"),audio=document.querySelector("#audio"),picker=document.querySelector("#filePicker"),mini=document.querySelector("#miniPlayer"),toast=document.querySelector("#toast");
const save=()=>localStorage.setItem("isai-state",JSON.stringify({...state,favorites:[...state.favorites]}));
const esc=s=>String(s).replace(/[&<>"']/g,c=>({"&":"&amp;","<":"&lt;",">":"&gt;",'"':"&quot;","'":"&#39;"}[c]));
const fmt=s=>Number.isFinite(s)?Math.floor(s/60)+":"+String(Math.floor(s%60)).padStart(2,"0"):"0:00";
const art=s=>s.cover?'<div class="song-art"><img src="'+s.cover+'" alt=""></div>':'<div class="song-art" style="background:'+s.color+'">♫</div>';
const albumArt=s=>s.cover?'<div class="album-art"><img src="'+s.cover+'" alt=""></div>':'<div class="album-art" style="background:'+s.color+'">♫</div>';
function msg(t){toast.textContent=t;toast.classList.add("show");clearTimeout(window.tt);window.tt=setTimeout(()=>toast.classList.remove("show"),1600)}
function setPage(p){state.page=p;render()}
function row(s){return '<div class="song" data-song="'+esc(String(s.id))+'">'+art(s)+'<div class="song-info"><b>'+esc(s.title)+'</b><span>'+esc(s.artist)+" · "+esc(s.album)+" · "+fmt(s.duration)+'</span></div><button class="heart '+(state.favorites.has(s.id)?"active":"")+'" data-fav="'+s.id+'">'+(state.favorites.has(s.id)?"♥":"♡")+'</button><button class="more" data-more="'+s.id+'">⋯</button></div>'}
function render(){
  document.body.classList.toggle("dark",state.theme==="dark");
  const themeMeta=document.querySelector('meta[name="theme-color"]');
  if(themeMeta)themeMeta.content=state.theme==="dark"?"#090909":"#f4f1e8";document.querySelectorAll(".nav").forEach(n=>n.classList.toggle("active",n.dataset.page===state.page));({home,explore,library,playlists,settings}[state.page]||home)();updateMini()}
function home(){
const recent=state.history.map(id=>songs.find(s=>s.id===id)).filter(Boolean).slice(0,6);
const albums=[...new Map(songs.map(s=>[s.album,s])).values()];
view.innerHTML='<section class="hero card"><div class="eyebrow">ISAI - The Music Experience</div><h1>Your Day,<br><span class="hero-line">Your Moments,</span><br>Your Isai</h1><p>A Refined Music Experience Designed Around The Songs You Love.</p><div class="hero-actions"><button class="pill primary" data-song="'+songs[0].id+'">▶ Listen now</button><button class="pill ghost" data-action="import">＋ Add music</button></div></section>'+
'<section class="section"><div class="row"><h2>Quick access</h2><button class="more" data-page="library">See all</button></div><div class="quick"><button class="card quick-card" data-page="library"><strong>'+songs.length+'</strong><span>Songs</span></button><button class="card quick-card" data-action="favorites"><strong>♥ '+state.favorites.size+'</strong><span>Liked songs</span></button><button class="card quick-card" data-page="playlists"><strong>'+Object.keys(state.playlists).length+'</strong><span>Playlists</span></button><button class="card quick-card" data-action="history"><strong>◷ '+state.history.length+'</strong><span>History</span></button></div></section>'+
'<section class="section"><div class="row"><h2>Albums & collections</h2></div><div class="shelf">'+albums.map(s=>'<button class="album-card" data-song="'+s.id+'">'+albumArt(s)+'<b>'+esc(s.album)+'</b><span>'+esc(s.artist)+'</span></button>').join("")+'</div></section>'+
'<section class="section"><div class="row"><h2>'+(recent.length?"Continue listening":"Recently added")+'</h2></div><div class="card list">'+(recent.length?recent:songs).slice(0,6).map(row).join("")+'</div></section>'+
'<section class="section card banner"><b><span class="dot"></span>ISAI local-first mode</b><p>Import your own audio and use the player, queue, favorites, playlists, theme and playback controls without an account.</p></section>';
wire()
}
function explore(){
view.innerHTML='<div class="eyebrow">Explore</div><h1>Find your sound.</h1><input id="search" class="search" placeholder="Songs, artists, albums…" autocomplete="off"><section class="section"><div class="chips">'+["All",...new Set(songs.map(s=>s.genre))].map((g,i)=>'<button class="chip '+(i===0?"active":"")+'" data-genre="'+esc(g)+'">'+esc(g)+'</button>').join("")+'</div></section><section class="section"><div class="row"><h2>All music</h2><button class="more" data-action="shuffle">⤨</button></div><div id="results" class="card list">'+songs.map(row).join("")+'</div></section>';
wire()
}
function library(){
view.innerHTML='<div class="eyebrow">Library</div><h1>Your collection.</h1><div class="chips"><button class="chip active">All</button><button class="chip" data-action="favorites">Liked</button><button class="chip" data-action="history">History</button><button class="chip" data-action="import">Import</button></div><section class="section"><div class="row"><h2>'+songs.length+' songs</h2><button class="more" data-action="shuffle">⤨</button></div><div class="card list">'+songs.map(row).join("")+'</div></section>';
wire()
}
function playlists(){
view.innerHTML='<div class="eyebrow">Playlists</div><h1>Made for you.</h1><div class="quick"><button class="card quick-card" data-action="new-playlist"><strong>＋</strong><span>Create playlist</span></button><button class="card quick-card" data-action="favorites"><strong>♥</strong><span>Liked songs</span></button></div><section class="section"><h2>Your playlists</h2><div class="card list">'+Object.keys(state.playlists).map(n=>'<button class="song" data-playlist="'+esc(n)+'">'+art(songs.find(s=>s.id===state.playlists[n][0])||songs[0])+'<div class="song-info"><b>'+esc(n)+'</b><span>'+state.playlists[n].length+' songs</span></div><span class="more">›</span></button>').join("")+'</div></section>';
wire()
}
function settings(){
view.innerHTML='<div class="eyebrow">Settings</div><h1>Make ISAI yours.</h1>'+
'<div class="card" style="padding:4px 18px">'+
'<div class="setting"><div><b>Dark theme</b><span>ISAI night appearance</span></div><button class="switch '+(state.theme==="dark"?"on":"")+'" data-action="toggle-theme"><div class="knob"></div></button></div>'+
'<div class="setting"><div><b>Shuffle</b><span>Randomize next tracks</span></div><button class="switch '+(state.shuffle?"on":"")+'" data-action="shuffle-toggle" aria-label="Toggle shuffle"><div class="knob"></div></button></div>'+
'<div class="setting"><div><b>Haptics</b><span>Subtle touch feedback</span></div><button class="switch '+(state.haptics?"on":"")+'" data-action="toggle-haptics" aria-label="Toggle haptics"><div class="knob"></div></button></div>'+
'<div class="setting"><div><b>Gestures</b><span>Swipe and long-press controls</span></div><button class="switch '+(state.gestures?"on":"")+'" data-action="toggle-gestures" aria-label="Toggle gestures"><div class="knob"></div></button></div>'+
'<div class="setting"><div><b>Equalizer</b><span>10-band player controls</span></div><button class="more" data-action="equalizer">›</button></div>'+
'<div class="setting"><div><b>Sleep timer</b><span>Stop playback automatically</span></div><button class="more" data-action="sleep">›</button></div>'+
'<div class="setting"><div><b>Playback speed</b><span>'+state.speed+'×</span></div><button class="more" data-action="speed">›</button></div>'+
'<div class="setting"><div><b>Queue</b><span>'+state.queue.length+' tracks</span></div><button class="more" data-action="queue">›</button></div><div class="setting"><div><b>Rescan device music</b><span>Refresh songs from Android storage</span></div><button class="more" data-action="rescan">↻</button></div><div class="setting"><div><b>Connected services</b><span>Spotify and YouTube</span></div><button class="more" data-action="services">›</button></div>'+
'</div>'+
'<section class="section card banner"><b>ISAI — The Music Player</b><p>Premium dark/gold visual system, your ISAI emblem and a mobile-first music-player interaction model.</p></section>';
wire()
}
function openPlayer(id){const s=songs.find(x=>String(x.id)===String(id));if(!s)return;haptic("LIGHT");state.playerOrigin=state.page;state.current=s;state.playing=true;state.history=[id,...state.history.filter(x=>x!==id)].slice(0,30);if(!state.queue.length)state.queue=songs.map(x=>x.id);save();load(s);player();updateMini()}
async function load(s){
  if(nativeLibraryReady&&s.src){
    const ok=await nativeLoad(s);
    if(ok)return;
  }
  if(!s.src){
    audio.removeAttribute("src");
    audio.load();
    state.playing=false;
    updateMini();
    return;
  }
  const playableSrc=window.Capacitor?.convertFileSrc
    ?window.Capacitor.convertFileSrc(s.src):s.src;
  audio.src=playableSrc;
  audio.volume=state.volume;
  audio.playbackRate=state.speed;
  try{await audio.play();state.playing=true}catch(error){state.playing=false;msg("Playback unavailable")}
  updateMini();
}
function player(){const s=state.current;if(!s)return;view.innerHTML='<div class="player"><button class="back" data-action="player-back">‹ Back</button><div class="big-cover" style="background:'+s.color+'">'+(s.cover?'<img src="'+s.cover+'" alt="">':"♫")+'</div><div class="player-title">'+esc(s.title)+'</div><div class="player-artist">'+esc(s.artist)+" · "+esc(s.album)+'</div><div class="progress"><input id="seek" type="range" min="0" max="'+Math.max(1,s.duration)+'" value="'+Math.floor(audio.currentTime||0)+'"><div class="times"><span id="cur">'+fmt(audio.currentTime)+'</span><span id="dur">'+fmt(audio.duration||s.duration)+'</span></div></div><div class="controls"><button class="control" data-action="shuffle">⤨</button><button class="control" data-action="previous">‹</button><button class="control main" data-action="play">'+(state.playing?"Ⅱ":"▶")+'</button><button class="control" data-action="next">›</button><button class="control" data-action="repeat">↻</button></div><div class="tools"><button class="chip" data-action="lyrics">Lyrics</button><button class="chip" data-action="queue">Queue</button><button class="chip" data-action="equalizer">EQ</button><button class="chip" data-action="sleep">Sleep</button></div></div>';wire();document.querySelector("#seek").oninput=async e=>{const value=+e.target.value;if(nativeLibraryReady&&nativeAudio&&nativeAssetId){try{await nativeAudio.setCurrentTime({assetId:nativeAssetId,time:value})}catch(error){console.warn("Native seek failed:",error)}}else{audio.currentTime=value}progress()}}
async function progress(){let current=audio.currentTime;if(nativeLibraryReady&&nativeAudio&&nativeAssetId){try{current=(await nativeAudio.getCurrentTime({assetId:nativeAssetId})).currentTime||0}catch(_){}}const s=document.querySelector("#seek");if(s)s.value=current;const c=document.querySelector("#cur");if(c)c.textContent=fmt(current);const d=document.querySelector("#dur");if(d)d.textContent=fmt(audio.duration||state.current?.duration)}
function updateMini(){if(!state.current){mini.classList.add("hidden");return}mini.classList.remove("hidden");document.querySelector("#miniTitle").textContent=state.current.title;document.querySelector("#miniArtist").textContent=state.current.artist;document.querySelector("#miniPlay").textContent=state.playing?"Ⅱ":"▶"}
async function togglePlay(){if(!state.current){openPlayer(songs[0].id);return}if(nativeLibraryReady&&nativeAudio&&nativeAssetId){try{if(state.playing){await nativeAudio.pause({assetId:nativeAssetId});state.playing=false}else{await nativePlugins.IsaiPlaybackService?.start?.();await nativeAudio.resume({assetId:nativeAssetId});state.playing=true}}catch(error){console.warn("Native toggle failed:",error)}}else if(state.playing){audio.pause();state.playing=false}else{state.playing=true;if(state.current.src)audio.play().catch(()=>state.playing=false)}updateMini();if(document.querySelector(".player"))player()}
function next(){
  if(!songs.length)return;
  const currentId=String(state.current?.id||"");
  if(state.shuffle){
    const pool=songs.filter(s=>String(s.id)!==currentId);
    if(pool.length){openPlayer(pool[Math.floor(Math.random()*pool.length)].id);return}
  }
  const queueIds=state.queue.length?state.queue:songs.map(s=>s.id);
  const i=queueIds.findIndex(id=>String(id)===currentId);
  const nextId=queueIds[(i+1+queueIds.length)%queueIds.length];
  const target=songs.find(s=>String(s.id)===String(nextId))||songs[0];
  openPlayer(target.id);
}
async function previous(){
  let currentTime=audio.currentTime||0;
  if(nativeLibraryReady&&nativeAudio&&nativeAssetId){
    try{currentTime=(await nativeAudio.getCurrentTime({assetId:nativeAssetId})).currentTime||0}catch(_){}
  }
  if(currentTime>5){
    if(nativeLibraryReady&&nativeAudio&&nativeAssetId){
      try{await nativeAudio.setCurrentTime({assetId:nativeAssetId,time:0})}catch(_){}
    }else audio.currentTime=0;
    haptic("LIGHT");
    progress();
    return;
  }
  const queueIds=state.queue.length?state.queue:songs.map(s=>s.id);
  const i=queueIds.findIndex(id=>String(id)===String(state.current?.id));
  const prevId=queueIds[(i-1+queueIds.length)%queueIds.length];
  const target=songs.find(s=>String(s.id)===String(prevId))||songs[0];
  openPlayer(target.id);
}
function toggleFav(id){const key=typeof id==="string"&&/^\d+$/.test(id)?Number(id):id;state.favorites.has(key)?state.favorites.delete(key):state.favorites.add(key);state.playlists["Liked Songs"]=[...state.favorites];save();haptic("MEDIUM");render()}
function shuffle(){state.shuffle=!state.shuffle;save();haptic("LIGHT");msg(state.shuffle?"Shuffle on":"Shuffle off")}
function repeat(){state.repeat=state.repeat==="all"?"one":state.repeat==="one"?"off":"all";save();haptic("LIGHT");msg("Repeat "+state.repeat)}
function modal(title,body){document.querySelector(".modal")?.remove();const m=document.createElement("div");m.className="modal";m.innerHTML='<section class="sheet"><div class="sheet-head"><h2>'+esc(title)+'</h2><button class="close" data-action="close">×</button></div>'+body+"</section>";document.body.appendChild(m);m.onclick=e=>{if(e.target===m)m.remove()};wire(m)}
function queue(){const q=(state.queue.length?state.queue:songs.map(s=>s.id)).map(id=>songs.find(s=>String(s.id)===String(id))).filter(Boolean);modal("Queue",q.map(s=>'<button class="queue-row" data-q="'+esc(String(s.id))+'">'+art(s)+'<div class="song-info"><b>'+esc(s.title)+'</b><span>'+esc(s.artist)+'</span></div></button>').join(""));document.querySelectorAll("[data-q]").forEach(x=>x.onclick=()=>{document.querySelector(".modal").remove();openPlayer(x.dataset.q)})}
function lyrics(){modal("Lyrics",'<div class="empty"><div style="font-size:45px">♫</div><p><b>'+esc(state.current?.title||"No song")+'</b></p><p>The ISAI lyrics screen is prepared for a synced-lyrics provider in the native build. This prototype does not invent lyric content.</p></div>')}
function eq(){const bands=[60,150,400,1000,2400,6000,12000];let html='<div class="chips"><button class="chip active">ISAI Flat</button><button class="chip">Bass</button><button class="chip">Vocal</button><button class="chip">Treble</button></div>';bands.forEach(f=>{html+='<div style="padding:14px 0;border-bottom:1px solid var(--line)"><label style="display:flex;justify-content:space-between;font-size:12px"><span>'+f+' Hz</span><span>0 dB</span></label><input style="width:100%;accent-color:var(--gold)" type="range" min="-12" max="12" value="0"></div>'});modal("Equalizer",html)}
function sleep(){modal("Sleep timer",'<div class="quick">'+[15,30,45,60,0].map(m=>'<button class="card quick-card" data-sleep="'+m+'"><strong>'+(m?m:"Off")+'</strong><span>'+(m?"minutes":"Cancel timer")+'</span></button>').join("")+"</div>");document.querySelectorAll("[data-sleep]").forEach(x=>x.onclick=()=>{const m=+x.dataset.sleep;state.sleepUntil=m?Date.now()+m*60000:0;save();document.querySelector(".modal")?.remove();msg(m?"Sleep timer set":"Sleep timer off")})}
function speed(){modal("Playback speed",'<div class="chips">'+[.75,1,1.25,1.5,2].map(v=>'<button class="chip '+(state.speed===v?"active":"")+'" data-speed="'+v+'">'+v+"×</button>").join("")+"</div>");document.querySelectorAll("[data-speed]").forEach(x=>x.onclick=()=>{state.speed=+x.dataset.speed;audio.playbackRate=state.speed;save();document.querySelector(".modal").remove();msg("Speed "+state.speed+"×");render()})}
function favorites(){view.innerHTML='<div class="eyebrow">Liked songs</div><h1>Your favorites.</h1><div class="card list">'+songs.filter(s=>state.favorites.has(s.id)).map(row).join("")+'</div>';wire()}
function history(){view.innerHTML='<div class="eyebrow">History</div><h1>Recently played.</h1><div class="card list">'+state.history.map(id=>songs.find(s=>s.id===id)).filter(Boolean).map(row).join("")+'</div>';wire()}
function showPlaylist(name){const ids=state.playlists[name]||[];view.innerHTML='<div class="eyebrow">Playlist</div><h1>'+esc(name)+'</h1><div class="card list">'+ids.map(id=>songs.find(s=>s.id===id)).filter(Boolean).map(row).join("")+'</div>';wire()}
function serviceConfig(){return JSON.parse(localStorage.getItem("isai-services")||"{}")}
function saveServiceConfig(v){localStorage.setItem("isai-services",JSON.stringify(v))}
function b64url(bytes){let s="";bytes.forEach(b=>s+=String.fromCharCode(b));return btoa(s).replace(/\+/g,"-").replace(/\//g,"_").replace(/=+$/,"")}
function randomString(n=64){const a=new Uint8Array(n);crypto.getRandomValues(a);return b64url(a)}
async function pkceChallenge(v){const h=await crypto.subtle.digest("SHA-256",new TextEncoder().encode(v));return b64url(new Uint8Array(h))}
async function spotifyLogin(){const c=serviceConfig();if(!c.spotifyClientId||!c.spotifyRedirect){msg("Add Spotify Client ID and redirect URI first");return}const v=randomString(),ch=await pkceChallenge(v),st=randomString(24);localStorage.setItem("isai-spotify-pkce",JSON.stringify({v,st,redirect:c.spotifyRedirect}));const u=new URL("https://accounts.spotify.com/authorize");u.search=new URLSearchParams({response_type:"code",client_id:c.spotifyClientId,scope:"user-read-private user-read-email user-read-playback-state user-modify-playback-state",code_challenge_method:"S256",code_challenge:ch,redirect_uri:c.spotifyRedirect,state:st}).toString();location.href=u}
async function finishSpotifyLogin(){const q=new URLSearchParams(location.search),code=q.get("code"),st=q.get("state");if(!code)return;const s=JSON.parse(localStorage.getItem("isai-spotify-pkce")||"null"),c=serviceConfig();if(!s||s.st!==st)return;try{const body=new URLSearchParams({grant_type:"authorization_code",code,redirect_uri:s.redirect,client_id:c.spotifyClientId,code_verifier:s.v});const r=await fetch("https://accounts.spotify.com/api/token",{method:"POST",headers:{"Content-Type":"application/x-www-form-urlencoded"},body});if(!r.ok)throw 0;localStorage.setItem("isai-spotify-token",JSON.stringify(await r.json()));localStorage.removeItem("isai-spotify-pkce");history.replaceState({},document.title,location.pathname);msg("Spotify connected")}catch(e){msg("Spotify sign-in failed")}}
async function youtubeLogin(){const c=serviceConfig();if(!c.youtubeClientId||!c.youtubeRedirect){msg("Add Google Client ID and redirect URI first");return}const v=randomString(),ch=await pkceChallenge(v),st=randomString(24);localStorage.setItem("isai-youtube-pkce",JSON.stringify({v,st,redirect:c.youtubeRedirect}));const u=new URL("https://accounts.google.com/o/oauth2/v2/auth");u.search=new URLSearchParams({client_id:c.youtubeClientId,response_type:"code",redirect_uri:c.youtubeRedirect,scope:"https://www.googleapis.com/auth/youtube.readonly",code_challenge:ch,code_challenge_method:"S256",access_type:"offline",prompt:"consent",state:st}).toString();location.href=u}
async function finishYouTubeLogin(){const q=new URLSearchParams(location.search),code=q.get("code"),st=q.get("state");if(!code)return;const s=JSON.parse(localStorage.getItem("isai-youtube-pkce")||"null"),c=serviceConfig();if(!s||s.st!==st)return;try{const body=new URLSearchParams({code,client_id:c.youtubeClientId,redirect_uri:s.redirect,grant_type:"authorization_code",code_verifier:s.v});const r=await fetch("https://oauth2.googleapis.com/token",{method:"POST",headers:{"Content-Type":"application/x-www-form-urlencoded"},body});if(!r.ok)throw 0;localStorage.setItem("isai-youtube-token",JSON.stringify(await r.json()));localStorage.removeItem("isai-youtube-pkce");history.replaceState({},document.title,location.pathname);msg("YouTube connected")}catch(e){msg("YouTube sign-in failed")}}
function services(){const c=serviceConfig(),sp=!!localStorage.getItem("isai-spotify-token"),yt=!!localStorage.getItem("isai-youtube-token");modal("Connected services",'<div class="setting"><div><b>Spotify</b><span>'+(sp?"Connected":"Not connected")+'</span></div><button class="more" data-service="spotify-login">'+(sp?"✓":"Connect")+'</button></div><div class="setting"><div><b>YouTube</b><span>'+(yt?"Connected":"Not connected")+'</span></div><button class="more" data-service="youtube-login">'+(yt?"✓":"Connect")+'</button></div><input id="spClient" class="search" value="'+esc(c.spotifyClientId||"")+'" placeholder="Spotify Client ID"><input id="spRedirect" class="search" value="'+esc(c.spotifyRedirect||"")+'" placeholder="Spotify HTTPS redirect URI"><input id="ytClient" class="search" value="'+esc(c.youtubeClientId||"")+'" placeholder="Google Client ID"><input id="ytRedirect" class="search" value="'+esc(c.youtubeRedirect||"")+'" placeholder="YouTube HTTPS redirect URI"><button class="pill primary" id="saveServices" style="width:100%;margin-top:12px">Save connection settings</button>');document.querySelector("#saveServices").onclick=()=>{saveServiceConfig({spotifyClientId:document.querySelector("#spClient").value.trim(),spotifyRedirect:document.querySelector("#spRedirect").value.trim(),youtubeClientId:document.querySelector("#ytClient").value.trim(),youtubeRedirect:document.querySelector("#ytRedirect").value.trim()});document.querySelector(".modal")?.remove();msg("Connection settings saved")};document.querySelector("[data-service=\"spotify-login\"]")?.addEventListener("click",spotifyLogin);document.querySelector("[data-service=\"youtube-login\"]")?.addEventListener("click",youtubeLogin)}
function newPlaylist(){const n=prompt("Playlist name");if(!n)return;state.playlists[n]=[];save();haptic("MEDIUM");render();msg("Playlist created")}
function importFiles(){picker.click()}
picker.onchange=e=>{
  const files=[...e.target.files];
  files.forEach(f=>{
    const id="local-"+Date.now()+"-"+Math.floor(Math.random()*999999);
    const src=URL.createObjectURL(f);
    urls.push(src);
    const item={id,title:f.name.replace(/\.[^.]+$/,""),artist:"Local file",album:"Imported",genre:"Local",duration:0,src,color:"linear-gradient(145deg,#171713,#b48b32)"};
    songs.push(item);
    const probe=new Audio(src);
    probe.onloadedmetadata=()=>{item.duration=Number.isFinite(probe.duration)?probe.duration:0;render()};
  });
  picker.value="";
  render();
  haptic("MEDIUM");
  msg(files.length+(files.length===1?" song":" songs")+" added");
}
async function handleEnded(){
  if(!state.current)return;
  if(state.repeat==="one"){await load(state.current);return}
  if(state.repeat==="off"){state.playing=false;nativePlugins.IsaiPlaybackService?.stop?.();save();updateMini();if(document.querySelector(".player"))player();return}
  next()
}
audio.ontimeupdate=progress;
audio.onloadedmetadata=progress;
audio.onplay=()=>{state.playing=true;updateMini()};
audio.onpause=()=>{if(!nativeAssetId){state.playing=false;updateMini()}};
audio.onended=handleEnded;
function wire(root=document){
root.querySelectorAll("[data-page]").forEach(x=>x.onclick=()=>setPage(x.dataset.page));
root.querySelectorAll("[data-song]").forEach(x=>x.onclick=()=>openPlayer(x.dataset.song));
root.querySelectorAll("[data-fav]").forEach(x=>x.onclick=e=>{e.stopPropagation();toggleFav(x.dataset.fav)});
root.querySelectorAll("[data-playlist]").forEach(x=>x.onclick=()=>showPlaylist(x.dataset.playlist));
root.querySelectorAll("[data-more]").forEach(x=>x.onclick=e=>{e.stopPropagation();const s=songs.find(v=>String(v.id)===String(x.dataset.more));modal(s.title,'<div class="setting"><div><b>Play now</b><span>Start immediately</span></div><button class="more" data-play="'+s.id+'">▶</button></div><div class="setting"><div><b>Add to queue</b><span>Play after current queue</span></div><button class="more" data-add="'+s.id+'">＋</button></div>')});
root.querySelectorAll("[data-play]").forEach(x=>x.onclick=()=>{document.querySelector(".modal")?.remove();openPlayer(x.dataset.play)});
root.querySelectorAll("[data-add]").forEach(x=>x.onclick=()=>{state.queue.push(x.dataset.add);save();document.querySelector(".modal")?.remove();msg("Added to queue")});
root.querySelectorAll("[data-action]").forEach(x=>{const a=x.dataset.action;const fn={home:()=>setPage("home"),search:()=>setPage("explore"),"toggle-theme":()=>{state.theme=state.theme==="dark"?"light":"dark";save();render()},import:importFiles,"open-player":()=>state.current&&player(),"player-back":()=>setPage(state.playerOrigin||"home"),play:togglePlay,previous,next,shuffle,repeat,queue,lyrics,equalizer:eq,sleep,speed,favorites,history,services,"rescan":()=>{haptic("LIGHT");initNativeMusic()},"new-playlist":newPlaylist,"shuffle-toggle":()=>{state.shuffle=!state.shuffle;save();haptic("LIGHT");render()},
"toggle-haptics":()=>{state.haptics=!state.haptics;save();if(state.haptics)haptic("LIGHT");render()},
"toggle-gestures":()=>{state.gestures=!state.gestures;save();haptic("LIGHT");render()},
close:()=>document.querySelector(".modal")?.remove()}[a];if(fn)x.onclick=fn});
root.querySelectorAll("[data-genre]").forEach(x=>x.onclick=()=>{root.querySelectorAll(".chip").forEach(c=>c.classList.remove("active"));x.classList.add("active");const g=x.dataset.genre;document.querySelector("#results").innerHTML=(g==="All"?songs:songs.filter(s=>s.genre===g)).map(row).join("");wire()});
const search=root.querySelector("#search");if(search)search.oninput=e=>{const q=e.target.value.toLowerCase();document.querySelector("#results").innerHTML=songs.filter(s=>(s.title+" "+s.artist+" "+s.album+" "+s.genre).toLowerCase().includes(q)).map(row).join("")||'<div class="empty">No matches found.</div>';wire()}
}
setInterval(async ()=>{
  if(state.sleepUntil&&Date.now()>=state.sleepUntil){
    state.sleepUntil=0;
    save();
    if(nativeLibraryReady&&nativeAudio&&nativeAssetId){
      try{await nativeAudio.pause({assetId:nativeAssetId})}catch(_){}
      try{await nativePlugins.IsaiPlaybackService?.stop?.()}catch(_){}
    }else if(!audio.paused){
      audio.pause();
    }
    state.playing=false;
    updateMini();
    if(document.querySelector(".player"))player();
    msg("Sleep timer ended");
  }
},1000);audio.volume=state.volume;document.querySelectorAll(".nav").forEach(x=>x.onclick=()=>setPage(x.dataset.page));finishSpotifyLogin();finishYouTubeLogin();render();initNativeMusic();setInterval(()=>{if(nativeLibraryReady&&state.current&&state.playing)progress()},1000);

/* ISAI mobile interaction layer: gestures + haptic feedback */
const touchState={kind:null,x:0,y:0,moved:false,longPress:false,timer:null};
function clearTouchTimer(){if(touchState.timer){clearTimeout(touchState.timer);touchState.timer=null}}
function gestureOpenPlayer(){if(state.current){haptic("MEDIUM");player()}}
function installMobileInteractions(){
  if(window.__isaiMobileInteractions)return;
  window.__isaiMobileInteractions=true;
  document.addEventListener("touchstart",e=>{
    if(!state.gestures||e.touches.length!==1)return;
    const point=e.touches[0],target=e.target;
    const miniTarget=target.closest(".mini");
    const playerTarget=target.closest(".player");
    const songTarget=target.closest(".song");
    clearTouchTimer();
    touchState.kind=null;touchState.moved=false;touchState.longPress=false;
    touchState.x=point.clientX;touchState.y=point.clientY;
    if(miniTarget){
      touchState.kind="mini";
      return;
    }
    if(playerTarget&&!target.closest("button,input,.tools,.progress")){
      touchState.kind="player";
      return;
    }
    if(songTarget&&!target.closest("button,input")){
      touchState.kind="song";
      touchState.timer=setTimeout(()=>{
        touchState.longPress=true;
        const id=songTarget.dataset.song;
        const song=songs.find(v=>String(v.id)===String(id));
        if(song){haptic("MEDIUM");modal(song.title,'<div class="setting"><div><b>Play now</b><span>Start immediately</span></div><button class="more" data-play="'+esc(String(song.id))+'">▶</button></div><div class="setting"><div><b>Add to queue</b><span>Play after current queue</span></div><button class="more" data-add="'+esc(String(song.id))+'">＋</button></div>')}
      },550);
    }
  },{passive:true});
  document.addEventListener("touchmove",e=>{
    if(!state.gestures||!touchState.kind||e.touches.length!==1)return;
    const point=e.touches[0],dx=point.clientX-touchState.x,dy=point.clientY-touchState.y;
    if(Math.abs(dx)>10||Math.abs(dy)>10){touchState.moved=true;clearTouchTimer()}
    if(touchState.kind==="mini"&&Math.abs(dy)>20&&Math.abs(dy)>Math.abs(dx)*1.15)e.preventDefault();
    if(touchState.kind==="player"&&Math.abs(dx)>20&&Math.abs(dx)>Math.abs(dy)*1.15)e.preventDefault();
    if(touchState.kind==="player"&&dy>20&&dy>Math.abs(dx)*1.15)e.preventDefault();
  },{passive:false});
  document.addEventListener("touchend",e=>{
    if(!state.gestures||!touchState.kind)return;
    const dx=(e.changedTouches[0]?.clientX||touchState.x)-touchState.x;
    const dy=(e.changedTouches[0]?.clientY||touchState.y)-touchState.y;
    const kind=touchState.kind,moved=touchState.moved,longPress=touchState.longPress;
    clearTouchTimer();
    touchState.kind=null;
    if(longPress){e.preventDefault();return}
    if(Math.max(Math.abs(dx),Math.abs(dy))<55)return;
    if(kind==="mini"&&dy<-55){gestureOpenPlayer();return}
    if(kind==="player"&&dy>65){haptic("LIGHT");setPage(state.playerOrigin||"home");return}
    if(kind==="player"&&Math.abs(dx)>65&&Math.abs(dx)>Math.abs(dy)*1.15){
      haptic("MEDIUM");
      if(dx<0)next();else previous();
    }
  },{passive:false});
  document.addEventListener("touchcancel",()=>{clearTouchTimer();touchState.kind=null}, {passive:true});
}
installMobileInteractions();