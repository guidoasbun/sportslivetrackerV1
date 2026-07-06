import { NextRequest, NextResponse } from "next/server";
import { getSession } from "@/lib/sessions";

const BACKEND_URL = process.env.BACKEND_URL || "http://localhost:8080";

export async function POST(request: NextRequest) {
    const session = await getSession();

    if (!session) {
        return NextResponse.json({ error: "Unauthorized" }, { status: 401 });
    }

    const accessToken =
        typeof session === "string" ? session : session.accessToken;

    let body: unknown;
    try {
        body = await request.json();
    } catch {
        return NextResponse.json(
            { error: "Request body is malformed" },
            { status: 400 }
        );
    }

    const backendResponse = await fetch(`${BACKEND_URL}/api/tts/synthesize`, {
        method: "POST",
        headers: {
            "Content-Type": "application/json",
            Authorization: `Bearer ${accessToken}`,
        },
        body: JSON.stringify(body),
    });

    if (!backendResponse.ok) {
        const errorBody = await backendResponse.text();
        return new NextResponse(errorBody, {
            status: backendResponse.status,
            headers: { "Content-Type": "application/json" },
        });
    }

    const audioBytes = await backendResponse.arrayBuffer();
    return new NextResponse(audioBytes, {
        status: 200,
        headers: { "Content-Type": "audio/mpeg" },
    });
}
