#!/bin/bash
# Run all tests across API, Lambda, Producer, and Frontend
# Exit on first failure so you immediately see what broke

set -e

echo "============================================"
echo "  Running ALL tests for GameShift Live"
echo "============================================"
echo ""

echo "▶ API Service (Java/Maven)"
echo "--------------------------------------------"
./mvnw test -pl api
echo "✓ API tests passed"
echo ""

echo "▶ Lambda Processor (Java/Maven)"
echo "--------------------------------------------"
./mvnw test -pl lambda
echo "✓ Lambda tests passed"
echo ""

echo "▶ Producer Service (Java/Maven)"
echo "--------------------------------------------"
./mvnw test -pl producer
echo "✓ Producer tests passed"
echo ""

echo "▶ Frontend (Vitest)"
echo "--------------------------------------------"
npm run test --prefix frontend
echo "✓ Frontend tests passed"
echo ""

echo "============================================"
echo "  ✓ All test suites passed!"
echo "============================================"
