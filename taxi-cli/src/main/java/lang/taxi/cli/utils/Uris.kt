package lang.taxi.cli.utils

import java.net.URI

fun URI.concat(other: String): URI {
    val thisString = this.toString()
    val normalizedThis = if (thisString.endsWith("/")) thisString else thisString + "/"
    val normalizedOther = if (other.startsWith("/")) other.substring(1) else other
    return URI.create(normalizedThis + normalizedOther)
}

fun String.toUri(): URI {
    return URI.create(this)
}
