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

#include <SkBlendMode.h>
#include <SkClipOp.h>
#include <deque>
#include <iostream>
#include <memory>

#include "SkCanvasVirtualEnforcer.h"
#include "SkPicture.h"
#include "SkScalar.h"
#include "SkStream.h"
#include "SkSurface.h"

#ifndef SKIA_TREEBUILDINGCANVAS_H
#define SKIA_TREEBUILDINGCANVAS_H

namespace v1 {

struct View {
  // The node corresponding to this view that will eventually be returned to
  // studio via grpc. Reference is kept here so we can set up the parent/child
  // relationship between nodes.
  ::layoutinspector::proto::InspectorView* node = nullptr;

  // Whether we've drawn into this view yet.
  bool didDraw = false;

  long id;

  explicit View(long id) : id(id) {}
};

class TreeBuildingCanvas : public SkCanvasVirtualEnforcer<SkCanvas> {
 public:
  /**
   * Parse the picture and populate root.
   *
   * @param skp The serialized SkPicture
   * @param len The length of skp
   * @param version The version of the request protocol. See definitions in
   * skia.proto.
   * @param requested_node_info The rendernodes to return, along with their size
   * and location. Nodes not included here will be merged into their parents.
   * @param scale The factor by which to scale the result. Usually the full-size
   * rendering won't be needed, so we can save the memory and bandwidth.
   * @param root The output proto to populate.
   */
  static void ParsePicture(
      const char* skp, size_t len, int version,
      const ::google::protobuf::RepeatedPtrField<
          ::layoutinspector::proto::RequestedNodeInfo>* requested_node_info,
      float scale, ::layoutinspector::proto::InspectorView* root) {
#ifdef TREEBUILDINGCANVAS_DEBUG
    std::cerr << "###start scale: " << scale << std::endl;
#endif
    std::unique_ptr<SkStreamAsset> stream =
        SkMemoryStream::MakeDirect(skp, len);
    sk_sp<SkPicture> picture(SkPicture::MakeFromStream(stream.get()));
    if (picture == nullptr) {
#ifdef TREEBUILDINGCANVAS_DEBUG
      std::cerr << "Got null picture, abort. Len was " << len << std::endl;
#endif

      return;
    }
    picture->ref();

    SkIRect rootBounds = SkIRect::MakeXYWH(0, 0, 1, 1);
    std::map<long, SkIRect> requested_nodes;
    for (const ::layoutinspector::proto::RequestedNodeInfo& node :
         *requested_node_info) {
      SkIRect rect =
          SkIRect::MakeXYWH(node.x() * scale, node.y() * scale,
                            node.width() * scale, node.height() * scale);
      rootBounds.join(rect);
      requested_nodes.insert(std::make_pair(node.id(), rect));
    }
    TreeBuildingCanvas canvas(version, root, rootBounds.width(),
                              rootBounds.height(), requested_nodes, scale);
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
      int width, int height, std::map<long, SkIRect> requested_nodes,
      float scale)
      : SkCanvasVirtualEnforcer<SkCanvas>(),
        request_version(version),
        request_scale(scale),
        surface(SkSurface::MakeRaster(
            SkImageInfo::Make(width, height, kBGRA_8888_SkColorType,
                              kUnpremul_SkAlphaType, SkColorSpace::MakeSRGB()),
            width * sizeof(int32_t), nullptr)),
        root(r),
        real_canvas(surface->getCanvas()),
        requested_nodes(std::move(requested_nodes)) {
    if (request_scale > 0) {
      real_canvas->scale(request_scale, request_scale);
    }
#ifdef TREEBUILDINGCANVAS_DEBUG
    printDebug("Create surface: %i x %i\n", width, height);
    printDebug("Canvas %s null\n", real_canvas == nullptr ? "is" : "is not");
#endif
  }

  void nonHeaderCommand();

  bool onPeekPixels(SkPixmap* pixmap) override;

  bool onAccessTopLayerPixels(SkPixmap* pixmap) override;

  SkImageInfo onImageInfo() const override;

  bool onGetProps(SkSurfaceProps* props) const override;

  void onFlush() override;

  void willSave() override;

  void willRestore() override;

  void onMarkCTM(const char*) override;

  void didConcat44(const SkM44& matrix) override;

  void didSetM44(const SkM44& matrix) override;

  void didTranslate(SkScalar dx, SkScalar dy) override;

  void didScale(SkScalar sx, SkScalar sy) override;

  void onDrawPaint(const SkPaint& paint) override;

  void onDrawBehind(const SkPaint& paint) override;

  void onDrawRect(const SkRect& rect, const SkPaint& paint) override;

  void onDrawRRect(const SkRRect& rrect, const SkPaint& paint) override;

  void onDrawDRRect(const SkRRect& outer, const SkRRect& inner,
                    const SkPaint& paint) override;

  void onDrawOval(const SkRect& oval, const SkPaint& paint) override;

  void onDrawArc(const SkRect& oval, SkScalar startAngle, SkScalar sweepAngle,
                 bool useCenter, const SkPaint& paint) override;

  void onDrawPath(const SkPath& path, const SkPaint& paint) override;

  void onDrawRegion(const SkRegion& region, const SkPaint& paint) override;

  void onDrawTextBlob(const SkTextBlob* blob, SkScalar x, SkScalar y,
                      const SkPaint& paint) override;

  void onDrawPatch(const SkPoint* cubics, const SkColor* colors,
                   const SkPoint* texCoords, SkBlendMode mode,
                   const SkPaint& paint) override;

  void onDrawPoints(PointMode mode, size_t count, const SkPoint* pts,
                    const SkPaint& paint) override;

  void onDrawImage2(const SkImage* image, SkScalar left, SkScalar top,
                    const SkSamplingOptions&, const SkPaint* paint) override;

  void onDrawImageRect2(const SkImage* image, const SkRect& src,
                        const SkRect& dst, const SkSamplingOptions&,
                        const SkPaint* paint,
                        SrcRectConstraint constraint) override;

  void onDrawImageLattice2(const SkImage* image, const Lattice& lattice,
                           const SkRect& dst, SkFilterMode filterMode,
                           const SkPaint* paint) override;

  void onDrawAtlas2(const SkImage* atlas, const SkRSXform xform[],
                    const SkRect src[], const SkColor colors[], int count,
                    SkBlendMode mode, const SkSamplingOptions&,
                    const SkRect* cull, const SkPaint* paint) override;

  void onDrawEdgeAAImageSet2(const SkCanvas::ImageSetEntry imageSet[],
                             int count, const SkPoint dstClips[],
                             const SkMatrix preViewMatrices[],
                             const SkSamplingOptions& options,
                             const SkPaint* paint,
                             SkCanvas::SrcRectConstraint constraint) override;

  virtual void onDrawVerticesObject(const SkVertices* vertices,
                                    SkBlendMode mode,
                                    const SkPaint& paint) override;

  void onDrawAnnotation(const SkRect&, const char* key, SkData*) override;

  void onDrawShadowRec(const SkPath& path, const SkDrawShadowRec& rec) override;

  void onDrawDrawable(SkDrawable* drawable, const SkMatrix* matrix) override;

  void onDrawPicture(const SkPicture* picture, const SkMatrix* matrix,
                     const SkPaint* paint) override;

  void onDrawEdgeAAQuad(const SkRect& rect, const SkPoint clip[4],
                        SkCanvas::QuadAAFlags aaFlags, const SkColor4f& color,
                        SkBlendMode mode) override;

  void onClipRect(const SkRect& rect, SkClipOp op,
                  ClipEdgeStyle edgeStyle) override;

  void onClipRRect(const SkRRect& rrect, SkClipOp op,
                   ClipEdgeStyle edgeStyle) override;

  void onClipPath(const SkPath& path, SkClipOp op,
                  ClipEdgeStyle edgeStyle) override;

  void onClipShader(sk_sp<SkShader> shader, SkClipOp clipOp) override;

  void onClipRegion(const SkRegion& deviceRgn, SkClipOp op) override;

  void onDiscard() override;

 private:
  int request_version;
  SkScalar request_scale;
  sk_sp<SkSurface> surface;
  ::layoutinspector::proto::InspectorView* root;
  SkCanvas* real_canvas;

  void exitView(bool hasData);

  void addView(long id);

  static long parseIdFromLabel(const char* label);

  std::deque<View> views;

  std::map<long, SkIRect> requested_nodes;

  // Create a view tree node to go into the returned proto.
  ::layoutinspector::proto::InspectorView* createNode(
      long id, std::reverse_iterator<std::deque<View>::iterator>& parent,
      bool hasData);

#ifdef TREEBUILDINGCANVAS_DEBUG
  int debug_indent = 0;

  void printDebug(const char* format, ...);

#endif
};
}  // namespace v1
#endif  // SKIA_TREEBUILDINGCANVAS_H
