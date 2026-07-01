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
mvn test -pl api -q
echo "✓ API tests passed"
echo ""

echo "▶ Lambda Processor (Java/Maven)"
echo "--------------------------------------------"
mvn test -pl lambda -q
echo "✓ Lambda tests passed"
echo ""

echo "▶ Producer Service (Java/Maven)"
echo "--------------------------------------------"
mvn test -pl producer -q
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
