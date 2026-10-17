package com.rishinnovations.isai

import android.app.Application
import android.content.Context
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.memory.MemoryCache
import com.rishinnovations.isai.data.db.IsaiDatabase
import com.rishinnovations.isai.data.library.ArtworkFetcher
import com.rishinnovations.isai.data.library.ArtworkKeyer
import com.rishinnovations.isai.data.library.ImportManager
import com.rishinnovations.isai.data.library.LibraryRepository
import com.rishinnovations.isai.data.library.LyricsRepository
import com.rishinnovations.isai.data.settings.AppSettings
import com.rishinnovations.isai.data.settings.SettingsRepository
import com.rishinnovations.isai.data.user.UserDataRepository
import com.rishinnovations.isai.playback.SleepTimerController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch

/** One-shot messages for the UI (snackbars), posted from anywhere in the process. */
object AppMessages {
    private val _flow = MutableSharedFlow<String>(extraBufferCapacity = 16)
    val flow: SharedFlow<String> = _flow
    fun post(message: String) {
        _flow.tryEmit(message)
    }
}

/** Hand-rolled dependency container. The playback service, widget and UI all share these instances. */
class AppContainer(app: Application) {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val mainScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    val settings = SettingsRepository(app)
    val db: IsaiDatabase = IsaiDatabase.build(app)
    val library = LibraryRepository(app, db, settings, scope)
    val imports = ImportManager(app, db, scope)
    val userData = UserDataRepository(db, scope)
    val lyrics = LyricsRepository(app)
    val online = com.rishinnovations.isai.data.online.OnlineRepository()
    val sleepTimer = SleepTimerController(mainScope)

    @Volatile
    private var latestSettings = AppSettings()

    init {
        scope.launch { settings.settings.collect { latestSettings = it } }
    }

    /** Last settings value, readable synchronously from any thread. */
    fun settingsSnapshot(): AppSettings = latestSettings
}

class IsaiApplication : Application(), ImageLoaderFactory {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }

    override fun newImageLoader(): ImageLoader = ImageLoader.Builder(this)
        .components {
            add(ArtworkKeyer())
            add(ArtworkFetcher.Factory())
        }
        .memoryCache { MemoryCache.Builder(this).maxSizePercent(0.15).build() }
        .crossfade(false)
        .build()
}

val Context.container: AppContainer
    get() = (applicationContext as IsaiApplication).container
