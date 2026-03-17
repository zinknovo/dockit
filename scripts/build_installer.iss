; scripts/build_installer.iss
; Inno Setup 配置文件，用于生成 Windows 一键安装包

[Setup]
AppId={{D0C611-F16-432-88E-D0CK17_APP}}
AppName=Dockit
AppVersion=1.0
AppPublisher=Zink
DefaultDirName={autopf}\Dockit
DefaultGroupName=Dockit
AllowNoIcons=yes
OutputDir=..\dist
OutputBaseFilename=Dockit_Setup_v1.0
Compression=lzma
SolidCompression=yes
WizardStyle=modern

[Languages]
Name: "english"; MessagesFile: "compiler:Default.isl"

[Tasks]
Name: "desktopicon"; Description: "{cm:CreateDesktopIcon}"; GroupDescription: "{cm:AdditionalIcons}"; Flags: unchecked

[Files]
; 打包后的 pyinstaller 目录
Source: "..\dist\dockit\*"; DestDir: "{app}"; Flags: ignoreversion recursesubdirs createallsubdirs

[Icons]
Name: "{group}\Dockit"; Filename: "{app}\dockit.exe"
Name: "{autodesktop}\Dockit"; Filename: "{app}\dockit.exe"; Tasks: desktopicon

[Run]
Filename: "{app}\dockit.exe"; Description: "{cm:LaunchProgram,Dockit}"; Flags: nowait postinstall skipifsilent
