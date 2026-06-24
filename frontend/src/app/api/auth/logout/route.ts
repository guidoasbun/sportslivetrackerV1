import { NextResponse } from "next/server";
import { deleteSession } from "@/lib/sessions";

export async function POST() {
    try {
        await deleteSession();
        return NextResponse.json({ success: true });
    } catch (error) {
        console.error("Logout error:", error);
        return NextResponse.json(
            { error: "Failed to log out" },
            { status: 500 }
        );
    }
}
