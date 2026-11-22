package app.hy2setup.i18n

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.hy2setup.platform.KeyValueStore
import java.util.Locale

/** Язык интерфейса. Исходные тексты в коде — русские; английский и иврит подставляются из каталогов переводов. */
enum class Lang(val code: String, val native: String, val rtl: Boolean) {
    RU("ru", "Русский", false),
    EN("en", "English", false),
    HE("he", "עברית", true),
    ;

    companion object {
        fun of(code: String?): Lang? = entries.firstOrNull { it.code == code }

        /** Язык по языку устройства: иврит и английский — свои, остальное (в том числе русский и соседние) — русский. */
        fun forLocale(l: Locale): Lang = when (l.language) {
            "he", "iw" -> HE
            "en" -> EN
            else -> RU
        }
    }
}

/**
 * Перевод интерфейса во время показа: текст на русском → текст на выбранном языке.
 *
 * Каталог — ресурс `hy2setup/i18n/<язык>.tsv` (строки «русский<TAB>перевод», `\n` и `\t` экранированы).
 * Динамические тексты («Серверов: {}») записаны шаблонами: `{}` — любая вставка; в переводе вставки можно
 * переставлять — `{1}`, `{2}`. Длинные тексты, склеенные из нескольких предложений, переводятся по предложениям.
 * Что в каталоге не найдено, остаётся по-русски.
 */
object I18n {
    private val current = mutableStateOf(Lang.RU)

    /** Выбранный язык; чтение внутри Compose подписывает экран на смену языка. */
    var lang: Lang
        get() = current.value
        private set(v) { current.value = v }

    val rtl: Boolean get() = lang.rtl

    const val PREF_KEY = "uiLang"

    /** Читает сохранённый язык; если его нет — [systemDefault]. */
    fun load(prefs: KeyValueStore, systemDefault: Lang = Lang.RU) {
        lang = Lang.of(prefs.getString(PREF_KEY, null)) ?: systemDefault
    }

    /** Переключает язык и запоминает выбор. */
    fun set(l: Lang, prefs: KeyValueStore?) {
        lang = l
        prefs?.edit()?.putString(PREF_KEY, l.code)?.apply()
    }

    /** Только для проверок. */
    internal fun setForTest(l: Lang) { lang = l }

    // ------------------------------------------------------------------------------------------

    private class Template(val regex: Regex, val out: String, val inner: Regex?)

    private class Catalog(val lang: Lang, val exact: HashMap<String, String>, val templates: List<Template>) {
        val memo = HashMap<String, String>()

        /** Точные тексты от длинных к коротким — для замены кусков в составных строках. */
        val byLength: List<Map.Entry<String, String>> by lazy { exact.entries.filter { it.key.length >= 6 }.sortedByDescending { it.key.length } }
    }

    private val catalogs = HashMap<Lang, Catalog>()
    private val CYR = Regex("[А-Яа-яЁё]")
    private val PREFIXES = listOf("==> ", "OK: ", "ПРОБЛЕМА: ", "ИНФО: ", "ОШИБКА: ", "ПРЕДУПРЕЖДЕНИЕ: ", "ВАЖНО: ", "✓ ", "✗ ", "! ", "▶ ")
    private val SPLIT = Regex("(?<=[.!?…])\\s+|\\n+")

    @Synchronized
    private fun catalog(l: Lang): Catalog = catalogs.getOrPut(l) { parse(l) }

    private fun unescape(s: String): String {
        if (!s.contains('\\')) return s
        val b = StringBuilder(s.length)
        var i = 0
        while (i < s.length) {
            val c = s[i]
            if (c == '\\' && i + 1 < s.length) {
                when (val n = s[i + 1]) { 'n' -> b.append('\n'); 't' -> b.append('\t'); else -> b.append(n) }
                i += 2
            } else { b.append(c); i++ }
        }
        return b.toString()
    }

    private fun parse(l: Lang): Catalog {
        val exact = HashMap<String, String>()
        val templates = ArrayList<Template>()
        val stream = I18n::class.java.getResourceAsStream("/hy2setup/i18n/${l.code}.tsv") ?: return Catalog(l, exact, templates)
        stream.bufferedReader(Charsets.UTF_8).useLines { lines ->
            for (line in lines) {
                if (line.isEmpty() || line.startsWith("#")) continue
                val tab = line.indexOf('\t')
                if (tab <= 0) continue
                val ru = unescape(line.substring(0, tab))
                val tr = unescape(line.substring(tab + 1))
                if (tr.isEmpty()) continue
                if (ru.contains("{}")) {
                    val parts = ru.split("{}")
                    val rx = parts.joinToString("(.+?)", prefix = "^", postfix = "$") { Regex.escape(it) }
                    // для составных строк — шаблон без привязки к краям (только если в нём хватает постоянного текста)
                    val inner = if (parts.sumOf { it.length } >= 12) Regex(parts.joinToString("([^\\n]{1,80}?)") { Regex.escape(it) }) else null
                    templates += Template(Regex(rx, RegexOption.DOT_MATCHES_ALL), tr, inner)
                } else exact[ru] = tr
            }
        }
        // длинные шаблоны первыми: «Сервер {} готов» не должен перехватывать «Сервер {} готов к переезду»
        templates.sortByDescending { it.regex.pattern.length }
        return Catalog(l, exact, templates)
    }

    private fun fill(c: Catalog, out: String, groups: List<String>): String {
        if (!out.contains('{')) return out
        var n = 0
        val sb = StringBuilder()
        var i = 0
        while (i < out.length) {
            if (out[i] == '{') {
                val close = out.indexOf('}', i)
                if (close > i) {
                    val key = out.substring(i + 1, close)
                    val idx = if (key.isEmpty()) n++ else key.toIntOrNull()?.minus(1)
                    if (idx != null && idx in groups.indices) { sb.append(translateIn(c.lang, groups[idx])); i = close + 1; continue }
                }
            }
            sb.append(out[i]); i++
        }
        return sb.toString()
    }

    private fun lookup(c: Catalog, s: String): String? {
        c.exact[s]?.let { return it }
        for (t in c.templates) {
            val m = t.regex.matchEntire(s) ?: continue
            return fill(c, t.out, m.groupValues.drop(1))
        }
        return null
    }

    private fun translateIn(l: Lang, s: String): String {
        if (l == Lang.RU || s.isEmpty() || !CYR.containsMatchIn(s)) return s
        val c = catalog(l)
        synchronized(c) { c.memo[s]?.let { return it } }
        val r = compute(c, s)
        synchronized(c) {
            if (c.memo.size > 6000) c.memo.clear()
            c.memo[s] = r
        }
        return r
    }

    private fun compute(c: Catalog, s: String): String {
        lookup(c, s)?.let { return it }
        // пробелы по краям не мешают
        val core = s.trim()
        if (core.length != s.length && core.isNotEmpty()) {
            val r = lookup(c, core)
            if (r != null) return s.substring(0, s.indexOf(core)) + r + s.substring(s.indexOf(core) + core.length)
        }
        // строки журнала: «ОШИБКА: …», «==> шаг», «✓ …» — приставка и текст переводятся отдельно
        for (p in PREFIXES) {
            if (s.length > p.length && s.startsWith(p)) return (lookup(c, p) ?: p) + translateIn(c.lang, s.substring(p.length))
        }
        // по предложениям
        val parts = SPLIT.split(s)
        if (parts.size > 1) {
            var any = false
            val sb = StringBuilder()
            var pos = 0
            for (p in parts) {
                val at = s.indexOf(p, pos)
                sb.append(s, pos, at)                    // разделитель между предложениями
                val t = lookup(c, p)
                if (t != null) { any = true; sb.append(t) } else sb.append(p)
                pos = at + p.length
            }
            sb.append(s, pos, s.length)
            if (any) return sb.toString()
        }
        return fragments(c, s)
    }

    /**
     * Составные тексты, склеенные в коде из кусков («…» + условие + «…»): переводим узнаваемые куски —
     * сначала шаблоны со вставками, затем точные тексты от длинных к коротким. Узнано хоть что-то — вернём это.
     */
    private fun fragments(c: Catalog, s: String): String {
        var text = s
        for (t in c.templates) {
            val rx = t.inner ?: continue
            if (!rx.containsMatchIn(text)) continue
            text = rx.replace(text) { m -> fill(c, t.out, m.groupValues.drop(1)) }
        }
        for ((ru, tr) in c.byLength) {
            if (text.contains(ru)) text = text.replace(ru, tr)
        }
        return text
    }

    /** Перевод текста интерфейса на выбранный язык. */
    fun tr(ru: String): String = translateIn(lang, ru)

    /** Перевод на язык [l] (для проверок и каталогов). */
    internal fun trIn(l: Lang, ru: String): String = translateIn(l, ru)

    /** Есть ли у каталога языка [l] перевод для [ru] (для проверок). */
    internal fun has(l: Lang, ru: String): Boolean = lookup(catalog(l), ru) != null
}
