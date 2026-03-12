from fastapi import FastAPI

from app.api import api_router

app = FastAPI(
    title="The Interview AI API",
    version="0.1.0",
)
app.include_router(api_router, prefix="/api/v1")


@app.get("/", summary="Root")
def read_root() -> dict[str, str]:
    return {
        "message": "The Interview AI API",
    }
