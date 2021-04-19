package com.android.tools.profgen

import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.zip.CRC32
import kotlin.test.assertEquals

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

    @Test
    fun testSerializationDeserializationForN() {
        val obf = ObfuscationMap(testData("mapping.txt"))
        val hrp = HumanReadableProfile(testData("fuzzy-composer-hrp.txt"))
        val apk = Apk(testData("app-release.apk"))
        val prof = ArtProfile(hrp, obf, apk)
        assertSerializationIntegrity(prof, ArtProfileSerializer.V0_0_1_N)
    }

    @Test
    fun testSerializationDeserializationForO() {
        val obf = ObfuscationMap(testData("mapping.txt"))
        val hrp = HumanReadableProfile(testData("fuzzy-composer-hrp.txt"))
        val apk = Apk(testData("app-release.apk"))
        val prof = ArtProfile(hrp, obf, apk)
        assertSerializationIntegrity(prof, ArtProfileSerializer.V0_0_5_O)
    }

    @Test
    fun testSerializationDeserializationForP() {
        val obf = ObfuscationMap(testData("mapping.txt"))
        val hrp = HumanReadableProfile(testData("fuzzy-composer-hrp.txt"))
        val apk = Apk(testData("app-release.apk"))
        val prof = ArtProfile(hrp, obf, apk)
        assertSerializationIntegrity(prof, ArtProfileSerializer.V0_1_0_P)
    }

    @Test
    fun testTranscodeFromPtoO() {
        val obf = ObfuscationMap(testData("mapping.txt"))
        val hrp = HumanReadableProfile(testData("fuzzy-composer-hrp.txt"))
        val apk = Apk(testData("app-release.apk"))
        val prof = ArtProfile(hrp, obf, apk)
        assertTranscodeIntegrity(prof, ArtProfileSerializer.V0_1_0_P, ArtProfileSerializer.V0_0_5_O)
    }

    @Test
    fun testTranscodeFromPtoN() {
        val obf = ObfuscationMap(testData("mapping.txt"))
        val hrp = HumanReadableProfile(testData("fuzzy-composer-hrp.txt"))
        val apk = Apk(testData("app-release.apk"))
        val prof = ArtProfile(hrp, obf, apk)
        assertTranscodeIntegrity(prof, ArtProfileSerializer.V0_1_0_P, ArtProfileSerializer.V0_0_1_N)
    }

    private fun assertSerializationIntegrity(prof: ArtProfile, serializer: ArtProfileSerializer) {
        var os = ByteArrayOutputStream()

        // save the synthetic profile in each version
        prof.save(os, serializer)

        // get the checksums of the files written to disk from memory profile
        val checksum1 = CRC32().apply { update(os.toByteArray()) }.value

        // read each profile from disk
        var deserialized = ArtProfile(os.toByteArray().inputStream())!!

        // ensure that the class/method counts of the profile serialized/deserialized are the same
        // as when we started
        assertEquals(prof.classCount, deserialized.classCount)
        assertEquals(prof.methodCount, deserialized.methodCount)

        os = ByteArrayOutputStream()
        // since these profiles were deserialized from disk, serialize them back to disk and deserialize them
        // again to ensure that they match
        deserialized.save(os, serializer)

        deserialized = ArtProfile(os.toByteArray().inputStream())!!

        assertEquals(prof.classCount, deserialized.classCount)
        assertEquals(prof.methodCount, deserialized.methodCount)

        // get the checksums of the files written to disk from the deserialized profiles
        val checksum2 = CRC32().apply { update(os.toByteArray()) }.value

        // Ensure that the file === ArtProfile(file).save(file, version)
        assertEquals(checksum1, checksum2)
    }

    private fun assertTranscodeIntegrity(prof: ArtProfile, start: ArtProfileSerializer, end: ArtProfileSerializer) {
        var os = ByteArrayOutputStream()

        // save the synthetic profile in each version
        prof.save(os, start)
        // read each profile from disk
        val startProf = ArtProfile(os.toByteArray().inputStream())!!

        os = ByteArrayOutputStream()
        // since these profiles were deserialized from disk, serialize them back to disk and deserialize them
        // again to ensure that they match
        startProf.save(os, end)

        val endProf = ArtProfile(os.toByteArray().inputStream())!!

        assertEquals(startProf.classCount, endProf.classCount)
        assertEquals(startProf.methodCount, endProf.methodCount)
    }

    private val ArtProfile.classCount: Int get() = profileData.values.sumBy { it.classes.size }
    private val ArtProfile.methodCount: Int get() = profileData.values.sumBy { it.methods.values.size }
}