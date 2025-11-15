#!/bin/bash
#

./gradlew clean build assemble

rm -rf ~/applications/forth-sender/* && tar xf build/distributions/forth-sender*.tar -C ~/applications/forth-sender/ --strip-components=1
