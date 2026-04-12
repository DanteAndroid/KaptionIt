@file:Repository("https://repo1.maven.org/maven2/")
@file:DependsOn("commons-codec:commons-codec:1.16.1")

import org.apache.commons.codec.language.DoubleMetaphone

val metaphone = DoubleMetaphone().apply { maxCodeLen = 64 }

fun soundsLike(a: String, b: String): Boolean {
    val x = a.trim()
    val y = b.trim()
    if (x.isEmpty() || y.isEmpty()) return false
    if (x.equals(y, ignoreCase = true)) return true
    return metaphone.isDoubleMetaphoneEqual(x, y) ||
        metaphone.isDoubleMetaphoneEqual(x, y, true)
}

println("Equivoc A vs equivocate: -> ${soundsLike("Equivoc A", "equivocate")}")
println("Equivoc A (no space) vs equivocate: -> ${soundsLike("Equivoc A".replace(" ", ""), "equivocate")}")
println("Equivoc vs equivocate: -> ${soundsLike("Equivoc", "equivocate")}")
