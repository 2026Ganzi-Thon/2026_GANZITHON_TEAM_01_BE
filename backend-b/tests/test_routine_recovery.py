import unittest

from som_engine.fatigue import calculate, level_for
from som_engine.models import FatigueRequest


class RoutineRecoveryTests(unittest.TestCase):
    def request(self, count=0, checkin=None, all_completed=False):
        return FatigueRequest(
            subjectKey="recovery-test",
            localDate="2026-11-19",
            completedRoutines=count,
            allRoutinesCompleted=all_completed,
            checkin=checkin,
        )

    def test_partial_completion_keeps_level_and_full_completion_reduces_once(self):
        checkin = {"mood": "TIRED", "stressors": ["WORK"]}
        baseline = calculate(self.request(checkin=checkin))
        for count in range(1, 4):
            self.assertEqual(calculate(self.request(count, checkin)).level, baseline.level)
        result = calculate(self.request(4, checkin, all_completed=True))
        self.assertEqual(result.level, max(1, baseline.level - 1))
        self.assertEqual(result.assetKey, f"sheep_level_{result.level}")
        self.assertEqual(level_for(result.score), result.level)

    def test_minimum_level_is_one(self):
        result = calculate(self.request(4, {"mood": "CALM"}, all_completed=True))
        self.assertEqual(result.level, 1)

    def test_routines_alone_do_not_invent_a_level(self):
        result = calculate(self.request(4, all_completed=True))
        self.assertEqual(result.status, "INSUFFICIENT_DATA")
        self.assertIsNone(result.level)


if __name__ == "__main__":
    unittest.main()
