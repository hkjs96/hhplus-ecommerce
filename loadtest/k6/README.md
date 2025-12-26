# k6 Load Test (STEP19)

## Run (host)

```bash
k6 run loadtest/k6/step19-all-apis.js
```

## Run (docker)

`docker-compose.yml`로 `app`이 올라간 상태에서, compose 네트워크에 붙여 실행합니다.

```bash
docker run --rm --network ecommerce_ecommerce-network \
  -e BASE_URL=http://app:8080 \
  -v "$PWD/loadtest/k6:/scripts" grafana/k6:latest \
  run /scripts/step19-all-apis.js
```

## Notes

- 기본 데이터는 `DataInitializer` 기준으로 `userId 1~153`, `productId 1~21`, `couponId 1~5`가 있다고 가정합니다.
- DB가 이미 초기화된 상태(데이터 누적)에서는 `productId`가 환경마다 다를 수 있어, 스크립트는 `/api/products` 응답에서 `stock`이 충분한 상품을 자동으로 고릅니다.
  - 조절: `MIN_STOCK_FOR_STABLE_FLOW` (default: `1000`)
- 쿠폰 발급/예약, 주문 생성/결제, 장바구니 쓰기는 상태 변화를 크게 만들 수 있어 기본 확률이 낮게 설정되어 있습니다. (ENV로 조절)
  - `CART_WRITE_PROB` (default: `0.3`)
  - `COUPON_RESERVE_PROB` (default: `0.05`), `COUPON_ISSUE_PROB` (default: `0.02`)
  - `ORDER_CREATE_PROB` (default: `0.05`), `ORDER_COMPLETE_PROB` (default: `0.02`)
