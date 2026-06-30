import { NextResponse } from "next/server";
import { getSession } from "@/lib/sessions";
import { getUser } from "@/lib/cognito";

export async function GET() {
    const session = await getSession();

    if (!session) {
        // No cookie found, or it expired
        return NextResponse.json({ authenticated: false }, { status: 401 });
    }

    // Extract the access token from either SessionData object or plain string (backward compat)
    const accessToken =
        typeof session === "object" ? session.accessToken : session;

    try {
        // Validate the JWT by calling Cognito GetUser with the AccessToken
        await getUser(accessToken);
        return NextResponse.json({ authenticated: true });
    } catch (error) {
        // Token is invalid, expired, or forged
        console.error("Session token validation failed:", error);
        return NextResponse.json({ authenticated: false }, { status: 401 });
    }
}
