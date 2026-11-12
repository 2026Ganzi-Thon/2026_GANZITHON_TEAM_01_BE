from datetime import date, datetime
from typing import Annotated, Literal

from pydantic import BaseModel, ConfigDict, Field, model_validator

Mood = Literal["CALM", "OKAY", "SO_SO", "ANXIOUS", "TIRED", "OTHER"]
Support = Literal["COMFORT", "VENT", "SHORT_BREAK", "SMALL_ACTION"]
Stressor = Literal["WORK", "FUTURE_WORRY", "COMPARISON", "RELATIONSHIP", "NO_REASON", "OTHER"]
Level = Annotated[int, Field(ge=1, le=4)]
Score = Annotated[int, Field(ge=0, le=100)]
Minutes = Annotated[float, Field(ge=0, le=1440)]


class Payload(BaseModel):
    model_config = ConfigDict(extra="forbid", allow_inf_nan=False)


class Subject(Payload):
    subjectKey: str = Field(min_length=1, max_length=128)


class Dated(Subject):
    localDate: date


class Checkin(Payload):
    mood: Mood | None = None
    stressors: list[Stressor] = Field(default_factory=list, max_length=6)
    supportNeed: Support | None = None


class Signal(Payload):
    source: str
    metric: str
    value: float | None = None
    valueAt: datetime | None = None
    unit: str | None = None
    hourBucket: int | None = Field(default=None, ge=0, le=23)


SignalMetric = Literal[
    "SLEEP_MINUTES", "SCREEN_TIME_MINUTES", "NIGHT_SCREEN_MINUTES",
    "CALENDAR_BUSY_MINUTES", "CALENDAR_AVERAGE_GAP_MINUTES",
]
SignalSource = Literal["SLEEP", "SCREEN_TIME", "CALENDAR_PERSONAL", "CALENDAR_WORK"]
MetricKey = tuple[SignalSource, SignalMetric]
METRIC_SOURCES = {
    "SLEEP_MINUTES": {"SLEEP"},
    "SCREEN_TIME_MINUTES": {"SCREEN_TIME"},
    "NIGHT_SCREEN_MINUTES": {"SCREEN_TIME"},
    "CALENDAR_BUSY_MINUTES": {"CALENDAR_PERSONAL", "CALENDAR_WORK"},
    "CALENDAR_AVERAGE_GAP_MINUTES": {"CALENDAR_PERSONAL", "CALENDAR_WORK"},
}


class SignalChange(Payload):
    source: SignalSource
    metric: SignalMetric
    deltaMinutes: Annotated[int, Field(ge=-1440, le=1440)]

    @model_validator(mode="after")
    def validate_change(self):
        if self.source not in METRIC_SOURCES[self.metric] or self.deltaMinutes == 0:
            raise ValueError("invalid signal change")
        return self


class SignalDay(Payload):
    localDate: date
    signals: list[Signal] = Field(max_length=100)


class Baseline(Payload):
    sleepMinutes: Minutes | None = None
    sleepRecordedDays: int = Field(default=0, ge=0)
    screenTimeMinutes: Minutes | None = None
    screenRecordedDays: int = Field(default=0, ge=0)


class FatigueRequest(Dated):
    timezone: Literal["Asia/Seoul"] = "Asia/Seoul"
    checkin: Checkin | None = None
    signals: list[Signal] = Field(default_factory=list, max_length=100)
    consentedSources: set[str] = Field(default_factory=set, max_length=20)
    baseline: Baseline | None = None
    recentSignals: list[SignalDay] = Field(default_factory=list, max_length=14)

    @model_validator(mode="after")
    def unique_past_dates(self):
        days = {record.localDate for record in self.recentSignals}
        if len(days) != len(self.recentSignals) or any(day >= self.localDate for day in days):
            raise ValueError("history must contain distinct past dates")
        return self


class FatigueResult(Payload):
    score: Score | None = None
    level: Level | None = None
    woolDisplayKg: float | None = None
    status: Literal["CALCULATED", "INSUFFICIENT_DATA"]
    statusText: str | None = None
    subText: str | None = None
    assetKey: str | None = None
    usedSources: list[str] = Field(default_factory=list)
    baselineStatus: Literal["PERSONAL", "DEFAULT", "NOT_USED"] = "NOT_USED"
    changes: list[SignalChange] = Field(default_factory=list)
    algorithmVersion: str
    calculatedAt: datetime


class DailyState(Payload):
    localDate: date
    level: Level | None = None
    score: Score | None = None
    woolDisplayKg: float | None = Field(default=None, gt=0)


class InsightRequest(Subject):
    from_: date = Field(alias="from")
    to: date
    days: list[DailyState] = Field(max_length=30)

    @model_validator(mode="after")
    def validate_period(self):
        if not 0 <= (self.to - self.from_).days < 30:
            raise ValueError("invalid period")
        dates = [day.localDate for day in self.days]
        if len(dates) != len(set(dates)) or any(not self.from_ <= day <= self.to for day in dates):
            raise ValueError("invalid daily records")
        return self


class InsightResult(Payload):
    averageLevel: float | None = None
    recordedDays: int
    changeSummary: str | None = None
    heaviestDate: date | None = None
    heaviestReason: str | None = None
    woolWeightKg: float | None = None


class RoutineRequest(Dated):
    level: Level | None = None
    mood: Mood | None = None
    supportNeed: Support | None = None
    catalogIds: list[str] = Field(max_length=50)
    completedRecently: list[str] = Field(default_factory=list, max_length=100)

    @model_validator(mode="after")
    def unique_catalog(self):
        if len(self.catalogIds) != len(set(self.catalogIds)):
            raise ValueError("duplicate catalog IDs")
        return self


class RoutineResult(Payload):
    routineIds: list[str]
    reason: str


class WeekSummary(Payload):
    averageLevel: float | None = Field(default=None, ge=1, le=4)
    recordedDays: int = Field(ge=0, le=7)


class ChatTurn(Payload):
    role: Literal["USER", "SOMI"]
    text: str | None = Field(default=None, max_length=1500)


class ChatRequest(Dated):
    mode: Literal["GREETING", "REPLY"]
    userText: str | None = Field(default=None, max_length=1500)
    recentMessages: list[ChatTurn] = Field(default_factory=list, max_length=30)
    todayCheckin: Checkin | None = None
    weekSummary: WeekSummary | None = None
    consentedSources: set[str] = Field(default_factory=set, max_length=20)


class ChatCard(Payload):
    type: Literal["WEEK_SUMMARY", "ROUTINE_RECOMMENDATION"]
    routineId: str | None = None
    title: str | None = None
    body: str | None = None


class ChatResult(Payload):
    text: str
    riskLevel: Literal["NONE", "LOW", "HIGH"] = "NONE"
    cards: list[ChatCard] = Field(default_factory=list)
