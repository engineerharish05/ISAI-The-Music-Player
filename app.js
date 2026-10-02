const songs=[
{id:1,title:"Midnight Architecture",artist:"ISAI Demo",album:"Digital Tamil",genre:"Electronic"},
{id:2,title:"Lavender Signal",artist:"ISAI Demo",album:"Digital Tamil",genre:"Ambient"},
{id:3,title:"நிலா",artist:"ISAI Demo",album:"Tamil Geometry",genre:"Tamil"},
{id:4,title:"Zero Latency",artist:"ISAI Demo",album:"System",genre:"Electronic"},
{id:5,title:"Purple Circuit",artist:"ISAI Demo",album:"System",genre:"Synthwave"},
{id:6,title:"அலை",artist:"ISAI Demo",album:"Tamil Geometry",genre:"Tamil"}];

let state={page:"home",current:null,playing:false,theme:localStorage.getItem("isai-theme")||"light",query:"",favorites:new Set(),history:[]};
const view=document.querySelector("#view"),mini=document.querySelector("#miniPlayer"),toast=document.querySelector("#toast");
document.body.classList.toggle("dark",state.theme==="dark");

const art=()=>'<div class="art">♫</div>';
function songRow(s){return '<div class="song" data-id="'+s.id+'">'+art()+'<div class="song-meta"><strong>'+s.title+'</strong><span>'+s.artist+' · '+s.album+'</span></div><button class="more" onclick="toggleFav('+s.id+',event)">'+(state.favorites.has(s.id)?"♥":"♡")+'</button></div>'}
function render(){document.querySelectorAll(".nav").forEach(x=>x.classList.toggle("active",x.dataset.page===state.page));({home,library,search,playlists,settings}[state.page]||home)()}
function home(){
const recent=state.history.length?songs.filter(s=>state.history.includes(s.id)).slice(0,4):songs.slice(0,4);
view.innerHTML='<div class="hero card"><div class="eyebrow">ISAI / THE MUSIC PLAYER</div><h1>Music, without friction.</h1><p>A calm, fast music experience built around your library, your sound and the Tamil identity of ISAI.</p><button class="cta" onclick="openPlayer('+songs[0].id+')">Start listening</button></div>'+
'<div class="section"><div class="row"><h2>Quick access</h2></div><div class="grid"><div class="card tile"><div class="big">'+songs.length+'</div><span>Songs</span></div><div class="card tile"><div class="big">'+new Set(songs.map(s=>s.artist)).size+'</div><span>Artists</span></div><div class="card tile"><div class="big">'+state.favorites.size+'</div><span>Favorites</span></div><div class="card tile"><div class="big">'+state.history.length+'</div><span>History</span></div></div></div>'+
'<div class="section"><div class="row"><h2>'+(state.history.length?"Continue listening":"Recently added")+'</h2><button class="more" onclick="setPage(\'library\')">See all</button></div><div class="song-list card list-card">'+recent.map(songRow).join("")+'</div></div>';bindSongs()
}
function library(){
view.innerHTML='<div class="eyebrow">Library</div><h1>Your music</h1><div class="chips"><button class="chip active">All</button><button class="chip">Songs</button><button class="chip">Albums</button><button class="chip">Artists</button><button class="chip">Folders</button></div><div class="section"><div class="row"><h2>'+songs.length+' songs</h2><button class="more" onclick="toastMsg(\'Library scan requested\')">↻</button></div><div class="song-list card list-card">'+songs.map(songRow).join("")+'</div></div>';bindSongs()
}
function search(){
const q=state.query.toLowerCase();const result=songs.filter(s=>(s.title+" "+s.artist+" "+s.album+" "+s.genre).toLowerCase().includes(q));
view.innerHTML='<div class="eyebrow">Search</div><h1>Find your sound.</h1><input autofocus class="search" id="searchInput" placeholder="Songs, artists, albums, genres…" value="'+state.query+'"><div class="song-list card list-card">'+(q?(result.length?result.map(songRow).join(""):'<div class="empty">No matches found.</div>'):'<div class="empty">Start typing to search your library.</div>')+'</div>';
const input=document.querySelector("#searchInput");input.oninput=e=>{state.query=e.target.value;search()};input.focus();input.setSelectionRange(input.value.length,input.value.length);bindSongs()
}
function playlists(){
view.innerHTML='<div class="eyebrow">Playlists</div><h1>Your collections.</h1><div class="grid"><div class="card tile"><div class="big">＋</div><span>Create playlist</span></div><div class="card tile"><div class="big">♥</div><span>Favorites · '+state.favorites.size+'</span></div></div><div class="section"><h2>Playlists</h2><div class="song-list card list-card"><div class="song"><div class="art">⌁</div><div class="song-meta"><strong>Late Night</strong><span>4 songs · ISAI</span></div></div><div class="song"><div class="art">இ</div><div class="song-meta"><strong>Tamil Geometry</strong><span>2 songs · ISAI</span></div></div></div></div>'
}
function settings(){
view.innerHTML='<div class="eyebrow">Settings</div><h1>ISAI controls.</h1><div class="card" style="padding:4px 18px"><div class="setting"><div><strong>Dark theme</strong><span>Low-light interface</span></div><button class="switch '+(state.theme==="dark"?"on":"")+'" onclick="toggleTheme()"><div class="knob"></div></button></div><div class="setting"><div><strong>Adaptive performance</strong><span>Audio gets priority over visuals</span></div><button class="switch on"><div class="knob"></div></button></div><div class="setting"><div><strong>Online features</strong><span>Connect only when enabled</span></div><button class="switch on"><div class="knob"></div></button></div><div class="setting"><div><strong>Equalizer</strong><span>System EQ</span></div><button class="more" onclick="toastMsg(\'Equalizer module is staged for the native build\')">›</button></div><div class="setting"><div><strong>Cloud library</strong><span>Google Drive and future providers</span></div><button class="more" onclick="toastMsg(\'Cloud integration is staged\')">›</button></div></div><div class="section"><div class="card tile"><div class="eyebrow">About</div><h2 style="margin-top:7px">ISAI — The Music Player</h2><span>Prototype · local-first · Tamil-inspired</span></div></div>'
}
function bindSongs(){document.querySelectorAll(".song[data-id]").forEach(el=>el.onclick=e=>{if(e.target.closest(".more"))return;openPlayer(+el.dataset.id)})}
function openPlayer(id){state.current=songs.find(s=>s.id===id);state.playing=true;state.history=[id,...state.history.filter(x=>x!==id)].slice(0,10);updateMini();renderPlayer()}
function renderPlayer(){
const s=state.current;if(!s)return;
view.innerHTML='<div class="player"><button class="back" onclick="setPage(\'home\')">‹ Back</button><div class="cover">♫</div><div class="player-title">'+s.title+'</div><div class="player-artist">'+s.artist+' · '+s.album+'</div><div class="progress"><input type="range" min="0" max="100" value="38"><div class="times"><span>1:24</span><span>4:12</span></div></div><div class="controls"><button class="control">↶</button><button class="control" onclick="previous()">‹</button><button class="control main" onclick="togglePlay()">'+(state.playing?"Ⅱ":"▶")+'</button><button class="control" onclick="next()">›</button><button class="control">↷</button></div><div class="chips"><button class="chip" onclick="toastMsg(\'Shuffle toggled\')">Shuffle</button><button class="chip" onclick="toastMsg(\'Repeat toggled\')">Repeat</button><button class="chip" onclick="toastMsg(\'Queue opened\')">Queue</button><button class="chip" onclick="toastMsg(\'Lyrics staged\')">Lyrics</button></div></div>'
}
function updateMini(){if(!state.current){mini.classList.add("hidden");return}mini.classList.remove("hidden");document.querySelector("#miniTitle").textContent=state.current.title;document.querySelector("#miniArtist").textContent=state.current.artist;document.querySelector("#playBtn").textContent=state.playing?"Ⅱ":"▶"}
function togglePlay(){state.playing=!state.playing;updateMini();if(state.current)renderPlayer()}
function next(){if(!state.current)return;const i=songs.findIndex(x=>x.id===state.current.id);openPlayer(songs[(i+1)%songs.length].id)}
function previous(){if(!state.current)return;const i=songs.findIndex(x=>x.id===state.current.id);openPlayer(songs[(i-1+songs.length)%songs.length].id)}
function toggleFav(id,e){e.stopPropagation();state.favorites.has(id)?state.favorites.delete(id):state.favorites.add(id);render()}
function setPage(p){state.page=p;render()}
function toggleTheme(){state.theme=state.theme==="dark"?"light":"dark";localStorage.setItem("isai-theme",state.theme);document.body.classList.toggle("dark",state.theme==="dark");render()}
function toastMsg(msg){toast.textContent=msg;toast.classList.add("show");setTimeout(()=>toast.classList.remove("show"),1800)}
document.querySelectorAll(".nav").forEach(b=>b.onclick=()=>setPage(b.dataset.page));
document.querySelector("#playBtn").onclick=togglePlay;document.querySelector("#nextBtn").onclick=next;document.querySelector("#prevBtn").onclick=previous;document.querySelector("#themeBtn").onclick=toggleTheme;render();
