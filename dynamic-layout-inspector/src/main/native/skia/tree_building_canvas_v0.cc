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
#include "tree_building_canvas_v0.h"

#include <memory>
#include <stack>

#include "SkImage.h"
#include "SkNoDrawCanvas.h"
#include "SkRRect.h"

namespace v0 {

TreeBuildingCanvas::~TreeBuildingCanvas() {
  if (!views.empty()) {
    std::cerr << "Found unclosed view!" << std::endl;
    while (!views.empty()) {
      views.pop_back();
    }
  }
}

void TreeBuildingCanvas::onClipRect(const SkRect& rect, SkClipOp op,
                                    ClipEdgeStyle edgeStyle) {
  views.back().canvas->clipRect(rect, op, edgeStyle);
  inHeader = false;
}

void TreeBuildingCanvas::onClipRRect(const SkRRect& rrect, SkClipOp op,
                                     ClipEdgeStyle edgeStyle) {
#ifdef TREEBUILDINGCANVAS_DEBUG
  std::cerr << "cliprrect" << std::endl << "input: ";
  rrect.dump();
  std::cerr << "total: ";
  views.back().canvas->getTotalMatrix().dump();
  std::cerr << std::flush;
#endif
  views.back().canvas->clipRRect(rrect, op, edgeStyle);
  inHeader = false;
}

void TreeBuildingCanvas::didConcat(const SkMatrix& matrix) {
  View& topView = views.back();
  topView.canvas->concat(matrix);
  if (inHeader) {
    if (topView.didConcat) {
      inHeader = false;
    } else {
      fixTranslation(matrix, topView);
    }
  }
  View& secondFromTop = *(views.rbegin() + 1);
  secondFromTop.canvas->concat(matrix);
  if (inHeader) {
    fixTranslation(matrix, secondFromTop);
  }
  topView.didConcat = true;
#ifdef TREEBUILDINGCANVAS_DEBUG
  std::cerr << "didConcat:" << std::endl;
  std::cerr << "input: ";
  matrix.dump();
  std::cerr << "total: ";
  views.back().canvas->getTotalMatrix().dump();
  std::cerr << std::flush;
#endif
}

void TreeBuildingCanvas::didTranslate(SkScalar dx, SkScalar dy) {
  this->didConcat(SkMatrix::MakeTrans(dx, dy));
}

void TreeBuildingCanvas::fixTranslation(const SkMatrix& matrix,
                                        View& view) const {
  SkScalar xTranslation = matrix.getTranslateX();
  SkScalar yTranslation = matrix.getTranslateY();
  view.canvas->translate(-xTranslation, -yTranslation);
  view.offsetX += xTranslation;
  view.offsetY += yTranslation;
}

void TreeBuildingCanvas::didSetMatrix(const SkMatrix& matrix) {
  SkMatrix newMatrix{};
  SkScalar buf[9];
  matrix.get9(buf);
  newMatrix.set9(buf);
  View& topView = views.back();
  newMatrix.preTranslate(-topView.offsetX, -topView.offsetY);
  topView.canvas->setMatrix(newMatrix);
#ifdef TREEBUILDINGCANVAS_DEBUG
  std::cerr << "didSetMatrix " << std::endl << "input: " << std::flush;
  matrix.dump();
  std::cerr << std::endl << "total: ";
  topView.canvas->getTotalMatrix().dump();
  std::cerr << std::flush;
#endif
  View& secondFromTop = *(views.rbegin() + 1);
  secondFromTop.canvas->setMatrix(newMatrix);
}

void TreeBuildingCanvas::willSave() {
  views.back().canvas->save();
#ifdef TREEBUILDINGCANVAS_DEBUG
  std::cerr << "willSave:";
  views.back().canvas->getTotalMatrix().dump();
  std::cerr << std::flush;
#endif
  View& secondFromTop = *(views.rbegin() + 1);
  secondFromTop.canvas->save();
}

void TreeBuildingCanvas::willRestore() {
  views.back().canvas->restore();
#ifdef TREEBUILDINGCANVAS_DEBUG
  std::cerr << "willRestore:";
  views.back().canvas->getTotalMatrix().dump();
  std::cerr << std::flush;
#endif
  View& secondFromTop = *(views.rbegin() + 1);
  secondFromTop.canvas->restore();
}

void TreeBuildingCanvas::nonHeaderCommand() {
  inHeader = false;
  if (!views.empty() && !views.back().didDraw) {
    views.back().didDraw = true;
    createRealCanvas();
  }
}

bool TreeBuildingCanvas::onPeekPixels(SkPixmap* pixmap) {
  return views.back().canvas->peekPixels(pixmap);
}

SkImageInfo TreeBuildingCanvas::onImageInfo() const {
  return views.back().canvas->imageInfo();
}

bool TreeBuildingCanvas::onGetProps(SkSurfaceProps* props) const {
  return views.back().canvas->getProps(props);
}

void TreeBuildingCanvas::onFlush() { views.back().canvas->flush(); }

void TreeBuildingCanvas::onDrawShadowRec(const SkPath& path,
                                         const SkDrawShadowRec& rec) {
  nonHeaderCommand();
#ifdef TREEBUILDINGCANVAS_DEBUG
  std::cerr << "drawShadow:";
  views.back().canvas->getTotalMatrix().dump();
  std::cerr << std::flush;
#endif
  views.back().canvas->private_draw_shadow_rec(path, rec);
}

void TreeBuildingCanvas::onDrawVerticesObject(const SkVertices* vertices,
                                              const SkVertices::Bone* bones,
                                              int boneCount, SkBlendMode mode,
                                              const SkPaint& paint) {
  nonHeaderCommand();
  views.back().canvas->drawVertices(vertices, bones, boneCount, mode, paint);
}

void TreeBuildingCanvas::onDrawImageRect(const SkImage* image,
                                         const SkRect* src, const SkRect& dst,
                                         const SkPaint* paint,
                                         SrcRectConstraint constraint) {
  nonHeaderCommand();
#ifdef TREEBUILDINGCANVAS_DEBUG
  std::cerr << "drawImageRect";
  std::cerr.flush();
  views.back().canvas->getTotalMatrix().dump();
  std::cerr.flush();
#endif

  views.back().canvas->drawImageRect(image, *src, dst, paint, constraint);
}

void TreeBuildingCanvas::onDrawBitmapRect(const SkBitmap& bitmap,
                                          const SkRect* src, const SkRect& dst,
                                          const SkPaint* paint,
                                          SrcRectConstraint constraint) {
  nonHeaderCommand();
  views.back().canvas->drawBitmapRect(bitmap, *src, dst, paint, constraint);
}

void TreeBuildingCanvas::onDrawPaint(const SkPaint& paint) {
  // can be empty if this is a dialog
  if (!views.empty()) {
    nonHeaderCommand();
    views.back().canvas->drawPaint(paint);
  }
}

void TreeBuildingCanvas::onDrawPoints(PointMode mode, size_t count,
                                      const SkPoint* pts,
                                      const SkPaint& paint) {
  nonHeaderCommand();
  views.back().canvas->drawPoints(mode, count, pts, paint);
}

void TreeBuildingCanvas::onDrawRect(const SkRect& rect, const SkPaint& paint) {
  nonHeaderCommand();
  views.back().canvas->drawRect(rect, paint);
}

void TreeBuildingCanvas::onDrawRegion(const SkRegion& region,
                                      const SkPaint& paint) {
  nonHeaderCommand();
  views.back().canvas->drawRegion(region, paint);
}

void TreeBuildingCanvas::onDrawOval(const SkRect& oval, const SkPaint& paint) {
  nonHeaderCommand();
  views.back().canvas->drawOval(oval, paint);
}

void TreeBuildingCanvas::onDrawRRect(const SkRRect& rrect,
                                     const SkPaint& paint) {
  nonHeaderCommand();
  views.back().canvas->drawRRect(rrect, paint);
}

void TreeBuildingCanvas::onDrawDRRect(const SkRRect& outer,
                                      const SkRRect& inner,
                                      const SkPaint& paint) {
  nonHeaderCommand();
  views.back().canvas->drawDRRect(outer, inner, paint);
}

void TreeBuildingCanvas::onDrawArc(const SkRect& oval, SkScalar startAngle,
                                   SkScalar sweepAngle, bool useCenter,
                                   const SkPaint& paint) {
  nonHeaderCommand();
  views.back().canvas->drawArc(oval, startAngle, sweepAngle, useCenter, paint);
}

void TreeBuildingCanvas::onDrawPath(const SkPath& path, const SkPaint& paint) {
  nonHeaderCommand();
  views.back().canvas->drawPath(path, paint);
}

void TreeBuildingCanvas::onDrawImage(const SkImage* image, SkScalar left,
                                     SkScalar top, const SkPaint* paint) {
  nonHeaderCommand();
  views.back().canvas->drawImage(image, left, top, paint);
}

void TreeBuildingCanvas::onDrawTextBlob(const SkTextBlob* blob, SkScalar x,
                                        SkScalar y, const SkPaint& paint) {
  nonHeaderCommand();
  views.back().canvas->drawTextBlob(blob, x, y, paint);
}

void TreeBuildingCanvas::onDrawPatch(const SkPoint* cubics,
                                     const SkColor* colors,
                                     const SkPoint* texCoords, SkBlendMode mode,
                                     const SkPaint& paint) {
  nonHeaderCommand();
  views.back().canvas->drawPatch(cubics, colors, texCoords, mode, paint);
}

void TreeBuildingCanvas::onDrawImageNine(const SkImage* image,
                                         const SkIRect& center,
                                         const SkRect& dst,
                                         const SkPaint* paint) {
  nonHeaderCommand();
  views.back().canvas->drawImageNine(image, center, dst, paint);
}

void TreeBuildingCanvas::onDrawImageLattice(const SkImage* image,
                                            const Lattice& lattice,
                                            const SkRect& dst,
                                            const SkPaint* paint) {
  nonHeaderCommand();
  views.back().canvas->drawImageLattice(image, lattice, dst, paint);
}

void TreeBuildingCanvas::onDrawBitmap(const SkBitmap& bitmap, SkScalar dx,
                                      SkScalar dy, const SkPaint* paint) {
  nonHeaderCommand();
  views.back().canvas->drawBitmap(bitmap, dx, dy, paint);
}

void TreeBuildingCanvas::onDrawBitmapNine(const SkBitmap& bitmap,
                                          const SkIRect& center,
                                          const SkRect& dst,
                                          const SkPaint* paint) {
  nonHeaderCommand();
  views.back().canvas->drawBitmapNine(bitmap, center, dst, paint);
}

void TreeBuildingCanvas::onDrawBitmapLattice(const SkBitmap& bitmap,
                                             const Lattice& lattice,
                                             const SkRect& dst,
                                             const SkPaint* paint) {
  nonHeaderCommand();
  views.back().canvas->drawBitmapLattice(bitmap, lattice, dst, paint);
}

void TreeBuildingCanvas::onDrawAtlas(const SkImage* atlas,
                                     const SkRSXform* xform, const SkRect* rect,
                                     const SkColor* colors, int count,
                                     SkBlendMode mode, const SkRect* cull,
                                     const SkPaint* paint) {
  nonHeaderCommand();
  views.back().canvas->drawAtlas(atlas, xform, rect, colors, count, mode, cull,
                                 paint);
}

void TreeBuildingCanvas::onDrawDrawable(SkDrawable* drawable,
                                        const SkMatrix* matrix) {
  nonHeaderCommand();
  views.back().canvas->drawDrawable(drawable, matrix);
}

void TreeBuildingCanvas::onDrawPicture(const SkPicture* picture,
                                       const SkMatrix* matrix,
                                       const SkPaint* paint) {
  nonHeaderCommand();
  views.back().canvas->drawPicture(picture, matrix, paint);
}

void TreeBuildingCanvas::onDrawAnnotation(const SkRect& rect, const char* key,
                                          SkData* value) {
#ifdef TREEBUILDINGCANVAS_DEBUG
  std::cerr << "annotation: " << key << std::endl;
#endif

  if (!strstr(key, "RenderNode")) {
    return;
  }

  if (key[0] != '/') {
    if (!views.empty()) {
      exitView(true);
    }
    addView(rect);
    views.back().label = key;
    addView(rect);
    views.back().label = key;
    inHeader = true;
  } else {
    inHeader = false;
    exitView(true);
    exitView(false);
    if (!views.empty()) {
      // TODO: Need to copy save/restore here
      const char* label = views.back().label;
      addView(SkRect::MakeWH(views.back().width, views.back().height));
      views.back().label = label;
    }
  }
}

::layoutinspector::proto::InspectorView* TreeBuildingCanvas::createNode(
    std::string& id, std::string& type, int offsetX, int offsetY, int width,
    int height, std::unique_ptr<std::string> image,
    std::reverse_iterator<std::deque<View>::iterator>& parent) {
  ::layoutinspector::proto::InspectorView* node;

  if (parent < views.rend()) {
    View& existing = *parent;

    if (existing.node == nullptr) {
      std::string existingType;
      if (existing.label) {
        existingType = existing.label;
      } else {
        existingType = "null";
      }
      std::string existingId = parseIdFromLabel(existing.label);
      std::unique_ptr<std::string> empty;
      existing.node = createNode(
          existingId, existingType, SkScalarRoundToInt(existing.offsetX),
          SkScalarRoundToInt(existing.offsetY),
          SkScalarRoundToInt(existing.width),
          SkScalarRoundToInt(existing.height), std::move(empty), ++parent);
    }
    node = existing.node->add_children();
  } else {
    node = root;
  }

  node->set_id_for_v0_only(id);
  node->set_type_for_v0_only(type);
  node->set_x_for_v0_only(offsetX);
  node->set_y_for_v0_only(offsetY);
  node->set_width(width);
  node->set_height(height);

  node->set_allocated_image(image.release());
  return node;
}

void TreeBuildingCanvas::exitView(bool hasData) {
  View& topView = views.back();
#ifdef TREEBUILDINGCANVAS_DEBUG
  std::cerr << "exitView" << std::endl;
#endif
  if (hasData && topView.didDraw) {
    std::string type;
    if (topView.label) {
      type = topView.label;
    } else {
      type = "null";
    }
    std::string id = parseIdFromLabel(topView.label);
    auto last = views.rbegin() + 1;
    createNode(id, type, SkScalarRoundToInt(topView.offsetX),
               SkScalarRoundToInt(topView.offsetY), topView.width,
               topView.height, std::move(topView.image), last);
  }
  views.pop_back();
}

void TreeBuildingCanvas::addView(const SkRect& rect) {
  int prevLeft = 0;
  int prevTop = 0;
  if (!views.empty()) {
    View& existing = views.back();
    prevLeft =
        SkScalarRoundToInt(existing.canvas->getTotalMatrix().getTranslateX()) +
        existing.offsetX;
    prevTop =
        SkScalarRoundToInt(existing.canvas->getTotalMatrix().getTranslateY()) +
        existing.offsetY;
  }

#ifdef TREEBUILDINGCANVAS_DEBUG
  std::cerr << "addView" << std::endl;
#endif
  views.emplace_back(rect.width(), rect.height(), prevLeft, prevTop);

  if (views.size() > 1) {
    View& existing = *(views.rbegin() + 1);
    std::stack<SkMatrix> intermediate;
    while (true) {
      intermediate.emplace(existing.canvas->getTotalMatrix());
      if (existing.canvas->getSaveCount() == 1) {
        break;
      }
      existing.canvas->restore();
    }
    while (true) {
      views.back().canvas->setMatrix(intermediate.top());
      existing.canvas->setMatrix(intermediate.top());
      intermediate.pop();
      if (intermediate.empty()) {
        break;
      }
      views.back().canvas->save();
      existing.canvas->save();
    }

    views.back().canvas->translate(
        views.back().canvas->getTotalMatrix().getTranslateX() * -1,
        views.back().canvas->getTotalMatrix().getTranslateY() * -1);
  }
}

void TreeBuildingCanvas::createRealCanvas() {
  SkCanvas& currentCanvas = *(views.back().canvas);
  int height = SkScalarRoundToInt(views.back().height);
  int width = SkScalarRoundToInt(views.back().width);
  SkImageInfo imageInfo =
      SkImageInfo::Make(width, height, kBGRA_8888_SkColorType,
                        kUnpremul_SkAlphaType, SkColorSpace::MakeSRGB());

  views.back().image.reset(new std::string());
  views.back().image->resize(width * height * sizeof(int32_t));

  SkBitmap bitmap;
  const auto* data =
      reinterpret_cast<const int32_t*>(views.back().image->data());
  bitmap.installPixels(imageInfo, const_cast<int32_t*>(data),
                       width * sizeof(int32_t));
  auto newCanvas = new SkCanvas(bitmap);

  std::stack<SkMatrix> intermediate;
#ifdef TREEBUILDINGCANVAS_DEBUG
  std::cerr << "creating new canvas: ";
  currentCanvas.getTotalMatrix().dump();
  std::cerr << std::flush;
#endif
  while (true) {
    const SkMatrix& matrix = currentCanvas.getTotalMatrix();
    intermediate.emplace(matrix);

    if (currentCanvas.getSaveCount() == 1) {
      break;
    }
    currentCanvas.restore();
  }
  while (true) {
    newCanvas->setMatrix(intermediate.top());
    intermediate.pop();
    if (intermediate.empty()) {
      break;
    }
    newCanvas->save();
  }
#ifdef TREEBUILDINGCANVAS_DEBUG
  std::cerr << "new is: ";
  newCanvas->getTotalMatrix().dump();
  std::cerr << std::flush;
#endif

  views.back().canvas.reset(newCanvas);
}

/**
 * Extract the id of a render node label.
 *
 * Use this old fashioned code to avoid using a regex which may or may not be
 * implemented on the machine used to build this code.
 *
 * @param label example: "RenderNode(id=1, name='LinearLayout')"
 * @param id output to receive the id as a string, example "1"
 */
std::string TreeBuildingCanvas::parseIdFromLabel(const char* label) {
  const char* start = strstr(label, "(id=");
  if (start == nullptr) {
    return "";
  }
  start += 4;
  const char* end = strchr(start, ',');
  std::string id(start, (end - start));
  return id;
}
}  // namespace v0