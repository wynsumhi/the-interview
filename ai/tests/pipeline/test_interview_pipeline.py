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
from app.pipeline.pii_masking import GuardrailPipelinePayload
from app.services.guardrail_service import MaskedEntity, MaskingResult
from app.services.interview_state_service import (
    AudioFeatures,
    InterviewStateDbPayload,
    InterviewStatePromptPayload,
)


class TestInterviewPipeline(unittest.TestCase):
    @staticmethod
    def _build_guardrail_payload(
        *,
        original_text: str,
        masked_text: str,
        entity_type: str,
        original_entity: str,
        duration_seconds: float,
        source: str,
    ) -> GuardrailPipelinePayload:
        return GuardrailPipelinePayload(
            stt_text=original_text,
            masked_text=masked_text,
            masking_result=MaskingResult(
                original_text=original_text,
                masked_text=masked_text,
                entities=[
                    MaskedEntity(
                        entity_type=entity_type,
                        original=original_entity,
                        replacement=f"[{entity_type}_1]",
                        start=0,
                        end=len(original_entity),
                        source=source,
                    )
                ],
                duration_seconds=duration_seconds,
            ),
        )

    @staticmethod
    def _build_state_db() -> InterviewStateDbPayload:
        return InterviewStateDbPayload(
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

    @staticmethod
    def _build_state_prompt(formatted_prompt: str) -> InterviewStatePromptPayload:
        return InterviewStatePromptPayload(
            top_state="calm",
            fused_scores={
                "calm": 0.7,
                "tense": 0.3,
                "anxious": 0.0,
                "low": 0.0,
                "engaged": 0.0,
            },
            formatted_prompt=formatted_prompt,
        )

    def test_run_interview_pipeline_connects_stt_state_llm_tts(self):
        with tempfile.TemporaryDirectory() as tmp_dir:
            audio_file = Path(tmp_dir) / "sample.wav"
            audio_file.write_bytes(b"fake-audio")

            interview_state_db = self._build_state_db()
            interview_state_prompt = self._build_state_prompt("[state prompt]\n- top_state: calm")

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
                    "app.pipeline.interview_pipeline.build_guardrail_payload",
                    return_value=self._build_guardrail_payload(
                        original_text="candidate answer",
                        masked_text="candidate [PHONE_1]",
                        entity_type="PHONE",
                        original_entity="010-1234-5678",
                        duration_seconds=0.0012,
                        source="rule:phone",
                    ),
                ) as guardrail_mock,
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
                    is_first_finished=True,
                    max_tokens=256,
                )

            self.assertEqual(
                result,
                InterviewPipelineResult(
                    stt_text="candidate answer",
                    masked_stt_text="candidate [PHONE_1]",
                    pii_masking_duration_seconds=0.0012,
                    interview_state_db=interview_state_db,
                    interview_state_prompt=interview_state_prompt,
                    llm_text="follow-up interview question",
                    tts_audio=b"tts-bytes",
                ),
            )
            stt_mock.assert_called_once_with(str(audio_file))
            guardrail_mock.assert_called_once_with("candidate answer")
            state_mock.assert_called_once_with(str(audio_file), "candidate answer")
            db_mock.assert_called_once_with(state_mock.return_value)
            prompt_mock.assert_called_once_with(state_mock.return_value)
            llm_mock.assert_called_once()
            llm_kwargs = llm_mock.call_args.kwargs
            self.assertEqual(llm_kwargs["system"], INTERVIEW_SYSTEM_PROMPT)
            self.assertEqual(llm_kwargs["max_tokens"], 256)
            self.assertEqual(llm_kwargs["messages"][0]["role"], "user")
            self.assertIn("[state prompt]\n- top_state: calm", llm_kwargs["messages"][0]["content"])
            self.assertIn("candidate [PHONE_1]", llm_kwargs["messages"][0]["content"])
            tts_mock.assert_called_once_with("follow-up interview question")

    def test_run_interview_pipeline_from_stt_text_skips_stt_and_runs_remaining_steps(self):
        with tempfile.TemporaryDirectory() as tmp_dir:
            audio_file = Path(tmp_dir) / "sample.wav"
            audio_file.write_bytes(b"fake-audio")

            interview_state_db = self._build_state_db()
            interview_state_prompt = self._build_state_prompt("[state prompt]\n- top_state: calm")

            with (
                patch("app.pipeline.interview_pipeline.speech_to_text") as stt_mock,
                patch(
                    "app.pipeline.interview_pipeline.analyze_interview_state",
                    return_value=object(),
                ) as state_mock,
                patch(
                    "app.pipeline.interview_pipeline.build_guardrail_payload",
                    return_value=self._build_guardrail_payload(
                        original_text="external stt answer",
                        masked_text="external [EMAIL_1]",
                        entity_type="EMAIL",
                        original_entity="hello@example.com",
                        duration_seconds=0.0008,
                        source="rule:email",
                    ),
                ) as guardrail_mock,
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
                    is_first_finished=True,
                    max_tokens=128,
                )

            self.assertEqual(
                result,
                InterviewPipelineResult(
                    stt_text="external stt answer",
                    masked_stt_text="external [EMAIL_1]",
                    pii_masking_duration_seconds=0.0008,
                    interview_state_db=interview_state_db,
                    interview_state_prompt=interview_state_prompt,
                    llm_text="follow-up interview question",
                    tts_audio=b"tts-bytes",
                ),
            )
            stt_mock.assert_not_called()
            guardrail_mock.assert_called_once_with("external stt answer")
            state_mock.assert_called_once_with(str(audio_file), "external stt answer")
            db_mock.assert_called_once_with(state_mock.return_value)
            prompt_mock.assert_called_once_with(state_mock.return_value)
            llm_mock.assert_called_once()
            llm_kwargs = llm_mock.call_args.kwargs
            self.assertEqual(llm_kwargs["system"], INTERVIEW_SYSTEM_PROMPT)
            self.assertEqual(llm_kwargs["max_tokens"], 128)
            self.assertEqual(llm_kwargs["messages"][0]["role"], "user")
            self.assertIn("[state prompt]\n- top_state: calm", llm_kwargs["messages"][0]["content"])
            self.assertIn("external [EMAIL_1]", llm_kwargs["messages"][0]["content"])
            tts_mock.assert_called_once_with("follow-up interview question")

    def test_run_interview_pipeline_from_stt_text_includes_rag_context_for_regular_interview(self):
        with tempfile.TemporaryDirectory() as tmp_dir:
            audio_file = Path(tmp_dir) / "sample.wav"
            audio_file.write_bytes(b"fake-audio")

            interview_state_db = self._build_state_db()
            interview_state_prompt = self._build_state_prompt("[state prompt]")

            with (
                patch(
                    "app.pipeline.interview_pipeline.analyze_interview_state",
                    return_value=object(),
                ),
                patch(
                    "app.pipeline.interview_pipeline.build_guardrail_payload",
                    return_value=self._build_guardrail_payload(
                        original_text="external stt answer",
                        masked_text="external [RRN_1]",
                        entity_type="RRN",
                        original_entity="900101-1234567",
                        duration_seconds=0.001,
                        source="rule:resident-registration-number",
                    ),
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
                    is_first_finished=True,
                    user_id="user-123",
                    previous_question="current question",
                    max_tokens=128,
                )

            rag_mock.assert_called_once_with(
                user_id="user-123",
                current_question="current question",
                current_answer="external [RRN_1]",
            )
            format_mock.assert_called_once()
            llm_mock.assert_called_once()
            llm_kwargs = llm_mock.call_args.kwargs
            self.assertEqual(llm_kwargs["system"], INTERVIEW_SYSTEM_PROMPT)
            self.assertEqual(llm_kwargs["max_tokens"], 128)
            self.assertEqual(llm_kwargs["messages"][0]["role"], "user")
            self.assertIn("[state prompt]", llm_kwargs["messages"][0]["content"])
            self.assertIn("rag context block", llm_kwargs["messages"][0]["content"])
            self.assertIn("external [RRN_1]", llm_kwargs["messages"][0]["content"])


if __name__ == "__main__":
    unittest.main()
