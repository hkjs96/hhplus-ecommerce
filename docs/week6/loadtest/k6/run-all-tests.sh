#!/bin/bash

# K6 Load Test Suite - Master Test Script
# This script runs all idempotency and cache performance tests

set -e  # Exit on error

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Configuration
BASE_URL=${BASE_URL:-http://localhost:8080}
RESULTS_DIR="docs/week6/loadtest/k6/results"

# Print header
echo -e "${GREEN}========================================${NC}"
echo -e "${GREEN}K6 Load Test Suite for Idempotency & Cache${NC}"
echo -e "${GREEN}========================================${NC}"
echo ""
echo "Base URL: $BASE_URL"
echo "Results Directory: $RESULTS_DIR"
echo ""

# Create results directory
mkdir -p "$RESULTS_DIR"

# Check if application is running
echo -e "${YELLOW}Checking if application is running...${NC}"
if ! curl -s -f "$BASE_URL/api/products" > /dev/null; then
    echo -e "${RED}Error: Application is not running at $BASE_URL${NC}"
    echo "Please start the application with: ./gradlew bootRun"
    exit 1
fi
echo -e "${GREEN}✓ Application is running${NC}"
echo ""

# Check if Redis is running
echo -e "${YELLOW}Checking if Redis is running...${NC}"
if ! redis-cli ping > /dev/null 2>&1; then
    echo -e "${RED}Warning: Redis is not running${NC}"
    echo "Cache tests may fail. Start Redis with: docker run -d -p 6379:6379 redis:7-alpine"
    read -p "Continue anyway? (y/n) " -n 1 -r
    echo
    if [[ ! $REPLY =~ ^[Yy]$ ]]; then
        exit 1
    fi
else
    echo -e "${GREEN}✓ Redis is running${NC}"
fi
echo ""

# Test 1: Order Creation Idempotency
echo -e "${YELLOW}========================================${NC}"
echo -e "${YELLOW}Test 1: Order Creation Idempotency${NC}"
echo -e "${YELLOW}========================================${NC}"
echo ""

if k6 run --out json="$RESULTS_DIR/order-idempotency-raw.json" \
    docs/week6/loadtest/k6/order-creation-idempotency-test.js; then
    echo -e "${GREEN}✓ Order Idempotency Test PASSED${NC}"
    ORDER_TEST_RESULT="PASS"
else
    echo -e "${RED}✗ Order Idempotency Test FAILED${NC}"
    ORDER_TEST_RESULT="FAIL"
fi
echo ""

# Wait between tests
sleep 5

# Test 2: Product Query Cache
echo -e "${YELLOW}========================================${NC}"
echo -e "${YELLOW}Test 2: Product Query Cache${NC}"
echo -e "${YELLOW}========================================${NC}"
echo ""

if k6 run --out json="$RESULTS_DIR/product-cache-raw.json" \
    docs/week6/loadtest/k6/product-query-cache-test.js; then
    echo -e "${GREEN}✓ Product Cache Test PASSED${NC}"
    PRODUCT_TEST_RESULT="PASS"
else
    echo -e "${RED}✗ Product Cache Test FAILED${NC}"
    PRODUCT_TEST_RESULT="FAIL"
fi
echo ""

# Wait between tests
sleep 5

# Test 3: Cart Cache
echo -e "${YELLOW}========================================${NC}"
echo -e "${YELLOW}Test 3: Cart Cache${NC}"
echo -e "${YELLOW}========================================${NC}"
echo ""

if k6 run --out json="$RESULTS_DIR/cart-cache-raw.json" \
    docs/week6/loadtest/k6/cart-cache-test.js; then
    echo -e "${GREEN}✓ Cart Cache Test PASSED${NC}"
    CART_TEST_RESULT="PASS"
else
    echo -e "${RED}✗ Cart Cache Test FAILED${NC}"
    CART_TEST_RESULT="FAIL"
fi
echo ""

# Generate Summary Report
echo -e "${YELLOW}========================================${NC}"
echo -e "${YELLOW}Generating Summary Report${NC}"
echo -e "${YELLOW}========================================${NC}"
echo ""

SUMMARY_FILE="$RESULTS_DIR/test-summary.txt"

cat > "$SUMMARY_FILE" <<EOF
=================================================================
K6 Load Test Suite - Final Summary Report
=================================================================

Test Execution Date: $(date)
Base URL: $BASE_URL

-----------------------------------------------------------------
Test Results
-----------------------------------------------------------------

1. Order Creation Idempotency Test: $ORDER_TEST_RESULT
2. Product Query Cache Test: $PRODUCT_TEST_RESULT
3. Cart Cache Test: $CART_TEST_RESULT

-----------------------------------------------------------------
Detailed Results
-----------------------------------------------------------------

Result files:
- Order Idempotency: $RESULTS_DIR/order-idempotency-summary.json
- Product Cache: $RESULTS_DIR/product-cache-summary.json
- Cart Cache: $RESULTS_DIR/cart-cache-summary.json

Raw output files:
- Order Idempotency Raw: $RESULTS_DIR/order-idempotency-raw.json
- Product Cache Raw: $RESULTS_DIR/product-cache-raw.json
- Cart Cache Raw: $RESULTS_DIR/cart-cache-raw.json

-----------------------------------------------------------------
Overall Status
-----------------------------------------------------------------

EOF

# Calculate overall result
if [[ "$ORDER_TEST_RESULT" == "PASS" && "$PRODUCT_TEST_RESULT" == "PASS" && "$CART_TEST_RESULT" == "PASS" ]]; then
    echo -e "Overall Result: ${GREEN}ALL TESTS PASSED ✓${NC}" | tee -a "$SUMMARY_FILE"
    OVERALL_RESULT=0
else
    echo -e "Overall Result: ${RED}SOME TESTS FAILED ✗${NC}" | tee -a "$SUMMARY_FILE"
    OVERALL_RESULT=1
fi

echo ""
echo "==================================================================" | tee -a "$SUMMARY_FILE"
echo ""

# Display summary
echo -e "${GREEN}Summary report saved to: $SUMMARY_FILE${NC}"
echo ""

# Open results directory (macOS only)
if [[ "$OSTYPE" == "darwin"* ]]; then
    echo -e "${YELLOW}Opening results directory...${NC}"
    open "$RESULTS_DIR"
fi

# Exit with appropriate code
exit $OVERALL_RESULT
