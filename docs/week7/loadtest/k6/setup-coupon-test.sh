#!/bin/bash

# Coupon Test Setup Script
# This script helps you find or create a coupon for testing

set -e

echo "==================================="
echo "Coupon Test Setup"
echo "==================================="
echo ""

# Check if MySQL is running
echo "1. Checking database connection..."
if ! command -v mysql &> /dev/null; then
    echo "⚠️  MySQL client not found. Please install MySQL client or use Docker."
    echo ""
    echo "Alternative: Check coupon ID manually:"
    echo "  1. Open your database tool (DBeaver, MySQL Workbench, etc.)"
    echo "  2. Run: SELECT id, name, total_quantity FROM coupons LIMIT 5;"
    echo "  3. Use the 'id' column value as COUPON_ID"
    exit 1
fi

echo "✅ MySQL client found"
echo ""

# Database connection details
DB_HOST=${DB_HOST:-localhost}
DB_PORT=${DB_PORT:-3306}
DB_NAME=${DB_NAME:-ecommerce}
DB_USER=${DB_USER:-root}
DB_PASS=${DB_PASS:-}

echo "2. Database settings:"
echo "   Host: $DB_HOST:$DB_PORT"
echo "   Database: $DB_NAME"
echo "   User: $DB_USER"
echo ""

# Try to connect and get coupon IDs
echo "3. Fetching existing coupons..."
MYSQL_CMD="mysql -h$DB_HOST -P$DB_PORT -u$DB_USER"
if [ -n "$DB_PASS" ]; then
    MYSQL_CMD="$MYSQL_CMD -p$DB_PASS"
fi

COUPONS=$(echo "SELECT id, name, total_quantity, issued_quantity FROM coupons LIMIT 5;" | $MYSQL_CMD -N $DB_NAME 2>/dev/null || echo "")

if [ -z "$COUPONS" ]; then
    echo "⚠️  No coupons found or unable to connect to database"
    echo ""
    echo "To create a test coupon, run:"
    echo ""
    echo "INSERT INTO coupons (name, discount_rate, total_quantity, issued_quantity, start_date, end_date, created_at, updated_at)"
    echo "VALUES ('Load Test Coupon', 10, 100, 0, NOW(), DATE_ADD(NOW(), INTERVAL 30 DAY), NOW(), NOW());"
    echo ""
    echo "Then run this script again."
    exit 1
fi

echo "✅ Found coupons:"
echo "$COUPONS" | while read -r line; do
    ID=$(echo "$line" | awk '{print $1}')
    NAME=$(echo "$line" | awk '{$1=""; print $0}' | sed 's/^ //')
    echo "   - ID: $ID, Name: $NAME"
done
echo ""

# Get the first coupon ID
FIRST_COUPON_ID=$(echo "$COUPONS" | head -1 | awk '{print $1}')

echo "==================================="
echo "✅ Setup Complete!"
echo "==================================="
echo ""
echo "Recommended COUPON_ID: $FIRST_COUPON_ID"
echo ""
echo "To run the coupon concurrency test:"
echo ""
echo "  cd /Users/jsb/hanghe-plus/ecommerce"
echo "  ./docs/week7/loadtest/k6/run-test.sh coupon $FIRST_COUPON_ID"
echo ""
echo "Or directly:"
echo ""
echo "  k6 run -e COUPON_ID=$FIRST_COUPON_ID docs/week7/loadtest/k6/step14-coupon-concurrency-test.js"
echo ""
