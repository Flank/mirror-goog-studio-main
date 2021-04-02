#!/bin/sh
# Clean up trailing spacer etc
kotlinc -script fixtext.kts -- --strip-duplicate-newlines --smart-quotes ..
# Rasterizes the book.md.html into pure HTML.
# Invoke from the lint/docs/tools/ directory.
# Make sure "npm" is installed first
cd web-docs
npm install
cd ../..
npx markdeep-rasterizer book.md.html .
npx markdeep-rasterizer api-guide.md.html .
npx markdeep-rasterizer user-guide.md.html .
kotlinc -script tools/fixlinks.kts -- book.html api-guide.html user-guide.html
