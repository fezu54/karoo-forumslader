FROM mcr.microsoft.com/devcontainers/java:21-bullseye

# Install additional tools for Android development
RUN apt-get update && apt-get install -y --no-install-recommends \
    wget \
    curl \
    unzip \
    git \
    build-essential \
    libssl-dev \
    libffi-dev \
    python3-dev \
    python3-pip \
    android-tools-adb \
    android-tools-fastboot \
    qemu-system-x86 \
    qemu-utils \
    libvirt-clients \
    libvirt-daemon-system \
    cpu-checker \
    kmod \
    && apt-get clean \
    && rm -rf /var/lib/apt/lists/*

# Create Android SDK directory
RUN mkdir -p /opt/android-sdk && \
    mkdir -p /home/vscode/.android

# Set environment variables
ENV ANDROID_SDK_ROOT=/opt/android-sdk \
    PATH=$PATH:/opt/android-sdk/cmdline-tools/tools/bin:/opt/android-sdk/platform-tools:/opt/android-sdk/tools/bin \
    ANDROID_EMULATOR_USE_SYSTEM_LIBS=1 \
    ANDROID_AVD_HOME=/home/vscode/.android/avd

# Make Android SDK readable by non-root users
RUN chmod -R 755 /opt/android-sdk && \
    chown -R 1000:1000 /opt/android-sdk

# Add KVM device access for vscode user
RUN usermod -a -G kvm vscode
