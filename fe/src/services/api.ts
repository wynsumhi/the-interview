import axios from "axios";

const api = axios.create({
  baseURL: `${process.env.API_URL || "http://localhost:8080"}/api/v1`,
  timeout: 5000,
});

export default api;
