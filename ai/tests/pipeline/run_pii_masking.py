from app.services.guardrail_service import format_masking_report, mask_sensitive_information


TEST_TEXT = (
    "연락처는 010-1234-5678 이고 이메일은 test.user@example.com 입니다. "
    "주민등록번호는 900101-1234567이며 계좌번호는 123-45-678901 입니다."
)


if __name__ == "__main__":
    print(format_masking_report(mask_sensitive_information(TEST_TEXT)))
