import unittest

from som_engine.fatigue import calculate, level_for
from som_engine.models import FatigueRequest


class RoutineRecoveryTests(unittest.TestCase):
    def request(self, count=0, checkin=None):
        return FatigueRequest(
            subjectKey="recovery-test",
            localDate="2026-11-19",
            completedRoutines=count,
            checkin=checkin,
        )

    def test_each_completed_routine_reduces_level_and_matching_display(self):
        checkin = {"mood": "TIRED", "stressors": ["WORK", "FUTURE_WORRY"]}
        baseline = calculate(self.request(checkin=checkin))
        self.assertGreater(baseline.level, 1)
        for count in range(1, 6):
            result = calculate(self.request(count, checkin))
            self.assertEqual(result.level, max(1, baseline.level - count))
            self.assertEqual(result.assetKey, f"sheep_level_{result.level}")
            self.assertEqual(level_for(result.score), result.level)

    def test_routines_alone_do_not_invent_a_level(self):
        result = calculate(self.request(4))
        self.assertEqual(result.status, "INSUFFICIENT_DATA")
        self.assertIsNone(result.level)


if __name__ == "__main__":
    unittest.main()
