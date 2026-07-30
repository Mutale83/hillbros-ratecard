; Inno Setup script for VoxAI (Windows).
; Build:  ISCC.exe /DAppVersion=v0.1.0 packaging\windows\VoxAI.iss
; Produces Output\VoxAI-<version>-windows-setup.exe
;
; Assumes the plugin has been built to build\VoxAI_artefacts\Release\.
; Code signing is not wired here — sign the resulting .exe separately with
; signtool if you have a certificate (see docs/ARCHITECTURE.md, Phase 5).

#ifndef AppVersion
  #define AppVersion "v0.0.0"
#endif

[Setup]
AppName=VoxAI
AppVersion={#AppVersion}
AppPublisher=HillBros Audio
DefaultDirName={autopf}\VoxAI
DisableDirPage=yes
DisableProgramGroupPage=yes
OutputDir=Output
OutputBaseFilename=VoxAI-{#AppVersion}-windows-setup
Compression=lzma2
SolidCompression=yes
ArchitecturesInstallIn64BitMode=x64compatible
ArchitecturesAllowed=x64compatible

[Types]
Name: "full"; Description: "Full installation"

[Components]
Name: "vst3"; Description: "VST3 plug-in"; Types: full; Flags: fixed

[Files]
; VST3 is a folder bundle — recurse it into the shared VST3 directory.
Source: "..\..\build\VoxAI_artefacts\Release\VST3\VoxAI.vst3\*"; \
  DestDir: "{commoncf}\VST3\VoxAI.vst3"; \
  Flags: ignoreversion recursesubdirs createallsubdirs; Components: vst3

[Messages]
WelcomeLabel2=This will install the VoxAI VST3 plug-in on your computer.%n%nAfter installing, rescan plug-ins in your DAW.
