const baseUrl = __ENV.BASE_URL || 'http://localhost:8080';
const couponId = Number(__ENV.COUPON_ID || 1);
const productId = Number(__ENV.TEST_PRODUCT_ID || 1);
const userId = Number(__ENV.TEST_USER_ID || 1);
const accuracyTarget = Number(__ENV.RANKING_ACCURACY_TARGET || 100);
const balanceChargeAmount = Number(__ENV.BALANCE_CHARGE_AMOUNT || 5_000_000);

const timeout = __ENV.HTTP_TIMEOUT || '5s';  // 기본 5초 timeout

const config = {
  baseUrl,
  couponId,
  productId,
  userId,
  accuracyTarget,
  balanceChargeAmount,
  timeout,
};

export default config;
