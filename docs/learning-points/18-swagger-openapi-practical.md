# Swagger/OpenAPI 문서화: 실무적으로 “신뢰할 수 있게” 운영하기

> 목적: Swagger/OpenAPI를 “그냥 켜두는 도구”가 아니라, 설계/구현/검증이 맞물린 “살아있는 문서”로 운영하는 기준을 정리한다.

---

## 1) OpenAPI와 Swagger UI

- **OpenAPI**: API 스펙을 표현하는 표준(주로 JSON/YAML)
- **Swagger UI**: OpenAPI를 읽어서 사람이 보기 좋게 렌더링하는 UI

스프링에서는 보통 `springdoc-openapi`로
- `/v3/api-docs` (OpenAPI JSON)
- `/swagger-ui/**` 또는 설정된 경로
를 제공하게 됩니다.

---

## 2) 이 레포 기준 설정 위치

- `src/main/resources/application.yml`의 `springdoc.*`
- `src/main/java/io/hhplus/ecommerce/config/OpenApiConfig.java`

---

## 3) “신뢰할 수 있는 문서”의 조건

Swagger 문서가 믿을 수 있으려면 아래가 중요합니다.

1. **문서가 코드에서 자동 생성**된다(수기 문서는 drift가 빠름)
2. **요청/응답 DTO가 레이어에서 일관**된다(필드명/nullable 규칙)
3. **에러 스펙이 일관**된다(에러 코드/응답 포맷)
4. 변경이 있을 때 문서도 자연스럽게 갱신된다(배포/실행 시 자동 반영)

---

## 4) 실무 팁

- “문서가 최신인지”를 확인하려면:
  - `/v3/api-docs`를 직접 확인(스키마/필드 포함)
  - 실제 호출 가능한 `/swagger-ui`에서 Try it out으로 smoke 테스트
- 문서 품질이 떨어지는 흔한 원인:
  - 컨트롤러에서 DTO가 자주 바뀌는데 수기 문서가 따라오지 못함
  - 에러 응답/상태 코드가 케이스마다 달라짐

---

## 5) 체크리스트 답변용 요약(3줄)

1. OpenAPI는 스펙, Swagger UI는 그 스펙을 렌더링하는 UI다.  
2. 코드에서 자동 생성되게 두고(`/v3/api-docs`), DTO/에러 포맷을 일관되게 유지해야 문서가 믿을만해진다.  
3. 변경 시 문서가 자동 갱신되는 흐름을 유지하는 게 핵심이다.

