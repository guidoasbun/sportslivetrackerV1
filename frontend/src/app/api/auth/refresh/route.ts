import { NextResponse } from "next/server";
import { refreshToken } from "@/lib/cognito";
import { getSession, updateSessionTokens, deleteSession } from "@/lib/sessions";

export async function POST() {
    try {
        const session = await getSession();

        if (!session) {
            return NextResponse.json(
                { error: "No session found" },
                { status: 401 }
            );
        }

        // Legacy sessions store only the access token as a plain string — no refresh token available
        if (typeof session === "string") {
            return NextResponse.json(
                { error: "Missing refresh token in session — please sign in again" },
                { status: 401 }
            );
        }

        // Call Cognito to refresh the access token
        const result = await refreshToken(session.refreshToken);

        if (!result || !result.AccessToken) {
            throw new Error("Token refresh failed - no access token returned");
        }

        // Update the session cookie with the new access token
        await updateSessionTokens(result.AccessToken);

        return NextResponse.json({ success: true });
    } catch (error: any) {
        console.error("Token refresh error:", error);

        // Refresh token expired or revoked
        if (error.name === "NotAuthorizedException") {
            await deleteSession();
            return NextResponse.json(
                { error: "Session expired" },
                { status: 401 }
            );
        }

        return NextResponse.json(
            { error: "Failed to refresh token" },
            { status: 500 }
        );
    }
}
