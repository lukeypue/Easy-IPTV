package com.easyiptv.player

import android.content.Context
import android.content.res.Configuration

/** Activity-scoped text enlargement that retains the user's accessibility setting. */
internal object AppReadability {
    /**
     * Call once from Activity.attachBaseContext, passing the original base context:
     * `super.attachBaseContext(AppReadability.wrap(newBase))`.
     *
     * Compose and native views/dialogs created with that Activity share this scale.
     * Density and DP geometry are unchanged; do not add a second LocalDensity or
     * text-size multiplier. Application-context views do not inherit this override.
     *
     * This is a configuration snapshot. On recreation, wrap the fresh system base
     * so updated accessibility settings are retained. Wrapping an already enlarged
     * context would compound the increase. Compose and native views can use different
     * SP conversion curves, including nonlinear scaling, so a 30% fontScale increase
     * does not guarantee 30% more pixels at every size or equal pixels across toolkits.
     */
    fun wrap(base: Context): Context {
        val configuration = Configuration(base.resources.configuration).apply {
            fontScale = base.resources.configuration.fontScale * 1.30f
        }
        return base.createConfigurationContext(configuration)
    }
}
