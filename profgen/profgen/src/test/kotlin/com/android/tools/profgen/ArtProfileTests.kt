package com.android.tools.profgen

import com.google.common.truth.Truth.assertThat
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.util.zip.CRC32
import java.util.zip.ZipFile
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.fail
import org.junit.Test

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
    fun testMultiDexDeserializationForP() {
        val prof = ArtProfile(testData("baseline-multidex.prof"))!!
        assertSerializationIntegrity(prof, ArtProfileSerializer.V0_1_0_P)
    }

    @Test
    fun testMultiDexSerializationDeserializationForN() {
        val fileP = testData("multidex/baseline-multidex.prof")
        val fileN = testData("multidex/baseline-multidex-n.prof")
        val fileMN = testData("multidex/baseline-multidex.profm")

        val desP = ArtProfile(fileP)!!
        val desN = ArtProfile(fileN)!!
        val desMN = ArtProfile(fileMN)!!

        val combined = desP.addMetadata(desMN, MetadataVersion.V_001)

        val osCP = ByteArrayOutputStream()
        combined.save(osCP, ArtProfileSerializer.V0_1_0_P)
        assertTrue(osCP.toByteArray().contentEquals(fileP.readBytes()))

        val osCN = ByteArrayOutputStream()
        combined.save(osCN, ArtProfileSerializer.V0_0_1_N)
        assertTrue(osCN.toByteArray().contentEquals(fileN.readBytes()))

        val oscNM = ByteArrayOutputStream()
        combined.save(oscNM, ArtProfileSerializer.METADATA_FOR_N)
        assertTrue(oscNM.toByteArray().contentEquals(fileMN.readBytes()))
    }

    @Test
    fun testMultiDexDeserializationForO() {
        val prof = ArtProfile(testData("baseline-multidex.prof"))!!
        assertSerializationIntegrity(prof, ArtProfileSerializer.V0_0_5_O)
    }

    @Test
    fun testJetNewsApk() {
        val obf = ObfuscationMap(testData("jetnews/mapping.txt"))
        val hrp = strictHumanReadableProfile("baseline-prof-all-compose.txt")
        val apk = Apk(testData("jetnews/app-release.apk"))
        val prof = ArtProfile(hrp, obf, apk)
        assertSerializationIntegrity(prof, ArtProfileSerializer.V0_1_0_P)
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
    fun testSerializationDeserializationForNMeta() {
        val obf = ObfuscationMap(testData("jetnews/mapping.txt"))
        val hrp = strictHumanReadableProfile("baseline-prof-all-compose.txt")
        val apk = Apk(testData("jetnews/app-release.apk"))
        val prof = ArtProfile(hrp, obf, apk)
        assertSerializationIntegrity(
                prof,
                ArtProfileSerializer.METADATA_FOR_N,
                checkTypeIds = false,
                checkClassIds = true,
                checkMethodCounts = false,
        )
    }

    @Test
    fun testSerializationDeserializationForMetadata_0_0_2() {
        val obf = ObfuscationMap(testData("jetnews/mapping.txt"))
        val hrp = strictHumanReadableProfile("baseline-prof-all-compose.txt")
        val apk = Apk(testData("jetnews/app-release.apk"))
        val prof = ArtProfile(hrp, obf, apk)
        assertSerializationIntegrity(
            prof,
            ArtProfileSerializer.METADATA_0_0_2,
            checkTypeIds = false,
            checkClassIds = true,
            checkMethodCounts = false,
        )
    }

    @Test
    fun testTranscodeFromPtoS() {
        val hrp = strictHumanReadableProfile("baseline-prof-all-compose.txt")
        val apk = Apk(testData("jetcaster/app-release.apk"))
        val prof = ArtProfile(hrp, ObfuscationMap.Empty, apk)
        val golden = testData("jetcaster/baseline-multidex-s.prof").readBytes()
        // Serialize
        val outP = ByteArrayOutputStream()
        outP.use {
            prof.save(outP, ArtProfileSerializer.V0_1_0_P)
        }
        val outM2 = ByteArrayOutputStream()
        outM2.use {
            prof.save(outM2, ArtProfileSerializer.METADATA_0_0_2)
        }
        // Reconstruct
        val baselineP = ArtProfile(outP.toByteArray().inputStream())
        val metadataM2 = ArtProfile(outM2.toByteArray().inputStream())
        assertNotNull(baselineP)
        assertNotNull(metadataM2)
        val merged = baselineP.addMetadata(metadataM2, MetadataVersion.V_002)
        assertEquals(prof.typeIdCount, merged.typeIdCount)
        assertEquals(prof.methodCount, merged.methodCount)
        // Serialize to S
        val outS = ByteArrayOutputStream()
        outS.use {
            merged.save(outS, ArtProfileSerializer.V0_1_5_S)
        }
        val baselineContents = outS.toByteArray()
        val baselineS = ArtProfile(baselineContents.inputStream())
        assertNotNull(baselineS)
        // All dex files should have `typeId`s after the merge
        val emptyTypeIds = baselineS.profileData.any { (file, _) ->
            file.header.typeIds == Span.Empty
        }
        assertFalse(emptyTypeIds)
        assertTrue {
            golden.contentEquals(baselineContents)
        }
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
        assertThat(profgenDexData.typeIndexes).isEqualTo(goldenDexData.typeIndexes)
        assertThat(profgenDexData.methods).isEqualTo(goldenDexData.methods)
        assertThat(goldenDexFile.dexChecksum).isEqualTo(profgenDexFile.dexChecksum)
        assertThat(goldenDexFile.dexChecksum).isEqualTo(profgenDexFile.dexChecksum)
        assertThat(goldenDexFile.header.methodIds.size)
            .isEqualTo(profgenDexFile.header.methodIds.size)
    }

    @Test
    fun testProfileKeyConversions() {
        assertEquals("base.apk!classes2.dex", profileKey("classes2.dex", "base.apk", "!"))
        assertEquals("base.apk:classes2.dex", profileKey("classes2.dex", "base.apk", ":"))
        assertEquals("base.apk", profileKey("classes.dex", "base.apk", "!"))
        assertEquals("base.apk", profileKey("classes.dex", "base.apk", ":"))
        assertEquals("classes.dex", profileKey("classes.dex", "", "!"))
        assertEquals("classes2.dex", profileKey("classes2.dex", "", ":"))
        assertEquals("base.apk", profileKey("base.apk", "", "!"))
        assertEquals("base.apk:classes2.dex", profileKey("base.apk:classes2.dex", "", ":"))
        assertEquals("base.apk:classes2.dex", profileKey("base.apk!classes2.dex", "", ":"))
        assertEquals("base.apk!classes2.dex", profileKey("base.apk:classes2.dex", "", "!"))
        assertEquals("base.apk!classes2.dex", profileKey("base.apk!classes2.dex", "", "!"))
    }

    @Test
    fun testDumpProfiles() {
        val obf = ObfuscationMap(testData("mapping.txt"))
        val hrp = strictHumanReadableProfile("fuzzy-composer-hrp.txt")
        val apk = Apk(testData("app-release.apk"))
        val prof = ArtProfile(hrp, obf, apk)
        val output = ByteArrayOutputStream()
        output.use {
            // Save profile to P format
            prof.save(it, ArtProfileSerializer.V0_1_0_P)
        }
        val input = output.toByteArray().inputStream()
        val builder = StringBuilder()
        input.use {
            val deserialized = ArtProfile(it)!!
            dumpProfile(builder, deserialized, apk, obf)
        }
        val hrf = builder.toString()
        println("--- Output Human readable profile ---")
        println(hrf)
        println("-------------------------------------")
        assert(hrf.isNotEmpty())
    }

    @Test
    fun testDumpProfiles_allowInvalidDexFiles() {
        // b/227394536 AGP only generates valid profiles for some dex files in the APK.
        // This can be worked around by using strict = false
        val obf = ObfuscationMap(testData("now-in-android/b-227394536/mapping.txt"))
        val apkFile = testData("now-in-android/b-227394536/app-release.apk")
        val apk = Apk(apkFile)
        val input = inputStreamOf(apkFile, "assets/dexopt/baseline.prof")
        input.use {
            val prof = ArtProfile(input)!!
            val builder = StringBuilder()
            dumpProfile(builder, prof, apk, obf, strict = false)
            val hrf = builder.toString()
            println("--- Output Human readable profile ---")
            println(hrf)
            println("-------------------------------------")
            assert(hrf.isNotEmpty())
        }
    }

    private fun inputStreamOf(apkFile: File, name: String): InputStream {
        val zip = ZipFile(apkFile)
        val entry = zip.entries().asSequence().first { it.name == name }
        return zip.getInputStream(entry)
    }

    private fun assertSerializationIntegrity(
            prof: ArtProfile,
            serializer: ArtProfileSerializer,
            checkTypeIds: Boolean = true,
            checkClassIds: Boolean = false,
            checkMethodCounts: Boolean = true,
    ) {
        var os = ByteArrayOutputStream()

        // save the synthetic profile in each version
        prof.save(os, serializer)

        // get the checksums of the files written to disk from memory profile
        val checksum1 = CRC32().apply { update(os.toByteArray()) }.value

        // read each profile from disk
        var deserialized = ArtProfile(os.toByteArray().inputStream())!!

        // ensure that the class/method counts of the profile serialized/deserialized are the same
        // as when we started
        if (checkTypeIds) {
            assertEquals(prof.typeIdCount, deserialized.typeIdCount)
        }
        if (checkClassIds) {
            assertEquals(prof.classIdCount, deserialized.classIdCount)
        }
        if (checkMethodCounts) {
            assertEquals(prof.methodCount, deserialized.methodCount)
        }

        os = ByteArrayOutputStream()
        // since these profiles were deserialized from disk, serialize them back to disk and deserialize them
        // again to ensure that they match
        deserialized.save(os, serializer)

        deserialized = ArtProfile(os.toByteArray().inputStream())!!

        if (checkTypeIds) {
            assertEquals(prof.typeIdCount, deserialized.typeIdCount)
        }
        if (checkClassIds) {
            assertEquals(prof.classIdCount, deserialized.classIdCount)
        }
        if (checkMethodCounts) {
            assertEquals(prof.methodCount, deserialized.methodCount)
        }

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

        assertEquals(startProf.typeIdCount, endProf.typeIdCount)
        assertEquals(startProf.methodCount, endProf.methodCount)
    }

    private val ArtProfile.typeIdCount: Int get() = profileData.values.sumBy { it.typeIndexes.size }
    private val ArtProfile.classIdCount: Int get() = profileData.values.sumBy { it.classIndexes.size }
    private val ArtProfile.methodCount: Int get() = profileData.values.sumBy { it.methods.values.size }
}

private fun strictHumanReadableProfile(path: String) =
    HumanReadableProfile(testData(path), strictDiagnostics)!!

private val strictDiagnostics = Diagnostics { error -> fail(error) }
