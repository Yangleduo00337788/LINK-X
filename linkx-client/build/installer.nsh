; LinkX NSIS 自定义脚本
; - 图形安装：完成页后自动启动
; - 静默安装（应用内更新 /S）：安装结束后自动启动
;
; 注意：MUI 配色由 electron-builder 默认处理，勿在 customHeader 重复 !define MUI_BGCOLOR，
; 否则会与内置脚本冲突导致 makensis 失败。

!macro customInstall
  ${If} ${Silent}
    Exec '"$INSTDIR\${APP_EXECUTABLE_FILENAME}"'
  ${EndIf}
!macroend

!macro customFinish
  ${IfNot} ${Silent}
    Exec '"$INSTDIR\${APP_EXECUTABLE_FILENAME}"'
  ${EndIf}
!macroend
