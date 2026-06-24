import { NextResponse } from "next/server";
import { confirmSignUp } from "@/lib/cognito";

export async function POST(request: Request) {
    try {
        const body = await request.json();
        const { email, code } = body;

        if (!email || !code) {
            return NextResponse.json(
                { error: "Email and confirmation code are required" },
                { status: 400 }
            );
        }

        // Verify the OTP with Cognito
        await confirmSignUp(email, code);

        // If it succeeds, the account is active!
        return NextResponse.json({ success: true });

    } catch (error: any) {
        console.error("Confirmation error:", error);
        return NextResponse.json(
            { error: error.message || "Invalid or expired confirmation code" },
            { status: 400 }
        );
    }
}
