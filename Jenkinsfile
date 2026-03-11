pipeline {
	agent any
	parameters {
		booleanParam(name: 'DEPLOY_BE', defaultValue: true, description: '백엔드 배포 여부')
		booleanParam(name: 'DEPLOY_FE', defaultValue: true, description: '프론트엔드 배포 여부')
		booleanParam(name: 'DEPLOY_BRIDGE', defaultValue: true, description: '중계서버 배포 여부')
	}
	stages {
		// 환경변수
		stage('Prepare Env') {
			steps {
				// Jenkins Credentials에서 .env 파일들을 각 위치에 뿌려줌
				withCredentials([
					file(credentialsId: 'ENV_FILE', variable: 'ENV'),
				]) {
					sh "cp $ENV infra/app/.env"
					sh "cp $ENV infra/database/.env"
					sh "cp $ENV infra/management/.env"
				}
			}
		}

		// 백엔드 컨테이너 CI/CD
		stage('Deploy Backend') {
			when {
				expression {
					params.DEPLOY_BE
				}
			}
			steps {
				// 백엔드만 빌드하고 배포 (기존 컨테이너는 유지됨)
				sh "docker-compose -f infra/app/app-compose.yml up -d --build backend"
			}
		}

		// 프론트엔드 컨테이너 CI/CD
		stage('Deploy Frontend') {
			when {
				expression {
					params.DEPLOY_FE
				}
			}
			steps {
				// 프론트엔드 배포
				sh "docker-compose -f infra/app/app-compose.yml up -d --build frontend"
			}
		}

		// Webhook 중계서버 컨테이너 CI/CD
		stage('Deploy BridgeServer') {
			when {
				expression {
					params.DEPLOY_BRIDGE
				}
			}
			steps {
				sh "docker-compose -f infra/management/management-compose.yml up -d --build bridgeserver"
			}
		}
	}
}