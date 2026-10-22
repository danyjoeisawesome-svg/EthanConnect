; Inno Setup script for EthanConnect PC. Compile with Inno Setup 6:  iscc installer\EthanConnect.iss
; Output: dist\EthanConnect-Setup-1.0.0.exe  (build dist\EthanConnect.exe first, see README)
#define AppName "EthanConnect PC"
#define AppVersion "1.0.0"

[Setup]
AppId={{6E2B6B0E-6C0D-4D0B-9A58-7D3C5B9E0A11}
AppName={#AppName}
AppVersion={#AppVersion}
AppPublisher=EthanConnect
DefaultDirName={autopf}\EthanConnect
DefaultGroupName=EthanConnect
OutputDir=..\dist
OutputBaseFilename=EthanConnect-Setup-{#AppVersion}
SetupIconFile=..\pc\ethanconnect.ico
UninstallDisplayIcon={app}\EthanConnect.exe
Compression=lzma2
SolidCompression=yes
PrivilegesRequired=admin
ArchitecturesInstallIn64BitMode=x64compatible
DisableProgramGroupPage=yes

[Tasks]
Name: "autostart"; Description: "Start EthanConnect when I sign in to Windows"; Flags: checkedonce
Name: "desktopicon"; Description: "Create a desktop shortcut"; Flags: unchecked

[Files]
Source: "..\dist\EthanConnect.exe"; DestDir: "{app}"; Flags: ignoreversion

[Icons]
Name: "{group}\EthanConnect"; Filename: "{app}\EthanConnect.exe"
Name: "{autodesktop}\EthanConnect"; Filename: "{app}\EthanConnect.exe"; Tasks: desktopicon

[Registry]
Root: HKCU; Subkey: "Software\Microsoft\Windows\CurrentVersion\Run"; ValueType: string; ValueName: "EthanConnect"; \
    ValueData: """{app}\EthanConnect.exe"" --background"; Flags: uninsdeletevalue; Tasks: autostart

[Run]
; Allow the TV / phone to reach the PC on the local network only (TCP 8443), not from the internet.
Filename: "{sys}\netsh.exe"; Parameters: "advfirewall firewall add rule name=""EthanConnect"" dir=in action=allow program=""{app}\EthanConnect.exe"" protocol=TCP localport=8443 profile=private,public remoteip=localsubnet"; Flags: runhidden
Filename: "{app}\EthanConnect.exe"; Description: "Launch EthanConnect"; Flags: nowait postinstall skipifsilent

[UninstallRun]
Filename: "{sys}\netsh.exe"; Parameters: "advfirewall firewall delete rule name=""EthanConnect"""; Flags: runhidden
Filename: "{sys}\taskkill.exe"; Parameters: "/F /IM EthanConnect.exe"; Flags: runhidden
