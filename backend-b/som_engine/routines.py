from .models import Mood, RoutineContext, RoutineItem, RoutineResult, Support

BREATHING = "BREATHING"
MOVEMENT = ("SHOULDER_ROLL", "WRIST_ARM_STRETCH", "SEATED_TWIST")
DEFAULT_DURATIONS = {BREATHING: 60, **dict.fromkeys(MOVEMENT, 30)}


def recommend(context: RoutineContext, mood: Mood | None = None, support: Support | None = None) -> RoutineResult:
    chosen = set(context.preferredRoutineIds)
    completed = set(context.completedRecently)
    durations = {routine: context.catalogDurations.get(routine, DEFAULT_DURATIONS.get(routine))
                 for routine in context.catalogIds}
    if context.level == 4 or support == "SHORT_BREAK":
        preferred = (BREATHING,)
    elif support == "SMALL_ACTION":
        preferred = MOVEMENT
    elif support or mood in {"ANXIOUS", "TIRED"} or context.level == 3:
        preferred = (BREATHING,)
    else:
        preferred = ()

    def time_rank(routine: str) -> int:
        duration = durations[routine]
        if context.availableSeconds is None:
            return 0
        if duration is None:
            return 1
        return 0 if duration <= context.availableSeconds else 2

    def ranking(routine: str) -> tuple:
        state_priority = context.level == 4 and routine not in preferred
        return time_rank(routine), state_priority, routine not in chosen, routine not in preferred, routine in completed

    ranked = sorted(context.catalogIds, key=ranking)
    if not ranked:
        reason = "현재 선택할 수 있는 루틴이 없어요."
    elif time_rank(ranked[0]) > 0:
        reason = "입력한 시간 안에 끝낼 수 있다고 확인된 루틴이 없어요."
    elif ranked[0] in chosen:
        reason = "선택한 회복 방식에 맞는 루틴을 먼저 골랐어요."
    elif ranked[0] in preferred:
        reason = ("지금 해볼 수 있는 짧은 동작을 먼저 골랐어요." if preferred == MOVEMENT
                  else "잠시 쉬어가기 좋은 숨 고르기를 먼저 골랐어요.")
    elif context.availableSeconds is not None:
        reason = "지금 사용할 수 있는 시간에 맞는 루틴을 먼저 골랐어요."
    elif ranked[0] not in completed:
        reason = "최근에 해보지 않은 루틴을 먼저 골랐어요."
    else:
        reason = "현재 선택할 수 있는 루틴이에요."

    return RoutineResult(
        routineIds=ranked, reason=reason,
        items=[RoutineItem(routineId=routine, durationSec=durations[routine]) for routine in ranked],
    )
