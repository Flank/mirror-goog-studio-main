#!/bin/sh
# Rasterizes the book.md.html into pure HTML.
# Invoke from the lint/docs/tools/ directory.
# Make sure "npm" is installed first
cd web-docs
npm install
cd ../..
npx markdeep-rasterizer book.md.html .
cd tools
kotlinc -script fixlinks.kts
