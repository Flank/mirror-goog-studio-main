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

extern "C" {

JNIEXPORT jbyteArray JNICALL
Java_com_android_tools_layoutinspector_SkiaParserTest_generateBoxes(
    JNIEnv *env, jobject instance) {
  std::cout << "foo" << std::endl;
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
  sk_sp<SkData> data = picture->serialize();

  ::layoutinspector::proto::GetViewTreeRequest request;
  add_requested_node(request, 0, 0, 1000, 2000, 1);
  add_requested_node(request, 300, 1200, 400, 500, 4);

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
}
