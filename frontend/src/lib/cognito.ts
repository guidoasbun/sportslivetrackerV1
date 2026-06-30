import {
    CognitoIdentityProviderClient,
    SignUpCommand,
    ConfirmSignUpCommand,
    InitiateAuthCommand,
    GetUserCommand,
    ForgotPasswordCommand,
    ConfirmForgotPasswordCommand,
    ResendConfirmationCodeCommand,
} from "@aws-sdk/client-cognito-identity-provider";

// Initialize the Cognito client using the region from our environment
const client = new CognitoIdentityProviderClient({
    region: process.env.AWS_REGION || "us-east-1",
});

// We need the App Client ID from our Terraform-provisioned Cognito setup
const CLIENT_ID = process.env.NEXT_PUBLIC_COGNITO_CLIENT_ID!;

/**
 * Registers a new user with their email and password
 */
export async function signUp(email: string, password: string) {
    const command = new SignUpCommand({
        ClientId: CLIENT_ID,
        Username: email,
        Password: password,
    });

    return await client.send(command);
}

/**
 * Confirms a new user's email using the OTP sent to them
 */
export async function confirmSignUp(email: string, code: string) {
    const command = new ConfirmSignUpCommand({
        ClientId: CLIENT_ID,
        Username: email,
        ConfirmationCode: code,
    });

    return await client.send(command);
}

/**
 * Logs in a user and returns their authentication tokens
 */
export async function login(email: string, password: string) {
    const command = new InitiateAuthCommand({
        ClientId: CLIENT_ID,
        AuthFlow: "USER_PASSWORD_AUTH",
        AuthParameters: {
            USERNAME: email,
            PASSWORD: password,
        },
    });

    const response = await client.send(command);
    return response.AuthenticationResult; // This contains the AccessToken, IdToken, and RefreshToken
}

/**
 * Exchanges an authorization code for tokens via the Cognito token endpoint
 */
export async function exchangeCodeForTokens(code: string, redirectUri: string) {
    const domain = process.env.NEXT_PUBLIC_COGNITO_DOMAIN!;
    const clientId = process.env.NEXT_PUBLIC_COGNITO_CLIENT_ID!;

    const params = new URLSearchParams({
        grant_type: 'authorization_code',
        client_id: clientId,
        code: code,
        redirect_uri: redirectUri,
    });

    const response = await fetch(`${domain}/oauth2/token`, {
        method: 'POST',
        headers: {
            'Content-Type': 'application/x-www-form-urlencoded',
        },
        body: params.toString(),
    });

    if (!response.ok) {
        const err = await response.json();
        throw new Error(err.error_description || 'Failed to exchange code for tokens');
    }

    return await response.json();
}

/**
 * Validates an access token by fetching the user profile from Cognito
 */
export async function getUser(accessToken: string) {
    const command = new GetUserCommand({
        AccessToken: accessToken,
    });
    return await client.send(command);
}

/**
 * Initiates a forgot password flow — sends a verification code to the user's email
 */
export async function forgotPassword(email: string) {
    const command = new ForgotPasswordCommand({
        ClientId: CLIENT_ID,
        Username: email,
    });
    return await client.send(command);
}

/**
 * Confirms a password reset using the verification code and sets the new password
 */
export async function confirmForgotPassword(email: string, code: string, newPassword: string) {
    const command = new ConfirmForgotPasswordCommand({
        ClientId: CLIENT_ID,
        Username: email,
        ConfirmationCode: code,
        Password: newPassword,
    });
    return await client.send(command);
}

/**
 * Resends the sign-up confirmation code to the user's email
 */
export async function resendConfirmationCode(email: string) {
    const command = new ResendConfirmationCodeCommand({
        ClientId: CLIENT_ID,
        Username: email,
    });
    return await client.send(command);
}

/**
 * Refreshes the access token using a valid refresh token
 */
export async function refreshToken(refreshToken: string) {
    const command = new InitiateAuthCommand({
        ClientId: CLIENT_ID,
        AuthFlow: "REFRESH_TOKEN_AUTH",
        AuthParameters: {
            REFRESH_TOKEN: refreshToken,
        },
    });
    const response = await client.send(command);
    return response.AuthenticationResult;
}
