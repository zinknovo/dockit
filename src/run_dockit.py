"""PyInstaller 入口，避免相对导入问题"""
import sys
from dockit.main import main

if __name__ == "__main__":
    sys.exit(main())
