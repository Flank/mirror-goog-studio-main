/*
 * Copyright (C) 2019 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
#include <google/protobuf/text_format.h>
#include <skia.pb.h>
#include <deque>
#include <iostream>
#include "SkCanvas.h"
#include "SkPicture.h"
#include "SkStream.h"

#ifndef SKIA_TREEBUILDINGCANVAS_H
#define SKIA_TREEBUILDINGCANVAS_H

struct View {
  std::unique_ptr<SkCanvas> canvas;
  ::layoutinspector::proto::InspectorView *node;
  bool didDraw = false;

  SkScalar offsetX = 0;
  SkScalar offsetY = 0;
  SkScalar width = 0;
  SkScalar height = 0;
  int *data = nullptr;
  const char *label = nullptr;
  bool didConcat = false;
};

class TreeBuildingCanvas : public SkCanvas {
 public:
  static void ParsePicture(const char *skp, size_t len,
                           ::layoutinspector::proto::InspectorView *root) {
    std::unique_ptr<SkStreamAsset> stream =
        SkMemoryStream::MakeDirect(skp, len);
    sk_sp<SkPicture> picture(SkPicture::MakeFromStream(stream.get()));
    if (picture == nullptr) {
      return;
    }
    picture->ref();
    TreeBuildingCanvas canvas(root);
    picture->playback(&canvas);

    picture->unref();
  }

 protected:
  explicit TreeBuildingCanvas(::layoutinspector::proto::InspectorView *r)
      : SkCanvas() {
    root = r;
  }

  void didConcat(const SkMatrix &matrix) override;

  void fixTranslation(const SkMatrix &matrix) const;

  void didSetMatrix(const SkMatrix &matrix) override;

  void willSave() override;

  void willRestore() override;

  void nonHeaderCommand();

  bool onPeekPixels(SkPixmap *pixmap) override;

  SkImageInfo onImageInfo() const override;

  bool onGetProps(SkSurfaceProps *props) const override;

  void onFlush() override;

  void onDrawShadowRec(const SkPath &path, const SkDrawShadowRec &rec) override;

  void onDrawVerticesObject(const SkVertices *vertices,
                            const SkVertices::Bone *bones, int boneCount,
                            SkBlendMode mode, const SkPaint &paint) override;

  void onDrawImageRect(const SkImage *image, const SkRect *src,
                       const SkRect &dst, const SkPaint *paint,
                       SrcRectConstraint constraint) override;

  void onDrawBitmapRect(const SkBitmap &bitmap, const SkRect *src,
                        const SkRect &dst, const SkPaint *paint,
                        SrcRectConstraint constraint) override;

  void onDrawPaint(const SkPaint &paint) override;

  void onDrawPoints(PointMode mode, size_t count, const SkPoint *pts,
                    const SkPaint &paint) override;

  void onDrawRect(const SkRect &rect, const SkPaint &paint) override;

  void onDrawRegion(const SkRegion &region, const SkPaint &paint) override;

  void onDrawOval(const SkRect &oval, const SkPaint &paint) override;

  void onDrawRRect(const SkRRect &rrect, const SkPaint &paint) override;

  void onDrawDRRect(const SkRRect &outer, const SkRRect &inner,
                    const SkPaint &paint) override;

  void onDrawArc(const SkRect &oval, SkScalar startAngle, SkScalar sweepAngle,
                 bool useCenter, const SkPaint &paint) override;

  void onDrawPath(const SkPath &path, const SkPaint &paint) override;

  void onDrawImage(const SkImage *image, SkScalar left, SkScalar top,
                   const SkPaint *paint) override;

  void onDrawTextBlob(const SkTextBlob *blob, SkScalar x, SkScalar y,
                      const SkPaint &paint) override;

  void onDrawPatch(const SkPoint *cubics, const SkColor *colors,
                   const SkPoint *texCoords, SkBlendMode mode,
                   const SkPaint &paint) override;

  void onDrawImageNine(const SkImage *image, const SkIRect &center,
                       const SkRect &dst, const SkPaint *paint) override;

  void onDrawImageLattice(const SkImage *image, const Lattice &lattice,
                          const SkRect &dst, const SkPaint *paint) override;

  void onDrawBitmap(const SkBitmap &bitmap, SkScalar dx, SkScalar dy,
                    const SkPaint *paint) override;

  void onDrawBitmapNine(const SkBitmap &bitmap, const SkIRect &center,
                        const SkRect &dst, const SkPaint *paint) override;

  void onDrawBitmapLattice(const SkBitmap &bitmap, const Lattice &lattice,
                           const SkRect &dst, const SkPaint *paint) override;

  void onDrawAtlas(const SkImage *atlas, const SkRSXform *xform,
                   const SkRect *rect, const SkColor *colors, int count,
                   SkBlendMode mode, const SkRect *cull,
                   const SkPaint *paint) override;

  void onDrawDrawable(SkDrawable *drawable, const SkMatrix *matrix) override;

  void onDrawPicture(const SkPicture *picture, const SkMatrix *matrix,
                     const SkPaint *paint) override;

  void onDrawAnnotation(const SkRect &rect, const char *key,
                        SkData *value) override;

  void onClipRect(const SkRect &rect, SkClipOp op,
                  ClipEdgeStyle edgeStyle) override;

 private:
  void exitView(const SkRect &rect, bool hasData);

  void addView(const SkRect &rect);

  void createRealCanvas();

  std::deque<View *> views;
  View *topView = nullptr;

  // b/121323050
  bool inHeader = true;
  ::layoutinspector::proto::InspectorView *root;

  ::layoutinspector::proto::InspectorView *createNode(
      std::string &id, std::string &type, int offsetX, int offsetY, int width,
      int height, int *data,
      std::reverse_iterator<std::deque<View *>::iterator> parent);
};

#endif  // SKIA_TREEBUILDINGCANVAS_H
