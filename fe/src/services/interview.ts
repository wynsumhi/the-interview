import api from "@/services/api";

export async function sendInterviewAnswer(audio: Blob) {
  const formData = new FormData();
  formData.append("audio", audio);

  const res = await api.post("/interview", formData);

  return res.data;
}
