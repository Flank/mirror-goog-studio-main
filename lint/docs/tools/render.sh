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
# References a link that doesn't exist here (but will
# in the place README.md.html is intended: as index.html
# in a place where the checks are hosted, see below)
mkdir -p checks
touch checks/index.md.html
npx markdeep-rasterizer README.md.html .

# Remove local absolute paths
kotlinc -script tools/fixlinks.kts -- book.html api-guide.html user-guide.html

# This file isn't checked in as HTML but is generated such that
# updating http://googlesamples.github.io/android-custom-lint-rules/
# (where it's needed) is easy
mv README.html index.html
