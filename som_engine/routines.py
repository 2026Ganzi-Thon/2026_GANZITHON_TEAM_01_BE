from .models import RoutineRequest, RoutineResult

BREATHING = "BREATHING"
MOVEMENT = ("SHOULDER_ROLL", "WRIST_ARM_STRETCH", "SEATED_TWIST")


def recommend(request: RoutineRequest) -> RoutineResult:
    if request.level == 4:
        preferred, reason = (BREATHING,), "지금은 부담이 적은 숨 고르기부터 제안해요."
    elif request.supportNeed == "SMALL_ACTION":
        preferred, reason = MOVEMENT, "지금 해볼 수 있는 짧은 동작을 먼저 골랐어요."
    elif request.supportNeed or request.mood in {"ANXIOUS", "TIRED"} or request.level == 3:
        preferred, reason = (BREATHING,), "잠시 쉬어가기 좋은 숨 고르기를 먼저 골랐어요."
    else:
        preferred, reason = (), "최근에 해보지 않은 루틴을 먼저 골랐어요."

    completed = set(request.completedRecently)
    ranked = sorted(request.catalogIds, key=lambda routine: (routine not in preferred, routine in completed))
    if preferred and not set(preferred).intersection(request.catalogIds):
        reason = "현재 선택할 수 있는 루틴을 최근 수행 이력에 따라 정렬했어요."
    if not ranked:
        reason = "현재 선택할 수 있는 루틴이 없어요."
    return RoutineResult(routineIds=ranked, reason=reason)
