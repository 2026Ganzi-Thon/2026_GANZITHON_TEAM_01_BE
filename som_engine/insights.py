from decimal import ROUND_HALF_UP, Decimal

from .models import InsightRequest, InsightResult


def summarize(request: InsightRequest) -> InsightResult:
    valid = sorted((day for day in request.days if day.level is not None), key=lambda day: day.localDate)
    if not valid:
        return InsightResult(recordedDays=0)

    heaviest = max(valid, key=lambda day: (day.level, day.score if day.score is not None else -1, day.localDate))
    average = (Decimal(sum(day.level for day in valid)) / len(valid)).quantize(Decimal("0.1"), ROUND_HALF_UP)
    if len(valid) == 1:
        change = "기록이 한 번 있어 아직 변화를 비교하기 어려워요."
    else:
        difference = valid[-1].level - valid[0].level
        direction = f"{abs(difference)}단계 {'높았어요' if difference > 0 else '낮았어요'}" if difference else "같았어요"
        change = f"최근 기록의 단계가 이 기간 첫 기록과 비교해 {direction}."
    return InsightResult(
        averageLevel=float(average), recordedDays=len(valid), changeSummary=change,
        heaviestDate=heaviest.localDate, heaviestReason=f"기록 중 가장 높은 {heaviest.level}단계였어요.",
        woolWeightKg=valid[-1].woolDisplayKg,
    )
