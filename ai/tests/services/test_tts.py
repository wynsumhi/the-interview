import os
import sys

sys.path.append(os.path.join(os.path.dirname(__file__), "../.."))

from app.services.tts_service import text_to_speech

# response = text_to_speech("안녕하세요. 테스트 메시지입니다. 방가방가 우리 친구 햄토리.")
# # 응답을 파일로 저장
# response.stream_to_file("test_output.mp3")
# print("저장 완료")
