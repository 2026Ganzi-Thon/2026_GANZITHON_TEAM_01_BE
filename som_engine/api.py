from fastapi import FastAPI, Request
from fastapi.exceptions import RequestValidationError
from fastapi.responses import JSONResponse

from . import fatigue, insights, routines, chat
from .models import FatigueRequest, FatigueResult, InsightRequest, InsightResult, RoutineRequest, RoutineResult, ChatRequest, ChatResult

app = FastAPI(title="SOM Backend B", version="0.1.0")


@app.exception_handler(RequestValidationError)
async def invalid_request(_request: Request, _error: RequestValidationError) -> JSONResponse:
    return JSONResponse(status_code=422, content={"error": {"code": "VALIDATION_ERROR"}})


@app.get("/healthz")
def health() -> dict[str, str]:
    return {"status": "ok"}


@app.post("/v1/fatigue/calculate")
def calculate(request: FatigueRequest) -> FatigueResult:
    return fatigue.calculate(request)


@app.post("/v1/insights/summarize")
def summarize(request: InsightRequest) -> InsightResult:
    return insights.summarize(request)


@app.post("/v1/routines/recommend")
def recommend(request: RoutineRequest) -> RoutineResult:
    return routines.recommend(request)


@app.post("/v1/chat/respond")
def respond(request: ChatRequest) -> ChatResult:
    return chat.reply(request)
