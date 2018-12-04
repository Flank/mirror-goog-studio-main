#!/usr/bin/env python

import os
import sys
import shutil

android_home = os.environ["QA_ANDROID_SDK_ROOT"]
if not os.path.exists(android_home) or not os.path.isdir(android_home):
  raise Exception("QA_ANDROID_SDK_ROOT does not point to a directory")

emu_dir = os.path.join(android_home, 'emulator')
sys_img_dir = os.path.join(android_home, 'system-images')

if not os.path.isdir(emu_dir) or not os.path.isdir(sys_img_dir):
  raise Exception("%s and %s do not exist as directories" % (emu_dir, sys_img_dir))

if len(sys.argv) != 2:
  raise Exception("%s takes in exactly 1 argument" % __main__)

target_path = sys.argv[1]
if not os.path.isdir(target_path):
  raise Exception("%s is not a directory" % target_path)

# QA_ANDROID_SDK_ROOT is non-empty.
target_emu_dir = os.path.join(target_path, "emulator")
target_sys_img_dir = os.path.join(target_path, "system-images")

shutil.copytree(emu_dir, target_emu_dir)
shutil.copytree(sys_img_dir, target_sys_img_dir)
