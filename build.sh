#!/bin/bash

OUTPUT_DIR=output
rm -rf "${OUTPUT_DIR}" && mkdir "${OUTPUT_DIR}"
./gradlew assemble -x javadoc
mv build/distributions/prometheus-exporter-*.zip "${OUTPUT_DIR}"