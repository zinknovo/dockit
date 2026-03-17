# PyInstaller spec for Dockit desktop client
# Usage: pyinstaller dockit.spec
# Output: macOS → dist/Dockit.app，Windows → dist/dockit/

import sys
from PyInstaller.utils.hooks import collect_data_files

block_cipher = None

# CustomTkinter 需要打包 theme/font 等数据文件
ctk_datas = collect_data_files("customtkinter", include_py_files=False)
extra_datas = [('USER_GUIDE_WIN.md', '.')]

a = Analysis(
    ["src/run_dockit.py"],
    pathex=["src"],
    binaries=[],
    datas=ctk_datas + extra_datas,
    hiddenimports=[
        "dockit.config_path",
        "pystray._win32",
        "PIL._tkinter_finder",
        "watchdog.observers",
        "watchdog.observers.polling",
        "yaml",
        "openpyxl",
        "pdfplumber",
        "docx",
    ],
    hookspath=[],
    hooksconfig={},
    runtime_hooks=[],
    excludes=[
        "tkinter.test",
        "unittest",
        "pydoc",
        "email",
        "http",
        "html",
        "xml.dom",
        "xml.sax",
        "distutils",
        "setuptools",
    ],
    win_no_prefer_redirects=False,
    win_private_assemblies=False,
    cipher=block_cipher,
    noarchive=False,
)

pyz = PYZ(a.pure, a.zipped_data, cipher=block_cipher)

exe = EXE(
    pyz,
    a.scripts,
    [],
    exclude_binaries=True,
    name="dockit",
    debug=False,
    bootloader_ignore_signals=False,
    strip=False,
    upx=True,
    console=False,  # GUI 应用，不弹终端
    disable_windowed_traceback=False,
    argv_emulation=False,
    target_arch=None,
    codesign_identity=None,
    entitlements_file=None,
)

coll = COLLECT(
    exe,
    a.binaries,
    a.zipfiles,
    a.datas,
    strip=False,
    upx=True,
    upx_exclude=[],
    name="dockit",
)

# macOS: 打成 .app，必须带 .app 后缀才被系统识别为应用（不弹终端）
if sys.platform == "darwin":
    app = BUNDLE(
        coll,
        name="Dockit.app",
        icon=None,
        bundle_identifier="com.dockit.app",
        info_plist={
            "NSHighResolutionCapable": True,
            "LSUIElement": False,
        },
    )
