# 1. Build Stage
FROM node:20-alpine AS builder

# pnpm 설치를 위해 corepack 활성화
RUN corepack enable && corepack prepare pnpm@latest --activate

WORKDIR /app

# 종속성 파일 복사 (pnpm-lock.yaml이 핵심!)
COPY package.json pnpm-lock.yaml ./

# 의존성 설치 (frozen-lockfile: lock파일과 일치하도록 설치)
RUN pnpm install --frozen-lockfile

# 전체 소스 복사 및 빌드
COPY . .
RUN pnpm build

# 2. Run Stage
FROM node:20-alpine AS runner
WORKDIR /app

# pnpm 실행을 위해 다시 설치
RUN corepack enable && corepack prepare pnpm@latest --activate

# 빌드 결과물과 의존성 전체 복사-
COPY --from=builder /app ./

EXPOSE 3000

# pnpm start로 서버 실행
CMD ["pnpm", "start"]