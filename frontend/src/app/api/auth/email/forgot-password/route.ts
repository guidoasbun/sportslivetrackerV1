import { NextResponse } from "next/server";
import { forgotPassword } from "@/lib/cognito";

export async function POST(request: Request) {
    try {
        const body = await request.json();
        const { email } = body;

        if (!email) {
            return NextResponse.json(
                { error: "Email is required" },
                { status: 400 }
            );
        }

        await forgotPassword(email);

        return NextResponse.json({
            success: true,
            message: "If an account exists with this email, a password reset code has been sent.",
        });
    } catch (error: any) {
        console.error("Forgot password error:", error);

        // Don't reveal whether the user exists — return generic success
        if (
            error.name === "UserNotFoundException" ||
            error.name === "LimitExceededException"
        ) {
            return NextResponse.json({
                success: true,
                message: "If an account exists with this email, a password reset code has been sent.",
            });
        }

        return NextResponse.json(
            { error: error.message || "An error occurred while processing your request" },
            { status: 500 }
        );
    }
}
