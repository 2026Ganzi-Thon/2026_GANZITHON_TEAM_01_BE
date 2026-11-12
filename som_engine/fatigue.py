from datetime import datetime
from zoneinfo import ZoneInfo

from . import policy
from .models import FatigueRequest, FatigueResult, Signal


def level_for(score: int) -> int:
    return 1 + sum(score >= threshold for threshold in policy.LEVEL_THRESHOLDS)


def daily_minutes(signals: list[Signal]) -> float | None:
    totals = [signal.value for signal in signals if signal.hourBucket is None]
    if totals:
        return max(totals)
    buckets: dict[int, float] = {}
    for signal in signals:
        bucket = signal.hourBucket
        buckets[bucket] = max(buckets.get(bucket, 0), signal.value)
    total = sum(buckets.values())
    return total if buckets and total <= 1440 else None


def calculate(request: FatigueRequest) -> FatigueResult:
    points: dict[str, float] = {}
    sources: list[str] = []
    baseline_modes: set[str] = set()
    checkin = request.checkin
    if checkin and checkin.mood in policy.MOOD_POINTS:
        stressors = set(checkin.stressors) - {"NO_REASON"}
        extra = min(policy.MAX_STRESSOR_POINTS, len(stressors) * policy.STRESSOR_POINTS)
        points["CHECKIN"] = policy.MOOD_POINTS[checkin.mood] + extra
        sources.append("CHECKIN")

    signal_groups: dict[tuple[str, str], list[Signal]] = {}
    for signal in request.signals:
        if (signal.source in request.consentedSources and signal.unit == "min"
                and signal.value is not None and 0 <= signal.value <= (60 if signal.hourBucket is not None else 1440)):
            signal_groups.setdefault((signal.source, signal.metric), []).append(signal)

    baseline = request.baseline
    comparisons = (
        ("SLEEP", "SLEEP_MINUTES", baseline.sleepMinutes if baseline else None,
         baseline.sleepRecordedDays if baseline else 0, policy.DEFAULT_SLEEP_MINUTES, -1, policy.SLEEP_DEFICIT_SCALE),
        ("SCREEN_TIME", "SCREEN_TIME_MINUTES", baseline.screenTimeMinutes if baseline else None,
         baseline.screenRecordedDays if baseline else 0, policy.DEFAULT_SCREEN_MINUTES, 1, policy.SCREEN_INCREASE_SCALE),
    )
    for source, metric, personal, days, default, direction, scale in comparisons:
        minutes = daily_minutes(signal_groups.get((source, metric), []))
        if minutes is None:
            continue
        use_personal = personal is not None and days >= policy.MIN_BASELINE_DAYS
        reference = personal if use_personal else default
        points[source] = min(100, max(0, (minutes - reference) * direction * 100 / scale))
        sources.append(source)
        baseline_modes.add("PERSONAL" if use_personal else "DEFAULT")

    calendar_minutes = []
    for source in sorted(policy.CALENDAR_SOURCES):
        minutes = daily_minutes(signal_groups.get((source, "CALENDAR_BUSY_MINUTES"), []))
        if minutes is not None:
            calendar_minutes.append(minutes)
            sources.append(source)
    if calendar_minutes:
        points["CALENDAR"] = min(100, sum(calendar_minutes) * 100 / policy.CALENDAR_BUSY_SCALE)

    metadata = {
        "algorithmVersion": policy.VERSION,
        "calculatedAt": datetime.now(ZoneInfo(request.timezone)),
    }
    if not points:
        return FatigueResult(status="INSUFFICIENT_DATA", **metadata)

    total = sum(value * policy.WEIGHTS[source] for source, value in points.items())
    score = int(total / sum(policy.WEIGHTS[source] for source in points) + 0.5)
    level = level_for(score)
    wool, status_text, sub_text = policy.LEVEL_DISPLAY[level - 1]
    return FatigueResult(
        score=score, level=level, woolDisplayKg=wool, status="CALCULATED",
        statusText=status_text, subText=sub_text, assetKey=f"sheep_level_{level}",
        usedSources=sources, baselineStatus="DEFAULT" if "DEFAULT" in baseline_modes else
        "PERSONAL" if baseline_modes else "NOT_USED", **metadata,
    )
