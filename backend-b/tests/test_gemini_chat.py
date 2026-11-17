import json
import unittest
from unittest.mock import patch

import httpx

from som_engine import chat
from som_engine.models import ChatRequest


class GeminiChatTests(unittest.TestCase):
    def setUp(self):
        environment = patch.dict("os.environ", {
            "SOM_CHAT_MODEL_PROVIDER": "gemini", "GEMINI_API_KEY": "gemini-test-key",
            "OPENAI_API_KEY": "openai-test-key",
        }, clear=True)
        environment.start()
        self.addCleanup(environment.stop)
        self.request = ChatRequest(
            subjectKey="not-sent",
            localDate="2026-11-17",
            mode="REPLY",
            userText="오늘은 잠깐 쉬고 싶어요.",
            recentMessages=[{"role": "USER", "text": "이전 대화는 외부에 보내지 않아요."}],
        )

    def run_reply(self, responder):
        original_client = httpx.Client
        with patch.object(chat.httpx, "Client", side_effect=lambda **kwargs: original_client(
            transport=httpx.MockTransport(responder), **kwargs,
        )):
            return chat.reply(self.request)

    def test_selected_provider_receives_only_current_message_and_supplies_routine_intent(self):
        def respond(request):
            self.assertEqual(str(request.url),
                             "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash-lite:generateContent")
            self.assertEqual(request.headers["x-goog-api-key"], "gemini-test-key")
            self.assertNotIn("Authorization", request.headers)
            body = json.loads(request.content)
            self.assertEqual(body["contents"], [{"role": "user", "parts": [{"text": self.request.userText}]}])
            self.assertEqual(body["generationConfig"]["responseMimeType"], "application/json")
            self.assertEqual(body["generationConfig"]["responseJsonSchema"]["required"], ["intent", "text"])
            return httpx.Response(200, json={"candidates": [{
                "finishReason": "STOP",
                "content": {"parts": [
                    {"thought": True, "text": "not part of response"},
                    {"text": json.dumps({"intent": "SHORT_BREAK", "text": "잠깐 숨을 고르며 쉬어볼까요?"})},
                ]},
            }]})

        result = self.run_reply(respond)
        self.assertEqual(result.text, "잠깐 숨을 고르며 쉬어볼까요?")
        self.assertEqual(result.cards[0].routineId, "BREATHING")

    def test_blocked_incomplete_or_malformed_replies_fall_back(self):
        responses = [
            httpx.Response(429),
            httpx.Response(200, json={"promptFeedback": {"blockReason": "SAFETY"}}),
            httpx.Response(200, json={"candidates": [{"finishReason": "MAX_TOKENS"}]}),
            httpx.Response(200, json={"candidates": [
                {"finishReason": "STOP", "content": {"parts": [{"text": "invalid JSON"}]}},
            ]}),
        ]
        for response in responses:
            with self.subTest(response=response):
                self.assertIn("지금 어떤 도움이 필요해요?", self.run_reply(lambda _: response).text)

    def test_missing_key_does_not_call_either_provider(self):
        with patch.dict("os.environ", {"GEMINI_API_KEY": ""}), patch.object(chat.httpx, "Client") as client:
            self.assertIn("지금 어떤 도움이 필요해요?", chat.reply(self.request).text)
        client.assert_not_called()
