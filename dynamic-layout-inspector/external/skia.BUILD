# TODO: ensure building with clang
# TODO: use info from external/skia/public.bzl?

config_setting(
    name = "windows",
    values = {"host_cpu": "x64_windows"},
    visibility = ["//visibility:public"],
)

config_setting(
    name = "mac",
    values = {"host_cpu": "darwin"},
    visibility = ["//visibility:public"],
)

cc_library(
    name = "libskia",
    # This list is taken from libskia in Android.bp in external/skia, with the following files 
    # removed since they are unneeded for our purposes and don't compile easily:
    # client_utils/android/BitmapRegionDecoder.cpp
    # client_utils/android/FrontBufferedStream.cpp
    # src/android/SkAndroidFrameworkUtils.cpp
    # src/android/SkAnimatedImage.cpp
    # src/codec/SkJpegCodec.cpp
    # src/codec/SkJpegDecoderMgr.cpp
    # src/codec/SkJpegUtility.cpp
    # src/codec/SkWebpCodec.cpp
    # src/images/SkJPEGWriteUtility.cpp
    # src/images/SkJpegEncoder.cpp
    # src/images/SkWebpEncoder.cpp
    # src/utils/SkShaperJSONWriter.cpp
    # third_party/libgifcodec/SkGifImageReader.cpp
    # third_party/libgifcodec/SkLibGifCodec.cpp
    # src/xml/SkDOM.cpp
    # src/xml/SkXMLParser.cpp
    # src/xml/SkXMLWriter.cpp
    # src/ports/SkImageGeneratorWIC.cpp
    #
    # And the following added:
    # third_party/skcms/skcms_internal.h
    # third_party/skcms/src/Transform_inl.h
    srcs = [
        "src/c/sk_effects.cpp",
        "src/c/sk_imageinfo.cpp",
        "src/c/sk_paint.cpp",
        "src/c/sk_surface.cpp",
        "src/codec/SkAndroidCodec.cpp",
        "src/codec/SkAndroidCodecAdapter.cpp",
        "src/codec/SkBmpBaseCodec.cpp",
        "src/codec/SkBmpCodec.cpp",
        "src/codec/SkBmpMaskCodec.cpp",
        "src/codec/SkBmpRLECodec.cpp",
        "src/codec/SkBmpStandardCodec.cpp",
        "src/codec/SkCodec.cpp",
        "src/codec/SkCodecImageGenerator.cpp",
        "src/codec/SkColorTable.cpp",
        "src/codec/SkEncodedInfo.cpp",
        "src/codec/SkIcoCodec.cpp",
        "src/codec/SkMaskSwizzler.cpp",
        "src/codec/SkMasks.cpp",
        "src/codec/SkParseEncodedOrigin.cpp",
        "src/codec/SkPngCodec.cpp",
        "src/codec/SkSampledCodec.cpp",
        "src/codec/SkSampler.cpp",
        "src/codec/SkStreamBuffer.cpp",
        "src/codec/SkSwizzler.cpp",
        "src/codec/SkWbmpCodec.cpp",
        "src/core/SkAAClip.cpp",
        "src/core/SkATrace.cpp",
        "src/core/SkAlphaRuns.cpp",
        "src/core/SkAnalyticEdge.cpp",
        "src/core/SkAnnotation.cpp",
        "src/core/SkArenaAlloc.cpp",
        "src/core/SkAutoPixmapStorage.cpp",
        "src/core/SkBBHFactory.cpp",
        "src/core/SkBigPicture.cpp",
        "src/core/SkBitmap.cpp",
        "src/core/SkBitmapCache.cpp",
        "src/core/SkBitmapDevice.cpp",
        "src/core/SkBitmapProcState.cpp",
        "src/core/SkBitmapProcState_matrixProcs.cpp",
        "src/core/SkBlendMode.cpp",
        "src/core/SkBlitRow_D32.cpp",
        "src/core/SkBlitter.cpp",
        "src/core/SkBlitter_A8.cpp",
        "src/core/SkBlitter_ARGB32.cpp",
        "src/core/SkBlitter_RGB565.cpp",
        "src/core/SkBlitter_Sprite.cpp",
        "src/core/SkBlurMF.cpp",
        "src/core/SkBlurMask.cpp",
        "src/core/SkBuffer.cpp",
        "src/core/SkCachedData.cpp",
        "src/core/SkCanvas.cpp",
        "src/core/SkCanvasPriv.cpp",
        "src/core/SkClipStack.cpp",
        "src/core/SkClipStackDevice.cpp",
        "src/core/SkColor.cpp",
        "src/core/SkColorFilter.cpp",
        "src/core/SkColorFilter_Matrix.cpp",
        "src/core/SkColorSpace.cpp",
        "src/core/SkColorSpaceXformSteps.cpp",
        "src/core/SkCompressedDataUtils.cpp",
        "src/core/SkContourMeasure.cpp",
        "src/core/SkConvertPixels.cpp",
        "src/core/SkCpu.cpp",
        "src/core/SkCubicClipper.cpp",
        "src/core/SkCubicMap.cpp",
        "src/core/SkData.cpp",
        "src/core/SkDataTable.cpp",
        "src/core/SkDebug.cpp",
        "src/core/SkDeferredDisplayList.cpp",
        "src/core/SkDeferredDisplayListRecorder.cpp",
        "src/core/SkDeque.cpp",
        "src/core/SkDescriptor.cpp",
        "src/core/SkDevice.cpp",
        "src/core/SkDistanceFieldGen.cpp",
        "src/core/SkDocument.cpp",
        "src/core/SkDraw.cpp",
        "src/core/SkDrawLooper.cpp",
        "src/core/SkDrawShadowInfo.cpp",
        "src/core/SkDraw_atlas.cpp",
        "src/core/SkDraw_text.cpp",
        "src/core/SkDraw_vertices.cpp",
        "src/core/SkDrawable.cpp",
        "src/core/SkEdge.cpp",
        "src/core/SkEdgeBuilder.cpp",
        "src/core/SkEdgeClipper.cpp",
        "src/core/SkExecutor.cpp",
        "src/core/SkFlattenable.cpp",
        "src/core/SkFont.cpp",
        "src/core/SkFontDescriptor.cpp",
        "src/core/SkFontMgr.cpp",
        "src/core/SkFontStream.cpp",
        "src/core/SkFont_serial.cpp",
        "src/core/SkGaussFilter.cpp",
        "src/core/SkGeometry.cpp",
        "src/core/SkGlobalInitialization_core.cpp",
        "src/core/SkGlyph.cpp",
        "src/core/SkGlyphBuffer.cpp",
        "src/core/SkGlyphRun.cpp",
        "src/core/SkGlyphRunPainter.cpp",
        "src/core/SkGpuBlurUtils.cpp",
        "src/core/SkGraphics.cpp",
        "src/core/SkHalf.cpp",
        "src/core/SkICC.cpp",
        "src/core/SkIDChangeListener.cpp",
        "src/core/SkImageFilter.cpp",
        "src/core/SkImageFilterCache.cpp",
        "src/core/SkImageFilterTypes.cpp",
        "src/core/SkImageGenerator.cpp",
        "src/core/SkImageInfo.cpp",
        "src/core/SkLatticeIter.cpp",
        "src/core/SkLineClipper.cpp",
        "src/core/SkLocalMatrixImageFilter.cpp",
        "src/core/SkM44.cpp",
        "src/core/SkMD5.cpp",
        "src/core/SkMalloc.cpp",
        "src/core/SkMallocPixelRef.cpp",
        "src/core/SkMarkerStack.cpp",
        "src/core/SkMask.cpp",
        "src/core/SkMaskBlurFilter.cpp",
        "src/core/SkMaskCache.cpp",
        "src/core/SkMaskFilter.cpp",
        "src/core/SkMaskGamma.cpp",
        "src/core/SkMath.cpp",
        "src/core/SkMatrix.cpp",
        "src/core/SkMatrix44.cpp",
        "src/core/SkMatrixImageFilter.cpp",
        "src/core/SkMiniRecorder.cpp",
        "src/core/SkMipmap.cpp",
        "src/core/SkMipmapAccessor.cpp",
        "src/core/SkModeColorFilter.cpp",
        "src/core/SkOpts.cpp",
        "src/core/SkOpts_erms.cpp",
        "src/core/SkOverdrawCanvas.cpp",
        "src/core/SkPaint.cpp",
        "src/core/SkPaintPriv.cpp",
        "src/core/SkPath.cpp",
        "src/core/SkPathBuilder.cpp",
        "src/core/SkPathEffect.cpp",
        "src/core/SkPathMeasure.cpp",
        "src/core/SkPathRef.cpp",
        "src/core/SkPath_serial.cpp",
        "src/core/SkPicture.cpp",
        "src/core/SkPictureData.cpp",
        "src/core/SkPictureFlat.cpp",
        "src/core/SkPictureImageGenerator.cpp",
        "src/core/SkPicturePlayback.cpp",
        "src/core/SkPictureRecord.cpp",
        "src/core/SkPictureRecorder.cpp",
        "src/core/SkPixelRef.cpp",
        "src/core/SkPixmap.cpp",
        "src/core/SkPoint.cpp",
        "src/core/SkPoint3.cpp",
        "src/core/SkPromiseImageTexture.cpp",
        "src/core/SkPtrRecorder.cpp",
        "src/core/SkQuadClipper.cpp",
        "src/core/SkRRect.cpp",
        "src/core/SkRTree.cpp",
        "src/core/SkRasterClip.cpp",
        "src/core/SkRasterPipeline.cpp",
        "src/core/SkRasterPipelineBlitter.cpp",
        "src/core/SkReadBuffer.cpp",
        "src/core/SkRecord.cpp",
        "src/core/SkRecordDraw.cpp",
        "src/core/SkRecordOpts.cpp",
        "src/core/SkRecordedDrawable.cpp",
        "src/core/SkRecorder.cpp",
        "src/core/SkRecords.cpp",
        "src/core/SkRect.cpp",
        "src/core/SkRegion.cpp",
        "src/core/SkRegion_path.cpp",
        "src/core/SkRemoteGlyphCache.cpp",
        "src/core/SkResourceCache.cpp",
        "src/core/SkRuntimeEffect.cpp",
        "src/core/SkScalar.cpp",
        "src/core/SkScalerCache.cpp",
        "src/core/SkScalerContext.cpp",
        "src/core/SkScan.cpp",
        "src/core/SkScan_AAAPath.cpp",
        "src/core/SkScan_AntiPath.cpp",
        "src/core/SkScan_Antihair.cpp",
        "src/core/SkScan_Hairline.cpp",
        "src/core/SkScan_Path.cpp",
        "src/core/SkSemaphore.cpp",
        "src/core/SkSharedMutex.cpp",
        "src/core/SkSpecialImage.cpp",
        "src/core/SkSpecialSurface.cpp",
        "src/core/SkSpinlock.cpp",
        "src/core/SkSpriteBlitter_ARGB32.cpp",
        "src/core/SkSpriteBlitter_RGB565.cpp",
        "src/core/SkStream.cpp",
        "src/core/SkStrikeCache.cpp",
        "src/core/SkStrikeForGPU.cpp",
        "src/core/SkStrikeSpec.cpp",
        "src/core/SkString.cpp",
        "src/core/SkStringUtils.cpp",
        "src/core/SkStroke.cpp",
        "src/core/SkStrokeRec.cpp",
        "src/core/SkStrokerPriv.cpp",
        "src/core/SkSurfaceCharacterization.cpp",
        "src/core/SkSwizzle.cpp",
        "src/core/SkTSearch.cpp",
        "src/core/SkTaskGroup.cpp",
        "src/core/SkTextBlob.cpp",
        "src/core/SkTextBlobTrace.cpp",
        "src/core/SkThreadID.cpp",
        "src/core/SkTime.cpp",
        "src/core/SkTypeface.cpp",
        "src/core/SkTypefaceCache.cpp",
        "src/core/SkTypeface_remote.cpp",
        "src/core/SkUnPreMultiply.cpp",
        "src/core/SkUtils.cpp",
        "src/core/SkVM.cpp",
        "src/core/SkVMBlitter.cpp",
        "src/core/SkVertState.cpp",
        "src/core/SkVertices.cpp",
        "src/core/SkWriteBuffer.cpp",
        "src/core/SkWriter32.cpp",
        "src/core/SkXfermode.cpp",
        "src/core/SkXfermodeInterpretation.cpp",
        "src/core/SkYUVAInfo.cpp",
        "src/core/SkYUVAPixmaps.cpp",
        "src/core/SkYUVMath.cpp",
        "src/core/SkYUVPlanesCache.cpp",
        "src/effects/Sk1DPathEffect.cpp",
        "src/effects/Sk2DPathEffect.cpp",
        "src/effects/SkColorMatrix.cpp",
        "src/effects/SkColorMatrixFilter.cpp",
        "src/effects/SkCornerPathEffect.cpp",
        "src/effects/SkDashPathEffect.cpp",
        "src/effects/SkDiscretePathEffect.cpp",
        "src/effects/SkEmbossMask.cpp",
        "src/effects/SkEmbossMaskFilter.cpp",
        "src/effects/SkHighContrastFilter.cpp",
        "src/effects/SkLayerDrawLooper.cpp",
        "src/effects/SkLumaColorFilter.cpp",
        "src/effects/SkOpPathEffect.cpp",
        "src/effects/SkOverdrawColorFilter.cpp",
        "src/effects/SkShaderMaskFilter.cpp",
        "src/effects/SkTableColorFilter.cpp",
        "src/effects/SkTableMaskFilter.cpp",
        "src/effects/SkTrimPathEffect.cpp",
        "src/effects/imagefilters/SkAlphaThresholdImageFilter.cpp",
        "src/effects/imagefilters/SkArithmeticImageFilter.cpp",
        "src/effects/imagefilters/SkBlendImageFilter.cpp",
        "src/effects/imagefilters/SkBlurImageFilter.cpp",
        "src/effects/imagefilters/SkColorFilterImageFilter.cpp",
        "src/effects/imagefilters/SkComposeImageFilter.cpp",
        "src/effects/imagefilters/SkDisplacementMapImageFilter.cpp",
        "src/effects/imagefilters/SkDropShadowImageFilter.cpp",
        "src/effects/imagefilters/SkImageImageFilter.cpp",
        "src/effects/imagefilters/SkLightingImageFilter.cpp",
        "src/effects/imagefilters/SkMagnifierImageFilter.cpp",
        "src/effects/imagefilters/SkMatrixConvolutionImageFilter.cpp",
        "src/effects/imagefilters/SkMergeImageFilter.cpp",
        "src/effects/imagefilters/SkMorphologyImageFilter.cpp",
        "src/effects/imagefilters/SkOffsetImageFilter.cpp",
        "src/effects/imagefilters/SkPictureImageFilter.cpp",
        "src/effects/imagefilters/SkShaderImageFilter.cpp",
        "src/effects/imagefilters/SkTileImageFilter.cpp",
        "src/image/SkImage.cpp",
        "src/image/SkImage_Lazy.cpp",
        "src/image/SkImage_Raster.cpp",
        "src/image/SkRescaleAndReadPixels.cpp",
        "src/image/SkSurface.cpp",
        "src/image/SkSurface_Raster.cpp",
        "src/images/SkImageEncoder.cpp",
        "src/images/SkPngEncoder.cpp",
        "src/lazy/SkDiscardableMemoryPool.cpp",
        "src/pathops/SkAddIntersections.cpp",
        "src/pathops/SkDConicLineIntersection.cpp",
        "src/pathops/SkDCubicLineIntersection.cpp",
        "src/pathops/SkDCubicToQuads.cpp",
        "src/pathops/SkDLineIntersection.cpp",
        "src/pathops/SkDQuadLineIntersection.cpp",
        "src/pathops/SkIntersections.cpp",
        "src/pathops/SkOpAngle.cpp",
        "src/pathops/SkOpBuilder.cpp",
        "src/pathops/SkOpCoincidence.cpp",
        "src/pathops/SkOpContour.cpp",
        "src/pathops/SkOpCubicHull.cpp",
        "src/pathops/SkOpEdgeBuilder.cpp",
        "src/pathops/SkOpSegment.cpp",
        "src/pathops/SkOpSpan.cpp",
        "src/pathops/SkPathOpsAsWinding.cpp",
        "src/pathops/SkPathOpsCommon.cpp",
        "src/pathops/SkPathOpsConic.cpp",
        "src/pathops/SkPathOpsCubic.cpp",
        "src/pathops/SkPathOpsCurve.cpp",
        "src/pathops/SkPathOpsDebug.cpp",
        "src/pathops/SkPathOpsLine.cpp",
        "src/pathops/SkPathOpsOp.cpp",
        "src/pathops/SkPathOpsQuad.cpp",
        "src/pathops/SkPathOpsRect.cpp",
        "src/pathops/SkPathOpsSimplify.cpp",
        "src/pathops/SkPathOpsTSect.cpp",
        "src/pathops/SkPathOpsTightBounds.cpp",
        "src/pathops/SkPathOpsTypes.cpp",
        "src/pathops/SkPathOpsWinding.cpp",
        "src/pathops/SkPathWriter.cpp",
        "src/pathops/SkReduceOrder.cpp",
        "src/pdf/SkClusterator.cpp",
        "src/pdf/SkDeflate.cpp",
        "src/pdf/SkJpegInfo.cpp",
        "src/pdf/SkKeyedImage.cpp",
        "src/pdf/SkPDFBitmap.cpp",
        "src/pdf/SkPDFDevice.cpp",
        "src/pdf/SkPDFDocument.cpp",
        "src/pdf/SkPDFFont.cpp",
        "src/pdf/SkPDFFormXObject.cpp",
        "src/pdf/SkPDFGradientShader.cpp",
        "src/pdf/SkPDFGraphicStackState.cpp",
        "src/pdf/SkPDFGraphicState.cpp",
        "src/pdf/SkPDFMakeCIDGlyphWidthsArray.cpp",
        "src/pdf/SkPDFMakeToUnicodeCmap.cpp",
        "src/pdf/SkPDFMetadata.cpp",
        "src/pdf/SkPDFResourceDict.cpp",
        "src/pdf/SkPDFShader.cpp",
        "src/pdf/SkPDFSubsetFont.cpp",
        "src/pdf/SkPDFTag.cpp",
        "src/pdf/SkPDFType1Font.cpp",
        "src/pdf/SkPDFTypes.cpp",
        "src/pdf/SkPDFUtils.cpp",
        "src/ports/SkDiscardableMemory_none.cpp",
        "src/ports/SkFontHost_FreeType.cpp",
        "src/ports/SkFontHost_FreeType_common.cpp",
        "src/ports/SkFontMgr_custom.cpp",
        "src/ports/SkFontMgr_custom_empty.cpp",
        "src/ports/SkFontMgr_custom_empty_factory.cpp",
        "src/ports/SkGlobalInitialization_default.cpp",
        "src/ports/SkImageGenerator_skia.cpp",
        "src/ports/SkMemory_malloc.cpp",
        "src/ports/SkOSFile_stdio.cpp",
        "src/sfnt/SkOTTable_name.cpp",
        "src/sfnt/SkOTUtils.cpp",
        "src/shaders/SkBitmapProcShader.cpp",
        "src/shaders/SkColorFilterShader.cpp",
        "src/shaders/SkColorShader.cpp",
        "src/shaders/SkComposeShader.cpp",
        "src/shaders/SkImageShader.cpp",
        "src/shaders/SkLocalMatrixShader.cpp",
        "src/shaders/SkPerlinNoiseShader.cpp",
        "src/shaders/SkPictureShader.cpp",
        "src/shaders/SkShader.cpp",
        "src/shaders/gradients/Sk4fGradientBase.cpp",
        "src/shaders/gradients/Sk4fLinearGradient.cpp",
        "src/shaders/gradients/SkGradientShader.cpp",
        "src/shaders/gradients/SkLinearGradient.cpp",
        "src/shaders/gradients/SkRadialGradient.cpp",
        "src/shaders/gradients/SkSweepGradient.cpp",
        "src/shaders/gradients/SkTwoPointConicalGradient.cpp",
        "src/sksl/SkSLASTNode.cpp",
        "src/sksl/SkSLAnalysis.cpp",
        "src/sksl/SkSLBuiltinTypes.cpp",
        "src/sksl/SkSLCompiler.cpp",
        "src/sksl/SkSLConstantFolder.cpp",
        "src/sksl/SkSLContext.cpp",
        "src/sksl/SkSLDehydrator.cpp",
        "src/sksl/SkSLIRGenerator.cpp",
        "src/sksl/SkSLInliner.cpp",
        "src/sksl/SkSLLexer.cpp",
        "src/sksl/SkSLMangler.cpp",
        "src/sksl/SkSLOperators.cpp",
        "src/sksl/SkSLOutputStream.cpp",
        "src/sksl/SkSLParser.cpp",
        "src/sksl/SkSLPool.cpp",
        "src/sksl/SkSLRehydrator.cpp",
        "src/sksl/SkSLSampleUsage.cpp",
        "src/sksl/SkSLSectionAndParameterHelper.cpp",
        "src/sksl/SkSLString.cpp",
        "src/sksl/SkSLUtil.cpp",
        "src/sksl/codegen/SkSLVMCodeGenerator.cpp",
        "src/sksl/dsl/DSLBlock.cpp",
        "src/sksl/dsl/DSLCase.cpp",
        "src/sksl/dsl/DSLCore.cpp",
        "src/sksl/dsl/DSLExpression.cpp",
        "src/sksl/dsl/DSLFunction.cpp",
        "src/sksl/dsl/DSLLayout.cpp",
        "src/sksl/dsl/DSLRuntimeEffects.cpp",
        "src/sksl/dsl/DSLStatement.cpp",
        "src/sksl/dsl/DSLType.cpp",
        "src/sksl/dsl/DSLVar.cpp",
        "src/sksl/dsl/priv/DSLFPs.cpp",
        "src/sksl/dsl/priv/DSLWriter.cpp",
        "src/sksl/ir/SkSLBinaryExpression.cpp",
        "src/sksl/ir/SkSLBlock.cpp",
        "src/sksl/ir/SkSLConstructor.cpp",
        "src/sksl/ir/SkSLConstructorArray.cpp",
        "src/sksl/ir/SkSLConstructorCompound.cpp",
        "src/sksl/ir/SkSLConstructorCompoundCast.cpp",
        "src/sksl/ir/SkSLConstructorDiagonalMatrix.cpp",
        "src/sksl/ir/SkSLConstructorMatrixResize.cpp",
        "src/sksl/ir/SkSLConstructorScalarCast.cpp",
        "src/sksl/ir/SkSLConstructorSplat.cpp",
        "src/sksl/ir/SkSLConstructorStruct.cpp",
        "src/sksl/ir/SkSLDoStatement.cpp",
        "src/sksl/ir/SkSLExpressionStatement.cpp",
        "src/sksl/ir/SkSLFieldAccess.cpp",
        "src/sksl/ir/SkSLForStatement.cpp",
        "src/sksl/ir/SkSLFunctionCall.cpp",
        "src/sksl/ir/SkSLFunctionDeclaration.cpp",
        "src/sksl/ir/SkSLIfStatement.cpp",
        "src/sksl/ir/SkSLIndexExpression.cpp",
        "src/sksl/ir/SkSLPostfixExpression.cpp",
        "src/sksl/ir/SkSLPrefixExpression.cpp",
        "src/sksl/ir/SkSLSetting.cpp",
        "src/sksl/ir/SkSLSwitchStatement.cpp",
        "src/sksl/ir/SkSLSwizzle.cpp",
        "src/sksl/ir/SkSLSymbolTable.cpp",
        "src/sksl/ir/SkSLTernaryExpression.cpp",
        "src/sksl/ir/SkSLType.cpp",
        "src/sksl/ir/SkSLVarDeclarations.cpp",
        "src/sksl/ir/SkSLVariable.cpp",
        "src/sksl/ir/SkSLVariableReference.cpp",
        "src/svg/SkSVGCanvas.cpp",
        "src/svg/SkSVGDevice.cpp",
        "src/utils/SkAnimCodecPlayer.cpp",
        "src/utils/SkBase64.cpp",
        "src/utils/SkCamera.cpp",
        "src/utils/SkCanvasStack.cpp",
        "src/utils/SkCanvasStateUtils.cpp",
        "src/utils/SkCharToGlyphCache.cpp",
        "src/utils/SkClipStackUtils.cpp",
        "src/utils/SkCustomTypeface.cpp",
        "src/utils/SkDashPath.cpp",
        "src/utils/SkEventTracer.cpp",
        "src/utils/SkFloatToDecimal.cpp",
        "src/utils/SkJSON.cpp",
        "src/utils/SkJSONWriter.cpp",
        "src/utils/SkMatrix22.cpp",
        "src/utils/SkMultiPictureDocument.cpp",
        "src/utils/SkNWayCanvas.cpp",
        "src/utils/SkNullCanvas.cpp",
        "src/utils/SkOSPath.cpp",
        "src/utils/SkOrderedFontMgr.cpp",
        "src/utils/SkPaintFilterCanvas.cpp",
        "src/utils/SkParse.cpp",
        "src/utils/SkParseColor.cpp",
        "src/utils/SkParsePath.cpp",
        "src/utils/SkPatchUtils.cpp",
        "src/utils/SkPolyUtils.cpp",
        "src/utils/SkShadowTessellator.cpp",
        "src/utils/SkShadowUtils.cpp",
        "src/utils/SkTextUtils.cpp",
        "src/utils/SkThreadUtils_pthread.cpp",
        "src/utils/SkThreadUtils_win.cpp",
        "src/utils/SkUTF.cpp",
        "src/utils/mac/SkCTFont.cpp",
        "src/utils/mac/SkCreateCGImageRef.cpp",
        "src/utils/win/SkAutoCoInitialize.cpp",
        "src/utils/win/SkDWrite.cpp",
        "src/utils/win/SkDWriteFontFileStream.cpp",
        "src/utils/win/SkDWriteGeometrySink.cpp",
        "src/utils/win/SkHRESULT.cpp",
        "src/utils/win/SkIStream.cpp",
        "src/utils/win/SkWGL_win.cpp",
        "third_party/skcms/skcms.cc",
        "third_party/skcms/skcms_internal.h",
        "third_party/skcms/src/Transform_inl.h",
	"src/opts/SkOpts_avx.cpp",
        "src/opts/SkOpts_hsw.cpp",
        "src/opts/SkOpts_skx.cpp",
        "src/opts/SkOpts_sse41.cpp",
        "src/opts/SkOpts_sse42.cpp",
        "src/opts/SkOpts_ssse3.cpp",
    ] + select({
        "windows": [
            "src/ports/SkDebug_win.cpp",
            "src/ports/SkImageEncoder_WIC.cpp",
            "src/ports/SkOSFile_win.cpp",
            "src/ports/SkOSLibrary_win.cpp",
        ],
        "mac": [
            "src/ports/SkDebug_stdio.cpp",
            "src/ports/SkImageEncoder_CG.cpp",
            "src/ports/SkImageGeneratorCG.cpp",
            "src/ports/SkOSFile_posix.cpp",
            "src/ports/SkOSLibrary_posix.cpp",
        ],
        "//conditions:default": [
            "src/ports/SkDebug_stdio.cpp",
            "src/ports/SkOSFile_posix.cpp",
            "src/ports/SkOSLibrary_posix.cpp",
        ],
    }) + glob(["src/**/*.h"]),
    hdrs = glob(["include/**/*.h"]),
    textual_hdrs = glob(["src/sksl/generated/*.sksl"]),
    copts = [
        "-DATRACE_TAG=ATRACE_TAG_VIEW",
        "-DSKIA_IMPLEMENTATION=1",
        "-DSK_PRINT_CODEC_MESSAGES",
        "-DFORTIFY_SOURCE=1",
        "-DSK_USER_CONFIG_HEADER=\\\"StudioConfig.h\\\"",
    ] + select({
        "windows": ["/DSK_BUILD_FOR_WIN"],  # TODO: anything else needed here?
        "mac": ["-DSK_BUILD_FOR_MAC"],  # TODO: anything else needed here?
        "//conditions:default": [
            "-DSK_BUILD_FOR_UNIX",
            "-mssse3",
            "-Wno-implicit-fallthrough",
            "-Wno-missing-field-initializers",
            "-Wno-thread-safety-analysis",
            "-Wno-unused-parameter",
            "-Wno-unused-variable",
            "-fvisibility=hidden",
            "-fexceptions",
            "-std=c++17",
        ],
    }),
    linkopts = select({"mac": [
        "-framework CoreGraphics",
        "-framework CoreText",
    ], "//conditions:default": []}),
    includes = [
        "include/atlastext/",
        "include/c/",
        "include/codec/",
        "include/core/",
        "include/docs/",
        "include/effects/",
        "include/encode/",
        "include/gpu/",
        "include/pathops/",
        "include/ports/",
        "include/private/",
        "include/svg/",
        "include/utils/",
        "include/third_party/skcms/",
        "include/third_party/vulkan/",
        "src/c/",
        "src/codec/",
        "src/core/",
        "src/effects/",
        "src/fonts/",
        "src/image/",
        "src/images/",
        "src/lazy/",
        "src/opts/",
        "src/pathops/",
        "src/pdf/",
        "src/ports/",
        "src/sfnt/",
        "src/shaders/",
        "src/shaders/gradients/",
        "src/sksl/",
        "src/utils/",
        "src/utils/win/",
        "src/xml/",
        "third_party/etc1/",
        "third_party/gif/",
    ] + select({"mac": ["include/utils/mac"], "//conditions:default": []}),
    visibility = ["//visibility:public"],
    deps = [
        "@freetype_repo//:libft2",
        "@libpng_repo//:libpng",
        "@skia_extra//:skia_includes",
    ],
)
