import { useRef } from "react";

export default function useRecording(onStop: (blob: Blob) => void) {
  // 컴포넌트가 다시 렌더링되어도 값을 유지하는 변수
  const mediaRecorderRef = useRef<MediaRecorder | null>(null);

  // 조각으로 들어오는 녹음 데이터를 모아 Blob으로 만듦
  const chunksRef = useRef<Blob[]>([]);

  const startRecording = async () => {
    const stream = await navigator.mediaDevices.getUserMedia({
      // 브라우저 마이크 접근 허용
      audio: true,
    });

    // 브라우저 녹음 객체 - MediaRecorder 생성
    const recorder = new MediaRecorder(stream);

    // 녹음 데이터 수집 - 녹음 데이터가 생길 때마다 실행
    mediaRecorderRef.current = recorder;

    recorder.ondataavailable = (event) => {
      chunksRef.current.push(event.data);
    };

    // 녹음이 끝나면 실행
    recorder.onstop = () => {
      // 새로운 녹음 파일 데이터를 생성(Binary Large Object)
      const blob = new Blob(chunksRef.current, {
        type: "audio/webm",
      });
      onStop(blob);

      chunksRef.current = [];
    };

    recorder.start();
  };

  const stopRecording = () => {
    mediaRecorderRef.current?.stop();
  };

  return {
    startRecording,
    stopRecording,
  };
}
