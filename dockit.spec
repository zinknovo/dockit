# PyInstaller spec for Dockit desktop client
# Usage: pyinstaller dockit.spec
# Output: dist/dockit/ (macOS/Linux) or dist/dockit/ (Windows)

from PyInstaller.utils.hooks import collect_data_files

block_cipher = None

# CustomTkinter 需要打包 theme/font 等数据文件
ctk_datas = collect_data_files("customtkinter", include_py_files=False)

a = Analysis(
    ["src/dockit/__main__.py"],
    pathex=["src"],
    binaries=[],
    datas=ctk_datas,
    hiddenimports=[
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
    excludes=[],
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
    console=True,  # 保留控制台以便 --confirm 模式输出
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
