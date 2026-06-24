import { NextResponse } from 'next/server';

export async function GET() {
    // A simple, fast endpoint that proves the Next.js server is alive
    return NextResponse.json({
        status: 'ok',
        timestamp: new Date().toISOString()
    });
}
