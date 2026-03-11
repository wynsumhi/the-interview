import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

from app.pipeline.interview_pipeline import InterviewPipelineResult, run_interview_pipeline


class TestInterviewPipeline(unittest.TestCase):
    def test_run_interview_pipeline_connects_stt_llm_tts(self):
        with tempfile.TemporaryDirectory() as tmp_dir:
            audio_file = Path(tmp_dir) / "sample.wav"
            audio_file.write_bytes(b"fake-audio")

            with (
                patch(
                    "app.pipeline.interview_pipeline.speech_to_text",
                    return_value="요즘 가장 애정을 쏟고 있는 작업 이야기를 들려드릴게요.",
                ) as stt_mock,
                patch(
                    "app.pipeline.interview_pipeline.create_message",
                    return_value="그 작업을 하면서 가장 오래 마음에 남은 장면은 무엇이었나요?",
                ) as llm_mock,
                patch(
                    "app.pipeline.interview_pipeline.text_to_speech",
                    return_value=b"tts-bytes",
                ) as tts_mock,
            ):
                result = run_interview_pipeline(
                    str(audio_file),
                    system_prompt="매거진 인터뷰용 꼬리 질문을 한 개만 생성하세요.",
                    max_tokens=256,
                )

            self.assertEqual(
                result,
                InterviewPipelineResult(
                    stt_text="요즘 가장 애정을 쏟고 있는 작업 이야기를 들려드릴게요.",
                    llm_text="그 작업을 하면서 가장 오래 마음에 남은 장면은 무엇이었나요?",
                    tts_audio=b"tts-bytes",
                ),
            )
            stt_mock.assert_called_once_with(str(audio_file))
            llm_mock.assert_called_once_with(
                messages=[
                    {
                        "role": "user",
                        "content": "요즘 가장 애정을 쏟고 있는 작업 이야기를 들려드릴게요.",
                    }
                ],
                system="매거진 인터뷰용 꼬리 질문을 한 개만 생성하세요.",
                max_tokens=256,
            )
            tts_mock.assert_called_once_with(
                "그 작업을 하면서 가장 오래 마음에 남은 장면은 무엇이었나요?"
            )


if __name__ == "__main__":
    unittest.main()
