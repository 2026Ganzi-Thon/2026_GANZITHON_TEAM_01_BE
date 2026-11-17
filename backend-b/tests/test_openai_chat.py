import json
import unittest
from unittest.mock import patch

import httpx

from som_engine import chat
from som_engine.models import ChatRequest


class OpenAIChatTests(unittest.TestCase):
    def setUp(self):
        environment = patch.dict(
            "os.environ", {"SOM_CHAT_MODEL_PROVIDER": "openai", "OPENAI_API_KEY": "test-key"}, clear=True
        )
        environment.start()
        self.addCleanup(environment.stop)

    def request(self, text="면접 결과를 기다리고 있어요.", mode="REPLY"):
        return ChatRequest(
            subjectKey="test-subject",
            localDate="2026-11-17",
            mode=mode,
            userText=text,
            recentMessages=[{"role": "USER", "text": "이전 메시지는 전송하지 않아요."}],
        )

    def mock_provider(self, responder):
        # Keep httpx request construction and response handling active without sending data.
        original_client = httpx.Client
        return patch.object(
            chat.httpx, "Client", side_effect=lambda **kwargs: original_client(
                transport=httpx.MockTransport(responder), **kwargs
            )
        )

    def test_valid_reply_preserves_routine_recommendation(self):
        def respond(request):
            body = json.loads(request.content)
            self.assertEqual(str(request.url), "https://api.openai.com/v1/responses")
            self.assertEqual(request.headers["Authorization"], "Bearer test-key")
            self.assertEqual(body["input"], self.request().userText)
            self.assertFalse(body["store"])
            self.assertTrue(body["text"]["format"]["strict"])
            self.assertFalse(body["text"]["format"]["schema"]["additionalProperties"])
            return httpx.Response(200, json={"status": "completed", "output": [{
                "type": "message", "role": "assistant", "content": [{
                    "type": "output_text",
                    "text": json.dumps({"intent": "SHORT_BREAK", "text": "기다리는 동안 잠깐 숨을 돌려볼까요?"}),
                }],
            }]})

        with self.mock_provider(respond):
            result = chat.reply(self.request())
        self.assertEqual(result.text, "기다리는 동안 잠깐 숨을 돌려볼까요?")
        self.assertEqual(result.cards[0].routineId, "BREATHING")

    def test_http_errors_and_refusals_keep_template_available(self):
        cases = [
            httpx.Response(429),
            httpx.Response(200, json={"status": "completed", "output": [{
                "type": "message", "role": "assistant", "content": [{"type": "refusal", "refusal": "refused"}],
            }]}),
            httpx.Response(200, json={"status": "incomplete"}),
        ]
        for response in cases:
            with self.subTest(response=response), self.mock_provider(lambda _: response):
                self.assertIn("지금 어떤 도움이 필요해요?", chat.reply(self.request()).text)

    def test_greeting_missing_text_and_crisis_do_not_call_provider(self):
        with patch.object(chat.httpx, "Client") as client:
            chat.reply(self.request(mode="GREETING"))
            chat.reply(self.request(text=None))
            crisis = chat.reply(self.request(text="죽고 싶어요."))
        client.assert_not_called()
        self.assertEqual(crisis.riskLevel, "HIGH")


if __name__ == "__main__":
    unittest.main()
