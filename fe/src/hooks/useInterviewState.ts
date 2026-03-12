import { useState } from "react";
import { InterviewStatus } from "@/types/interview";

export const useInterviewState = () => {
  const [state, setState] = useState<InterviewStatus>("CONNECTING");

  const startThinking = () => {
    setState("AI_THINKING");
  };

  const startSpeaking = () => {
    setState("AI_SPEAKING");
  };

  const startRecording = () => {
    setState("USER_RECORDING");
  };

  const startUploading = () => {
    setState("UPLOADING");
  };

  const waitAI = () => {
    setState("WAITING_AI");
  };

  const finishInterview = () => {
    setState("FINISHED");
  };

  return {
    state,
    setState,
    startThinking,
    startSpeaking,
    startRecording,
    startUploading,
    waitAI,
    finishInterview,
  };
};
