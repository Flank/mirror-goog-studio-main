#include <SkGradientShader.h>
#include <SkPictureRecorder.h>
#include <jni.h>
#include <string>
#include "skia.grpc.pb.h"
#include "skia.pb.h"
#include "tree_building_canvas.h"

void add_requested_node(::layoutinspector::proto::GetViewTreeRequest &request,
                        int x, int y, int width, int height, int id) {
  ::layoutinspector::proto::RequestedNodeInfo *node1 =
      request.add_requested_nodes();
  node1->set_x(x);
  node1->set_y(y);
  node1->set_width(width);
  node1->set_height(height);
  node1->set_id(id);
}

jbyteArray build_tree(sk_sp<SkPicture> picture,
                      ::layoutinspector::proto::GetViewTreeRequest request,
                      JNIEnv *env) {
  sk_sp<SkData> data = picture->serialize();
  auto root = ::layoutinspector::proto::InspectorView();
  v1::TreeBuildingCanvas::ParsePicture(
      static_cast<const char *>(data->data()), data->size(), 1,
      &(request.requested_nodes()), 1.0, &root);

  std::string str;
  root.SerializeToString(&str);

  int size = str.length();
  jbyteArray result = env->NewByteArray(size);
  env->SetByteArrayRegion(result, 0, size, (jbyte *)(str.c_str()));
  return result;
}

extern "C" {

JNIEXPORT jbyteArray JNICALL
Java_com_android_tools_layoutinspector_SkiaParserTest_generateBoxes(
    JNIEnv *env, jobject instance) {
  SkPictureRecorder recorder;
  SkPaint paint;

  paint.setStyle(SkPaint::kFill_Style);
  paint.setAntiAlias(true);
  paint.setStrokeWidth(0);

  SkCanvas *canvas = recorder.beginRecording({0, 0, 1000, 2000});
  const SkRect &skRect1 = SkRect::MakeXYWH(0, 0, 1000, 2000);
  canvas->drawAnnotation(skRect1, "RenderNode(id=1, name='LinearLayout')",
                         nullptr);
  paint.setColor(SK_ColorYELLOW);
  canvas->drawRect(skRect1, paint);

  const SkRect &skRect2 = SkRect::MakeXYWH(0, 0, 500, 1000);
  canvas->drawAnnotation(skRect2, "RenderNode(id=2, name='FrameLayout')",
                         nullptr);
  canvas->save();
  canvas->translate(100, 100);
  paint.setColor(SK_ColorBLUE);
  canvas->drawRect(skRect2, paint);

  const SkRect &skRect3 = SkRect::MakeXYWH(0, 0, 200, 500);
  canvas->drawAnnotation(skRect3, "RenderNode(id=3, name='AppCompatButton')",
                         nullptr);
  canvas->save();
  canvas->translate(200, 200);
  paint.setColor(SK_ColorBLACK);
  canvas->drawRect(skRect3, paint);
  canvas->restore();
  canvas->drawAnnotation(skRect3, "/RenderNode(id=3, name='AppCompatButton')",
                         nullptr);

  canvas->restore();
  canvas->drawAnnotation(skRect2, "/RenderNode(id=2, name='FrameLayout')",
                         nullptr);

  const SkRect &skRect4 = SkRect::MakeXYWH(0, 0, 400, 500);
  canvas->drawAnnotation(skRect4, "RenderNode(id=4, name='Button')", nullptr);
  canvas->save();
  canvas->translate(300, 1200);
  paint.setColor(SK_ColorRED);
  canvas->drawRect(skRect4, paint);
  canvas->restore();
  canvas->drawAnnotation(skRect4, "/RenderNode(id=4, name='Button')", nullptr);

  canvas->drawAnnotation(skRect1, "/RenderNode(id=1, name='LinearLayout')",
                         nullptr);

  sk_sp<SkPicture> picture = recorder.finishRecordingAsPicture();

  ::layoutinspector::proto::GetViewTreeRequest request;
  add_requested_node(request, 0, 0, 1000, 2000, 1);
  add_requested_node(request, 300, 1200, 400, 500, 4);

  return build_tree(picture, request, env);
}

JNIEXPORT jbyteArray JNICALL
Java_com_android_tools_layoutinspector_SkiaParserTest_generateTransformedViews(
    JNIEnv *env, jobject instance) {
  SkPictureRecorder recorder;
  SkPaint paint;

  paint.setStyle(SkPaint::kFill_Style);
  paint.setAntiAlias(true);
  paint.setStrokeWidth(0);

  SkCanvas *canvas = recorder.beginRecording({0, 0, 256, 256});
  canvas->drawAnnotation(SkRect::MakeXYWH(0, 0, 256, 256),
                         "RenderNode(id=1, name='Node1')", nullptr);
  canvas->drawColor(SK_ColorYELLOW);
  const SkRect &skRect1 = SkRect::MakeXYWH(0, 0, 400, 300);
  canvas->drawAnnotation(skRect1, "RenderNode(id=2, name='Transformed')",
                         nullptr);

  paint.setStyle(SkPaint::kFill_Style);
  paint.setAntiAlias(true);
  paint.setStrokeWidth(0);

  SkColor colors[] = {SK_ColorBLUE, SK_ColorRED};
  SkScalar positions[] = {0.0, 1.0};
  SkPoint pts[] = {{0, 0}, {0, 300}};

  SkMatrix matrix;
  matrix.setIdentity();
  matrix.setRotate(50);
  matrix.setPerspX(0.002);
  matrix.setPerspY(0.001);
  matrix.setTranslateX(200);
  matrix.setTranslateY(60);

  auto lgs = SkGradientShader::MakeLinear(pts, colors, positions, 2,
                                          SkTileMode::kMirror, 0, &matrix);

  paint.setShader(lgs);
  canvas->save();
  canvas->concat(matrix);
  canvas->drawRect(skRect1, paint);

  canvas->drawAnnotation(skRect1, "RenderNode(id=3, name='NestedTransform')",
                         nullptr);
  canvas->save();
  canvas->translate(200, 100);
  canvas->scale(0.3, 0.4);
  paint.setShader(nullptr);
  paint.setColor(SK_ColorBLACK);
  canvas->drawRect(SkRect::MakeXYWH(0, 0, 400, 300), paint);
  canvas->restore();
  canvas->drawAnnotation(skRect1, "/RenderNode(id=3, name='NestedTransform')",
                         nullptr);
  canvas->restore();

  canvas->drawAnnotation(skRect1, "/RenderNode(id=2, name='Transformed')",
                         nullptr);
  paint.setColor(SK_ColorGREEN);
  canvas->drawRect(SkRect::MakeXYWH(100, 100, 40, 40), paint);

  canvas->drawAnnotation(skRect1, "/RenderNode(id=1, name='Node1')", nullptr);

  sk_sp<SkPicture> picture = recorder.finishRecordingAsPicture();

  ::layoutinspector::proto::GetViewTreeRequest request;
  add_requested_node(request, 0, 0, 256, 256, 1);
  add_requested_node(request, 0, 60, 254, 206, 2);
  add_requested_node(request, 98, 185, 90, 55, 3);

  return build_tree(picture, request, env);
}
}
