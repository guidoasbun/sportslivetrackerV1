// frontend/src/lib/constants.ts

export const SPORT_TYPES = {
    SOCCER: 'SOCCER',
    FOOTBALL: 'FOOTBALL',
    BASKETBALL: 'BASKETBALL',
    BASEBALL: 'BASEBALL',
    HOCKEY: 'HOCKEY',
    FORMULA_1: 'FORMULA_1'
} as const;

export type SportType = keyof typeof SPORT_TYPES;

// This points to our Spring Boot backend
export const API_BASE_URL = process.env.NEXT_PUBLIC_API_URL || 'http://localhost:8080/api';
