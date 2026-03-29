from app.pipeline.pii_masking import build_guardrail_payload


def test_build_guardrail_payload_masks_stt_text_for_future_llm_step() -> None:
    stt_text = "연락처는 010-9876-5432 이고 이메일은 hello@example.com 입니다."

    payload = build_guardrail_payload(stt_text)

    assert payload.stt_text == stt_text
    assert payload.masked_text == "연락처는 [PHONE_1] 이고 이메일은 [EMAIL_1] 입니다."
    assert [entity.entity_type for entity in payload.masking_result.entities] == ["PHONE", "EMAIL"]
