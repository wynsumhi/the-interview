from tests.utils.pii_benchmark_utils import TEST_TEXT, format_masking_report, mask_sensitive_information


if __name__ == "__main__":
    print(format_masking_report(mask_sensitive_information(TEST_TEXT)))
