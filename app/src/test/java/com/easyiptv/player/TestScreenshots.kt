package com.easyiptv.player

internal object TestScreenshots {
    fun save(name:String) {
        val activity=androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry.getInstance()
            .getActivitiesInStage(androidx.test.runner.lifecycle.Stage.RESUMED).first()
        val dialog=org.robolectric.shadows.ShadowDialog.getLatestDialog()
        val view=(dialog?.takeIf {it.isShowing}?.window ?: activity.window).decorView
        val bitmap=android.graphics.Bitmap.createBitmap(view.width,view.height,android.graphics.Bitmap.Config.ARGB_8888)
        view.draw(android.graphics.Canvas(bitmap))
        java.io.File(System.getProperty("java.io.tmpdir"),name).outputStream().use {bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)}
    }
}
