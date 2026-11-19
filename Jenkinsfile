// Self-contained Android build pipeline for Uncomplicated.
//
// It provisions everything it needs by itself — a Temurin JDK 17 and the Android SDK
// (command-line tools, platform-tools, platform android-36, build-tools 36.0.0) — into a
// per-agent cache under $HOME/.android-ci, then builds with the committed Gradle wrapper
// (which downloads Gradle 8.14.4 itself). Nothing needs to be pre-installed on the agent
// except a POSIX shell with: git, curl, unzip, tar.
//
// Point a Jenkins "Pipeline from SCM" job at this repo; no global tool config required.

pipeline {
    agent any

    options {
        timestamps()
        disableConcurrentBuilds()
        timeout(time: 45, unit: 'MINUTES')
        buildDiscarder(logRotator(numToKeepStr: '15', artifactNumToKeepStr: '10'))
    }

    parameters {
        booleanParam(
            name: 'RELEASE',
            defaultValue: false,
            description: 'Also build a signed release (production) APK. Requires the Jenkins ' +
                         'credentials listed in the "Build Signed Release" stage.'
        )
    }

    environment {
        // Persistent tool cache (survives workspace cleanups → fast rebuilds).
        TOOLS_DIR        = "${HOME}/.android-ci"
        JAVA_HOME        = "${HOME}/.android-ci/jdk-17"
        ANDROID_SDK_ROOT = "${HOME}/.android-ci/android-sdk"
        ANDROID_HOME     = "${HOME}/.android-ci/android-sdk"
        GRADLE_USER_HOME = "${HOME}/.android-ci/gradle"   // caches Gradle + dependencies
        // Pinned versions. cmdline-tools 23.0 — 12.0 (as used by the msgs pipeline) predates
        // android-36 and cannot install it.
        CMDLINE_TOOLS_VER = "16111833"
        ANDROID_API       = "36"
        BUILD_TOOLS_VER   = "36.0.0"
        // Put the provisioned JDK + SDK tools on PATH for all steps.
        PATH = "${HOME}/.android-ci/jdk-17/bin:${HOME}/.android-ci/android-sdk/platform-tools:${PATH}"
    }

    stages {
        stage('Checkout') {
            steps {
                checkout scm
            }
        }

        stage('Provision JDK 17') {
            steps {
                sh label: 'Install Temurin JDK 17', script: '''
                    set -eu
                    mkdir -p "$TOOLS_DIR"
                    if [ ! -x "$JAVA_HOME/bin/javac" ]; then
                        echo "==> Downloading Temurin JDK 17 (latest GA, linux x64)"
                        # For arm64 agents, change x64 -> aarch64 in the URL below.
                        curl -fSL "https://api.adoptium.net/v3/binary/latest/17/ga/linux/x64/jdk/hotspot/normal/eclipse" \
                            -o /tmp/jdk17.tar.gz
                        rm -rf "$JAVA_HOME"; mkdir -p "$JAVA_HOME"
                        tar -xzf /tmp/jdk17.tar.gz -C "$JAVA_HOME" --strip-components=1
                        rm -f /tmp/jdk17.tar.gz
                    fi
                    "$JAVA_HOME/bin/java" -version
                '''
            }
        }

        stage('Provision Android SDK') {
            steps {
                sh label: 'Install Android SDK packages', script: '''
                    set -eu
                    SDKMANAGER="$ANDROID_SDK_ROOT/cmdline-tools/latest/bin/sdkmanager"
                    if [ ! -x "$SDKMANAGER" ]; then
                        echo "==> Downloading Android command-line tools"
                        curl -fSL "https://dl.google.com/android/repository/commandlinetools-linux-${CMDLINE_TOOLS_VER}_latest.zip" \
                            -o /tmp/cmdline-tools.zip
                        rm -rf "$ANDROID_SDK_ROOT/cmdline-tools/latest" "$ANDROID_SDK_ROOT/cmdline-tools/tmp"
                        mkdir -p "$ANDROID_SDK_ROOT/cmdline-tools/tmp"
                        unzip -q /tmp/cmdline-tools.zip -d "$ANDROID_SDK_ROOT/cmdline-tools/tmp"
                        # The zip extracts to a "cmdline-tools" dir; sdkmanager expects it at ".../latest".
                        mv "$ANDROID_SDK_ROOT/cmdline-tools/tmp/cmdline-tools" "$ANDROID_SDK_ROOT/cmdline-tools/latest"
                        rm -rf "$ANDROID_SDK_ROOT/cmdline-tools/tmp" /tmp/cmdline-tools.zip
                    fi

                    echo "==> Accepting licenses + installing SDK packages"
                    yes | "$SDKMANAGER" --sdk_root="$ANDROID_SDK_ROOT" --licenses >/dev/null 2>&1 || true
                    "$SDKMANAGER" --sdk_root="$ANDROID_SDK_ROOT" \
                        "platform-tools" "platforms;android-${ANDROID_API}" "build-tools;${BUILD_TOOLS_VER}"
                '''
            }
        }

        stage('Build & Test') {
            steps {
                sh label: 'Gradle unit tests + debug APK + CLI jar', script: '''
                    set -eu
                    echo "sdk.dir=$ANDROID_SDK_ROOT" > local.properties
                    chmod +x gradlew
                    # :core carries the logic the app and the CLI share, so its tests are
                    # the ones that matter most; :cli:jar is the desktop logger.
                    ./gradlew --no-daemon --stacktrace clean \
                        :core:test :cli:test :app:testDebugUnitTest \
                        assembleDebug :cli:jar
                '''
            }
        }

        stage('Build Signed Release') {
            when { expression { params.RELEASE } }
            // Create these in Jenkins (Manage Jenkins → Credentials):
            //   android-release-keystore  → Secret file   (the .jks/.keystore)
            //   android-keystore-password → Secret text
            //   android-key-alias         → Secret text
            //   android-key-password      → Secret text
            // The app's signingConfig reads these from the environment (see app/build.gradle.kts).
            steps {
                withCredentials([
                    file(credentialsId: 'android-release-keystore', variable: 'RELEASE_STORE_FILE'),
                    string(credentialsId: 'android-keystore-password', variable: 'RELEASE_STORE_PASSWORD'),
                    string(credentialsId: 'android-key-alias', variable: 'RELEASE_KEY_ALIAS'),
                    string(credentialsId: 'android-key-password', variable: 'RELEASE_KEY_PASSWORD'),
                ]) {
                    sh label: 'Gradle assembleRelease + bundleRelease (signed, R8-minified)', script: '''
                        set -eu
                        chmod +x gradlew
                        # Both: the APK is what you sideload onto your own phone, the bundle
                        # is the only thing Play accepts for a new app.
                        ./gradlew --no-daemon --stacktrace assembleRelease bundleRelease

                        # Fail loudly if an unsigned APK slipped through: without a keystore AGP
                        # emits app-release-unsigned.apk instead, which must never ship.
                        APK=app/build/outputs/apk/release/app-release.apk
                        test -f "$APK" || { echo "Signed release APK not found (unsigned build?)"; exit 1; }
                        "$ANDROID_SDK_ROOT/build-tools/${BUILD_TOOLS_VER}/apksigner" verify --verbose "$APK"

                        # The bundle needs checking a different way. An unsigned .aab keeps the
                        # same name -- there is no app-release-unsigned.aab -- so the filename
                        # proves nothing and only the signature does. apksigner cannot read a
                        # bundle; jarsigner can, because an .aab carries a JAR signature.
                        AAB=app/build/outputs/bundle/release/app-release.aab
                        test -f "$AAB" || { echo "Release bundle not found"; exit 1; }
                        # Not -strict: an upload key is self-signed by design, and -strict
                        # reports that as an error. "jar verified" vs "jar is unsigned" is
                        # the distinction that matters, and the self-signed warning is
                        # expected -- Play re-signs with the app signing key on its side.
                        jarsigner -verify "$AAB" | tee /tmp/aab-verify.txt
                        grep -q "^jar verified" /tmp/aab-verify.txt || {
                            echo "Release bundle is NOT signed - Play would reject it"; exit 1; }
                    '''
                }
            }
        }
    }

    post {
        always {
            junit testResults: '**/build/test-results/**/*.xml',
                  allowEmptyResults: true
        }
        success {
            archiveArtifacts artifacts: 'app/build/outputs/apk/**/*.apk', fingerprint: true
            // Only produced by the release stage, so empty on an ordinary build.
            archiveArtifacts artifacts: 'app/build/outputs/bundle/release/*.aab',
                             allowEmptyArchive: true, fingerprint: true
            archiveArtifacts artifacts: 'cli/build/libs/uncomplicated.jar', fingerprint: true
            // Release builds are minified, so keep the R8 mapping — without it a production
            // stack trace is unreadable, and it is not reproducible from a later build.
            archiveArtifacts artifacts: 'app/build/outputs/mapping/release/mapping.txt',
                             allowEmptyArchive: true, fingerprint: true
            sh 'echo "Artifacts:"; find app/build/outputs -name "*.apk" -o -name "*.aab" | xargs -r ls -lh'
        }
    }
}
