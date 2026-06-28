package dev.overequal.text

/**
 * Word tokenization + the English stopword list, shared by the word-based charts.
 * Mirrors the reference's regex (`[a-zA-Z0-9'@#]+`, URLs stripped) and its
 * stopword set (the bundled `common_english.txt` plus single letters and common
 * contractions).
 */
object Words {
    private val URL = Regex("""https?://\S+""")
    private val TOKEN = Regex("""[a-zA-Z0-9'@#]+""")

    val stopwords: Set<String> by lazy { loadStopwords() }

    fun tokens(content: String): List<String> = TOKEN.findall(URL.replace(content, " ").lowercase())

    /** Content words: not a stopword, length > 1, not purely numeric. */
    fun contentWords(content: String): List<String> =
        tokens(content).filter { it.length > 1 && it !in stopwords && !it.all { c -> c.isDigit() } }

    private fun Regex.findall(s: String): List<String> = findAll(s).map { it.value }.toList()

    private fun loadStopwords(): Set<String> {
        val base =
            Words::class.java
                .getResourceAsStream("/wordlists/common_english.txt")
                ?.bufferedReader()
                ?.useLines { lines -> lines.map { it.trim().lowercase() }.filter { it.isNotEmpty() }.toHashSet() }
                ?: hashSetOf()
        base += "abcdefghijklmnopqrstuvwxyz".map { it.toString() }
        base +=
            listOf(
                "don't",
                "it's",
                "that's",
                "i'm",
                "i've",
                "i'll",
                "you're",
                "they're",
                "can't",
                "doesn't",
                "didn't",
                "isn't",
                "what's",
                "haven't",
                "there's",
                "i'd",
                "you'll",
                "you've",
                "won't",
                "wasn't",
                "couldn't",
                "wouldn't",
                "shouldn't",
                "aren't",
                "weren't",
                "hasn't",
                "hadn't",
                "let's",
                "we're",
                "we'll",
                "we've",
                "he's",
                "she's",
                "it'll",
                "that'll",
                "who's",
                "where's",
                "how's",
                "dont",
                "cant",
                "didnt",
                "doesnt",
                "isnt",
                "wont",
                "wasnt",
                "couldnt",
                "wouldnt",
                "shouldnt",
                "im",
                "ive",
                "ill",
                "id",
                "youre",
                "theyre",
                "whats",
                "havent",
                "theres",
                "theyll",
                "youve",
                "youd",
                "were",
                "well",
                "weve",
                "hes",
                "shes",
                "its",
                "thats",
                "whos",
                "wheres",
                "hows",
                "gonna",
                "wanna",
                "gotta",
                "kinda",
                "sorta",
                "lotsa",
            )
        return base
    }
}
