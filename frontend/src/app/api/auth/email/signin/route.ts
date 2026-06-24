import { NextResponse } from "next/server";
import { login } from "@/lib/cognito";
import { createSession } from "@/lib/sessions";

export async function POST(request: Request) {
    try {
        // Parse the JSON body sent from the client
        const body = await request.json();
        const { email, password } = body;

        if (!email || !password) {
            return NextResponse.json(
                { error: "Email and password are required" },
                { status: 400 }
            );
        }

        // 1. Call our Cognito wrapper to authenticate with AWS
        const authResult = await login(email, password);

        if (!authResult || !authResult.AccessToken) {
            throw new Error("Login failed - no access token returned");
        }

        // 2. Create the HTTP-only cookie session using the new token
        await createSession(authResult.AccessToken);

        // 3. Return success to the client
        return NextResponse.json({ success: true });

    } catch (error: any) {
        console.error("Signin error:", error);

        // AWS throws specific errors (like NotAuthorizedException) which we can pass along
        return NextResponse.json(
            { error: error.message || "Invalid email or password" },
            { status: 401 }
        );
    }
}
