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
#include <regex>
#include "SkImage.h"
#include "SkNoDrawCanvas.h"

using std::deque;
using std::string;
using std::unique_ptr;
using namespace ::layoutinspector::proto;

void TreeBuildingCanvas::onClipRect(const SkRect &rect, SkClipOp op,
                                    ClipEdgeStyle edgeStyle) {
  SkCanvas::onClipRect(rect, op, edgeStyle);
  inHeader = false;
}

void TreeBuildingCanvas::didConcat(const SkMatrix &matrix) {
  topView->canvas->concat(matrix);
  if (inHeader) {
    if (topView->didConcat) {
      inHeader = false;
    } else {
      fixTranslation(matrix);
    }
  }
  views.pop_back();
  views.back()->canvas->concat(matrix);
  if (inHeader) {
    fixTranslation(matrix);
  }
  views.push_back(topView);
  topView->didConcat = true;
};

void TreeBuildingCanvas::fixTranslation(const SkMatrix &matrix) const {
  SkScalar xTranslation = matrix.getTranslateX();
  SkScalar yTranslation = matrix.getTranslateY();
  views.back()->canvas->translate(-xTranslation, -yTranslation);
  views.back()->offsetX += xTranslation;
  views.back()->offsetY += yTranslation;
}

void TreeBuildingCanvas::didSetMatrix(const SkMatrix &matrix) {
  SkMatrix newMatrix{};
  SkScalar buf[9];
  matrix.get9(buf);
  newMatrix.set9(buf);
  newMatrix.preTranslate(-topView->offsetX, -topView->offsetY);
  topView->canvas->setMatrix(newMatrix);
  views.pop_back();
  views.back()->canvas->setMatrix(newMatrix);
  views.push_back(topView);
}

void TreeBuildingCanvas::willSave() {
  topView->canvas->save();
  views.pop_back();
  views.back()->canvas->save();
  views.push_back(topView);
}

void TreeBuildingCanvas::willRestore() {
  topView->canvas->restore();
  views.pop_back();
  views.back()->canvas->restore();
  views.push_back(topView);
}

void TreeBuildingCanvas::nonHeaderCommand() {
  inHeader = false;
  if (!topView->didDraw) {
    topView->didDraw = true;
    createRealCanvas();
  }
}

bool TreeBuildingCanvas::onPeekPixels(SkPixmap *pixmap) {
  return topView->canvas->peekPixels(pixmap);
}

SkImageInfo TreeBuildingCanvas::onImageInfo() const {
  return topView->canvas->imageInfo();
}

bool TreeBuildingCanvas::onGetProps(SkSurfaceProps *props) const {
  return topView->canvas->getProps(props);
}

void TreeBuildingCanvas::onFlush() { topView->canvas->flush(); }

void TreeBuildingCanvas::onDrawShadowRec(const SkPath &path,
                                         const SkDrawShadowRec &rec) {
  nonHeaderCommand();
  topView->canvas->private_draw_shadow_rec(path, rec);
}

void TreeBuildingCanvas::onDrawVerticesObject(const SkVertices *vertices,
                                              const SkVertices::Bone *bones,
                                              int boneCount, SkBlendMode mode,
                                              const SkPaint &paint) {
  nonHeaderCommand();
  topView->canvas->drawVertices(vertices, bones, boneCount, mode, paint);
}

void TreeBuildingCanvas::onDrawImageRect(const SkImage *image,
                                         const SkRect *src, const SkRect &dst,
                                         const SkPaint *paint,
                                         SrcRectConstraint constraint) {
  nonHeaderCommand();
  topView->canvas->drawImageRect(image, *src, dst, paint, constraint);
}

void TreeBuildingCanvas::onDrawBitmapRect(const SkBitmap &bitmap,
                                          const SkRect *src, const SkRect &dst,
                                          const SkPaint *paint,
                                          SrcRectConstraint constraint) {
  nonHeaderCommand();
  topView->canvas->drawBitmapRect(bitmap, *src, dst, paint, constraint);
}

void TreeBuildingCanvas::onDrawPaint(const SkPaint &paint) {
  nonHeaderCommand();
  topView->canvas->drawPaint(paint);
}

void TreeBuildingCanvas::onDrawPoints(PointMode mode, size_t count,
                                      const SkPoint *pts,
                                      const SkPaint &paint) {
  nonHeaderCommand();
  topView->canvas->drawPoints(mode, count, pts, paint);
}

void TreeBuildingCanvas::onDrawRect(const SkRect &rect, const SkPaint &paint) {
  nonHeaderCommand();
  topView->canvas->drawRect(rect, paint);
}

void TreeBuildingCanvas::onDrawRegion(const SkRegion &region,
                                      const SkPaint &paint) {
  nonHeaderCommand();
  topView->canvas->drawRegion(region, paint);
}

void TreeBuildingCanvas::onDrawOval(const SkRect &oval, const SkPaint &paint) {
  nonHeaderCommand();
  topView->canvas->drawOval(oval, paint);
}

void TreeBuildingCanvas::onDrawRRect(const SkRRect &rrect,
                                     const SkPaint &paint) {
  nonHeaderCommand();
  topView->canvas->drawRRect(rrect, paint);
}

void TreeBuildingCanvas::onDrawDRRect(const SkRRect &outer,
                                      const SkRRect &inner,
                                      const SkPaint &paint) {
  nonHeaderCommand();
  topView->canvas->drawDRRect(outer, inner, paint);
}

void TreeBuildingCanvas::onDrawArc(const SkRect &oval, SkScalar startAngle,
                                   SkScalar sweepAngle, bool useCenter,
                                   const SkPaint &paint) {
  nonHeaderCommand();
  topView->canvas->drawArc(oval, startAngle, sweepAngle, useCenter, paint);
}

void TreeBuildingCanvas::onDrawPath(const SkPath &path, const SkPaint &paint) {
  nonHeaderCommand();
  topView->canvas->drawPath(path, paint);
}

void TreeBuildingCanvas::onDrawImage(const SkImage *image, SkScalar left,
                                     SkScalar top, const SkPaint *paint) {
  nonHeaderCommand();
  topView->canvas->drawImage(image, left, top, paint);
}

void TreeBuildingCanvas::onDrawTextBlob(const SkTextBlob *blob, SkScalar x,
                                        SkScalar y, const SkPaint &paint) {
  nonHeaderCommand();
  topView->canvas->drawTextBlob(blob, x, y, paint);
}

void TreeBuildingCanvas::onDrawPatch(const SkPoint *cubics,
                                     const SkColor *colors,
                                     const SkPoint *texCoords, SkBlendMode mode,
                                     const SkPaint &paint) {
  nonHeaderCommand();
  topView->canvas->drawPatch(cubics, colors, texCoords, mode, paint);
}

void TreeBuildingCanvas::onDrawImageNine(const SkImage *image,
                                         const SkIRect &center,
                                         const SkRect &dst,
                                         const SkPaint *paint) {
  nonHeaderCommand();
  topView->canvas->drawImageNine(image, center, dst, paint);
}

void TreeBuildingCanvas::onDrawImageLattice(const SkImage *image,
                                            const Lattice &lattice,
                                            const SkRect &dst,
                                            const SkPaint *paint) {
  nonHeaderCommand();
  topView->canvas->drawImageLattice(image, lattice, dst, paint);
}

void TreeBuildingCanvas::onDrawBitmap(const SkBitmap &bitmap, SkScalar dx,
                                      SkScalar dy, const SkPaint *paint) {
  nonHeaderCommand();
  topView->canvas->drawBitmap(bitmap, dx, dy, paint);
}

void TreeBuildingCanvas::onDrawBitmapNine(const SkBitmap &bitmap,
                                          const SkIRect &center,
                                          const SkRect &dst,
                                          const SkPaint *paint) {
  nonHeaderCommand();
  topView->canvas->drawBitmapNine(bitmap, center, dst, paint);
}

void TreeBuildingCanvas::onDrawBitmapLattice(const SkBitmap &bitmap,
                                             const Lattice &lattice,
                                             const SkRect &dst,
                                             const SkPaint *paint) {
  nonHeaderCommand();
  topView->canvas->drawBitmapLattice(bitmap, lattice, dst, paint);
}

void TreeBuildingCanvas::onDrawAtlas(const SkImage *atlas,
                                     const SkRSXform *xform, const SkRect *rect,
                                     const SkColor *colors, int count,
                                     SkBlendMode mode, const SkRect *cull,
                                     const SkPaint *paint) {
  nonHeaderCommand();
  topView->canvas->drawAtlas(atlas, xform, rect, colors, count, mode, cull,
                             paint);
}

void TreeBuildingCanvas::onDrawDrawable(SkDrawable *drawable,
                                        const SkMatrix *matrix) {
  nonHeaderCommand();
  topView->canvas->drawDrawable(drawable, matrix);
}

void TreeBuildingCanvas::onDrawPicture(const SkPicture *picture,
                                       const SkMatrix *matrix,
                                       const SkPaint *paint) {
  nonHeaderCommand();
  topView->canvas->drawPicture(picture, matrix, paint);
}

void TreeBuildingCanvas::onDrawAnnotation(const SkRect &rect, const char *key,
                                          SkData *value) {
  if (strstr(key, "RenderNode")) {
    if (key[0] != '/') {
      if (!views.empty()) {
        views.pop_back();
      }
      addView(rect);
      views.back()->label = key;
      addView(rect);
      topView = views.back();
      topView->label = key;
      inHeader = true;
    } else {
      inHeader = false;
      exitView(rect, true);
      exitView(rect, false);
      if (!views.empty()) {
        // TODO: Need to copy save/restore here
        const char *label = views.back()->label;
        addView(SkRect::MakeWH(views.back()->width, views.back()->height));
        topView = views.back();
        topView->label = label;
      }
    }
  }
}

InspectorView *TreeBuildingCanvas::createNode(
    string &id, string &type, int offsetX, int offsetY, int width, int height,
    int *data, std::reverse_iterator<deque<View *>::iterator> parent) {
  InspectorView *node;

  if (parent < views.rend()) {
    View *existing = *parent;

    if (existing->node == nullptr) {
      string existingType;
      if (existing->label) {
        existingType = existing->label;
      } else {
        existingType = "null";
      }
      string existingId;
      // todo: factor out
      std::regex rgx(".*id=(\\d+),.*");
      std::cmatch match;
      if (std::regex_search(existing->label, match, rgx)) {
        existingId = match[1];
      }

      existing->node = createNode(
          existingId, existingType, SkScalarRoundToInt(existing->offsetX),
          SkScalarRoundToInt(existing->offsetY),
          SkScalarRoundToInt(existing->width),
          SkScalarRoundToInt(existing->height), nullptr, ++parent);
    }
    node = existing->node->add_children();
  } else {
    node = root;
  }

  node->set_id(id);
  node->set_type(type);
  node->set_x(offsetX);
  node->set_y(offsetY);
  node->set_width(width);
  node->set_height(height);

  if (data != nullptr) {
    node->set_image(data, width * height * sizeof(int));
  }
  return node;
}

void TreeBuildingCanvas::exitView(const SkRect &rect, bool hasData) {
  View *view = views.back();
  views.pop_back();

  int width = SkScalarRoundToInt(rect.width());
  int height = SkScalarRoundToInt(rect.height());

  if (hasData && view->didDraw) {
    string type;
    if (view->label) {
      type = view->label;
    } else {
      type = "null";
    }
    // todo: factor out
    std::regex rgx(".*id=(\\d+),.*");
    std::cmatch match;
    string id;
    if (std::regex_search(view->label, match, rgx)) {
      id = match[1];
    }

    createNode(id, type, SkScalarRoundToInt(view->offsetX),
               SkScalarRoundToInt(view->offsetY), width, height, view->data,
               views.rbegin());
  }

  // used by proto
  view->data = nullptr;
  delete view;
}

void TreeBuildingCanvas::addView(const SkRect &rect) {
  int width = SkScalarRoundToInt(rect.width());
  int height = SkScalarRoundToInt(rect.height());

  int prevLeft = 0;
  int prevTop = 0;
  View *existing = nullptr;
  if (!views.empty()) {
    existing = views.back();
    prevLeft =
        SkScalarRoundToInt(existing->canvas->getTotalMatrix().getTranslateX()) +
        existing->offsetX;
    prevTop =
        SkScalarRoundToInt(existing->canvas->getTotalMatrix().getTranslateY()) +
        existing->offsetY;
  }

  View *view = new View();

  view->offsetX = prevLeft;
  view->offsetY = prevTop;
  view->width = rect.width();
  view->height = rect.height();
  SkCanvas *canvas = new SkNoDrawCanvas(width, height);

  if (!views.empty()) {
    deque<SkMatrix> intermediate;
    while (true) {
      intermediate.push_back(existing->canvas->getTotalMatrix());
      if (existing->canvas->getSaveCount() == 1) {
        break;
      }
      existing->canvas->restore();
    }
    while (true) {
      canvas->setMatrix(intermediate.back());
      existing->canvas->setMatrix(intermediate.back());
      intermediate.pop_back();
      if (intermediate.empty()) {
        break;
      }
      canvas->save();
      existing->canvas->save();
    }

    canvas->translate(canvas->getTotalMatrix().getTranslateX() * -1,
                      canvas->getTotalMatrix().getTranslateY() * -1);
  }
  view->canvas.reset(canvas);
  views.push_back(view);
}

void TreeBuildingCanvas::createRealCanvas() {
  SkCanvas &currentCanvas = *(views.back()->canvas);
  int height = SkScalarRoundToInt(views.back()->height);
  int width = SkScalarRoundToInt(views.back()->width);
  SkImageInfo imageInfo =
      SkImageInfo::Make(width, height, kBGRA_8888_SkColorType,
                        kUnpremul_SkAlphaType, SkColorSpace::MakeSRGB());
  int *actualArray = new int[width * height];
  for (int i = 0; i < width * height; i++) {
    actualArray[i] = 0;
  }
  views.back()->data = actualArray;

  SkBitmap bitmap;
  bitmap.installPixels(imageInfo, actualArray, width * 4);
  auto newCanvas = new SkCanvas(bitmap);

  deque<SkMatrix> intermediate;
  while (true) {
    intermediate.push_back(currentCanvas.getTotalMatrix());
    if (currentCanvas.getSaveCount() == 1) {
      break;
    }
    currentCanvas.restore();
  }
  while (true) {
    newCanvas->setMatrix(intermediate.back());
    intermediate.pop_back();
    if (intermediate.empty()) {
      break;
    }
    newCanvas->save();
  }

  views.back()->canvas.reset(newCanvas);
}
