from app.services.guardrail_service import mask_sensitive_information


def test_mask_sensitive_information_replaces_standard_pii() -> None:
    text = (
        "연락처는 010-1234-5678 이고 이메일은 test.user@example.com 입니다. "
        "주민등록번호는 900101-1234567이며 계좌번호는 123-45-678901 입니다."
    )

    result = mask_sensitive_information(text)

    assert result.masked_text == (
        "연락처는 [PHONE_1] 이고 이메일은 [EMAIL_1] 입니다. "
        "주민등록번호는 [RRN_1]이며 계좌번호는 [ACCOUNT_1] 입니다."
    )
    assert [entity.entity_type for entity in result.entities] == [
        "PHONE",
        "EMAIL",
        "RRN",
        "ACCOUNT",
    ]


def test_mask_sensitive_information_replaces_card_number() -> None:
    text = "결제 카드는 1234-5678-9012-3456 입니다."

    result = mask_sensitive_information(text)

    assert result.masked_text == "결제 카드는 [CARD_1] 입니다."
    assert [entity.original for entity in result.entities] == ["1234-5678-9012-3456"]


def test_mask_sensitive_information_keeps_non_pii_text_intact() -> None:
    text = "이 문장에는 마스킹할 개인정보가 없습니다."

    result = mask_sensitive_information(text)

    assert result.masked_text == text
    assert result.entities == []
