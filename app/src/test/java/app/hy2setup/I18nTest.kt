package app.hy2setup

import app.hy2setup.i18n.I18n
import app.hy2setup.i18n.Lang
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.Locale

/** Перевод интерфейса: русский (исходный), английский, иврит; смена языка и подстановки в динамических текстах. */
class I18nTest {
    @After
    fun back() = I18n.setForTest(Lang.RU)

    private val CYR = Regex("[А-Яа-яЁё]")

    @Test
    fun russianIsTheSource() {
        I18n.setForTest(Lang.RU)
        assertEquals("Инструменты", I18n.tr("Инструменты"))
        assertEquals("vpngramm v4.7.6", I18n.tr("vpngramm v4.7.6"))
    }

    @Test
    fun englishAndHebrewExactTexts() {
        assertEquals("Tools", I18n.trIn(Lang.EN, "Инструменты"))
        assertEquals("כלים", I18n.trIn(Lang.HE, "Инструменты"))
        assertEquals("Home", I18n.trIn(Lang.EN, "Главная"))
        assertEquals("ראשי", I18n.trIn(Lang.HE, "Главная"))
        // собственное название языка не переводится
        assertEquals("Русский", I18n.trIn(Lang.EN, "Русский"))
        assertEquals("Interface language", I18n.trIn(Lang.EN, "Язык интерфейса"))
        assertEquals("שפת הממשק", I18n.trIn(Lang.HE, "Язык интерфейса"))
    }

    @Test
    fun templatesKeepTheirValues() {
        assertEquals("Servers: 3", I18n.trIn(Lang.EN, "Серверов: 3"))
        assertEquals("שרתים: 3", I18n.trIn(Lang.HE, "Серверов: 3"))
        assertEquals("STEP 2 of 4. DNS at name.com", I18n.trIn(Lang.EN, "ШАГ 2 из 4. DNS на name.com"))
        // несколько вставок и текст в них тоже переводится («телефона» → «the phone»)
        assertEquals(
            "Saved passwords, the SSH key, the name.com token and the OneProvider keys were deleted from the phone.",
            I18n.trIn(Lang.EN, "Сохранённые пароли, SSH-ключ, токен name.com и ключи OneProvider удалены с телефона."),
        )
    }

    @Test
    fun longTextsAreTranslatedBySentences() {
        val ru = "Служба работает. Служба не работает — попробуйте «Рестарт»."
        assertEquals("The service is running. The service is not running — try \"Restart\".", I18n.trIn(Lang.EN, ru))
        // склейка из нескольких кусков, которых нет целиком: узнаваемые куски переводятся
        val glued = "Ядро 6.1.0-13-amd64 старее 6.7 — для AmneziaWG приложение поставит новое ядро и перезагрузит сервер."
        val en = I18n.trIn(Lang.EN, glued)
        assertFalse(en, CYR.containsMatchIn(en))
        assertTrue(en, en.startsWith("Kernel 6.1.0-13-amd64 is older than 6.7"))
    }

    /** Строки журнала с сервера: приставка («ОШИБКА:», «==>», «✓») и текст переводятся отдельно. */
    @Test
    fun serverLogLines() {
        assertEquals("==> Configuring the firewall", I18n.trIn(Lang.EN, "==> Настройка файрвола"))
        assertEquals("PROBLEM: service hysteria-server.service is not running (inactive)", I18n.trIn(Lang.EN, "ПРОБЛЕМА: служба hysteria-server.service не работает (inactive)"))
        assertEquals("OK: service xray.service is running", I18n.trIn(Lang.EN, "OK: служба xray.service работает"))
        assertEquals("✓ The service is running.", I18n.trIn(Lang.EN, "✓ Служба работает."))
        assertEquals("שגיאה: נדרשות הרשאות root", I18n.trIn(Lang.HE, "ОШИБКА: нужны права root"))
        assertEquals("WARNING: fail2ban is not enabled: boom", I18n.trIn(Lang.EN, "ПРЕДУПРЕЖДЕНИЕ: fail2ban не включён: boom"))
    }

    @Test
    fun unknownTextStaysAsIs() {
        assertEquals("Неизвестный текст без перевода", I18n.trIn(Lang.EN, "Неизвестный текст без перевода"))
        assertEquals("203.0.113.10", I18n.trIn(Lang.HE, "203.0.113.10"))
    }

    @Test
    fun switchingLanguage() {
        I18n.setForTest(Lang.EN)
        assertEquals("Tools", I18n.tr("Инструменты"))
        assertFalse(I18n.rtl)
        I18n.setForTest(Lang.HE)
        assertEquals("כלים", I18n.tr("Инструменты"))
        assertTrue(I18n.rtl)
        I18n.setForTest(Lang.RU)
        assertEquals("Инструменты", I18n.tr("Инструменты"))
    }

    @Test
    fun languageFromDeviceLocale() {
        assertEquals(Lang.HE, Lang.forLocale(Locale("he")))
        assertEquals(Lang.HE, Lang.forLocale(Locale("iw")))
        assertEquals(Lang.EN, Lang.forLocale(Locale.US))
        assertEquals(Lang.RU, Lang.forLocale(Locale("ru")))
        assertEquals(Lang.RU, Lang.forLocale(Locale("uk")))
        assertEquals(Lang.EN, Lang.of("en")); assertEquals(null, Lang.of("fr"))
    }

    @Test
    fun choiceIsRemembered() {
        val store = object : app.hy2setup.platform.KeyValueStore {
            val m = HashMap<String, String?>()
            override fun getString(key: String, default: String?) = m[key] ?: default
            override fun getBoolean(key: String, default: Boolean) = default
            override fun getInt(key: String, default: Int) = default
            override fun edit() = object : app.hy2setup.platform.KeyValueStore.Editor {
                override fun putString(key: String, value: String?) = also { m[key] = value }
                override fun putBoolean(key: String, value: Boolean) = this
                override fun putInt(key: String, value: Int) = this
                override fun remove(key: String) = also { m.remove(key) }
                override fun apply() {}
            }
        }
        I18n.load(store, Lang.EN)
        assertEquals(Lang.EN, I18n.lang)                    // нет сохранённого — язык устройства
        I18n.set(Lang.HE, store)
        I18n.load(store, Lang.RU)
        assertEquals(Lang.HE, I18n.lang)                    // сохранённый выбор сильнее языка устройства
    }

    /** Каталоги читаются, а вставки {} в переводе совпадают с русским текстом. */
    @Test
    fun catalogsAreConsistent() {
        for (lang in listOf("en", "he")) {
            val lines = I18n::class.java.getResourceAsStream("/hy2setup/i18n/$lang.tsv")!!.bufferedReader().readLines().filter { it.isNotBlank() }
            assertTrue("$lang: ${lines.size}", lines.size > 1000)
            for (l in lines) {
                val (ru, tr) = l.split("\t", limit = 2).let { it[0] to it.getOrElse(1) { "" } }
                assertTrue("$lang: пустой перевод для $ru", tr.isNotBlank())
                assertEquals("$lang: вставки в «$ru»", Regex("\\{\\}").findAll(ru).count(), Regex("\\{\\d*\\}").findAll(tr).count())
                assertFalse("$lang: в переводе осталась кириллица: $tr", CYR.containsMatchIn(tr) && tr != ru)
            }
        }
    }

    /** Все готовые русские тексты экранов («...» без вставок) есть в каталогах — новый текст без перевода не пройдёт. */
    @Test
    fun everyScreenTextIsTranslated() {
        val root = listOf("src/main/java/app/hy2setup/ui", "../app/src/main/java/app/hy2setup/ui").map { File(it) }.firstOrNull { it.isDirectory } ?: return
        val literal = Regex("\"((?:[^\"\\\\\\n$]|\\\\.)*[А-Яа-яЁё](?:[^\"\\\\\\n$]|\\\\.)*)\"")
        val missing = ArrayList<String>()
        for (f in root.listFiles { x -> x.extension == "kt" }!!.sorted()) {
            var prev = ""
            for (line in f.readLines()) {
                val t = line.trim()
                val glued = prev.endsWith("+") || t.endsWith("+") || t.contains("\" + \"")   // кусок склеенного текста — переводится целиком
                if (t.isNotEmpty()) prev = t
                if (t.startsWith("//") || t.startsWith("*") || t.startsWith("/*") || glued) continue
                for (m in literal.findAll(line)) {
                    val ru = m.groupValues[1].replace("\\n", "\n").replace("\\\"", "\"").replace("\\\\", "\\")
                    if (!I18n.has(Lang.EN, ru) || !I18n.has(Lang.HE, ru)) missing += "${f.name}: $ru"
                }
            }
        }
        assertTrue("нет перевода (${missing.size}):\n" + missing.take(40).joinToString("\n"), missing.isEmpty())
    }
}
