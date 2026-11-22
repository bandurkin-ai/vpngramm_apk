package app.hy2setup.ui

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import app.hy2setup.MainViewModel
import app.hy2setup.StableProfile
import app.hy2setup.ProfileVps
import app.hy2setup.i18n.I18n
import app.hy2setup.i18n.Lang
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Язык интерфейса: переключатель в «Инструментах» меняет все тексты сразу, иврит — справа налево. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h1600dp-xxhdpi")
class LanguageUiTest {
    @get:Rule val compose = createComposeRule()

    @Before fun ru() = I18n.set(Lang.RU, null)
    @After fun back() = I18n.set(Lang.RU, null)

    private fun vm() = MainViewModel(ApplicationProvider.getApplicationContext<android.app.Application>()).apply {
        selectedTab = 2
        plusProfile = StableProfile.create("example.org", "vpn", "", "https://www.bing.com/").copy(vps = ProfileVps("1", "203.0.113.10", "vpn"))
        host = "203.0.113.10"
    }

    @OptIn(com.github.takahirom.roborazzi.ExperimentalRoborazziApi::class)
    @Test
    fun switchFromToolsScreen() {
        val model = vm()
        compose.setContent { Hy2Theme(dark = false, dynamic = false) { app.hy2setup.i18n.I18nRoot { AppRoot(model) } } }
        compose.onNodeWithText("Язык интерфейса").assertExists()
        compose.onNodeWithText("Доступ к серверам").assertExists()

        compose.onNodeWithText("English").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Interface language").assertExists()
        compose.onNodeWithText("Server access").assertExists()
        compose.onNodeWithText("Доступ к серверам").assertDoesNotExist()
        compose.onNodeWithText("Home").assertExists()   // нижняя навигация тоже переведена
        com.github.takahirom.roborazzi.captureScreenRoboImage("build/screens/9-lang-en.png")

        compose.onNodeWithText("עברית").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("שפת הממשק").assertExists()
        compose.onNodeWithText("גישה לשרתים").assertExists()
        com.github.takahirom.roborazzi.captureScreenRoboImage("build/screens/9-lang-he.png")

        compose.onNodeWithText("Русский").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Язык интерфейса").assertExists()
    }

    @Test
    fun hebrewLayoutIsRightToLeft() {
        I18n.set(Lang.HE, null)
        var dir: androidx.compose.ui.unit.LayoutDirection? = null
        compose.setContent {
            app.hy2setup.i18n.I18nRoot { dir = androidx.compose.ui.platform.LocalLayoutDirection.current }
        }
        compose.waitForIdle()
        org.junit.Assert.assertEquals(androidx.compose.ui.unit.LayoutDirection.Rtl, dir)
    }
}
