"""
计费入口：测试阶段全部免费，后续在此接入真实计费逻辑。

扩展示例：
- 接入 Stripe/支付宝等支付
- 按 TIER_LIMITS 做额度校验
- 订阅到期校验
"""

from db import TIER_LIMITS, get_monthly_usage, is_subscription_active


def can_use_classify(user: dict) -> tuple[bool, str | None]:
    """
    校验用户是否可使用 classify。
    返回 (True, None) 表示可用；(False, "原因") 表示不可用。

    测试阶段：恒返回可用。
    正式收费：在此做额度、订阅状态校验。
    """
    # 测试阶段：全部免费
    return True, None

    # 正式收费时启用：
    # if not is_subscription_active(user):
    #     return False, "订阅已过期，请续费后使用"
    # limit = TIER_LIMITS.get(user["tier"], 50)
    # used = get_monthly_usage(user["id"])
    # if used >= limit:
    #     return False, f"本月额度已用尽 ({limit} 次)，请升级付费版"
    # return True, None


def get_user_limit(user: dict) -> int:
    """用户当月额度上限"""
    return TIER_LIMITS.get(user.get("tier", "free"), 999999)
