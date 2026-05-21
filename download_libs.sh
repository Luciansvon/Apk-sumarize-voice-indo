#!/usr/bin/env bash
# Download sherpa-onnx Android AAR before building
set -euo pipefail

LIBS_DIR="app/libs"
SHERPA_VERSION="1.10.35"
SHERPA_AAR="sherpa-onnx-v${SHERPA_VERSION}-android.aar"
SHERPA_URL="https://github.com/k2-fsa/sherpa-onnx/releases/download/v${SHERPA_VERSION}/${SHERPA_AAR}"

mkdir -p "$LIBS_DIR"

if [ -f "$LIBS_DIR/$SHERPA_AAR" ]; then
    echo "sherpa-onnx AAR already exists, skipping download."
else
    echo "Downloading sherpa-onnx v${SHERPA_VERSION} AAR (~50MB)..."
    curl -L --retry 4 --retry-delay 2 -o "$LIBS_DIR/$SHERPA_AAR" "$SHERPA_URL"
    echo "Done: $LIBS_DIR/$SHERPA_AAR"
fi

echo ""
echo "Setup complete. Now build the project:"
echo "  ./gradlew assembleDebug"
echo ""
echo "Note: AI models (Whisper + Gemma) are downloaded automatically"
echo "on first app launch (~600MB total, requires internet once)."
