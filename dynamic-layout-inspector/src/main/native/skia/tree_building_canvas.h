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
#include <SkNoDrawCanvas.h>
#include <google/protobuf/text_format.h>
#include <skia.pb.h>

#include <deque>
#include <iostream>
#include <memory>

#include "SkCanvas.h"
#include "SkPicture.h"
#include "SkStream.h"

#ifndef SKIA_TREEBUILDINGCANVAS_H
#define SKIA_TREEBUILDINGCANVAS_H

struct View {
  // The canvas into which to draw. Before any actual draw commands have been
  // done is a SkNoDrawCanvas, to track any transformations that are applied.
  std::unique_ptr<SkCanvas> canvas;

  // The node corresponding to this view that will eventually be returned to
  // studio via grpc. Reference is kept here so we can set up the parent/child
  // relationship between nodes.
  ::layoutinspector::proto::InspectorView* node = nullptr;

  // Whether we've drawn into this view yet.
  bool didDraw = false;

  SkScalar offsetX = 0;
  SkScalar offsetY = 0;
  SkScalar width = 0;
  SkScalar height = 0;

  // The bitmap data. Proto can only take ownership of strings, not arrays, so
  // this is how we store it.
  std::unique_ptr<std::string> image;

  // The class of this view
  const char* label = nullptr;

  // Whether we've performed any transforms in this view yet. The first
  // concatenation (if it comes before any draw commands) actually applies to
  // the previous view, not this one, so this tracks whether that first concat
  // has happened already
  bool didConcat = false;

  View(SkScalar width, SkScalar height, SkScalar offsetX, SkScalar offsetY)
      : offsetX(offsetX), offsetY(offsetY), width(width), height(height) {
    canvas.reset(new SkNoDrawCanvas(width, height));
  }
};

class TreeBuildingCanvas : public SkCanvas {
 public:
  static void ParsePicture(
      const char* skp, size_t len, int version,
      const ::google::protobuf::RepeatedField< ::google::protobuf::int64>*
          known_ids,
      ::layoutinspector::proto::InspectorView* root) {
#ifdef TREEBUILDINGCANVAS_DEBUG
    std::cerr << "###start" << std::endl;
#endif
    std::unique_ptr<SkStreamAsset> stream =
        SkMemoryStream::MakeDirect(skp, len);
    sk_sp<SkPicture> picture(SkPicture::MakeFromStream(stream.get()));
    if (picture == nullptr) {
      return;
    }
    picture->ref();
    TreeBuildingCanvas canvas(version, root, known_ids);
    picture->playback(&canvas);

    picture->unref();
#ifdef TREEBUILDINGCANVAS_DEBUG
    std::cerr << "###end" << std::endl;
#endif
  }

  ~TreeBuildingCanvas() override;

 protected:
  explicit TreeBuildingCanvas(
      int version, ::layoutinspector::proto::InspectorView* r,
      const ::google::protobuf::RepeatedField< ::google::protobuf::int64>* ids)
      : SkCanvas() {
    request_version = version;
    root = r;
    known_ids = ids;
  }

  void didConcat(const SkMatrix& matrix) override;

  void fixTranslation(const SkMatrix& matrix, View& view) const;

  void didSetMatrix(const SkMatrix& matrix) override;

  void didTranslate(SkScalar dx, SkScalar dy) override;

  void willSave() override;

  void willRestore() override;

  void nonHeaderCommand();

  bool onPeekPixels(SkPixmap* pixmap) override;

  SkImageInfo onImageInfo() const override;

  bool onGetProps(SkSurfaceProps* props) const override;

  void onFlush() override;

  void onDrawShadowRec(const SkPath& path, const SkDrawShadowRec& rec) override;

  void onDrawVerticesObject(const SkVertices* vertices,
                            const SkVertices::Bone* bones, int boneCount,
                            SkBlendMode mode, const SkPaint& paint) override;

  void onDrawImageRect(const SkImage* image, const SkRect* src,
                       const SkRect& dst, const SkPaint* paint,
                       SrcRectConstraint constraint) override;

  void onDrawBitmapRect(const SkBitmap& bitmap, const SkRect* src,
                        const SkRect& dst, const SkPaint* paint,
                        SrcRectConstraint constraint) override;

  void onDrawPaint(const SkPaint& paint) override;

  void onDrawPoints(PointMode mode, size_t count, const SkPoint* pts,
                    const SkPaint& paint) override;

  void onDrawRect(const SkRect& rect, const SkPaint& paint) override;

  void onDrawRegion(const SkRegion& region, const SkPaint& paint) override;

  void onDrawOval(const SkRect& oval, const SkPaint& paint) override;

  void onDrawRRect(const SkRRect& rrect, const SkPaint& paint) override;

  void onDrawDRRect(const SkRRect& outer, const SkRRect& inner,
                    const SkPaint& paint) override;

  void onDrawArc(const SkRect& oval, SkScalar startAngle, SkScalar sweepAngle,
                 bool useCenter, const SkPaint& paint) override;

  void onDrawPath(const SkPath& path, const SkPaint& paint) override;

  void onDrawImage(const SkImage* image, SkScalar left, SkScalar top,
                   const SkPaint* paint) override;

  void onDrawTextBlob(const SkTextBlob* blob, SkScalar x, SkScalar y,
                      const SkPaint& paint) override;

  void onDrawPatch(const SkPoint* cubics, const SkColor* colors,
                   const SkPoint* texCoords, SkBlendMode mode,
                   const SkPaint& paint) override;

  void onDrawImageNine(const SkImage* image, const SkIRect& center,
                       const SkRect& dst, const SkPaint* paint) override;

  void onDrawImageLattice(const SkImage* image, const Lattice& lattice,
                          const SkRect& dst, const SkPaint* paint) override;

  void onDrawBitmap(const SkBitmap& bitmap, SkScalar dx, SkScalar dy,
                    const SkPaint* paint) override;

  void onDrawBitmapNine(const SkBitmap& bitmap, const SkIRect& center,
                        const SkRect& dst, const SkPaint* paint) override;

  void onDrawBitmapLattice(const SkBitmap& bitmap, const Lattice& lattice,
                           const SkRect& dst, const SkPaint* paint) override;

  void onDrawAtlas(const SkImage* atlas, const SkRSXform* xform,
                   const SkRect* rect, const SkColor* colors, int count,
                   SkBlendMode mode, const SkRect* cull,
                   const SkPaint* paint) override;

  void onDrawDrawable(SkDrawable* drawable, const SkMatrix* matrix) override;

  void onDrawPicture(const SkPicture* picture, const SkMatrix* matrix,
                     const SkPaint* paint) override;

  void onDrawAnnotation(const SkRect& rect, const char* key,
                        SkData* value) override;

  void onClipRect(const SkRect& rect, SkClipOp op,
                  ClipEdgeStyle edgeStyle) override;

  void onClipRRect(const SkRRect& rrect, SkClipOp op,
                   ClipEdgeStyle edgeStyle) override;

 private:
  void exitView(bool hasData);

  void addView(const SkRect& rect);

  void createRealCanvas();

  std::string parseIdFromLabel(const char* label);

  std::deque<View> views;

  // See b/121323050
  // Currently there are some transforms applied after the annotation indicating
  // the start of a node that should actually be done in the context of the
  // parent node. This is a hack to make them go into the right place--while
  // we're "inHeader", the commands are applied to the parent.
  bool inHeader = true;
  int request_version;
  ::layoutinspector::proto::InspectorView* root;
  const ::google::protobuf::RepeatedField< ::google::protobuf::int64>*
      known_ids;

  // Create a view tree node to go into the returned proto. This will release
  // &data and give ownership of it to the newly created node.
  ::layoutinspector::proto::InspectorView* createNode(
      std::string& id, std::string& type, int offsetX, int offsetY, int width,
      int height, std::unique_ptr<std::string> data,
      std::reverse_iterator<std::deque<View>::iterator>& parent);

  bool isKnownId(std::string& id);
};

#endif  // SKIA_TREEBUILDINGCANVAS_H
