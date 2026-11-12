from datetime import timedelta
from decimal import ROUND_HALF_UP, Decimal

from .evidence import aggregate, best_changes, describe
from .models import DailyLevel, InsightRequest, InsightResult
from .policy import LEVEL_DISPLAY


def summarize(request: InsightRequest) -> InsightResult:
    valid = sorted((day for day in request.days if day.level is not None), key=lambda day: day.localDate)
    by_date = {day.localDate: day.level for day in valid}
    dates = [request.from_ + timedelta(days=offset) for offset in range((request.to - request.from_).days + 1)]
    series = [DailyLevel(localDate=day, level=by_date.get(day)) for day in dates]
    if not valid:
        return InsightResult(recordedDays=0, dailyLevels=series)

    heaviest = max(valid, key=lambda day: (day.level, day.score if day.score is not None else -1, day.localDate))
    average = (Decimal(sum(day.level for day in valid)) / len(valid)).quantize(Decimal("0.1"), ROUND_HALF_UP)
    if len(valid) == 1:
        change = "기록이 한 번 있어 아직 변화를 비교하기 어려워요."
    else:
        difference = valid[-1].level - valid[0].level
        direction = f"{abs(difference)}단계 {'높았어요' if difference > 0 else '낮았어요'}" if difference else "같았어요"
        change = f"최근 기록의 단계가 이 기간 첫 기록과 비교해 {direction}."
    reasons = best_changes(heaviest.changes, request.consentedSources, adverse_only=True)
    reason = describe(reasons[0]) if reasons else f"기록 중 가장 높은 {heaviest.level}단계였어요."
    _, status_text, sub_text = LEVEL_DISPLAY[valid[-1].level - 1]
    return InsightResult(
        averageLevel=float(average), recordedDays=len(valid), changeSummary=change,
        heaviestDate=heaviest.localDate, heaviestReason=reason,
        woolWeightKg=valid[-1].woolDisplayKg, statusText=status_text, subText=sub_text,
        dailyLevels=series,
        changes=aggregate((change for day in valid for change in day.changes), request.consentedSources),
    )
