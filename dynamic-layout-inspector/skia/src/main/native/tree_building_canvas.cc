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
#include "tree_building_canvas.h"

#include <SkClipOp.h>
#include <SkImageInfo.h>
#include <memory>
#include <stack>

#include "SkImage.h"
#include "SkRRect.h"

namespace v1 {
TreeBuildingCanvas::~TreeBuildingCanvas() {
  if (!views.empty()) {
    std::cerr << "Found unclosed view!" << std::endl;
    while (!views.empty()) {
      views.pop_back();
    }
  }
}

bool TreeBuildingCanvas::onPeekPixels(SkPixmap* pixmap) {
  return real_canvas->peekPixels(pixmap);
}

bool TreeBuildingCanvas::onAccessTopLayerPixels(SkPixmap* pixmap) {
  return false;
}

SkImageInfo TreeBuildingCanvas::onImageInfo() const {
  return real_canvas->imageInfo();
}

bool TreeBuildingCanvas::onGetProps(SkSurfaceProps* props) const {
  return real_canvas->getProps(props);
}

void TreeBuildingCanvas::onFlush() { real_canvas->flush(); }

void TreeBuildingCanvas::willSave() {
  real_canvas->save();
#ifdef TREEBUILDINGCANVAS_DEBUG
  printDebug("willSave %i id: %i\n", real_canvas->getSaveCount(),
             views.back().id);
  real_canvas->getTotalMatrix().dump();
  SkIRect clip = real_canvas->getDeviceClipBounds();
  printDebug("   clip:  l:%i r:%i t:%i b:%i\n", clip.left(), clip.right(),
             clip.top(), clip.bottom());
#endif
}

void TreeBuildingCanvas::willRestore() {
#ifdef TREEBUILDINGCANVAS_DEBUG
  printDebug("willRestore %i id: %i\n", real_canvas->getSaveCount(),
             views.back().id);
  real_canvas->getTotalMatrix().dump();
#endif
  real_canvas->restore();
#ifdef TREEBUILDINGCANVAS_DEBUG
  real_canvas->getTotalMatrix().dump();
#endif
}

void TreeBuildingCanvas::onMarkCTM(const char* name) {
  real_canvas->markCTM(name);
}

void TreeBuildingCanvas::didConcat44(const SkM44& matrix) {
  real_canvas->concat(matrix);
#ifdef TREEBUILDINGCANVAS_DEBUG
  printDebug("didConcat\n");
  matrix.dump();
#endif
}

void TreeBuildingCanvas::didSetM44(const SkM44& matrix) {
#ifdef TREEBUILDINGCANVAS_DEBUG
  printDebug("orig was\n", request_scale);
  matrix.dump();
#endif

  SkM44 scaled = SkM44::Scale(request_scale, request_scale).preConcat(matrix);
  real_canvas->setMatrix(scaled);
#ifdef TREEBUILDINGCANVAS_DEBUG
  printDebug("didSetMatrix\n");
  matrix.dump();
  std::cerr << std::endl << "total: ";
  real_canvas->getTotalMatrix().dump();
  std::cerr << std::flush;
#endif
}

void TreeBuildingCanvas::didTranslate(SkScalar dx, SkScalar dy) {
  this->didConcat44(SkM44::Translate(dx, dy));
}

void TreeBuildingCanvas::didScale(SkScalar sx, SkScalar sy) {
  this->didConcat44(SkM44::Scale(sx, sy));
}

void TreeBuildingCanvas::onDrawPaint(const SkPaint& paint) {
  // can be empty if this is a dialog
  // TODO: still relevant?
  if (!views.empty()) {
    nonHeaderCommand();
    real_canvas->drawPaint(paint);
  }
}

void TreeBuildingCanvas::onDrawBehind(const SkPaint& pant) {
  // Unimplemented for now
}

void TreeBuildingCanvas::onDrawRect(const SkRect& rect, const SkPaint& paint) {
  nonHeaderCommand();
  real_canvas->drawRect(rect, paint);
}

void TreeBuildingCanvas::onDrawRRect(const SkRRect& rrect,
                                     const SkPaint& paint) {
  nonHeaderCommand();
  real_canvas->drawRRect(rrect, paint);
}

void TreeBuildingCanvas::onDrawDRRect(const SkRRect& outer,
                                      const SkRRect& inner,
                                      const SkPaint& paint) {
  nonHeaderCommand();
  real_canvas->drawDRRect(outer, inner, paint);
}

void TreeBuildingCanvas::onDrawOval(const SkRect& oval, const SkPaint& paint) {
  nonHeaderCommand();
  real_canvas->drawOval(oval, paint);
}

void TreeBuildingCanvas::onDrawArc(const SkRect& oval, SkScalar startAngle,
                                   SkScalar sweepAngle, bool useCenter,
                                   const SkPaint& paint) {
  nonHeaderCommand();
  real_canvas->drawArc(oval, startAngle, sweepAngle, useCenter, paint);
}

void TreeBuildingCanvas::onDrawPath(const SkPath& path, const SkPaint& paint) {
  nonHeaderCommand();
  real_canvas->drawPath(path, paint);
}

void TreeBuildingCanvas::onDrawRegion(const SkRegion& region,
                                      const SkPaint& paint) {
  nonHeaderCommand();
  real_canvas->drawRegion(region, paint);
}

void TreeBuildingCanvas::onDrawTextBlob(const SkTextBlob* blob, SkScalar x,
                                        SkScalar y, const SkPaint& paint) {
  nonHeaderCommand();
  real_canvas->drawTextBlob(blob, x, y, paint);
}

void TreeBuildingCanvas::onDrawPatch(const SkPoint* cubics,
                                     const SkColor* colors,
                                     const SkPoint* texCoords, SkBlendMode mode,
                                     const SkPaint& paint) {
  nonHeaderCommand();
  real_canvas->drawPatch(cubics, colors, texCoords, mode, paint);
}

void TreeBuildingCanvas::onDrawPoints(PointMode mode, size_t count,
                                      const SkPoint* pts,
                                      const SkPaint& paint) {
  nonHeaderCommand();
  real_canvas->drawPoints(mode, count, pts, paint);
}

void TreeBuildingCanvas::onDrawImage2(const SkImage* image, SkScalar left,
                                      SkScalar top,
                                      const SkSamplingOptions& options,
                                      const SkPaint* paint) {
#ifdef TREEBUILDINGCANVAS_DEBUG
  std::cerr << "drawImage";
  std::cerr.flush();
  real_canvas->getTotalMatrix().dump();
  std::cerr.flush();
  std::cerr << "top: " << top << " left: " << left << std::endl;
#endif

  nonHeaderCommand();
  real_canvas->drawImage(image, left, top, options, paint);
}

void TreeBuildingCanvas::onDrawImageRect2(const SkImage* image,
                                          const SkRect& src, const SkRect& dst,
                                          const SkSamplingOptions& options,
                                          const SkPaint* paint,
                                          SrcRectConstraint constraint) {
  nonHeaderCommand();
#ifdef TREEBUILDINGCANVAS_DEBUG
  printDebug("drawImageRect\n");
  printDebug("   src:  x:%f y:%f w:%f h:%f\n", src.x(), src.y(), src.width(),
             src.height());
  printDebug("   dst:  x:%f y:%f w:%f h:%f\n", dst.x(), dst.y(), dst.width(),
             dst.height());
  SkIRect clip = real_canvas->getDeviceClipBounds();
  printDebug("   clip:  l:%i r:%i t:%i b:%i\n", clip.left(), clip.right(),
             clip.top(), clip.bottom());
  real_canvas->getTotalMatrix().dump();
  real_canvas->getTotalMatrix().mapRect(dst).dump();
#endif

  real_canvas->drawImageRect(image, src, dst, options, paint, constraint);
}

void TreeBuildingCanvas::onDrawImageLattice2(const SkImage* image,
                                             const Lattice& lattice,
                                             const SkRect& dst,
                                             SkFilterMode filterMode,
                                             const SkPaint* paint) {
  nonHeaderCommand();
  real_canvas->drawImageLattice(image, lattice, dst, filterMode, paint);
}

void TreeBuildingCanvas::onDrawAtlas2(
    const SkImage* atlas, const SkRSXform xform[], const SkRect src[],
    const SkColor colors[], int count, SkBlendMode mode,
    const SkSamplingOptions& options, const SkRect* cull,
    const SkPaint* paint) {
  nonHeaderCommand();
  real_canvas->drawAtlas(atlas, xform, src, colors, count, mode, options, cull,
                         paint);
}

void TreeBuildingCanvas::onDrawEdgeAAImageSet2(
    const SkCanvas::ImageSetEntry imageSet[], int count,
    const SkPoint dstClips[], const SkMatrix preViewMatrices[],
    const SkSamplingOptions& options, const SkPaint* paint,
    SkCanvas::SrcRectConstraint constraint) {
  real_canvas->experimental_DrawEdgeAAImageSet(
      imageSet, count, dstClips, preViewMatrices, options, paint, constraint);
}

void TreeBuildingCanvas::onDrawVerticesObject(const SkVertices* vertices,
                                              SkBlendMode mode,
                                              const SkPaint& paint) {
  nonHeaderCommand();
  real_canvas->drawVertices(vertices, mode, paint);
}

void TreeBuildingCanvas::onDrawAnnotation(const SkRect&, const char* key,
                                          SkData*) {
#ifdef TREEBUILDINGCANVAS_DEBUG
  printDebug("annotation: %s\n", key);
#endif

  if (!strstr(key, "RenderNode")) {
#ifdef TREEBUILDINGCANVAS_DEBUG
    std::cerr << "not known id, skip" << std::endl;
#endif
    return;
  }
  long id = parseIdFromLabel(key);
  auto requested_node = requested_nodes.find(id);

  if (requested_node == requested_nodes.end()) {
    // The id is not found in the views or compose nodes in the component
    // tree. Paint this part on the parent canvas.
#ifdef TREEBUILDINGCANVAS_DEBUG
    printDebug("skip\n");
#endif
    return;
  }

  if (key[0] != '/') {  // Enter a node
#ifdef TREEBUILDINGCANVAS_DEBUG
    debug_indent++;
#endif

    if (!views.empty()) {
      exitView(true);
    }

    addView(id);
    addView(id);

  } else {  // Exit a node
#ifdef TREEBUILDINGCANVAS_DEBUG
    debug_indent--;
#endif
    exitView(true);
    exitView(false);
    if (!views.empty()) {
      addView(views.back().id);
    }
  }
}

void TreeBuildingCanvas::onDrawShadowRec(const SkPath& path,
                                         const SkDrawShadowRec& rec) {
  nonHeaderCommand();
#ifdef TREEBUILDINGCANVAS_DEBUG
  printDebug("drawShadow:\n");
#endif
  real_canvas->private_draw_shadow_rec(path, rec);
}

void TreeBuildingCanvas::onDrawDrawable(SkDrawable* drawable,
                                        const SkMatrix* matrix) {
  nonHeaderCommand();
  real_canvas->drawDrawable(drawable, matrix);
}

void TreeBuildingCanvas::onDrawPicture(const SkPicture* picture,
                                       const SkMatrix* matrix,
                                       const SkPaint* paint) {
  nonHeaderCommand();
  real_canvas->drawPicture(picture, matrix, paint);
}

void TreeBuildingCanvas::onDrawEdgeAAQuad(const SkRect& rect,
                                          const SkPoint clip[4],
                                          SkCanvas::QuadAAFlags aaFlags,
                                          const SkColor4f& color,
                                          SkBlendMode mode) {
  real_canvas->experimental_DrawEdgeAAQuad(rect, clip, aaFlags, color, mode);
}

void TreeBuildingCanvas::onClipRect(const SkRect& rect, SkClipOp op,
                                    ClipEdgeStyle edgeStyle) {
  real_canvas->clipRect(rect, op, edgeStyle);
}

void TreeBuildingCanvas::onClipRRect(const SkRRect& rrect, SkClipOp op,
                                     ClipEdgeStyle edgeStyle) {
#ifdef TREEBUILDINGCANVAS_DEBUG
  printDebug("cliprrect: %f %f %f %f\n", rrect.getBounds().x(),
             rrect.getBounds().y(), rrect.getBounds().width(),
             rrect.getBounds().height());
#endif
  real_canvas->clipRRect(rrect, op, edgeStyle);
}

void TreeBuildingCanvas::onClipPath(const SkPath& path, SkClipOp op,
                                    ClipEdgeStyle edgeStyle) {
  real_canvas->clipPath(path, op, edgeStyle == kSoft_ClipEdgeStyle);
}

void TreeBuildingCanvas::onClipShader(sk_sp<SkShader> shader, SkClipOp clipOp) {
  nonHeaderCommand();
  real_canvas->clipShader(shader, clipOp);
}

void TreeBuildingCanvas::onClipRegion(const SkRegion& deviceRgn, SkClipOp op) {
  real_canvas->clipRegion(deviceRgn, op);
}

void TreeBuildingCanvas::onDiscard() { real_canvas->discard(); }

void TreeBuildingCanvas::nonHeaderCommand() {
  if (!views.empty() && !views.back().didDraw) {
    views.back().didDraw = true;
  }
}

::layoutinspector::proto::InspectorView* TreeBuildingCanvas::createNode(
    long id, std::reverse_iterator<std::deque<View>::iterator>& parent,
    bool hasData) {
  ::layoutinspector::proto::InspectorView* node;
  if (parent < views.rend()) {
    View& parentView = *parent;

    // Create parent nodes up to the root if need be.
    if (parentView.node == nullptr) {
      std::unique_ptr<std::string> empty;
      parentView.node = createNode(parentView.id, ++parent, false);
    }
    node = parentView.node->add_children();
  } else {
    node = root;
  }

  node->set_id(id);

  if (hasData) {
    SkImageInfo imageInfo = real_canvas->imageInfo();
    SkIRect rect = requested_nodes.find(id)->second;
    int nBytes = imageInfo.bytesPerPixel() * rect.width() * rect.height();
    std::string bytes;
    bytes.resize(nBytes);

    SkImageInfo newImageInfo =
        SkImageInfo::Make(rect.size(), imageInfo.colorInfo());
    real_canvas->readPixels(
        newImageInfo,
        const_cast<void*>(reinterpret_cast<const void*>(bytes.data())),
        imageInfo.bytesPerPixel() * newImageInfo.width(), rect.x(), rect.y());
    node->set_image(std::move(bytes));
    node->set_width(rect.width());
    node->set_height(rect.height());
#ifdef TREEBUILDINGCANVAS_DEBUG
    printDebug("createNode x:%i y:%i w:%i h:%i\n", rect.x(), rect.y(),
               rect.width(), rect.height());
#endif
  }
#ifdef TREEBUILDINGCANVAS_DEBUG
  else {
    printDebug("createNode no data\n");
  }
#endif

  return node;
}

void TreeBuildingCanvas::exitView(bool hasData) {
  View& topView = views.back();
#ifdef TREEBUILDINGCANVAS_DEBUG
  printDebug("exitView hasData: %d didDraw: %d\n", hasData, topView.didDraw);
#endif
  if (hasData && topView.didDraw) {
    auto last = views.rbegin() + 1;
    createNode(topView.id, last, true);
  }
  views.pop_back();
}

void TreeBuildingCanvas::addView(long id) {
#ifdef TREEBUILDINGCANVAS_DEBUG
  printDebug("addView %li\n", id);
#endif

  real_canvas->clear(SK_ColorTRANSPARENT);
  views.emplace_back(id);
}

/**
 * Extract the id of a render node label.
 *
 * Use this old fashioned code to avoid using a regex which may or may not
 * be implemented on the machine used to build this code.
 *
 * @param label example: "RenderNode(id=1, name='LinearLayout')"
 * @param id output to receive the id as a string, example "1"
 */
long TreeBuildingCanvas::parseIdFromLabel(const char* label) {
  const char* start = strstr(label, "(id=");
  if (start == nullptr) {
    return 0;
  }
  start += 4;
  const char* end = strchr(start, ',');
  std::string id(start, (end - start));
  return std::stol(id);
}

#ifdef TREEBUILDINGCANVAS_DEBUG
void TreeBuildingCanvas::printDebug(const char* format, ...) {
  va_list args;
  va_start(args, format);

  for (int i = 0; i < debug_indent; i++) {
    vfprintf(stderr, "  ", args);
  }
  vfprintf(stderr, format, args);
}
#endif

}  // namespace v1
