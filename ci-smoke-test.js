const fs = require("fs");

function read(path) {
  if (!fs.existsSync(path)) throw new Error("Missing required file: " + path);
  return fs.readFileSync(path, "utf8");
}
function must(text, pattern, label) {
  if (!pattern.test(text)) throw new Error("Smoke check failed: " + label);
}

const index = read("index.html");
const app = read("app.js");
const final = read("final.js");
const online = read("online.js");
const pkg = JSON.parse(read("package.json"));
const native = read("native/android/IsaiMediaStorePlugin.java");
const workflow = read(".github/workflows/android.yml");

must(index, /backend\.js.*app\.js.*final\.js.*online\.js/s, "script load order");
must(index, /id="view"/, "main view mount");
must(index, /id="miniPlayer"/, "mini player mount");
must(app, /function wire\(/, "event wiring function");
for (const action of ["home","search","toggle-theme","import","play","previous","next","shuffle","repeat","queue","lyrics","equalizer","sleep","speed","favorites","history","services","rescan"]) {
  must(app, new RegExp('["'] + action + '["'] + "\s*:"), "action handler: " + action);
}
must(app, /function installMobileInteractions\(/, "gesture layer");
must(app, /touchstart.*touchmove.*touchend/s, "touch gesture handlers");
must(app, /haptic\("LIGHT"\)|haptic\("MEDIUM"\)/, "haptic calls");
must(final, /navigator\.mediaSession/, "media session integration");
must(final, /nativeLoad\s*=\s*async function/, "native audio override");
must(final, /togglePlay\s*=\s*async function/, "play/pause override");
must(final, /data-player-fav/, "player favorite wiring");
must(online, /youtube-search/, "YouTube search endpoint");
must(online, /YT\.Player/, "YouTube player");
must(online, /#onlineMount/, "Explore online mount");
must(native, /@CapacitorPlugin\(\s*name = "IsaiMediaStore"/s, "native plugin registration");
must(native, /public void scan\(/, "native scan method");
must(native, /public void prepare\(/, "native prepare method");
must(native, /startPlaybackService/, "foreground playback bridge");
must(pkg.dependencies["@capgo/capacitor-native-audio"], "NativeAudio dependency");
must(pkg.dependencies["@capacitor/haptics"], "Haptics dependency");
must(workflow, /node --check app\.js/, "JavaScript validation");
must(workflow, /Build release APK and bundle/, "release build");
must(workflow, /Upload release APK/, "release APK artifact");
must(workflow, /Upload debug APK/, "debug APK artifact");

console.log("ISAI integration smoke checks passed.");
