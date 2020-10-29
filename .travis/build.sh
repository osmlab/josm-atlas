#!/usr/bin/env sh

chmod u+x gradlew

./gradlew clean build compileJava_minJosm compileJava_latestJosm compileJava_testedJosm
