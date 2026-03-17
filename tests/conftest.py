"""pytest fixtures for dockit"""

import sys
from pathlib import Path

import pytest

# 确保项目根目录在 path 中
sys.path.insert(0, str(Path(__file__).resolve().parent.parent))


@pytest.fixture
def config(tmp_path):
    return {
        "watch_dir": str(tmp_path / "dockit_watch"),
        "archive_dir": str(tmp_path / "dockit_archive"),
        "extensions": [".pdf"],
        "filename_template": "{document_type}_{plaintiff}v{defendant}_{date}",
        "folder_template": "{case_number}_{cause_of_action}",
        "max_party_name_length": 10,
    }
