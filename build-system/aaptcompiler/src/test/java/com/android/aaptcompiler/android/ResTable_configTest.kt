package com.android.aaptcompiler.android

import com.google.common.truth.Truth
import org.junit.Test
import java.lang.AssertionError

class ResTable_configTest {

  @Test
  fun testBlockMethods() {
    val config = ResTable_config(
      52.hostToDevice(),
      // imsi block
      0x01020304.hostToDevice(),
      // locale block
      0x05060708.hostToDevice(),
      // screenType block
      0x090a0b0c.hostToDevice(),
      // input block
      0x0d0e0f10.hostToDevice(),
      // screenSize block
      0x11121314.hostToDevice(),
      // version block
      0x15161718.hostToDevice(),
      // screenConfig block
      0x191a1b1c.hostToDevice(),
      // screenSizeDp block
      0x1d1e1f20.hostToDevice(),
      // localeScript block
      byteArrayOf(0x21, 0x22, 0x23, 0x24),
      // localeVariant block
      byteArrayOf(0x25, 0x26, 0x27, 0x28, 0x29, 0x2a, 0x2b, 0x2c),
      // screenConfig2 block
      0x2d2e2f30.hostToDevice(),
      // localeNumberSystem block
      byteArrayOf(0x31, 0x32, 0x33, 0x34, 0x35, 0x36, 0x37, 0x38))

    Truth.assertThat(config.size).isEqualTo(52)
    // Blocks are Little Endian and therefore the lower bits come first in the block. In other words
    // if the mcc is the "first" part of the imsi block it should be equal to the lower two bytes of
    // the imsi block. As the "smaller" bytes come first in little endian format.
    // imsi block
    Truth.assertThat(config.mcc).isEqualTo(0x0304)
    Truth.assertThat(config.mnc).isEqualTo(0x0102)
    Truth.assertThat(config.getImsi()).isEqualTo(0x01020304.hostToDevice())

    // locale block
    Truth.assertThat(config.language.toList()).isEqualTo(byteArrayOf(0x07, 0x08).toList())
    Truth.assertThat(config.country.toList()).isEqualTo(byteArrayOf(0x05, 0x06).toList())
    Truth.assertThat(config.getLocale()).isEqualTo(0x05060708.hostToDevice())

    // screenType block
    Truth.assertThat(config.orientation).isEqualTo(0x0c)
    Truth.assertThat(config.touchscreen).isEqualTo(0x0b)
    Truth.assertThat(config.density).isEqualTo(0x090a)
    Truth.assertThat(config.getScreenType()).isEqualTo(0x090a0b0c.hostToDevice())

    // input block
    Truth.assertThat(config.keyboard).isEqualTo(0x10)
    Truth.assertThat(config.navigation).isEqualTo(0x0f)
    Truth.assertThat(config.inputFlags).isEqualTo(0x0e)
    // Padding is ignored, and thus removed.
    Truth.assertThat(config.getInput()).isEqualTo(0x000e0f10.hostToDevice())

    // screenSize block
    Truth.assertThat(config.screenWidth).isEqualTo(0x1314)
    Truth.assertThat(config.screenHeight).isEqualTo(0x1112)
    Truth.assertThat(config.getScreenSize()).isEqualTo(0x11121314.hostToDevice())

    // version block
    Truth.assertThat(config.sdkVersion).isEqualTo(0x1718)
    Truth.assertThat(config.minorVersion).isEqualTo(0x1516)
    Truth.assertThat(config.getVersion()).isEqualTo(0x15161718.hostToDevice())

    // screenConfig block
    Truth.assertThat(config.screenLayout).isEqualTo(0x1c)
    Truth.assertThat(config.uiMode).isEqualTo(0x1b)
    Truth.assertThat(config.smallestScreenWidthDp).isEqualTo(0x191a)
    Truth.assertThat(config.getScreenConfig()).isEqualTo(0x191a1b1c.hostToDevice())

    // screenSizeDp block
    Truth.assertThat(config.screenWidthDp).isEqualTo(0x1f20)
    Truth.assertThat(config.screenHeightDp).isEqualTo(0x1d1e)
    Truth.assertThat(config.getScreenSizeDp()).isEqualTo(0x1d1e1f20.hostToDevice())

    // localeScript block
    Truth.assertThat(config.localeScript.toList())
      .isEqualTo(byteArrayOf(0x21, 0x22, 0x23, 0x24).toList())

    // localeVariant block
    Truth.assertThat(config.localeVariant.toList())
      .isEqualTo(byteArrayOf(0x25, 0x26, 0x27, 0x28, 0x29, 0x2a, 0x2b, 0x2c).toList())

    // screenConfig2 block
    Truth.assertThat(config.screenLayout2).isEqualTo(0x30)
    Truth.assertThat(config.colorMode).isEqualTo(0x2f)
    // padding is ignored and thus removed.
    Truth.assertThat(config.getScreenConfig2()).isEqualTo(0x00002f30.hostToDevice())

    Truth.assertThat(config.localeScriptWasComputed).isFalse()

    // localeNumberSystem block
    Truth.assertThat(config.localeNumberSystem.toList())
      .isEqualTo(byteArrayOf(0x31, 0x32, 0x33, 0x34, 0x35, 0x36, 0x37, 0x38).toList())
  }

  private fun matchTestUnequal(
    default: ResTable_config, first: ResTable_config, second: ResTable_config) {
    // Default should match everything.
    Truth.assertThat(default.match(first)).isTrue()
    Truth.assertThat(default.match(second)).isTrue()
    // Nothing specific should match default.
    Truth.assertThat(first.match(default)).isFalse()
    Truth.assertThat(second.match(default)).isFalse()
    // everything should match itself.
    Truth.assertThat(first.match(first)).isTrue()
    Truth.assertThat(second.match(second)).isTrue()
    // first and second are different
    Truth.assertThat(first.match(second)).isFalse()
    Truth.assertThat(second.match(first)).isFalse()
  }

  private fun matchTestOneWay(
    default: ResTable_config, matches: ResTable_config, noMatch: ResTable_config) {
    // Default should match everything.
    Truth.assertThat(default.match(matches)).isTrue()
    Truth.assertThat(default.match(noMatch)).isTrue()
    // Nothing specific should match default.
    Truth.assertThat(matches.match(default)).isFalse()
    Truth.assertThat(noMatch.match(default)).isFalse()
    // everything should match itself.
    Truth.assertThat(matches.match(matches)).isTrue()
    Truth.assertThat(noMatch.match(noMatch)).isTrue()
    // matches should match noMatch
    // but not the other way around
    Truth.assertThat(matches.match(noMatch)).isTrue()
    Truth.assertThat(noMatch.match(matches)).isFalse()
  }

  @Test
  fun testMatches() {
    val default = ResTable_config()

    val mcc1 = ResTable_config(mcc = 310)
    val mcc2 = ResTable_config(mcc = 311)

    matchTestUnequal(default, mcc1, mcc2)

    val mnc1 = ResTable_config(mnc = 1)
    val mnc2 = ResTable_config(mnc = 2)

    matchTestUnequal(default, mnc1, mnc2)

    val lang1 = ResTable_config(language = ResTable_config.ENGLISH)
    val lang2 = ResTable_config(language = ResTable_config.FILIPINO)

    matchTestUnequal(default, lang1, lang2)

    val layoutDir1 = ResTable_config(screenLayout = ResTable_config.SCREEN_LAYOUT.DIR_LTR)
    val layoutDir2 = ResTable_config(screenLayout = ResTable_config.SCREEN_LAYOUT.DIR_RTL)

    matchTestUnequal(default, layoutDir1, layoutDir2)

    val screenSize1 = ResTable_config(screenLayout = ResTable_config.SCREEN_LAYOUT.SIZE_SMALL)
    val screenSize2 = ResTable_config(screenLayout = ResTable_config.SCREEN_LAYOUT.SIZE_LARGE)

    // Smaller screens can match larger ones.
    matchTestOneWay(default, screenSize1, screenSize2)

    val screenLong1 = ResTable_config(screenLayout = ResTable_config.SCREEN_LAYOUT.SCREENLONG_NO)
    val screenLong2 = ResTable_config(screenLayout = ResTable_config.SCREEN_LAYOUT.SCREENLONG_YES)

    matchTestUnequal(default, screenLong1, screenLong2)

    // test that having multiple specified layout values doesn't interfere with matching.
    val screenLayout = ResTable_config(
      screenLayout = (ResTable_config.SCREEN_LAYOUT.SIZE_LARGE.toInt() or
        ResTable_config.SCREEN_LAYOUT.SCREENLONG_YES.toInt()).toByte())

    Truth.assertThat(screenSize1.match(screenLayout)).isTrue()
    Truth.assertThat(screenLayout.match(screenSize1)).isFalse()
    Truth.assertThat(screenLong2.match(screenLayout)).isTrue()
    Truth.assertThat(screenLayout.match(screenLong2)).isFalse()

    val uiType1 = ResTable_config(uiMode = ResTable_config.UI_MODE.TYPE_VR_HEADSET)
    val uiType2 = ResTable_config(uiMode = ResTable_config.UI_MODE.TYPE_APPLIANCE)

    matchTestUnequal(default, uiType1, uiType2)

    val uiNight1 = ResTable_config(uiMode = ResTable_config.UI_MODE.NIGHT_NO)
    val uiNight2 = ResTable_config(uiMode = ResTable_config.UI_MODE.NIGHT_YES)

    matchTestUnequal(default, uiNight1, uiNight2)

    val uiMode = ResTable_config(
      uiMode = (ResTable_config.UI_MODE.NIGHT_YES.toInt() or
        ResTable_config.UI_MODE.TYPE_APPLIANCE.toInt()).toByte())

    // test that having multiple specified uiMode values doesn't interfere with matching.
    Truth.assertThat(uiType2.match(uiMode)).isTrue()
    Truth.assertThat(uiMode.match(uiType2)).isFalse()
    Truth.assertThat(uiNight2.match(uiMode)).isTrue()
    Truth.assertThat(uiMode.match(uiNight2)).isFalse()

    val screenRound1 =
      ResTable_config(screenLayout2 = ResTable_config.SCREEN_LAYOUT2.SCREENROUND_YES)
    val screenRound2 =
      ResTable_config(screenLayout2 = ResTable_config.SCREEN_LAYOUT2.SCREENROUND_NO)

    matchTestUnequal(default, screenRound1, screenRound2)

    val hdr1 = ResTable_config(colorMode = ResTable_config.COLOR_MODE.HDR_YES)
    val hdr2 = ResTable_config(colorMode = ResTable_config.COLOR_MODE.HDR_NO)

    matchTestUnequal(default, hdr1, hdr2)

    val wideGamut1 = ResTable_config(colorMode = ResTable_config.COLOR_MODE.WIDE_GAMUT_YES)
    val wideGamut2 = ResTable_config(colorMode = ResTable_config.COLOR_MODE.WIDE_GAMUT_NO)

    matchTestUnequal(default, wideGamut1, wideGamut2)

    val colorMode = ResTable_config(
      colorMode = (ResTable_config.COLOR_MODE.HDR_NO.toInt() or
        ResTable_config.COLOR_MODE.WIDE_GAMUT_YES.toInt()).toByte())

    // test that having multiple specified colorMode values doesn't interfere with matching.
    Truth.assertThat(hdr2.match(colorMode)).isTrue()
    Truth.assertThat(colorMode.match(hdr2)).isFalse()
    Truth.assertThat(wideGamut1.match(colorMode)).isTrue()
    Truth.assertThat(colorMode.match(wideGamut1)).isFalse()

    val screenWidthDp1 = ResTable_config(screenWidthDp = 480)
    val screenWidthDp2 = ResTable_config(screenWidthDp = 960)

    // smaller widths match larger widths
    matchTestOneWay(default, screenWidthDp1, screenWidthDp2)

    val screenHeightDp1 = ResTable_config(screenHeightDp = 640)
    val screenHeightDp2 = ResTable_config(screenHeightDp = 1280)

    // small heights match larger heights
    matchTestOneWay(default, screenHeightDp1, screenHeightDp2)

    val orientation1 = ResTable_config(orientation = ResTable_config.ORIENTATION.LAND)
    val orientation2 = ResTable_config(orientation = ResTable_config.ORIENTATION.PORT)

    matchTestUnequal(default, orientation1, orientation2)

    val touchScreen1 = ResTable_config(touchscreen = ResTable_config.TOUCHSCREEN.FINGER)
    val touchScreen2 = ResTable_config(touchscreen = ResTable_config.TOUCHSCREEN.NOTOUCH)

    matchTestUnequal(default, touchScreen1, touchScreen2)

    val navHidden1 = ResTable_config(inputFlags = ResTable_config.INPUT_FLAGS.NAVHIDDEN_YES)
    val navHidden2 = ResTable_config(inputFlags = ResTable_config.INPUT_FLAGS.NAVHIDDEN_NO)

    matchTestUnequal(default, navHidden1, navHidden2)

    val keysHidden1 = ResTable_config(inputFlags = ResTable_config.INPUT_FLAGS.KEYSHIDDEN_YES)
    val keysHidden2 = ResTable_config(inputFlags = ResTable_config.INPUT_FLAGS.KEYSHIDDEN_SOFT)

    matchTestUnequal(default, keysHidden1, keysHidden2)

    val keysHidden3 = ResTable_config(inputFlags = ResTable_config.INPUT_FLAGS.KEYSHIDDEN_NO)

    // KEYSHIDDEN NO should match KEYSHIDDEN SOFT, but not the other way around.
    Truth.assertThat(keysHidden2.match(keysHidden3)).isFalse()
    Truth.assertThat(keysHidden3.match(keysHidden2)).isTrue()

    val keyboard1 = ResTable_config(keyboard = ResTable_config.KEYBOARD.QWERTY)
    val keyboard2 = ResTable_config(keyboard = ResTable_config.KEYBOARD.TWELVEKEY)

    matchTestUnequal(default, keyboard1, keyboard2)

    val navigation1 = ResTable_config(navigation = ResTable_config.NAVIGATION.DPAD)
    val navigation2 = ResTable_config(navigation = ResTable_config.NAVIGATION.TRACKBALL)

    matchTestUnequal(default, navigation1, navigation2)

    val screenWidth1 = ResTable_config(screenWidth = 480)
    val screenWidth2 = ResTable_config(screenWidth = 960)

    // Smaller widths will match.
    matchTestOneWay(default, screenWidth1, screenWidth2)

    val screenHeight1 = ResTable_config(screenHeight = 360)
    val screenHeight2 = ResTable_config(screenHeight = 720)

    // Smaller heights will match.
    matchTestOneWay(default, screenHeight1, screenHeight2)

    val sdkVersion1 = ResTable_config(sdkVersion = 16)
    val sdkVersion2 = ResTable_config(sdkVersion = 20)

    // Lower sdk versions will match.
    matchTestOneWay(default, sdkVersion1, sdkVersion2)

    val minorVersion1 = ResTable_config(minorVersion = 1)
    val minorVersion2 = ResTable_config(minorVersion = 2)

    matchTestUnequal(default, minorVersion1, minorVersion2)
  }

  private fun testLocaleSpecificityGreater(greater: ResTable_config, lesser: ResTable_config) {
    Truth.assertThat(greater.isLocaleMoreSpecificThan(lesser)).isGreaterThan(0)
    Truth.assertThat(lesser.isLocaleMoreSpecificThan(greater)).isLessThan(0)
  }

  private fun testLocaleSpecificityEqual(first: ResTable_config, second: ResTable_config) {
    Truth.assertThat(first.isLocaleMoreSpecificThan(second)).isEqualTo(0)
    //  if they are the 'exact' same, there is no reason to retest.
    if (first !== second) {
      Truth.assertThat(second.isLocaleMoreSpecificThan(first)).isEqualTo(0)
    }
  }

  @Test
  fun testLocaleIsMoreSpecificThan() {
    val default = ResTable_config()
    val languageOnly = ResTable_config(language = byteArrayOf('e'.toByte(), 'n'.toByte()))
    val countryOnly = ResTable_config(country = byteArrayOf('U'.toByte(), 'S'.toByte()))
    val languageAndCountry = ResTable_config(
      language = byteArrayOf('e'.toByte(), 'n'.toByte()),
      country = byteArrayOf('U'.toByte(), 'S'.toByte()))
    val localeVariant = ResTable_config(
      localeVariant = byteArrayOf(0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08))
    val scriptComputed = ResTable_config(
      localeScriptWasComputed = true, localeScript = byteArrayOf(0x01, 0x02, 0x03, 0x04))
    val localeScript = ResTable_config(
      localeScript = byteArrayOf(0x01, 0x02, 0x03, 0x04))
    val numberingSystem = ResTable_config(
      localeNumberSystem = byteArrayOf(0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08))

    // configs are consistent with themselves.
    testLocaleSpecificityEqual(default, default)
    testLocaleSpecificityEqual(languageOnly, languageOnly)
    testLocaleSpecificityEqual(countryOnly, countryOnly)
    testLocaleSpecificityEqual(languageAndCountry, languageAndCountry)
    testLocaleSpecificityEqual(localeVariant, localeVariant)
    testLocaleSpecificityEqual(scriptComputed, scriptComputed)
    testLocaleSpecificityEqual(localeScript, localeScript)
    testLocaleSpecificityEqual(numberingSystem, numberingSystem)

    // Everything is more specific than default.
    testLocaleSpecificityGreater(languageOnly, default)
    testLocaleSpecificityGreater(countryOnly, default)
    testLocaleSpecificityGreater(languageAndCountry, default)
    testLocaleSpecificityGreater(localeVariant, default)
    testLocaleSpecificityGreater(localeScript, default)
    testLocaleSpecificityGreater(numberingSystem, default)
    // If script is computed and that is the only difference, it is the same as default.
    testLocaleSpecificityEqual(scriptComputed, default)

    // Language is considered before anything else.
    testLocaleSpecificityGreater(languageOnly, countryOnly)
    testLocaleSpecificityGreater(languageAndCountry, countryOnly)
    testLocaleSpecificityGreater(languageOnly, localeVariant)
    testLocaleSpecificityGreater(languageAndCountry, localeVariant)
    testLocaleSpecificityGreater(languageOnly, scriptComputed)
    testLocaleSpecificityGreater(languageAndCountry, scriptComputed)
    testLocaleSpecificityGreater(languageOnly, localeScript)
    testLocaleSpecificityGreater(languageAndCountry, localeScript)
    testLocaleSpecificityGreater(languageOnly, numberingSystem)
    testLocaleSpecificityGreater(languageAndCountry, numberingSystem)

    // Country is next most important.
    testLocaleSpecificityGreater(languageAndCountry, languageOnly)
    testLocaleSpecificityGreater(countryOnly, localeVariant)
    testLocaleSpecificityGreater(countryOnly, scriptComputed)
    testLocaleSpecificityGreater(countryOnly, localeScript)
    testLocaleSpecificityGreater(countryOnly, numberingSystem)

    // localeVariant is next most
    testLocaleSpecificityGreater(localeVariant, scriptComputed)
    testLocaleSpecificityGreater(localeVariant, localeScript)
    testLocaleSpecificityGreater(localeVariant, numberingSystem)

    // localeScript is next most
    testLocaleSpecificityGreater(localeScript, scriptComputed)
    testLocaleSpecificityGreater(localeScript, numberingSystem)

    // localeScript being computed is not considered. Therefore numbering system is next on the list
    testLocaleSpecificityGreater(numberingSystem, scriptComputed)

    // finally test that unequal but matching scripts do not have higher specificity.
    val equalConfig1 = ResTable_config(language = ResTable_config.FILIPINO)
    val equalConfig2 = ResTable_config(language = ResTable_config.TAGALOG)
    testLocaleSpecificityEqual(equalConfig1, equalConfig2)
  }

  private fun testSpecificityTransitive(vararg greatestToLeast: ResTable_config) {
    for (config in greatestToLeast) {
      // Configs are not more specific than themselves
      Truth.assertThat(config.isMoreSpecificThan(config)).isFalse()
    }

    for (i in 0.until(greatestToLeast.size)) {
      for (j in (i+1).until(greatestToLeast.size)) {
        val greater = greatestToLeast[i]
        val lesser = greatestToLeast[j]
        try {
          Truth.assertThat(greater.isMoreSpecificThan(lesser)).isTrue()
          Truth.assertThat(lesser.isMoreSpecificThan(greater)).isFalse()
        } catch (e: AssertionError) {
          throw AssertionError(
            "Failed with greater = \"$greater\" at index $i and lesser = \"$lesser\" at index $j.",
            e)
        }
      }
    }
  }

  @Test
  fun testIsMoreSpecificThan() {
    val mcc = ResTable_config(mcc = 310)
    val mnc = ResTable_config(mnc = 1)
    val locale = ResTable_config(language = ResTable_config.ENGLISH)
    val layoutDir = ResTable_config(screenLayout = ResTable_config.SCREEN_LAYOUT.DIR_LTR)
    val smallestScreenWidthDp = ResTable_config(smallestScreenWidthDp = 640)
    val screenWidthDp = ResTable_config(screenWidthDp = 640)
    val screenHeightDp = ResTable_config(screenHeightDp = 480)
    val screenRound =
      ResTable_config(screenLayout2 = ResTable_config.SCREEN_LAYOUT2.SCREENROUND_YES)
    val hdr = ResTable_config(colorMode = ResTable_config.COLOR_MODE.HDR_YES)
    val wideGamut = ResTable_config(colorMode = ResTable_config.COLOR_MODE.WIDE_GAMUT_NO)
    val orientation = ResTable_config(orientation = ResTable_config.ORIENTATION.LAND)
    val uiModeType = ResTable_config(uiMode = ResTable_config.UI_MODE.TYPE_VR_HEADSET)
    val uiModeNight = ResTable_config(uiMode = ResTable_config.UI_MODE.NIGHT_YES)
    val touchScreen = ResTable_config(touchscreen = ResTable_config.TOUCHSCREEN.FINGER)
    val keysHidden = ResTable_config(inputFlags = ResTable_config.INPUT_FLAGS.KEYSHIDDEN_YES)
    val navHidden = ResTable_config(inputFlags = ResTable_config.INPUT_FLAGS.NAVHIDDEN_YES)
    val keyboard = ResTable_config(keyboard = ResTable_config.KEYBOARD.QWERTY)
    val navigation = ResTable_config(navigation = ResTable_config.NAVIGATION.DPAD)
    val screenWidth = ResTable_config(screenWidth = 480)
    val screenHeight = ResTable_config(screenHeight = 640)
    val sdkVersion = ResTable_config(sdkVersion = 16)
    val minorVersion = ResTable_config(minorVersion = 2)
    val default = ResTable_config()

    // Test specificity by priority.
    testSpecificityTransitive(
      mcc,
      mnc,
      locale,
      layoutDir,
      smallestScreenWidthDp,
      screenWidthDp,
      screenHeightDp,
      screenRound,
      hdr,
      wideGamut,
      orientation,
      uiModeType,
      uiModeNight,
      touchScreen,
      keysHidden,
      navHidden,
      keyboard,
      navigation,
      screenWidth,
      screenHeight,
      sdkVersion,
      minorVersion,
      default)
  }

  private fun testLocaleCompareTransitive(vararg firstToLast: ResTable_config) {
    for (config in firstToLast) {
      // Things should be equal to themselves.
      Truth.assertThat(config.compareLocales(config)).isEqualTo(0)
    }

    for (i in 0.until(firstToLast.size)) {
      for (j in (i+1).until(firstToLast.size)) {
        val less = firstToLast[i]
        val more = firstToLast[j]
        try {
          Truth.assertThat(less.compareLocales(more)).isLessThan(0)
          Truth.assertThat(more.compareLocales(less)).isGreaterThan(0)
        } catch (e: AssertionError) {
          throw AssertionError(
            "Failed with less = \"$less\" at index $i and more = \"$more\" at index $j.", e)
        }
      }
    }
  }

  @Test
  fun testLocaleCompare() {
    val default = ResTable_config()
    val defaultWithVariant = ResTable_config(localeVariant = byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8))
    val localeNoCountry1 = ResTable_config(language = ResTable_config.ENGLISH)
    val localeNoCountryWithScript1 = ResTable_config(
      language = ResTable_config.ENGLISH, localeScript = byteArrayOf(1, 2, 3, 4))
    val localeNoCountryWithScript2 = ResTable_config(
      language = ResTable_config.ENGLISH, localeScript = byteArrayOf(1, 2, 3, 5))
    val localeNoCountry2 = ResTable_config(language = byteArrayOf('f'.toByte(), 'r'.toByte()))
    val localeNoCountryWithScript3 = ResTable_config(
      language = byteArrayOf('f'.toByte(), 'r'.toByte()), localeScript = byteArrayOf(1, 2, 3, 4))
    val localeNoLang = ResTable_config(country = ResTable_config.UNITED_STATES)
    val localeBasic = ResTable_config(
      language = ResTable_config.ENGLISH, country = ResTable_config.UNITED_STATES)
    val localeBasicWithScript = ResTable_config(
      language = ResTable_config.ENGLISH,
      country = ResTable_config.UNITED_STATES,
      localeScript = byteArrayOf(1, 2, 3, 4))
    val localeBasicWithScriptAndVariant = ResTable_config(
      language = ResTable_config.ENGLISH,
      country = ResTable_config.UNITED_STATES,
      localeScript = byteArrayOf(1, 2, 3, 4),
      localeVariant = byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8))
    val localeBasicWithScriptAndVariant2 = ResTable_config(
      language = ResTable_config.ENGLISH,
      country = ResTable_config.UNITED_STATES,
      localeScript = byteArrayOf(1, 2, 3, 4),
      localeVariant = byteArrayOf(11, 12, 13, 14, 15, 16, 17, 18))
    val localeBasicWithScriptVariantAndNumberSystem = ResTable_config(
      language = ResTable_config.ENGLISH,
      country = ResTable_config.UNITED_STATES,
      localeScript = byteArrayOf(1, 2, 3, 4),
      localeVariant = byteArrayOf(11, 12, 13, 14, 15, 16, 17, 18),
      localeNumberSystem = byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8))
    val localeBasicWithScriptVariantAndNumberSystem2 = ResTable_config(
      language = ResTable_config.ENGLISH,
      country = ResTable_config.UNITED_STATES,
      localeScript = byteArrayOf(1, 2, 3, 4),
      localeVariant = byteArrayOf(11, 12, 13, 14, 15, 16, 17, 18),
      localeNumberSystem = byteArrayOf(11, 12, 13, 14, 15, 16, 17, 18))

    testLocaleCompareTransitive(
      default,
      defaultWithVariant,
      localeNoCountry1,
      localeNoCountryWithScript1,
      localeNoCountryWithScript2,
      localeNoCountry2,
      localeNoCountryWithScript3,
      localeNoLang,
      localeBasic,
      localeBasicWithScript,
      localeBasicWithScriptAndVariant,
      localeBasicWithScriptAndVariant2,
      localeBasicWithScriptVariantAndNumberSystem,
      localeBasicWithScriptVariantAndNumberSystem2)

    // Ensure computed scripts do not affect results.
    val localeBasicWithComputedScript = ResTable_config(
      language = ResTable_config.ENGLISH,
      country = ResTable_config.UNITED_STATES,
      localeScriptWasComputed = true,
      localeScript = byteArrayOf(1, 2, 3, 4))
    Truth.assertThat(localeBasic.compareLocales(localeBasicWithComputedScript)).isEqualTo(0)
  }

  private fun testCompareTransitive(vararg firstToLast: ResTable_config) {
    for (config in firstToLast) {
      // Things should be equal to themselves.
      Truth.assertThat(config.compareTo(config)).isEqualTo(0)
    }

    for (i in 0.until(firstToLast.size)) {
      for (j in (i+1).until(firstToLast.size)) {
        val less = firstToLast[i]
        val more = firstToLast[j]
        try {
          Truth.assertThat(less.compareTo(more)).isLessThan(0)
          Truth.assertThat(more.compareTo(less)).isGreaterThan(0)
        } catch (e: AssertionError) {
          throw AssertionError(
            "Failed with less = \"$less\" at index $i and more = \"$more\" at index $j.", e)
        }
      }
    }
  }

  @Test
  fun testCompare() {
    val default = ResTable_config()
    val screenSizeDp = ResTable_config(screenWidthDp = 240, screenHeightDp = 320)
    val screenSizeDp2 = ResTable_config(screenWidthDp = 480, screenHeightDp = 640)
    val smallestScreenWidthDp = ResTable_config(smallestScreenWidthDp = 480)
    val smallestScreenWidthDp2 = ResTable_config(smallestScreenWidthDp = 960)
    val uiMode = ResTable_config(uiMode = ResTable_config.UI_MODE.NIGHT_YES)
    val colorMode = ResTable_config(colorMode = ResTable_config.COLOR_MODE.WIDE_GAMUT_NO)
    val screenLayout2 = ResTable_config(
      screenLayout2 = ResTable_config.SCREEN_LAYOUT2.SCREENROUND_YES)
    val screenLayout = ResTable_config(screenLayout = ResTable_config.SCREEN_LAYOUT.SIZE_LARGE)
    val version = ResTable_config(sdkVersion = 22)
    val screenSize = ResTable_config(screenWidth = 480, screenHeight = 320)
    val input = ResTable_config(inputFlags = ResTable_config.INPUT_FLAGS.KEYSHIDDEN_YES)
    val screenType = ResTable_config(orientation = ResTable_config.ORIENTATION.PORT)
    // Ensure density matters for ordering.
    val screenType2 = ResTable_config(density = 240)
    val locale = ResTable_config(language = ResTable_config.ENGLISH)
    val imsi = ResTable_config(mcc = 310)

    testCompareTransitive(
      default,
      screenSizeDp,
      screenSizeDp2,
      smallestScreenWidthDp,
      smallestScreenWidthDp2,
      uiMode,
      colorMode,
      screenLayout2,
      screenLayout,
      version,
      screenSize,
      input,
      screenType,
      screenType2,
      locale,
      imsi)
  }

  @Test
  fun testDiff() {
    val default = ResTable_config()
    val first = ResTable_config(mcc = 310, mnc = 10, screenWidth = 480, screenHeight = 640)
    val second = ResTable_config(mcc = 310, language = ResTable_config.ENGLISH, screenHeight = 480)
    val third = ResTable_config(
      sdkVersion = 23,
      screenWidth = 480,
      screenHeight = 640,
      screenLayout = ResTable_config.SCREEN_LAYOUT.SIZE_LARGE)

    Truth.assertThat(default.diff(default)).isEqualTo(0)
    Truth.assertThat(first.diff(first)).isEqualTo(0)
    Truth.assertThat(second.diff(second)).isEqualTo(0)
    Truth.assertThat(third.diff(third)).isEqualTo(0)

    val firstDefault =
      ResTable_config.CONFIG_MCC or ResTable_config.CONFIG_MNC or ResTable_config.CONFIG_SCREEN_SIZE
    Truth.assertThat(first.diff(default)).isEqualTo(firstDefault)
    Truth.assertThat(default.diff(first)).isEqualTo(firstDefault)

    val secondDefault =
      ResTable_config.CONFIG_MCC or
        ResTable_config.CONFIG_LOCALE or
        ResTable_config.CONFIG_SCREEN_SIZE
    Truth.assertThat(second.diff(default)).isEqualTo(secondDefault)
    Truth.assertThat(default.diff(second)).isEqualTo(secondDefault)

    val thirdDefault =
      ResTable_config.CONFIG_VERSION or
        ResTable_config.CONFIG_SCREEN_SIZE or
        ResTable_config.CONFIG_SCREEN_LAYOUT
    Truth.assertThat(third.diff(default)).isEqualTo(thirdDefault)
    Truth.assertThat(default.diff(third)).isEqualTo(thirdDefault)

    val firstSecond =
      ResTable_config.CONFIG_MNC or
        ResTable_config.CONFIG_LOCALE or
        ResTable_config.CONFIG_SCREEN_SIZE
    Truth.assertThat(first.diff(second)).isEqualTo(firstSecond)
    Truth.assertThat(second.diff(first)).isEqualTo(firstSecond)

    val firstThird =
      ResTable_config.CONFIG_MCC or
        ResTable_config.CONFIG_MNC or
        ResTable_config.CONFIG_VERSION or
        ResTable_config.CONFIG_SCREEN_LAYOUT
    Truth.assertThat(first.diff(third)).isEqualTo(firstThird)
    Truth.assertThat(third.diff(first)).isEqualTo(firstThird)

    val secondThird =
      ResTable_config.CONFIG_MCC or
        ResTable_config.CONFIG_LOCALE or
        ResTable_config.CONFIG_SCREEN_SIZE or
        ResTable_config.CONFIG_VERSION or
        ResTable_config.CONFIG_SCREEN_LAYOUT
    Truth.assertThat(second.diff(third)).isEqualTo(secondThird)
    Truth.assertThat(third.diff(second)).isEqualTo(secondThird)

    // layout dir is separate from ResTable_config.CONFIG_SCREEN_LAYOUT
    val layoutDir = ResTable_config(screenLayout = ResTable_config.SCREEN_LAYOUT.DIR_LTR)
    val diff = ResTable_config.CONFIG_LAYOUTDIR
    Truth.assertThat(default.diff(layoutDir)).isEqualTo(diff)
    Truth.assertThat(layoutDir.diff(default)).isEqualTo(diff)
  }

  private fun isBetterLocaleTransitive(
    requested: ResTable_config, vararg leastToGreatest: ResTable_config) {

    for (config in leastToGreatest) {
      // no config is better than itself.
      Truth.assertThat(config.isLocaleBetterThan(config, requested)).isFalse()
    }

    for (i in 0.until(leastToGreatest.size)) {
      for (j in (i+1).until(leastToGreatest.size)) {
        val worse = leastToGreatest[i]
        val better = leastToGreatest[j]
        try {
          Truth.assertThat(worse.isLocaleBetterThan(better, requested)).isFalse()
          Truth.assertThat(better.isLocaleBetterThan(worse, requested)).isTrue()
        } catch (e: AssertionError) {
          throw AssertionError(
            "Failed with worse = \"$worse\" at index $i and better = \"$better\" at index $j.", e)
        }
      }
    }
  }

  @Test
  fun testIsBetterLocale() {
    val requested = ResTable_config(
      language = ResTable_config.FILIPINO,
      country = byteArrayOf(1, 2),
      localeVariant = byteArrayOf(3, 4, 5, 6, 7, 8, 9, 10),
      localeNumberSystem = byteArrayOf(11, 12, 13, 14, 15, 16, 17, 18))

    val default = ResTable_config()
    val country = ResTable_config(country = byteArrayOf(1, 2))
    val language1 = ResTable_config(language = ResTable_config.TAGALOG)
    val language2 = ResTable_config(language = ResTable_config.FILIPINO)
    val languageAndCountry =
      ResTable_config(language = ResTable_config.FILIPINO, country = byteArrayOf(1, 2))
    val localeNumberSystem = ResTable_config(
      language = ResTable_config.FILIPINO,
      country = byteArrayOf(1, 2),
      localeNumberSystem = byteArrayOf(11, 12, 13, 14, 15, 16, 17, 18))
    val localeVariant = ResTable_config(
      language = ResTable_config.FILIPINO,
      country = byteArrayOf(1, 2),
      localeVariant = byteArrayOf(3, 4, 5, 6, 7, 8, 9, 10))
    val almostTotalMatch = ResTable_config(
      language = ResTable_config.TAGALOG,
      country = byteArrayOf(1, 2),
      localeVariant = byteArrayOf(3, 4, 5, 6, 7, 8, 9, 10),
      localeNumberSystem = byteArrayOf(11, 12, 13, 14, 15, 16, 17, 18))

    isBetterLocaleTransitive(
      requested,
      default,
      country,
      language1,
      language2,
      languageAndCountry,
      localeNumberSystem,
      localeVariant,
      almostTotalMatch,
      requested)

    // Test that if the locale variant doesn't match it doesn't count.
    val localeVariantFail = ResTable_config(
      language = ResTable_config.FILIPINO,
      country = byteArrayOf(1, 2),
      localeVariant = byteArrayOf(3, 4, 5, 6, 7, 8, 9, 11))

    Truth.assertThat(localeVariantFail.isLocaleBetterThan(localeNumberSystem, requested)).isFalse()
    Truth.assertThat(localeNumberSystem.isLocaleBetterThan(localeVariantFail, requested)).isTrue()
    Truth.assertThat(localeVariantFail.isLocaleBetterThan(languageAndCountry, requested)).isFalse()
  }

  private fun isBetterTransitive(
    requested: ResTable_config, vararg leastToGreatest: ResTable_config) {

    for (config in leastToGreatest) {
      // no config is better than itself.
      Truth.assertThat(config.isBetterThan(config, requested)).isFalse()
    }

    for (i in 0.until(leastToGreatest.size)) {
      for (j in (i+1).until(leastToGreatest.size)) {
        val worse = leastToGreatest[i]
        val better = leastToGreatest[j]
        try {
          Truth.assertThat(worse.isBetterThan(better, requested)).isFalse()
          Truth.assertThat(better.isBetterThan(worse, requested)).isTrue()
        } catch (e: AssertionError) {
          throw AssertionError(
            "Failed with worse = \"$worse\" at index $i and better = \"$better\" at index $j.", e)
        }
      }
    }
  }

  @Test
  fun testIsBetterThan() {
    val requested = ResTable_config(
      52.hostToDevice(),
      // imsi block
      0x41424344.hostToDevice(),
      // locale block
      0x01020304.hostToDevice(),
      // screenType block
      0x494a4b4c.hostToDevice(),
      // input block
      0x4d4e4f10.hostToDevice(),
      // screenSize block
      0x11121314.hostToDevice(),
      // version block
      0x15161718.hostToDevice(),
      // screenConfig block
      0x191affff.hostToDevice(),
      // screenSizeDp block
      0x1d1e1f20.hostToDevice(),
      // localeScript block
      byteArrayOf(0x21, 0x22, 0x23, 0x24),
      // localeVariant block
      byteArrayOf(0x25, 0x26, 0x27, 0x28, 0x29, 0x2a, 0x2b, 0x2c),
      // screenConfig2 block
      0xffffffff.toInt().hostToDevice(),
      // localeNumberSystem block
      byteArrayOf(0x31, 0x32, 0x33, 0x34, 0x35, 0x36, 0x37, 0x38))

    val default = ResTable_config()
    val minorVersion = ResTable_config(minorVersion = requested.minorVersion)
    val sdkVersion1 = ResTable_config(sdkVersion = 0x0020)
    // higher sdkVersion will match better.
    val sdkVersion2 = ResTable_config(sdkVersion = requested.sdkVersion)
    val screenSize1 = ResTable_config(screenWidth = requested.screenWidth)
    val screenSize2 = ResTable_config(
      screenWidth = requested.screenWidth, screenHeight = requested.screenHeight)
    val navigation = ResTable_config(navigation = requested.navigation)
    val keyboard = ResTable_config(keyboard = requested.keyboard)
    val navHidden = ResTable_config(
      inputFlags = (requested.inputFlags.toInt() and
        ResTable_config.INPUT_FLAGS.NAVHIDDEN_MASK).toByte())
    val keysHiddenAlmostMatch = ResTable_config(
      inputFlags = ResTable_config.INPUT_FLAGS.KEYSHIDDEN_NO)
    val keysHiddenMatch = ResTable_config(
      inputFlags = (requested.inputFlags.toInt() and
        ResTable_config.INPUT_FLAGS.KEYSHIDDEN_MASK).toByte())
    val touchscreen = ResTable_config(touchscreen = requested.touchscreen)
    // scaling up is worse than scaling down.
    val densityLow = ResTable_config(density = requested.density - 20)
    val densityHigh = ResTable_config(density = requested.density + 20)
    val densityEqual = ResTable_config(density = requested.density)
    val nightMode = ResTable_config(
      uiMode = (requested.uiMode.toInt() and
        ResTable_config.UI_MODE.NIGHT_MASK).toByte())
    val uiType = ResTable_config(
      uiMode = (requested.uiMode.toInt() and
        ResTable_config.UI_MODE.TYPE_MASK).toByte())
    val orientation = ResTable_config(orientation = requested.orientation)
    val hdr = ResTable_config(
      colorMode = (requested.colorMode.toInt() and
        ResTable_config.COLOR_MODE.HDR_MASK).toByte())
    val gamut = ResTable_config(
      colorMode = (requested.colorMode.toInt() and
        ResTable_config.COLOR_MODE.WIDE_GAMUT_MASK).toByte())
    val round = ResTable_config(
      screenLayout2 = (requested.screenLayout2.toInt() and
        ResTable_config.SCREEN_LAYOUT2.SCREENROUND_MASK).toByte())
    val long = ResTable_config(
      screenLayout = (requested.screenLayout.toInt() and
        ResTable_config.SCREEN_LAYOUT.SCREENLONG_MASK).toByte())
    val sizeFlag = ResTable_config(
      screenLayout = (requested.screenLayout.toInt() and
        ResTable_config.SCREEN_LAYOUT.SIZE_MASK).toByte())
    val screenSizeDp1 = ResTable_config(screenWidthDp = requested.screenWidthDp)
    val screenSizeDp2 = ResTable_config(
      screenWidthDp = requested.screenWidthDp, screenHeightDp = requested.screenHeightDp)
    val smallestScreenWidth1 = ResTable_config(
      smallestScreenWidthDp = requested.smallestScreenWidthDp - 20)
    // Closer to requested will match better.
    val smallestScreenWidth2 = ResTable_config(
      smallestScreenWidthDp = requested.smallestScreenWidthDp)
    val screenDir = ResTable_config(
      screenLayout = (requested.screenLayout.toInt() and
        ResTable_config.SCREEN_LAYOUT.DIR_MASK).toByte())
    val mnc = ResTable_config(mnc = requested.mnc)
    val mcc = ResTable_config(mcc = requested.mcc)

    isBetterTransitive(
      requested,
      default,
      minorVersion,
      sdkVersion1,
      sdkVersion2,
      screenSize1,
      screenSize2,
      navigation,
      keyboard,
      navHidden,
      keysHiddenAlmostMatch,
      keysHiddenMatch,
      touchscreen,
      densityLow,
      densityHigh,
      densityEqual,
      nightMode,
      uiType,
      orientation,
      hdr,
      gamut,
      round,
      long,
      sizeFlag,
      screenSizeDp1,
      screenSizeDp2,
      smallestScreenWidth1,
      smallestScreenWidth2,
      screenDir,
      mnc,
      mcc,
      requested)
  }

  @Test
  fun testToString() {
    val default = ResTable_config()
    Truth.assertThat(default.toString()).isEqualTo("DEFAULT")

    val config1 = ResTable_config(mcc = 120)
    Truth.assertThat(config1.toString()).isEqualTo("mcc120")

    val config2 = ResTable_config(mnc = 120)
    Truth.assertThat(config2.toString()).isEqualTo("mnc120")

    val config3 = ResTable_config(language = ResTable_config.ENGLISH)
    Truth.assertThat(config3.toString()).isEqualTo("en")

    val config4 =
      ResTable_config(language = ResTable_config.ENGLISH, country =  ResTable_config.UNITED_STATES)
    Truth.assertThat(config4.toString()).isEqualTo("en-rUS")

    val config5 =
      ResTable_config(
        language = ResTable_config.ENGLISH,
        localeScript = byteArrayOf('l'.toByte(), 'a'.toByte(), 't'.toByte(), 'n'.toByte()))
    Truth.assertThat(config5.toString()).isEqualTo("b+en+latn")

    val config6 =
      ResTable_config(
        language = ResTable_config.ENGLISH,
        country = ResTable_config.UNITED_STATES,
        localeScript = byteArrayOf('l'.toByte(), 'a'.toByte(), 't'.toByte(), 'n'.toByte()),
        localeVariant = byteArrayOf('h'.toByte(), 'i'.toByte(), 0, 0, 0, 0, 0, 0),
        localeNumberSystem = byteArrayOf(
          't'.toByte(), 'h'.toByte(), 'e'.toByte(), 'r'.toByte(), 'e'.toByte(), 0, 0, 0))
    Truth.assertThat(config6.toString()).isEqualTo("b+en+latn+US+hi+u+nu+there")

    val config7 = ResTable_config(screenWidth = 480, screenHeight = 640)
    Truth.assertThat(config7.toString()).isEqualTo("480x640")
    val config8 = ResTable_config(screenWidthDp = 480, screenHeightDp = 640)
    Truth.assertThat(config8.toString()).isEqualTo("w480dp-h640dp")

    val config9 =
      ResTable_config(
        52,
        310,
        100,
        ResTable_config.ENGLISH,
        ResTable_config.UNITED_STATES,
        ResTable_config.ORIENTATION.LAND,
        ResTable_config.TOUCHSCREEN.FINGER,
        ResTable_config.DENSITY.XXHIGH,
        ResTable_config.KEYBOARD.QWERTY,
        ResTable_config.NAVIGATION.DPAD,
        (ResTable_config.INPUT_FLAGS.NAVHIDDEN_YES.toInt() or
          ResTable_config.INPUT_FLAGS.KEYSHIDDEN_SOFT.toInt()).toByte(),
        0,
        0,
        13,
        2,
        (ResTable_config.SCREEN_LAYOUT.DIR_LTR.toInt() or
          ResTable_config.SCREEN_LAYOUT.SIZE_LARGE.toInt() or
          ResTable_config.SCREEN_LAYOUT.SCREENLONG_YES.toInt()).toByte(),
        (ResTable_config.UI_MODE.NIGHT_NO.toInt() or
          ResTable_config.UI_MODE.TYPE_DESK.toInt()).toByte(),
        480,
        480,
        640,
        byteArrayOf('l'.toByte(), 'a'.toByte(), 't'.toByte(), 'n'.toByte()),
        byteArrayOf('h'.toByte(), 'i'.toByte(), 0, 0, 0, 0, 0, 0),
        ResTable_config.SCREEN_LAYOUT2.SCREENROUND_YES,
        (ResTable_config.COLOR_MODE.HDR_YES.toInt() or
          ResTable_config.COLOR_MODE.WIDE_GAMUT_YES.toInt()).toByte(),
        false,
        byteArrayOf('t'.toByte(), 'h'.toByte(), 'e'.toByte(), 'r'.toByte(), 'e'.toByte(), 0, 0, 0))
    Truth.assertThat(config9.toString()).isEqualTo(
      "mcc310-mnc100-b+en+latn+US+hi+u+nu+there-ldltr-sw480dp-w480dp-h640dp-large-long-" +
        "round-widecg-highdr-land-desk-notnight-xxhdpi-finger-keyssoft-qwerty-navhidden-dpad-v13.2")
  }
}