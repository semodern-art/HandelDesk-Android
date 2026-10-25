package de.handeltheke.handeldesk

import android.app.Application
import de.handeltheke.handeldesk.data.local.HandelDeskDatabase
import de.handeltheke.handeldesk.data.remote.BridgeClient
import de.handeltheke.handeldesk.data.repo.*
import de.handeltheke.handeldesk.security.SecureBridgeSettings

class HandelDeskApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}

class AppContainer(app: Application) {
    val db = HandelDeskDatabase.get(app)
    val bridgeSettings = SecureBridgeSettings(app)
    val bridge = BridgeClient(bridgeSettings)
    val productSearch = ProductSearchRepository(db.products())
    val productEditor = ProductEditorRepository(db)
    val snapshot = SnapshotRepository(db, bridge)
    val imageCache = ImageCacheRepository(app, db, bridge)
    val references = ReferenceRepository(db)
    val sync = SyncRepository(db, bridge)
}
