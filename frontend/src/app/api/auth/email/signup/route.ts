import { NextResponse } from "next/server";
import { signUp } from "@/lib/cognito";

export async function POST(request: Request) {
    try {
        const body = await request.json();
        const { email, password } = body;

        if (!email || !password) {
            return NextResponse.json(
                { error: "Email and password are required" },
                { status: 400 }
            );
        }

        // 1. Register the user in Cognito (this triggers the OTP email)
        await signUp(email, password);

        // 2. Return success, but remind the client to route the user to the confirm page
        return NextResponse.json({
            success: true,
            message: "Please check your email for a confirmation code."
        });

    } catch (error: any) {
        console.error("Signup error:", error);
        return NextResponse.json(
            { error: error.message || "Failed to create account" },
            { status: 400 }
        );
    }
}
