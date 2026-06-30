import { NextResponse } from "next/server";
import { confirmForgotPassword } from "@/lib/cognito";

export async function POST(request: Request) {
    try {
        const body = await request.json();
        const { email, code, newPassword } = body;

        if (!email || !code || !newPassword) {
            return NextResponse.json(
                { error: "Email, code, and new password are required" },
                { status: 400 }
            );
        }

        await confirmForgotPassword(email, code, newPassword);

        return NextResponse.json({
            success: true,
            message: "Password has been reset successfully.",
        });
    } catch (error: any) {
        console.error("Reset password error:", error);

        if (error.name === "CodeMismatchException") {
            return NextResponse.json(
                { error: "Invalid or expired code" },
                { status: 400 }
            );
        }

        if (error.name === "ExpiredCodeException") {
            return NextResponse.json(
                { error: "Code has expired, request a new one" },
                { status: 400 }
            );
        }

        return NextResponse.json(
            { error: error.message || "An error occurred while resetting your password" },
            { status: 500 }
        );
    }
}
