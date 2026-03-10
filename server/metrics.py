"""Prometheus metrics for Grafana dashboards"""

from prometheus_client import CONTENT_TYPE_LATEST, Counter, generate_latest

CLASSIFY_TOTAL = Counter(
    "dockit_classify_total",
    "Total classify API calls",
    ["user_id", "tier"],
)
CLASSIFY_ERRORS = Counter(
    "dockit_classify_errors_total",
    "Classify API errors by reason",
    ["reason"],
)
AUTH_TOTAL = Counter(
    "dockit_auth_total",
    "Auth events",
    ["action"],
)


def metrics_content() -> tuple[bytes, str]:
    return generate_latest(), CONTENT_TYPE_LATEST
