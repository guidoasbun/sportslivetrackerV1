const { URL } = require('url');
const msg = "Failed to parse URL from undefined/oauth2/token";
const requestUrl = "http://10.0.0.1/api/auth/callback/cognito";
console.log(new URL("/login?error=" + encodeURIComponent(msg), requestUrl).toString());
