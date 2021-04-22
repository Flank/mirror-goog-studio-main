package com.android.tools.profgen

import org.junit.Test

class ArtProfileTests {
    @Test
    fun testExactMethod() {
        val obf = ObfuscationMap(testData("mapping.txt"))
        val hrp = HumanReadableProfile(testData("exact-composer-hrp.txt"))
        val apk = Apk(testData("app-release.apk"))
        val profile = ArtProfile(hrp, obf, apk)
        assert(profile.profileData.isNotEmpty())
    }

    @Test
    fun testFuzzyMethods() {
        val obf = ObfuscationMap(testData("mapping.txt"))
        val hrp = HumanReadableProfile(testData("fuzzy-composer-hrp.txt"))
        val apk = Apk(testData("app-release.apk"))
        val profile = ArtProfile(hrp, obf, apk)
        assert(profile.profileData.isNotEmpty())
    }
}
