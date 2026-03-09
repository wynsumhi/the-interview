import sys
import os
sys.path.append(os.path.join(os.path.dirname(__file__), '../..'))

from app.services.tts_service import text_to_speech

response = text_to_speech("안녕하세요. The Interview 테스트입니다.")
# 응답을 파일로 저장
response.stream_to_file("test_output.mp3")
print("저장 완료")