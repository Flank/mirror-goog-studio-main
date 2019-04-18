// this is defined non-configurably in SkFontHost_FreeType.cpp, but
// isn't available on windows. Re-set it here.
#undef SK_FREETYPE_DLOPEN
#define SK_FREETYPE_DLOPEN 0
