from pathlib import Path
import shutil

p = Path('app/build.gradle.kts')
s = p.read_text()
if 'org.robolectric:robolectric' not in s:
    s = s.replace('android {', 'android {\n    testOptions { unitTests.isIncludeAndroidResources = true }', 1)
    s = s.replace('    val composeBom =', '''    testImplementation("org.robolectric:robolectric:4.14.1")
    testImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
    val composeBom =''', 1)
p.write_text(s)
tests = Path('app/src/test/java/com/easyiptv/player')
tests.mkdir(parents=True, exist_ok=True)
shutil.copyfile('tools/tests/LibraryItemMenusTest.kt', tests / 'LibraryItemMenusTest.kt')
