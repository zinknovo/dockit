#!/usr/bin/env python3
"""生成测试用假传票 PDF，用于本地测试"""

from pathlib import Path

try:
    from reportlab.lib.pagesizes import A4
    from reportlab.pdfgen import canvas
    from reportlab.pdfbase import pdfmetrics
    from reportlab.pdfbase.ttfonts import TTFont
except ImportError:
    print("需要安装: uv add reportlab")
    raise

# 使用系统自带的华文黑体或宋体
FONT = "Helvetica"  # 英文备用，中文需系统字体
FONT_PATHS = [
    "/System/Library/Fonts/PingFang.ttc",  # macOS
    "C:/Windows/Fonts/msyh.ttc",           # Windows (Microsoft YaHei)
    "/usr/share/fonts/truetype/wqy/wqy-microhei.ttc", # Linux (if installed)
]

for p in FONT_PATHS:
    if Path(p).exists():
        try:
            pdfmetrics.registerFont(TTFont("SimHei", p))
            FONT = "SimHei"
            break
        except Exception:
            continue


def gen_summons_pdf(output_path: Path) -> None:
    """生成一份假传票 PDF"""
    c = canvas.Canvas(str(output_path), pagesize=A4)
    c.setFont(FONT, 12)
    y = 750

    lines = [
        "传票",
        "",
        "案号：（2024）京0105民初12345号",
        "案由：合同纠纷",
        "法院：北京市朝阳区人民法院",
        "",
        "原告：张三",
        "被告：北京某某科技有限公司",
        "",
        "开庭时间：2024年4月15日9时30分",
        "开庭地点：第三法庭",
        "审判长：王法官",
        "",
        "举证期限：2024年4月1日前",
        "答辩期限：收到之日起15日内",
        "",
        "特此通知。",
    ]
    for line in lines:
        c.drawString(80, y, line)
        y -= 25

    c.save()
    print(f"已生成: {output_path}")


if __name__ == "__main__":
    import sys
    # 默认输出到项目目录，可手动复制到 Downloads 测试
    default = Path(__file__).resolve().parent.parent / "tests" / "fixtures" / "test_传票.pdf"
    out = Path(sys.argv[1]) if len(sys.argv) > 1 else default
    out.parent.mkdir(parents=True, exist_ok=True)
    gen_summons_pdf(out)
