from dataclasses import dataclass

from fastapi import Depends, HTTPException, status
from fastapi.security import HTTPAuthorizationCredentials, HTTPBearer

from app.core import settings

# Authorization 헤더의 Bearer 토큰을 읽되, 누락 시 직접 401 응답을 만들기 위해 auto_error를 끈다.
bearer_scheme = HTTPBearer(auto_error=False)


@dataclass(frozen=True)
class AuthContext:
    token: str | None
    scopes: frozenset[str]


def _parse_token_scope_map(raw_value: str) -> dict[str, frozenset[str]]:
    """환경 변수 문자열을 `토큰 -> scope 집합` 형태로 변환한다."""

    token_scope_map: dict[str, frozenset[str]] = {}

    for item in raw_value.split(";"):
        entry = item.strip()
        if not entry:
            continue

        token, separator, raw_scopes = entry.partition("|")
        normalized_token = token.strip()
        if not normalized_token:
            continue

        scopes = frozenset(
            scope.strip()
            for scope in raw_scopes.split(",")
            if separator and scope.strip()
        )
        token_scope_map[normalized_token] = scopes

    return token_scope_map


def _is_auth_enabled() -> bool:
    """현재 실행 환경에서 인증을 강제할지 여부를 반환한다."""

    return settings.AUTH_ENABLED


def _get_token_scope_map() -> dict[str, frozenset[str]]:
    """AUTH_TOKENS 설정값을 매 요청 시점의 권한 맵으로 해석한다."""

    return _parse_token_scope_map(settings.AUTH_TOKENS)


def _build_unauthorized_exception(detail: str) -> HTTPException:
    """401 응답과 Bearer 인증 헤더를 함께 내려준다."""

    return HTTPException(
        status_code=status.HTTP_401_UNAUTHORIZED,
        detail=detail,
        headers={"WWW-Authenticate": "Bearer"},
    )


def require_scopes(*required_scopes: str):
    """지정한 scope를 가진 Bearer 토큰만 통과시키는 FastAPI dependency를 만든다."""

    def dependency(
        credentials: HTTPAuthorizationCredentials | None = Depends(bearer_scheme),
    ) -> AuthContext:
        # 로컬 개발이나 초기 셋업 단계에서는 인증을 끄고 기존 동작을 유지할 수 있다.
        if not _is_auth_enabled():
            return AuthContext(token=None, scopes=frozenset())

        token_scope_map = _get_token_scope_map()
        # 인증을 켰다면 최소 한 개 이상의 유효 토큰 정의가 있어야 한다.
        if not token_scope_map:
            raise HTTPException(
                status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
                detail="Authentication is enabled but AUTH_TOKENS is not configured.",
            )

        # Authorization 헤더가 없거나 Bearer 토큰 값이 비어 있으면 401을 반환한다.
        if credentials is None or not credentials.credentials:
            raise _build_unauthorized_exception("Missing bearer token.")

        token = credentials.credentials
        granted_scopes = token_scope_map.get(token)
        # 등록되지 않은 토큰은 인증 실패로 처리한다.
        if granted_scopes is None:
            raise _build_unauthorized_exception("Invalid bearer token.")

        # 운영 편의를 위해 모든 권한을 의미하는 와일드카드 scope를 지원한다.
        if "*" in granted_scopes:
            return AuthContext(token=token, scopes=granted_scopes)

        # 필요한 scope 중 하나라도 빠져 있으면 인가 실패(403)로 막는다.
        missing_scopes = [scope for scope in required_scopes if scope not in granted_scopes]
        if missing_scopes:
            raise HTTPException(
                status_code=status.HTTP_403_FORBIDDEN,
                detail=f"Missing required scope: {missing_scopes[0]}",
            )

        return AuthContext(token=token, scopes=granted_scopes)

    return dependency
