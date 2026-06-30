import { NextResponse } from "next/server";
import { resendConfirmationCode } from "@/lib/cognito";

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

        await resendConfirmationCode(email);

        return NextResponse.json({
            success: true,
            message: "A new code has been sent",
        });
    } catch (error: any) {
        console.error("Resend confirmation code error:", error);

        if (error.name === "CodeDeliveryFailureException") {
            return NextResponse.json(
                { error: "Could not send code" },
                { status: 500 }
            );
        }

        if (
            error.name === "LimitExceededException" ||
            error.name === "UserNotFoundException"
        ) {
            // Return generic success to prevent account enumeration
            return NextResponse.json({
                success: true,
                message: "A new code has been sent",
            });
        }

        return NextResponse.json(
            { error: "Unable to resend code" },
            { status: 400 }
        );
    }
}
