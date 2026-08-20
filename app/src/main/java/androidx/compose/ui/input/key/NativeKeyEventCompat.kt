package androidx.compose.ui.input.key

/**
 * Compatibility bridge for the MainActivity import used by this project.
 * Compose exposes nativeKeyEvent as a KeyEvent member property on Android;
 * this package-level extension makes the explicit import resolvable while
 * delegating to that member without changing call sites.
 */
@Suppress("EXTENSION_SHADOWED_BY_MEMBER")
val KeyEvent.nativeKeyEvent: android.view.KeyEvent
    get() = this.nativeKeyEvent
