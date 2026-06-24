import { NextResponse } from "next/server";
import { getSession } from "@/lib/sessions";
import { getUser } from "@/lib/cognito";

export async function GET() {
    const session = await getSession();

    if (!session) {
        // No cookie found, or it expired
        return NextResponse.json({ authenticated: false }, { status: 401 });
    }

    try {
        // Validate the JWT by calling Cognito GetUser with the AccessToken
        await getUser(session);
        return NextResponse.json({ authenticated: true });
    } catch (error) {
        // Token is invalid, expired, or forged
        console.error("Session token validation failed:", error);
        return NextResponse.json({ authenticated: false }, { status: 401 });
    }
}
