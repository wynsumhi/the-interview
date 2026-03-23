import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

from app.pipeline.interview_pipeline import (
    INTERVIEW_SYSTEM_PROMPT,
    InterviewPipelineResult,
    run_interview_pipeline,
    run_interview_pipeline_from_stt_text,
)
from app.services.interview_state_service import (
    AudioFeatures,
    InterviewStateDbPayload,
    InterviewStatePromptPayload,
)


class TestInterviewPipeline(unittest.TestCase):
    def test_run_interview_pipeline_connects_stt_state_llm_tts(self):
        with tempfile.TemporaryDirectory() as tmp_dir:
            audio_file = Path(tmp_dir) / "sample.wav"
            audio_file.write_bytes(b"fake-audio")

            interview_state_db = InterviewStateDbPayload(
                answer_id=123,
                top_state="calm",
                fused_scores={"calm": 0.7, "tense": 0.3},
                audio_features=AudioFeatures(
                    duration_sec=1.0,
                    rms_mean=0.02,
                    rms_std=0.01,
                    zcr_mean=0.1,
                    silence_ratio=0.2,
                    voiced_ratio=0.8,
                    speech_rate_proxy=0.8,
                    pitch_mean_hz=120.0,
                    pitch_std_hz=10.0,
                ),
            )
            interview_state_prompt = InterviewStatePromptPayload(
                top_state="calm",
                fused_scores={
                    "calm": 0.7,
                    "tense": 0.3,
                    "anxious": 0.0,
                    "low": 0.0,
                    "engaged": 0.0,
                },
                formatted_prompt="[인터뷰 상태 참고]\n- top_state: calm",
            )

            with (
                patch(
                    "app.pipeline.interview_pipeline.speech_to_text",
                    return_value="candidate answer",
                ) as stt_mock,
                patch(
                    "app.pipeline.interview_pipeline.analyze_interview_state",
                    return_value=object(),
                ) as state_mock,
                patch(
                    "app.pipeline.interview_pipeline.build_interview_state_db_payload",
                    return_value=interview_state_db,
                ) as db_mock,
                patch(
                    "app.pipeline.interview_pipeline.build_interview_state_prompt_payload",
                    return_value=interview_state_prompt,
                ) as prompt_mock,
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
                    answer_id=123,
                    is_first_finished=True,
                    max_tokens=256,
                )

            self.assertEqual(
                result,
                InterviewPipelineResult(
                    stt_text="candidate answer",
                    interview_state_db=interview_state_db,
                    interview_state_prompt=interview_state_prompt,
                    llm_text="follow-up interview question",
                    tts_audio=b"tts-bytes",
                ),
            )
            stt_mock.assert_called_once_with(str(audio_file))
            state_mock.assert_called_once_with(str(audio_file), "candidate answer")
            db_mock.assert_called_once_with(123, state_mock.return_value)
            prompt_mock.assert_called_once_with(state_mock.return_value)
            llm_mock.assert_called_once_with(
                messages=[
                    {
                        "role": "user",
                        "content": (
                            "[인터뷰 상태 참고]\n- top_state: calm\n\n"
                            "[사용자 답변]\ncandidate answer"
                        ),
                    }
                ],
                system=INTERVIEW_SYSTEM_PROMPT,
                max_tokens=256,
            )
            tts_mock.assert_called_once_with("follow-up interview question")

    def test_run_interview_pipeline_from_stt_text_skips_stt_and_runs_remaining_steps(self):
        with tempfile.TemporaryDirectory() as tmp_dir:
            audio_file = Path(tmp_dir) / "sample.wav"
            audio_file.write_bytes(b"fake-audio")

            interview_state_db = InterviewStateDbPayload(
                answer_id=321,
                top_state="calm",
                fused_scores={"calm": 0.7, "tense": 0.3},
                audio_features=AudioFeatures(
                    duration_sec=1.0,
                    rms_mean=0.02,
                    rms_std=0.01,
                    zcr_mean=0.1,
                    silence_ratio=0.2,
                    voiced_ratio=0.8,
                    speech_rate_proxy=0.8,
                    pitch_mean_hz=120.0,
                    pitch_std_hz=10.0,
                ),
            )
            interview_state_prompt = InterviewStatePromptPayload(
                top_state="calm",
                fused_scores={
                    "calm": 0.7,
                    "tense": 0.3,
                    "anxious": 0.0,
                    "low": 0.0,
                    "engaged": 0.0,
                },
                formatted_prompt="[?명꽣酉??곹깭 李멸퀬]\n- top_state: calm",
            )

            with (
                patch("app.pipeline.interview_pipeline.speech_to_text") as stt_mock,
                patch(
                    "app.pipeline.interview_pipeline.analyze_interview_state",
                    return_value=object(),
                ) as state_mock,
                patch(
                    "app.pipeline.interview_pipeline.build_interview_state_db_payload",
                    return_value=interview_state_db,
                ) as db_mock,
                patch(
                    "app.pipeline.interview_pipeline.build_interview_state_prompt_payload",
                    return_value=interview_state_prompt,
                ) as prompt_mock,
                patch(
                    "app.pipeline.interview_pipeline.create_message",
                    return_value="follow-up interview question",
                ) as llm_mock,
                patch(
                    "app.pipeline.interview_pipeline.text_to_speech",
                    return_value=b"tts-bytes",
                ) as tts_mock,
            ):
                result = run_interview_pipeline_from_stt_text(
                    str(audio_file),
                    stt_text="external stt answer",
                    answer_id=321,
                    is_first_finished=True,
                    max_tokens=128,
                )

            self.assertEqual(
                result,
                InterviewPipelineResult(
                    stt_text="external stt answer",
                    interview_state_db=interview_state_db,
                    interview_state_prompt=interview_state_prompt,
                    llm_text="follow-up interview question",
                    tts_audio=b"tts-bytes",
                ),
            )
            stt_mock.assert_not_called()
            state_mock.assert_called_once_with(str(audio_file), "external stt answer")
            db_mock.assert_called_once_with(321, state_mock.return_value)
            prompt_mock.assert_called_once_with(state_mock.return_value)
            llm_mock.assert_called_once_with(
                messages=[
                    {
                        "role": "user",
                        "content": (
                            "[?명꽣酉??곹깭 李멸퀬]\n- top_state: calm\n\n"
                            "[사용자 답변]\nexternal stt answer"
                        ),
                    }
                ],
                system=INTERVIEW_SYSTEM_PROMPT,
                max_tokens=128,
            )
            tts_mock.assert_called_once_with("follow-up interview question")

    def test_run_interview_pipeline_from_stt_text_includes_rag_context_for_regular_interview(self):
        with tempfile.TemporaryDirectory() as tmp_dir:
            audio_file = Path(tmp_dir) / "sample.wav"
            audio_file.write_bytes(b"fake-audio")

            interview_state_db = InterviewStateDbPayload(
                answer_id=321,
                top_state="calm",
                fused_scores={"calm": 0.7, "tense": 0.3},
                audio_features=AudioFeatures(
                    duration_sec=1.0,
                    rms_mean=0.02,
                    rms_std=0.01,
                    zcr_mean=0.1,
                    silence_ratio=0.2,
                    voiced_ratio=0.8,
                    speech_rate_proxy=0.8,
                    pitch_mean_hz=120.0,
                    pitch_std_hz=10.0,
                ),
            )
            interview_state_prompt = InterviewStatePromptPayload(
                top_state="calm",
                fused_scores={
                    "calm": 0.7,
                    "tense": 0.3,
                    "anxious": 0.0,
                    "low": 0.0,
                    "engaged": 0.0,
                },
                formatted_prompt="[state prompt]",
            )

            with (
                patch(
                    "app.pipeline.interview_pipeline.analyze_interview_state",
                    return_value=object(),
                ),
                patch(
                    "app.pipeline.interview_pipeline.build_interview_state_db_payload",
                    return_value=interview_state_db,
                ),
                patch(
                    "app.pipeline.interview_pipeline.build_interview_state_prompt_payload",
                    return_value=interview_state_prompt,
                ),
                patch(
                    "app.pipeline.interview_pipeline.retrieve_similar_interview_history",
                    return_value=[object()],
                ) as rag_mock,
                patch(
                    "app.pipeline.interview_pipeline.format_retrieved_turns_for_prompt",
                    return_value="rag context block",
                ) as format_mock,
                patch(
                    "app.pipeline.interview_pipeline.create_message",
                    return_value="follow-up interview question",
                ) as llm_mock,
                patch(
                    "app.pipeline.interview_pipeline.text_to_speech",
                    return_value=b"tts-bytes",
                ),
            ):
                run_interview_pipeline_from_stt_text(
                    str(audio_file),
                    stt_text="external stt answer",
                    answer_id=321,
                    is_first_finished=True,
                    user_id="user-123",
                    previous_question="current question",
                    max_tokens=128,
                )

            rag_mock.assert_called_once_with(
                user_id="user-123",
                current_question="current question",
                current_answer="external stt answer",
            )
            format_mock.assert_called_once()
            llm_mock.assert_called_once_with(
                messages=[
                    {
                        "role": "user",
                        "content": (
                            "[state prompt]\n\n"
                            "[유사한 과거 인터뷰 참고]\nrag context block\n\n"
                            "[사용자 답변]\nexternal stt answer"
                        ),
                    }
                ],
                system=INTERVIEW_SYSTEM_PROMPT,
                max_tokens=128,
            )


if __name__ == "__main__":
    unittest.main()
