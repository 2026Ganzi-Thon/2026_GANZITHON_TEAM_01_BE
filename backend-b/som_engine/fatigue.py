from datetime import datetime
from statistics import median
from zoneinfo import ZoneInfo

from . import policy
from .models import METRIC_SOURCES, FatigueRequest, FatigueResult, MetricKey, Signal, SignalChange


def level_for(score: int) -> int:
    return 1 + sum(score >= threshold for threshold in policy.LEVEL_THRESHOLDS)


def daily_observations(signals: list[Signal], consent: set[str]) -> dict[MetricKey, float]:
    observations: dict[MetricKey, float] = {}
    screen_buckets: dict[int, float] = {}
    for signal in signals:
        source, metric, value, hour = signal.source, signal.metric, signal.value, signal.hourBucket
        if source not in consent or source not in METRIC_SOURCES.get(metric, ()) or signal.unit != "min":
            continue
        limit = 60 if hour is not None else len(policy.NIGHT_HOURS) * 60 if metric == "NIGHT_SCREEN_MINUTES" else 1440
        if value is None or not 0 <= value <= limit:
            continue
        if metric == "SCREEN_TIME_MINUTES" and hour is not None:
            screen_buckets[hour] = max(screen_buckets.get(hour, 0), value)
        elif hour is None:
            key = (source, metric)
            observations[key] = max(observations.get(key, 0), value)

    if screen_buckets:
        night = [value for hour, value in screen_buckets.items() if hour in policy.NIGHT_HOURS]
        if night:
            key = ("SCREEN_TIME", "NIGHT_SCREEN_MINUTES")
            observations.setdefault(key, sum(night))
        observations.setdefault(("SCREEN_TIME", "SCREEN_TIME_MINUTES"), sum(screen_buckets.values()))
    return observations


def personal_baselines(request: FatigueRequest) -> dict[MetricKey, float]:
    samples: dict[MetricKey, list[float]] = {}
    for day in request.recentSignals:
        if (request.localDate - day.localDate).days > policy.BASELINE_WINDOW_DAYS:
            continue
        for key, value in daily_observations(day.signals, request.consentedSources).items():
            samples.setdefault(key, []).append(value)
    personal = {key: median(values) for key, values in samples.items() if len(values) >= policy.MIN_BASELINE_DAYS}
    baseline = request.baseline
    if baseline:
        if baseline.sleepMinutes is not None and baseline.sleepRecordedDays >= policy.MIN_BASELINE_DAYS:
            personal.setdefault(("SLEEP", "SLEEP_MINUTES"), baseline.sleepMinutes)
        if baseline.screenTimeMinutes is not None and baseline.screenRecordedDays >= policy.MIN_BASELINE_DAYS:
            personal.setdefault(("SCREEN_TIME", "SCREEN_TIME_MINUTES"), baseline.screenTimeMinutes)
    return personal


def comparison_points(value: float, reference: float, direction: int, scale: int) -> float:
    return min(100, max(0, (value - reference) * direction * 100 / scale))


def calculate(request: FatigueRequest) -> FatigueResult:
    points: dict[str, float] = {}
    sources: list[str] = []
    baseline_modes: set[str] = set()
    changes: list[SignalChange] = []
    checkin = request.checkin
    if checkin and checkin.mood in policy.MOOD_POINTS:
        stressors = set(checkin.stressors) - {"NO_REASON"}
        extra = min(policy.MAX_STRESSOR_POINTS, len(stressors) * policy.STRESSOR_POINTS)
        points["CHECKIN"] = policy.MOOD_POINTS[checkin.mood] + extra
        sources.append("CHECKIN")

    today = daily_observations(request.signals, request.consentedSources)
    personal = personal_baselines(request)
    screen_metric = "NIGHT_SCREEN_MINUTES" if ("SCREEN_TIME", "NIGHT_SCREEN_MINUTES") in today else "SCREEN_TIME_MINUTES"
    screen_default, screen_scale = (
        (policy.DEFAULT_NIGHT_SCREEN_MINUTES, policy.NIGHT_SCREEN_INCREASE_SCALE)
        if screen_metric == "NIGHT_SCREEN_MINUTES"
        else (policy.DEFAULT_SCREEN_MINUTES, policy.SCREEN_INCREASE_SCALE)
    )
    for source, metric, default, direction, scale in (
        ("SLEEP", "SLEEP_MINUTES", policy.DEFAULT_SLEEP_MINUTES, -1, policy.SLEEP_DEFICIT_SCALE),
        ("SCREEN_TIME", screen_metric, screen_default, 1, screen_scale),
    ):
        key = (source, metric)
        if key in today:
            points[source] = comparison_points(today[key], personal.get(key, default), direction, scale)
            sources.append(source)
            baseline_modes.add("PERSONAL" if key in personal else "DEFAULT")

    busy_total = 0
    busy_reference = 0
    uncalibrated_busy = False
    gap_points = []
    for source in sorted(policy.CALENDAR_SOURCES):
        busy_key = (source, "CALENDAR_BUSY_MINUTES")
        gap_key = (source, "CALENDAR_AVERAGE_GAP_MINUTES")
        if busy_key not in today and not (gap_key in today and gap_key in personal):
            continue
        sources.append(source)
        if busy_key in today:
            busy_total += today[busy_key]
            if busy_key in personal:
                busy_reference += personal[busy_key]
            else:
                uncalibrated_busy = True
        if gap_key in today and gap_key in personal:
            gap_points.append(comparison_points(today[gap_key], personal[gap_key], -1, policy.CALENDAR_GAP_DEFICIT_SCALE))
            baseline_modes.add("PERSONAL")
    if any(source in policy.CALENDAR_SOURCES for source in sources):
        if uncalibrated_busy:
            baseline_modes.add("DEFAULT")
        elif any((source, "CALENDAR_BUSY_MINUTES") in today for source in policy.CALENDAR_SOURCES):
            baseline_modes.add("PERSONAL")
        busy_points = comparison_points(
            busy_total, 0 if uncalibrated_busy else busy_reference, 1, policy.CALENDAR_BUSY_SCALE,
        )
        points["CALENDAR"] = max(busy_points, *gap_points) if gap_points else busy_points

    for (source, metric), value in sorted(today.items()):
        key = (source, metric)
        if key in personal:
            delta = round(value - personal[key])
            if delta:
                changes.append(SignalChange(source=source, metric=metric, deltaMinutes=delta))

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
        usedSources=sources, changes=changes, baselineStatus="DEFAULT" if "DEFAULT" in baseline_modes else
        "PERSONAL" if baseline_modes else "NOT_USED", **metadata,
    )
