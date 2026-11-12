from collections import defaultdict
from collections.abc import Iterable

from .models import MetricKey, PeriodSignalChange, SignalChange

METRIC_LABELS = {
    "SLEEP_MINUTES": "수면 시간이",
    "NIGHT_SCREEN_MINUTES": "야간 휴대폰 사용 시간이",
    "SCREEN_TIME_MINUTES": "휴대폰 사용 시간이",
    "CALENDAR_AVERAGE_GAP_MINUTES": "일정 사이 평균 여유 시간이",
    "CALENDAR_BUSY_MINUTES": "일정으로 바빴던 시간이",
}
SHORTER_MEANS_HEAVIER = {"SLEEP_MINUTES", "CALENDAR_AVERAGE_GAP_MINUTES"}


def adverse(change: SignalChange) -> bool:
    return (change.deltaMinutes < 0) == (change.metric in SHORTER_MEANS_HEAVIER)


def best_changes(
    changes: Iterable[SignalChange], consent: set[str], *, adverse_only: bool = False,
) -> list[SignalChange]:
    available = [change for change in changes if change.source in consent and (not adverse_only or adverse(change))]
    night_available = any(change.metric == "NIGHT_SCREEN_MINUTES" for change in available)
    ranked = sorted(
        (change for change in available if not (night_available and change.metric == "SCREEN_TIME_MINUTES")),
        key=lambda change: (not adverse(change), -abs(change.deltaMinutes), change.metric, change.source),
    )
    distinct = {}
    for change in ranked:
        distinct.setdefault(change.metric, change)
    return list(distinct.values())[:2]


def describe(change: SignalChange) -> str:
    label = METRIC_LABELS[change.metric]
    if change.source == "CALENDAR_WORK":
        label = "업무 " + label
    elif change.source == "CALENDAR_PERSONAL":
        label = "개인 " + label
    direction = "짧았어요" if change.deltaMinutes < 0 else "길었어요"
    amount = abs(change.deltaMinutes)
    if isinstance(change, PeriodSignalChange):
        return f"변화가 기록된 {change.changedDays}일의 {label} 평소보다 평균 {amount}분 {direction}."
    return f"그날 {label} 평소보다 {amount}분 {direction}."


def aggregate(changes: Iterable[SignalChange], consent: set[str]) -> list[PeriodSignalChange]:
    differences: dict[MetricKey, list[int]] = defaultdict(list)
    for change in changes:
        if change.source in consent:
            differences[(change.source, change.metric)].append(change.deltaMinutes)

    period = []
    for (source, metric), values in sorted(differences.items()):
        delta = round(sum(values) / len(values))
        if delta:
            period.append(PeriodSignalChange(source=source, metric=metric, deltaMinutes=delta, changedDays=len(values)))
    return period
