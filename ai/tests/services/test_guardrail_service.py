from tests.utils.pii_benchmark_utils import mask_sensitive_information


def test_mask_sensitive_information_replaces_standard_pii() -> None:
    text = (
        "연락은 010-1234-5678 또는 test.user@example.com 으로 주세요. "
        "주민번호는 900101-1234567이고, 국민은행 123-45-678901 계좌로 송금하면 됩니다."
    )

    result = mask_sensitive_information(text)

    assert result.masked_text == (
        "연락은 [PHONE_1] 또는 [EMAIL_1] 으로 주세요. "
        "주민번호는 [RRN_1]이고, [ACCOUNT_1] 계좌로 송금하면 됩니다."
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
