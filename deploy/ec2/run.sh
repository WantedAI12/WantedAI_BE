#!/bin/bash
# systemd(perfumery.service)가 실행하는 앱 기동 스크립트.
# SSM Parameter Store(/perfumery/prod/*)의 값을 같은 이름의 환경변수로 올린 뒤 jar를 띄운다.
# 비밀값은 파일로 남기지 않고 이 프로세스 환경에만 존재한다.
set -euo pipefail

REGION=ap-northeast-2
PARAM_PATH=/perfumery/prod

while IFS=$'\t' read -r name value; do
	export "${name##*/}=${value}"
done < <(aws ssm get-parameters-by-path --region "$REGION" --path "$PARAM_PATH" \
	--with-decryption --query 'Parameters[].[Name,Value]' --output text)

export SPRING_PROFILES_ACTIVE=prod

# t3.small(2GB)에 MySQL이 같이 떠 있으므로 힙을 제한한다.
# 8080은 127.0.0.1에만 열고 외부 트래픽은 nginx(443)를 거친다.
exec java -Xms256m -Xmx768m -XX:+ExitOnOutOfMemoryError \
	-jar /opt/perfumery/app.jar \
	--server.address=127.0.0.1 --server.port=8080
