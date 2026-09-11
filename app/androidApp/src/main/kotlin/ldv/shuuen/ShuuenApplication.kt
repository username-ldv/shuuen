package ldv.shuuen

import android.app.Application
import io.github.aakira.napier.DebugAntilog
import io.github.aakira.napier.Napier

class ShuuenApplication : Application() {
  override fun onCreate() {
    super.onCreate()
    Napier.base(DebugAntilog())
  }
}
