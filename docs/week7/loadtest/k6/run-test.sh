#!/bin/bash

# K6 Load Test Runner Script
# Usage: ./run-test.sh [improved|original]

set -e

cd "$(dirname "$0")/../../../.."  # Go to project root

echo "==================================="
echo "K6 Load Test Runner"
echo "==================================="
echo "Current directory: $(pwd)"
echo ""

if [ "$1" == "improved" ] || [ "$1" == "" ]; then
    echo "🚀 Running IMPROVED ranking test..."
    echo ""
    k6 run docs/week7/loadtest/k6/step13-ranking-improved-test.js
elif [ "$1" == "original" ]; then
    echo "⚠️  Running ORIGINAL ranking test (will likely fail)..."
    echo ""
    k6 run docs/week7/loadtest/k6/step13-ranking-load-test.js
elif [ "$1" == "coupon" ]; then
    if [ -z "$2" ]; then
        echo "❌ Error: COUPON_ID is required"
        echo "Usage: ./run-test.sh coupon <COUPON_ID>"
        exit 1
    fi
    echo "🎫 Running coupon concurrency test..."
    echo "Coupon ID: $2"
    echo ""
    k6 run -e COUPON_ID=$2 docs/week7/loadtest/k6/step14-coupon-concurrency-test.js
else
    echo "❌ Unknown test type: $1"
    echo ""
    echo "Usage:"
    echo "  ./run-test.sh              # Run improved test (default)"
    echo "  ./run-test.sh improved     # Run improved test"
    echo "  ./run-test.sh original     # Run original test"
    echo "  ./run-test.sh coupon <ID>  # Run coupon test"
    exit 1
fi
