import sys
import os
sys.path.append(os.path.join(os.path.dirname(__file__), '../..'))

from app.services.stt_service import speech_to_text

response = speech_to_text("test_output.mp3")
print("변환된 텍스트:", response)