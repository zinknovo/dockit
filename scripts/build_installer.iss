; scripts/build_installer.iss
; Inno Setup 配置文件，用于生成 Windows 一键安装包
; 
; 准备工作:
; 1. 在 Windows 电脑上安装 Inno Setup 6+
; 2. 先运行: uv run pyinstaller dockit.spec
; 3. 用 Inno Setup Compiler 打开此文件，按 F9 编译生成 Setup.exe

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
Name: "chinesesimplified"; MessagesFile: "compiler:Languages\ChineseSimplified.isl"

[Tasks]
Name: "desktopicon"; Description: "{cm:CreateDesktopIcon}"; GroupDescription: "{cm:AdditionalIcons}"; Flags: unchecked

[Files]
; 打包后的 pyinstaller 目录
Source: "..\dist\dockit\*"; DestDir: "{app}"; Flags: ignoreversion recursesubdirs createallsubdirs
; 如果需要包含额外的文档或库，可以在这里添加

[Icons]
Name: "{group}\Dockit"; Filename: "{app}\dockit.exe"
Name: "{autodesktop}\Dockit"; Filename: "{app}\dockit.exe"; Tasks: desktopicon

[Run]
Filename: "{app}\dockit.exe"; Description: "{cm:LaunchProgram,Dockit}"; Flags: nowait postinstall skipifsilent
