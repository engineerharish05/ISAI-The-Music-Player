/* ISAI final hardening layer
 * Keeps the existing visual shell while making the web/native player stateful,
 * persistent, and safer for real-world testing.
 */
(function(){
  "use strict";

  const DB_NAME = "isai-library";
  const DB_VERSION = 1;
  const STORE = "tracks";
  let idb = null;

  function openLibraryDB(){
    if(!window.indexedDB) return Promise.resolve(null);
    if(idb) return idb;
    idb = new Promise((resolve,reject)=>{
      const req = indexedDB.open(DB_NAME, DB_VERSION);
      req.onupgradeneeded = () => {
        if(!req.result.objectStoreNames.contains(STORE)){
          req.result.createObjectStore(STORE,{keyPath:"id"});
        }
      };
      req.onsuccess = () => resolve(req.result);
      req.onerror = () => reject(req.error);
    }).catch(error=>{
      console.warn("ISAI IndexedDB unavailable:",error);
      idb = null;
      return null;
    });
    return idb;
  }

  function dbRequest(mode, action){
    return openLibraryDB().then(db=>{
      if(!db) return null;
      return new Promise((resolve,reject)=>{
        const tx = db.transaction(STORE,mode);
        const store = tx.objectStore(STORE);
        let request;
        try { request = action(store); }
        catch(error){ reject(error); return; }
        if(request){
          request.onsuccess = () => resolve(request.result);
          request.onerror = () => reject(request.error);
        }else{
          tx.oncomplete = () => resolve(true);
          tx.onerror = () => reject(tx.error);
        }
      });
    });
  }

  async function saveImportedTrack(track, file){
    try{
      await dbRequest("readwrite",store=>store.put({
        id:track.id,
        title:track.title,
        artist:track.artist,
        album:track.album,
        genre:track.genre,
        duration:track.duration,
        color:track.color,
        name:file.name,
        type:file.type || "audio/mpeg",
        blob:file
      }));
    }catch(error){
      console.warn("ISAI could not persist imported track:",error);
      msg("Added for this session; browser storage was unavailable");
    }
  }

  async function deleteImportedTrack(id){
    try{
      await dbRequest("readwrite",store=>store.delete(id));
    }catch(error){
      console.warn("ISAI could not delete imported track:",error);
    }
  }

  async function hydrateBrowserLibrary(){
    if(isNative) return;
    try{
      const records = await dbRequest("readonly",store=>store.getAll());
      if(!Array.isArray(records) || !records.length) return;
      records.sort((a,b)=>String(a.title).localeCompare(String(b.title)));
      for(const record of records){
        if(songs.some(s=>String(s.id)===String(record.id))) continue;
        const src=URL.createObjectURL(record.blob);
        urls.push(src);
        songs.push({
          id:record.id,
          title:record.title,
          artist:record.artist || "Local file",
          album:record.album || "Imported",
          genre:record.genre || "Local",
          duration:Number(record.duration)||0,
          src,
          displayName:record.name || "track",
          color:record.color || "linear-gradient(145deg,#171713,#b48b32)"
        });
      }
      if(state.queue.length===0) state.queue=songs.map(s=>s.id);
      render();
    }catch(error){
      console.warn("ISAI library restore failed:",error);
    }
  }

  function persistCurrent(){
    try{
      state.currentId = state.current ? state.current.id : null;
      save();
    }catch(error){
      console.warn("ISAI state persistence failed:",error);
    }
  }

  function mediaSessionUpdate(){
    if(!("mediaSession" in navigator) || !state.current) return;
    try{
      const s=state.current;
      navigator.mediaSession.metadata=new MediaMetadata({
        title:s.title || "ISAI",
        artist:s.artist || "Unknown artist",
        album:s.album || "ISAI",
        artwork:[{src:"assets/isai-logo.jpg",sizes:"512x512",type:"image/jpeg"}]
      });
      navigator.mediaSession.playbackState=state.playing?"playing":"paused";
      if(audio.duration && Number.isFinite(audio.duration) && audio.duration>0 &&
         Number.isFinite(audio.currentTime)){
        navigator.mediaSession.setPositionState?.({
          duration:audio.duration,
          playbackRate:audio.playbackRate || 1,
          position:Math.min(audio.currentTime,audio.duration)
        });
      }
    }catch(error){}
  }

  function installMediaSession(){
    if(!("mediaSession" in navigator)) return;
    const handlers={
      play:()=>togglePlay(),
      pause:()=>togglePlay(),
      nexttrack:()=>next(),
      previoustrack:()=>previous(),
      stop:()=>{
        try{audio.pause()}catch(_){}
        state.playing=false;
        updateMini();
        mediaSessionUpdate();
      },
      seekbackward:e=>{
        if(nativeLibraryReady&&nativeAudio&&nativeAssetId){
          nativeAudio.getCurrentTime({assetId:nativeAssetId}).then(r=>{
            nativeAudio.setCurrentTime({assetId:nativeAssetId,time:Math.max(0,(r.currentTime||0)-(e.seekOffset||10))});
          }).catch(()=>{});
        }else{
          audio.currentTime=Math.max(0,audio.currentTime-(e.seekOffset||10));
        }
      },
      seekforward:e=>{
        const d=Number.isFinite(audio.duration)?audio.duration:(state.current?.duration||0);
        if(nativeLibraryReady&&nativeAudio&&nativeAssetId){
          nativeAudio.getCurrentTime({assetId:nativeAssetId}).then(r=>{
            nativeAudio.setCurrentTime({assetId:nativeAssetId,time:Math.min(d,(r.currentTime||0)+(e.seekOffset||10))});
          }).catch(()=>{});
        }else{
          audio.currentTime=Math.min(d,audio.currentTime+(e.seekOffset||10));
        }
      },
      seekto:e=>{
        const d=Number.isFinite(audio.duration)?audio.duration:(state.current?.duration||0);
        const t=Math.max(0,Math.min(d,e.seekTime||0));
        if(nativeLibraryReady&&nativeAudio&&nativeAssetId){
          nativeAudio.setCurrentTime({assetId:nativeAssetId,time:t}).catch(()=>{});
        }else{
          audio.currentTime=t;
        }
      }
    };
    Object.entries(handlers).forEach(([name,fn])=>{
      try{navigator.mediaSession.setActionHandler(name,fn)}catch(_){}
    });
  }

  installMediaSession();

  /* Correct native-audio handling for local file:// assets. */
  nativeLoad = async function(s){
    if(!nativeAudio || !mediaStore || !s?.src) return false;
    try{
      if(nativeAssetId){
        try{await nativeAudio.stop({assetId:nativeAssetId})}catch(_){}
        try{await nativeAudio.unload({assetId:nativeAssetId})}catch(_){}
        nativeAssetId=null;
      }
      const prepared=await mediaStore.prepare({
        uri:s.src,
        displayName:s.displayName || "track.mp3"
      });
      await mediaStore.startPlaybackService?.();
      const assetId="isai-"+String(s.id).replace(/[^a-zA-Z0-9_-]/g,"_");
      await nativeAudio.preload({
        assetId,
        assetPath:prepared.uri,
        isUrl:true,
        audioChannelNum:1,
        volume:Math.max(.1,Math.min(1,state.volume)),
        notificationMetadata:{
          title:s.title,
          artist:s.artist,
          album:s.album
        }
      });
      nativeAssetId=assetId;
      try{await nativeAudio.setRate({assetId,rate:state.speed})}catch(_){}
      await nativeAudio.play({assetId});
      state.playing=true;
      mediaSessionUpdate();
      updateMini();
      return true;
    }catch(error){
      console.warn("ISAI native playback failed:",error);
      nativeAssetId=null;
      state.playing=false;
      return false;
    }
  };

  load = async function(s){
    state.current=s;
    state.currentId=s.id;
    if(nativeLibraryReady && s.src){
      const ok=await nativeLoad(s);
      if(ok){
        player();
        updateMini();
        return;
      }
    }
    if(!s.src){
      audio.removeAttribute("src");
      audio.load();
      state.playing=false;
      player();
      updateMini();
      mediaSessionUpdate();
      return;
    }
    if(nativeAssetId){
      try{await nativeAudio?.stop({assetId:nativeAssetId})}catch(_){}
      try{await nativeAudio?.unload({assetId:nativeAssetId})}catch(_){}
      nativeAssetId=null;
    }
    audio.src=s.src;
    audio.volume=state.volume;
    audio.playbackRate=state.speed;
    try{
      await audio.play();
      state.playing=true;
    }catch(error){
      state.playing=false;
      msg("Tap play to start this track");
    }
    player();
    updateMini();
    mediaSessionUpdate();
  };

  openPlayer = function(id){
    const s=songs.find(x=>String(x.id)===String(id));
    if(!s) return;
    haptic("LIGHT");
    state.playerOrigin=state.page;
    state.current=s;
    state.currentId=s.id;
    state.playing=true;
    state.history=[s.id,...state.history.filter(x=>String(x)!==String(s.id))].slice(0,50);
    if(!state.queue.length) state.queue=songs.map(x=>x.id);
    persistCurrent();
    load(s);
  };

  togglePlay = async function(){
    if(!state.current){
      if(songs.length) openPlayer(songs[0].id);
      return;
    }
    if(nativeLibraryReady && nativeAudio && nativeAssetId){
      try{
        if(state.playing){
          await nativeAudio.pause({assetId:nativeAssetId});
          state.playing=false;
        }else{
          await mediaStore?.startPlaybackService?.();
          await nativeAudio.resume({assetId:nativeAssetId});
          state.playing=true;
        }
      }catch(error){
        console.warn("ISAI native play/pause failed:",error);
        msg("Playback control unavailable");
      }
    }else if(state.playing){
      audio.pause();
      state.playing=false;
    }else{
      try{
        await audio.play();
        state.playing=true;
      }catch(error){
        state.playing=false;
        msg("Tap again to allow playback");
      }
    }
    persistCurrent();
    updateMini();
    mediaSessionUpdate();
    if(document.querySelector(".player")) player();
  };

  next = function(){
    if(!songs.length) return;
    const currentId=String(state.current?.id||"");
    const candidates=songs.filter(s=>String(s.id)!==currentId);
    if(state.shuffle && candidates.length){
      openPlayer(candidates[Math.floor(Math.random()*candidates.length)].id);
      return;
    }
    const queueIds=(state.queue.length?state.queue:songs.map(s=>s.id))
      .filter(id=>songs.some(s=>String(s.id)===String(id)));
    if(!queueIds.length) return;
    const i=queueIds.findIndex(id=>String(id)===currentId);
    const nextId=queueIds[(i+1+queueIds.length)%queueIds.length];
    openPlayer(nextId);
  };

  previous = async function(){
    let currentTime=audio.currentTime||0;
    if(nativeLibraryReady&&nativeAudio&&nativeAssetId){
      try{currentTime=(await nativeAudio.getCurrentTime({assetId:nativeAssetId})).currentTime||0}catch(_){}
    }
    if(currentTime>5){
      if(nativeLibraryReady&&nativeAudio&&nativeAssetId){
        try{await nativeAudio.setCurrentTime({assetId:nativeAssetId,time:0})}catch(_){}
      }else{
        audio.currentTime=0;
      }
      return;
    }
    const queueIds=(state.queue.length?state.queue:songs.map(s=>s.id))
      .filter(id=>songs.some(s=>String(s.id)===String(id)));
    if(!queueIds.length) return;
    const i=queueIds.findIndex(id=>String(id)===String(state.current?.id));
    openPlayer(queueIds[(i-1+queueIds.length)%queueIds.length]);
  };

  progress = async function(){
    let current=audio.currentTime||0;
    let duration=audio.duration;
    if(nativeLibraryReady&&nativeAudio&&nativeAssetId){
      try{
        current=(await nativeAudio.getCurrentTime({assetId:nativeAssetId})).currentTime||0;
        duration=(await nativeAudio.getDuration({assetId:nativeAssetId})).duration||state.current?.duration||0;
      }catch(_){}
    }
    const seek=document.querySelector("#seek");
    if(seek){
      seek.max=Math.max(1,duration||state.current?.duration||1);
      seek.value=current;
    }
    const c=document.querySelector("#cur");
    const d=document.querySelector("#dur");
    if(c)c.textContent=fmt(current);
    if(d)d.textContent=fmt(duration||state.current?.duration||0);
    mediaSessionUpdate();
  };

  updateMini = function(){
    if(!state.current){
      mini.classList.add("hidden");
      return;
    }
    mini.classList.remove("hidden");
    document.querySelector("#miniTitle").textContent=state.current.title;
    document.querySelector("#miniArtist").textContent=state.current.artist;
    document.querySelector("#miniPlay").textContent=state.playing?"Ⅱ":"▶";
    mediaSessionUpdate();
  };

  player = function(){
    const s=state.current;
    if(!s) return;
    const current=audio.currentTime||0;
    const duration=audio.duration||s.duration||1;
    view.innerHTML=
      '<div class="player">'+
      '<button class="back" data-action="player-back">‹ Back</button>'+
      '<div class="big-cover" style="background:'+s.color+'">'+(s.cover?'<img src="'+s.cover+'" alt="">':"♫")+'</div>'+
      '<button class="heart '+(state.favorites.has(s.id)?"active":"")+'" data-player-fav="'+esc(String(s.id))+'" aria-label="Favorite">'+
        (state.favorites.has(s.id)?"♥":"♡")+
      '</button>'+
      '<div class="player-title">'+esc(s.title)+'</div>'+
      '<div class="player-artist">'+esc(s.artist)+" · "+esc(s.album)+'</div>'+
      '<div class="progress"><input id="seek" type="range" min="0" max="'+Math.max(1,duration)+'" value="'+Math.floor(current)+'"><div class="times"><span id="cur">'+fmt(current)+'</span><span id="dur">'+fmt(duration)+'</span></div></div>'+
      '<div class="volume-row"><span>−</span><input id="volume" type="range" min="0" max="1" step=".01" value="'+state.volume+'"><span>＋</span></div>'+
      '<div class="controls"><button class="control" data-action="shuffle">⤨</button><button class="control" data-action="previous">‹</button><button class="control main" data-action="play">'+(state.playing?"Ⅱ":"▶")+'</button><button class="control" data-action="next">›</button><button class="control" data-action="repeat">↻</button></div>'+
      '<div class="tools"><button class="chip" data-action="lyrics">Lyrics</button><button class="chip" data-action="queue">Queue</button><button class="chip" data-action="equalizer">EQ</button><button class="chip" data-action="sleep">Sleep</button></div>'+
      '</div>';
    wire();
    const seek=document.querySelector("#seek");
    if(seek)seek.oninput=async e=>{
      const value=Number(e.target.value)||0;
      if(nativeLibraryReady&&nativeAudio&&nativeAssetId){
        try{await nativeAudio.setCurrentTime({assetId:nativeAssetId,time:value})}catch(_){}
      }else audio.currentTime=value;
      progress();
    };
    const volume=document.querySelector("#volume");
    if(volume)volume.oninput=async e=>{
      state.volume=Math.max(0,Math.min(1,Number(e.target.value)||0));
      audio.volume=state.volume;
      if(nativeLibraryReady&&nativeAudio&&nativeAssetId){
        try{await nativeAudio.setVolume({assetId:nativeAssetId,volume:Math.max(.1,state.volume)})}catch(_){}
      }
      persistCurrent();
    };
    document.querySelector("[data-player-fav]")?.addEventListener("click",e=>{
      e.stopPropagation();
      toggleFav(e.currentTarget.dataset.playerFav);
      player();
    });
  };

  queue = function(){
    const q=(state.queue.length?state.queue:songs.map(s=>s.id))
      .map(id=>songs.find(s=>String(s.id)===String(id))).filter(Boolean);
    const body=q.length
      ? q.map((s,i)=>
        '<div class="queue-row">'+art(s)+
        '<div class="song-info"><b>'+esc(s.title)+'</b><span>'+esc(s.artist)+'</span></div>'+
        '<button class="more" data-q-play="'+esc(String(s.id))+'">▶</button>'+
        '<button class="more" data-q-remove="'+esc(String(s.id))+'">×</button></div>'
      ).join("")
      : '<div class="empty">Queue is empty.</div>';
    modal("Queue",'<div class="row" style="margin-bottom:8px"><span class="more">'+q.length+' tracks</span><button class="more" data-q-clear>Clear</button></div>'+body);
    document.querySelectorAll("[data-q-play]").forEach(x=>x.onclick=()=>{
      document.querySelector(".modal")?.remove();
      openPlayer(x.dataset.qPlay);
    });
    document.querySelectorAll("[data-q-remove]").forEach(x=>x.onclick=()=>{
      state.queue=state.queue.filter(id=>String(id)!==String(x.dataset.qRemove));
      persistCurrent();
      queue();
    });
    document.querySelector("[data-q-clear]")?.addEventListener("click",()=>{
      state.queue=[];
      persistCurrent();
      queue();
    });
  };

  row = function(s){
    const favorite=state.favorites.has(s.id);
    return '<div class="song" data-song="'+esc(String(s.id))+'">'+
      art(s)+
      '<div class="song-info"><b>'+esc(s.title)+'</b><span>'+esc(s.artist)+" · "+esc(s.album)+" · "+fmt(s.duration)+'</span></div>'+
      '<button class="heart '+(favorite?"active":"")+'" data-fav="'+esc(String(s.id))+'">'+(favorite?"♥":"♡")+'</button>'+
      '<button class="more" data-more="'+esc(String(s.id))+'">⋯</button>'+
      '</div>';
  };

  const oldWire = wire;
  wire = function(root=document){
    oldWire(root);
    root.querySelectorAll("[data-more]").forEach(x=>{
      x.onclick=e=>{
        e.stopPropagation();
        const s=songs.find(v=>String(v.id)===String(x.dataset.more));
        if(!s) return;
        const imported=!isNative && String(s.id).startsWith("web-");
        modal(s.title,
          '<div class="setting"><div><b>Play now</b><span>Start immediately</span></div><button class="more" data-play="'+esc(String(s.id))+'">▶</button></div>'+
          '<div class="setting"><div><b>Add to queue</b><span>Play after current queue</span></div><button class="more" data-add="'+esc(String(s.id))+'">＋</button></div>'+
          (imported?'<div class="setting"><div><b>Remove from library</b><span>Delete this imported file</span></div><button class="more" data-delete="'+esc(String(s.id))+'">×</button></div>':"")
        );
        document.querySelector("[data-delete]")?.addEventListener("click",async()=>{
          const id=document.querySelector("[data-delete]").dataset.delete;
          await deleteImportedTrack(id);
          const i=songs.findIndex(v=>String(v.id)===String(id));
          if(i>=0){
            if(songs[i].src)URL.revokeObjectURL(songs[i].src);
            songs.splice(i,1);
          }
          state.queue=state.queue.filter(v=>String(v)!==String(id));
          state.history=state.history.filter(v=>String(v)!==String(id));
          state.favorites.delete(id);
          if(state.current&&String(state.current.id)===String(id)) state.current=null;
          persistCurrent();
          document.querySelector(".modal")?.remove();
          render();
          msg("Removed from library");
        });
      };
    });
  };

  /* Browser imports survive reloads through IndexedDB. */
  picker.onchange = async e=>{
    const files=[...e.target.files].filter(f=>f.type.startsWith("audio/") || /\.(mp3|m4a|aac|wav|ogg|flac|opus|webm)$/i.test(f.name));
    if(!files.length){
      picker.value="";
      msg("Choose an audio file");
      return;
    }
    let added=0;
    for(const file of files){
      const id="web-"+Date.now()+"-"+Math.random().toString(36).slice(2,9);
      const src=URL.createObjectURL(file);
      urls.push(src);
      const item={
        id,
        title:file.name.replace(/\.[^.]+$/,""),
        artist:"Local file",
        album:"Imported",
        genre:"Local",
        duration:0,
        src,
        displayName:file.name,
        color:"linear-gradient(145deg,#171713,#b48b32)"
      };
      songs.push(item);
      const probe=new Audio();
      probe.preload="metadata";
      probe.onloadedmetadata=async()=>{
        item.duration=Number.isFinite(probe.duration)?probe.duration:0;
        await saveImportedTrack(item,file);
        render();
      };
      probe.src=src;
      added++;
    }
    picker.value="";
    if(state.queue.length===0) state.queue=songs.map(s=>s.id);
    persistCurrent();
    render();
    haptic("MEDIUM");
    msg(added+(added===1?" song":" songs")+" added");
  };

  /* Browser audio lifecycle + native playback lifecycle. */
  audio.onplay=()=>{
    state.playing=true;
    updateMini();
    mediaSessionUpdate();
  };
  audio.onpause=()=>{
    if(!nativeAssetId){
      state.playing=false;
      updateMini();
      mediaSessionUpdate();
    }
  };
  audio.ontimeupdate=progress;
  audio.onloadedmetadata=progress;
  audio.onended=handleEnded;

  window.addEventListener("beforeunload",()=>{
    urls.forEach(u=>{try{URL.revokeObjectURL(u)}catch(_){}});
  });

  /* Small UI additions for the glass now-playing controls. */
  if(!document.getElementById("isai-final-style")){
    const style=document.createElement("style");
    style.id="isai-final-style";
    style.textContent=".player>.heart{margin:14px 0 0;width:46px;height:46px;background:rgba(255,255,255,.07);border:1px solid rgba(255,255,255,.14)}.volume-row{width:100%;display:flex;align-items:center;gap:9px;margin:8px 0 4px;color:var(--muted);font-size:12px}.volume-row input{flex:1;accent-color:var(--gold)}.queue-row .more{margin-left:2px}";
    document.head.appendChild(style);
  }

  /* Restore imported tracks before the first meaningful user interaction. */
  hydrateBrowserLibrary().then(()=>{
    if(state.currentId && !state.current){
      const restored=songs.find(s=>String(s.id)===String(state.currentId));
      if(restored) state.current=restored;
    }
    render();
    updateMini();
  });

  /* Native library scan is intentionally retained from the original implementation. */
  if(isNative) initNativeMusic();
  installMediaSession();
  render();
})();
