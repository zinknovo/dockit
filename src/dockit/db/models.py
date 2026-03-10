# models.py - 数据模型定义
"""法律文书结构化信息的数据模型"""

from dataclasses import dataclass, field


@dataclass
class DocumentInfo:
    """LLM 提取出的法律文书结构化信息"""

    document_type: str  # 传票/民事判决书/民事裁定书/举证通知书/...
    case_number: str | None  # 案号
    court_name: str | None  # 法院名称
    plaintiff: str | None  # 主要原告（第一个）
    defendant: str | None  # 主要被告（第一个）
    document_date: str | None  # YYYY-MM-DD
    cause_of_action: str | None  # 案由
    hearing_time: str | None  # 开庭时间
    hearing_location: str | None  # 开庭地点
    evidence_deadline: str | None  # 举证期限
    defense_deadline: str | None  # 答辩期限
    appeal_deadline: str | None  # 上诉期限
    judge: str | None  # 审判长
    panel_members: list[str] | None  # 合议庭成员
    judgment_result: str | None  # 判决/裁定主文摘要
    judgment_amount: float | None  # 判决金额（元）
    raw_json: dict = field(default_factory=dict)  # LLM 返回的原始 JSON
