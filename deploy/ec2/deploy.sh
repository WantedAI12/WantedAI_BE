#!/bin/bash
# GitHub Actions(deploy.yml)가 SSM으로 서버에서 root로 실행하는 배포 스크립트.
#   bash deploy.sh <git-sha>
# releases/<sha>의 jar·설정을 받아 교체하고 재시작한다. 헬스체크가 실패하면 직전 jar로 되돌린다.
# 주의: 비밀값이 로그에 남지 않도록 set -x를 쓰지 않는다.
set -euo pipefail

SHA="${1:?usage: deploy.sh <git-sha>}"
REGION=ap-northeast-2
REL="s3://perfumery-studio-deploy/releases/${SHA}"
APP_DIR=/opt/perfumery
HEALTH_URL=http://127.0.0.1:8080/api/v1/actuator/health
STAGE="$(mktemp -d)"
trap 'rm -rf "$STAGE"' EXIT

echo "[deploy] release ${SHA}"
aws s3 cp "${REL}/app.jar" "${STAGE}/app.jar" --only-show-errors --region "$REGION"
aws s3 cp "${REL}/ec2/" "${STAGE}/ec2/" --recursive --only-show-errors --region "$REGION"

wait_healthy() {
	for _ in $(seq 1 36); do
		if [ "$(curl -s -o /dev/null -w '%{http_code}' "$HEALTH_URL" || true)" = "200" ]; then
			return 0
		fi
		sleep 5
	done
	return 1
}

# 직전 버전 보관(롤백용)
if [ -f "${APP_DIR}/app.jar" ]; then
	cp -p "${APP_DIR}/app.jar" "${APP_DIR}/app.jar.prev"
	cp -p "${APP_DIR}/run.sh" "${APP_DIR}/run.sh.prev"
fi

install -o perfumery -g perfumery -m 644 "${STAGE}/app.jar" "${APP_DIR}/app.jar"
install -o perfumery -g perfumery -m 755 "${STAGE}/ec2/run.sh" "${APP_DIR}/run.sh"
install -o root -g root -m 644 "${STAGE}/ec2/perfumery.service" /etc/systemd/system/perfumery.service
systemctl daemon-reload

# nginx 설정은 문법 검사를 통과할 때만 반영한다.
if ! cmp -s "${STAGE}/ec2/nginx-perfumery.conf" /etc/nginx/conf.d/perfumery.conf; then
	cp -p /etc/nginx/conf.d/perfumery.conf "${STAGE}/nginx-perfumery.conf.bak"
	install -o root -g root -m 644 "${STAGE}/ec2/nginx-perfumery.conf" /etc/nginx/conf.d/perfumery.conf
	if nginx -t 2>&1; then
		systemctl reload nginx
	else
		echo "[deploy] nginx config invalid - keeping previous config"
		install -o root -g root -m 644 "${STAGE}/nginx-perfumery.conf.bak" /etc/nginx/conf.d/perfumery.conf
	fi
fi

echo "[deploy] restarting perfumery"
systemctl restart perfumery

if wait_healthy; then
	echo "[deploy] healthy: ${SHA}"
	echo "${SHA}" > "${APP_DIR}/RELEASE"
	exit 0
fi

echo "[deploy] health check FAILED - recent logs:"
journalctl -u perfumery --no-pager -o cat -n 60 | grep -vE 'password|secret|token' || true

if [ -f "${APP_DIR}/app.jar.prev" ]; then
	echo "[deploy] rolling back to previous release ($(cat "${APP_DIR}/RELEASE" 2>/dev/null || echo unknown))"
	install -o perfumery -g perfumery -m 644 "${APP_DIR}/app.jar.prev" "${APP_DIR}/app.jar"
	install -o perfumery -g perfumery -m 755 "${APP_DIR}/run.sh.prev" "${APP_DIR}/run.sh"
	systemctl restart perfumery
	if wait_healthy; then
		echo "[deploy] rollback healthy"
	else
		echo "[deploy] rollback ALSO unhealthy - manual check needed"
	fi
fi
exit 1
