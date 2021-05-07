package com.android.tools.profgen

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.zip.CRC32
import kotlin.test.assertEquals
import kotlin.test.fail

class ArtProfileTests {
    @Test
    fun testExactMethod() {
        val obf = ObfuscationMap(testData("mapping.txt"))
        val hrp = strictHumanReadableProfile("exact-composer-hrp.txt")
        val apk = Apk(testData("app-release.apk"))
        val profile = ArtProfile(hrp, obf, apk)
        assert(profile.profileData.isNotEmpty())
    }

    @Test
    fun testFuzzyMethods() {
        val obf = ObfuscationMap(testData("mapping.txt"))
        val hrp = strictHumanReadableProfile("fuzzy-composer-hrp.txt")
        val apk = Apk(testData("app-release.apk"))
        val profile = ArtProfile(hrp, obf, apk)
        assert(profile.profileData.isNotEmpty())
    }

    @Test
    fun testSerializationDeserializationForN() {
        val obf = ObfuscationMap(testData("mapping.txt"))
        val hrp = strictHumanReadableProfile("fuzzy-composer-hrp.txt")
        val apk = Apk(testData("app-release.apk"))
        val prof = ArtProfile(hrp, obf, apk)
        assertSerializationIntegrity(prof, ArtProfileSerializer.V0_0_1_N)
    }

    @Test
    fun testSerializationDeserializationForO() {
        val obf = ObfuscationMap(testData("mapping.txt"))
        val hrp = strictHumanReadableProfile("fuzzy-composer-hrp.txt")
        val apk = Apk(testData("app-release.apk"))
        val prof = ArtProfile(hrp, obf, apk)
        assertSerializationIntegrity(prof, ArtProfileSerializer.V0_0_5_O)
    }

    @Test
    fun testSerializationDeserializationForP() {
        val obf = ObfuscationMap(testData("mapping.txt"))
        val hrp = strictHumanReadableProfile("fuzzy-composer-hrp.txt")
        val apk = Apk(testData("app-release.apk"))
        val prof = ArtProfile(hrp, obf, apk)
        assertSerializationIntegrity(prof, ArtProfileSerializer.V0_1_0_P)
    }

    @Test
    fun testTranscodeFromPtoO() {
        val obf = ObfuscationMap(testData("mapping.txt"))
        val hrp = strictHumanReadableProfile("fuzzy-composer-hrp.txt")
        val apk = Apk(testData("app-release.apk"))
        val prof = ArtProfile(hrp, obf, apk)
        assertTranscodeIntegrity(prof, ArtProfileSerializer.V0_1_0_P, ArtProfileSerializer.V0_0_5_O)
    }

    @Test
    fun testTranscodeFromPtoN() {
        val obf = ObfuscationMap(testData("mapping.txt"))
        val hrp = strictHumanReadableProfile("fuzzy-composer-hrp.txt")
        val apk = Apk(testData("app-release.apk"))
        val prof = ArtProfile(hrp, obf, apk)
        assertTranscodeIntegrity(prof, ArtProfileSerializer.V0_1_0_P, ArtProfileSerializer.V0_0_1_N)
    }

    @Test
    fun testCompareAgainstProfmanProfile() {
        val golden = ArtProfile(testData("com.example.countdown.prof"))!!
        val hrp = HumanReadableProfile(
            testData("com.example.countdown-hrp.txt"), strictDiagnostics)!!
        val profgen =
            ArtProfile(hrp, ObfuscationMap.Empty, Apk(testData("app-release.apk")))

        assertThat(golden.profileData.size).isEqualTo(profgen.profileData.size)
        val (goldenDexFile, goldenDexData) = golden.profileData.toList().first()
        val (profgenDexFile, profgenDexData) = profgen.profileData.toList().first()
        assertThat(profgenDexData.classes).isEqualTo(goldenDexData.classes)
        assertThat(profgenDexData.methods).isEqualTo(goldenDexData.methods)
        assertThat(goldenDexFile.dexChecksum).isEqualTo(profgenDexFile.dexChecksum)
        assertThat(goldenDexFile.dexChecksum).isEqualTo(profgenDexFile.dexChecksum)
        assertThat(goldenDexFile.header.methodIds.size)
            .isEqualTo(profgenDexFile.header.methodIds.size)
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

private fun strictHumanReadableProfile(path: String) =
    HumanReadableProfile(testData(path), strictDiagnostics)!!

private val strictDiagnostics = Diagnostics { error -> fail(error) }
