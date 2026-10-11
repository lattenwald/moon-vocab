ADB         ?= adb

VERSION_NAME := $(shell grep -oP 'versionName = "\K[^"]+' app/build.gradle.kts)
VERSION_CODE := $(shell git rev-list --count HEAD 2>/dev/null || echo 1)

ANDROID_HOME ?= $(shell grep -oP '^sdk\.dir=\K.*' local.properties 2>/dev/null)

GRADLE      := JAVA_HOME=$(JAVA_HOME) ./gradlew
KEYSTORE    := $(HOME)/.android/debug.keystore
APKSIGNER   := $(lastword $(sort $(wildcard $(ANDROID_HOME)/build-tools/*/apksigner)))

RELEASE_DIR := app/build/outputs/apk/release
DEBUG_DIR   := app/build/outputs/apk/debug
RELEASE_APK := $(RELEASE_DIR)/moon-vocab-$(VERSION_NAME)-$(VERSION_CODE).apk
DEBUG_APK   := $(DEBUG_DIR)/moon-vocab-$(VERSION_NAME)-$(VERSION_CODE)-debug.apk

.DEFAULT_GOAL := help

.PHONY: help
help:
	@echo 'moon-vocab $(VERSION_NAME) (code $(VERSION_CODE))'
	@grep -E '^[a-zA-Z0-9_-]+:.*?## .*$$' $(MAKEFILE_LIST) | sort | awk 'BEGIN {FS = ":.*?## "}; {printf "\033[36m%-18s\033[0m %s\n", $$1, $$2}'

.PHONY: require-java
require-java:
	@jc=java; [ -n "$(JAVA_HOME)" ] && jc="$(JAVA_HOME)/bin/java"; \
	  if ! "$$jc" -version >/dev/null 2>&1; then \
	    echo "error: no usable java (JAVA_HOME='$(JAVA_HOME)') — install a JDK 17+ or set JAVA_HOME" >&2; exit 1; fi; \
	  v=`"$$jc" -version 2>&1 | head -n1 | grep -oE '[0-9]+' | head -n1`; \
	  if [ "$$v" -lt 17 ]; then \
	    echo "error: java is JDK $$v — Gradle needs 17+ (archlinux-java set java-25-openjdk, or export JAVA_HOME)" >&2; exit 1; fi

.PHONY: require-sdk
require-sdk:
	@if [ -z "$(ANDROID_HOME)" ]; then \
	  echo 'error: no Android SDK — set sdk.dir in local.properties or export ANDROID_HOME (e.g. ~/Android/Sdk)' >&2; exit 1; fi
	@if [ -z "$(APKSIGNER)" ] || [ ! -x "$(APKSIGNER)" ]; then \
	  echo 'error: apksigner not found under $(ANDROID_HOME)/build-tools/*/' >&2; exit 1; fi

.PHONY: require-device
require-device:
	@n=`timeout --signal=KILL 120 $(ADB) devices | awk 'NR > 1 && $$2 == "device"' | wc -l`; \
	  if [ "$$n" -eq 0 ]; then \
	    echo 'error: no device connected (check `adb devices`)' >&2; exit 1; fi

.PHONY: test
test: require-java ## Run JVM unit tests
	$(GRADLE) :app:testDebugUnitTest

.PHONY: check
check: require-java ## Run JVM unit tests + lint (no device)
	$(GRADLE) :app:testDebugUnitTest :app:lintDebug

.PHONY: debug
debug: require-java ## Build the debug APK
	$(GRADLE) :app:assembleDebug
	@rm -f $(DEBUG_DIR)/moon-vocab-*-debug.apk
	@cp $(DEBUG_DIR)/app-debug.apk $(DEBUG_APK)
	@echo "built $(DEBUG_APK)"

.PHONY: release
release: require-java require-sdk ## Build + sign the minified release APK with the debug keystore
	$(GRADLE) :app:assembleRelease
	@rm -f $(RELEASE_DIR)/moon-vocab-*.apk
	$(APKSIGNER) sign \
	  --ks $(KEYSTORE) --ks-pass pass:android \
	  --ks-key-alias androiddebugkey --key-pass pass:android \
	  --out $(RELEASE_APK) \
	  $(RELEASE_DIR)/app-release-unsigned.apk
	$(APKSIGNER) verify $(RELEASE_APK)
	@echo "built + signed $(RELEASE_APK)"

.PHONY: install
install: require-device debug ## Build the debug APK and adb-install it (override: ADB=/path/adb)
	timeout --signal=KILL 120 $(ADB) install -r $(DEBUG_APK)
