import { NextResponse } from "next/server";
import { getSession } from "@/lib/sessions";

export async function GET() {
    const session = await getSession();

    if (!session) {
        // No cookie found, or it expired
        return NextResponse.json({ authenticated: false }, { status: 401 });
    }

    // Cookie found! You could optionally decode the JWT here to return the user's email
    // For now, simply knowing they are authenticated is enough.
    return NextResponse.json({ authenticated: true });
}
