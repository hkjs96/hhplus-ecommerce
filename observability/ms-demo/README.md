# Microservices-style demo (Gateway → Order → Payment) with LGTM

이 디렉터리는 **과제와 분리된 데모**를 위해 “마이크로서비스처럼 보이는” 트레이스/서비스그래프를 만들기 위한 구성입니다.

## Run
```bash
docker compose -f observability/ms-demo/docker-compose.yml up -d --build
```

## k6 (steady traffic)
> 부하 테스트가 아니라 “흐름/실패” 가시화가 목적입니다.

```bash
docker compose -f observability/ms-demo/docker-compose.yml run --rm k6
```

## URLs
- Gateway (Spring Cloud Gateway): http://localhost:8080
- Grafana: http://localhost:3000 (admin/admin)
- Prometheus: http://localhost:9090

## Troubleshooting
- `observability/docs/OBSERVABILITY_TROUBLESHOOTING.md`
