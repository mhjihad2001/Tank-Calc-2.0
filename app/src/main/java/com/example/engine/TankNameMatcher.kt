package com.example.engine

object TankNameMatcher {

    fun normalize(name: String): String {
        return name
            .replace(Regex("[\\u2010\\u2011\\u2012\\u2013\\u2014\\u2015\\u2016\\u2017\\u2018\\u2019\\u2020\\u2021\\u2022\\u2023\\u2024\\u2025\\u2026\\u2027\\u2028\\u2029\\u202A\\u202B\\u202C\\u202D\\u202E\\u202F\\u2030\\u2031\\u2032\\u2033\\u2034\\u2035\\u2036\\u2037\\u2038\\u2039\\u203A\\u203B\\u203C\\u203D\\u203E\\u203F\\u2040\\u2041\\u2042\\u2043\\u2044\\u2045\\u2046\\u2047\\u2048\\u2049\\u204A\\u204B\\u204C\\u204D\\u204E\\u204F\\u2050\\u2051\\u2052\\u2053\\u2054\\u2055\\u2056\\u2057\\u2058\\u2059\\u205A\\u205B\\u205C\\u205D\\u205E\\u205F\\u2060]"), "-")
            .replace(Regex("[^a-zA-Z0-9-]"), "")
            .trim()
            .uppercase()
    }

    fun simpleMatch(name1: String, name2: String): Boolean {
        return normalize(name1) == normalize(name2)
    }

    fun alphanumericMatch(name1: String, name2: String): Boolean {
        val norm1 = name1.replace(Regex("[^a-zA-Z0-9]"), "").uppercase()
        val norm2 = name2.replace(Regex("[^a-zA-Z0-9]"), "").uppercase()
        return norm1 == norm2
    }

    fun findVolume(volumes: Map<String, Double>, tankName: String): Double? {
        if (volumes.isEmpty()) return null
        volumes[tankName]?.let { return it }
        val norm = normalize(tankName)
        val direct = volumes.entries.find { normalize(it.key) == norm }
        if (direct != null) return direct.value
        val alpha = volumes.entries.find { alphanumericMatch(it.key, tankName) }
        if (alpha != null) return alpha.value
        return null
    }
}
