---
kind: external_dependency
name: Electron 31 桌面运行时
slug: electron
category: external_dependency
category_hints:
    - vendor_identity
scope:
    - '**'
---

前端基于 Electron 31 构建跨平台桌面应用，主进程负责窗口管理、IPC 和多窗口，Preload 通过 contextBridge 暴露有限 API（如 secureStorage、openMoments、openNoteEditor）给渲染进程。打包使用 electron-builder，Windows 输出 NSIS，macOS 输出 DMG，Linux 输出 AppImage。渲染进程禁用 nodeIntegration，Token 和锁屏 PIN 通过 OS 级 safeStorage 加密落盘。