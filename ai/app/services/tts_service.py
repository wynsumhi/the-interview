from openai import OpenAI
import os

client = OpenAI(
    api_key=os.getenv("GMS_KEY"),
    base_url= "https://gms.ssafy.io/gmsapi/api.openai.com/v1",
)

def text_to_speech(text: str):
    response = client.audio.speech.create(
        model="gpt-4o-mini-tts",
        input=text,
        voice="alloy"
    )
    return response