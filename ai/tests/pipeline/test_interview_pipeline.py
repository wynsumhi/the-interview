import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

from app.pipeline.interview_pipeline import (
    INTERVIEW_SYSTEM_PROMPT,
    InterviewPipelineResult,
    run_interview_pipeline,
)


class TestInterviewPipeline(unittest.TestCase):
    def test_run_interview_pipeline_connects_stt_llm_tts(self):
        with tempfile.TemporaryDirectory() as tmp_dir:
            audio_file = Path(tmp_dir) / "sample.wav"
            audio_file.write_bytes(b"fake-audio")

            with (
                patch(
                    "app.pipeline.interview_pipeline.speech_to_text",
                    return_value="candidate answer",
                ) as stt_mock,
                patch(
                    "app.pipeline.interview_pipeline.create_message",
                    return_value="follow-up interview question",
                ) as llm_mock,
                patch(
                    "app.pipeline.interview_pipeline.text_to_speech",
                    return_value=b"tts-bytes",
                ) as tts_mock,
            ):
                result = run_interview_pipeline(
                    str(audio_file),
                    max_tokens=256,
                )

            self.assertEqual(
                result,
                InterviewPipelineResult(
                    stt_text="candidate answer",
                    llm_text="follow-up interview question",
                    tts_audio=b"tts-bytes",
                ),
            )
            stt_mock.assert_called_once_with(str(audio_file))
            llm_mock.assert_called_once_with(
                messages=[
                    {
                        "role": "user",
                        "content": "candidate answer",
                    }
                ],
                system=INTERVIEW_SYSTEM_PROMPT,
                max_tokens=256,
            )
            tts_mock.assert_called_once_with("follow-up interview question")


if __name__ == "__main__":
    unittest.main()
