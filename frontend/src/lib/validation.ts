export interface ValidationError {
  field: string;
  rule: string;
  message: string;
}

export interface ValidationResult {
  success: boolean;
  errors: ValidationError[];
}

/**
 * Validates an email address against standard format rules:
 * - Exactly one "@" character
 * - Local part: 1–64 characters
 * - Domain part: contains at least one ".", each label 1–63 characters
 * - Total length ≤ 254
 */
export function validateEmail(email: string): ValidationResult {
  const errors: ValidationError[] = [];

  // Rule: total length ≤ 254
  if (email.length > 254) {
    errors.push({
      field: "email",
      rule: "maxLength",
      message: "Email must not exceed 254 characters",
    });
  }

  // Rule: exactly one "@"
  const atCount = email.split("@").length - 1;
  if (atCount !== 1) {
    errors.push({
      field: "email",
      rule: "atSign",
      message: "Email must contain exactly one @ character",
    });
    // Can't validate local/domain parts without a single "@"
    return { success: false, errors };
  }

  const [localPart, domainPart] = email.split("@");

  // Rule: local part 1–64 characters
  if (localPart.length < 1 || localPart.length > 64) {
    errors.push({
      field: "email",
      rule: "localPartLength",
      message: "Local part (before @) must be 1–64 characters",
    });
  }

  // Rule: domain must contain at least one "."
  if (!domainPart.includes(".")) {
    errors.push({
      field: "email",
      rule: "domainDot",
      message: "Domain must contain at least one dot",
    });
  } else {
    // Rule: each domain label must be 1–63 characters
    const labels = domainPart.split(".");
    const invalidLabel = labels.some(
      (label) => label.length < 1 || label.length > 63
    );
    if (invalidLabel) {
      errors.push({
        field: "email",
        rule: "domainLabelLength",
        message: "Each domain label must be 1–63 characters",
      });
    }
  }

  return { success: errors.length === 0, errors };
}

// Special characters allowed in Cognito passwords
const SPECIAL_CHARS = new Set(
  `^ $ * . [ ] { } ( ) ? " ! @ # % & / \\ , > < ' : ; | _ ~ \` = + -`.split(" ")
);

/**
 * Validates a password against Cognito password policy:
 * - Length: 8–256 characters
 * - At least one uppercase letter (A-Z)
 * - At least one lowercase letter (a-z)
 * - At least one digit (0-9)
 * - At least one special character from the allowed set
 */
export function validatePassword(password: string): ValidationResult {
  const errors: ValidationError[] = [];

  // Rule: length 8–256
  if (password.length < 8) {
    errors.push({
      field: "password",
      rule: "minLength",
      message: "Password must be at least 8 characters",
    });
  }
  if (password.length > 256) {
    errors.push({
      field: "password",
      rule: "maxLength",
      message: "Password must not exceed 256 characters",
    });
  }

  // Rule: at least one uppercase
  if (!/[A-Z]/.test(password)) {
    errors.push({
      field: "password",
      rule: "uppercase",
      message: "Password must contain at least one uppercase letter",
    });
  }

  // Rule: at least one lowercase
  if (!/[a-z]/.test(password)) {
    errors.push({
      field: "password",
      rule: "lowercase",
      message: "Password must contain at least one lowercase letter",
    });
  }

  // Rule: at least one digit
  if (!/[0-9]/.test(password)) {
    errors.push({
      field: "password",
      rule: "digit",
      message: "Password must contain at least one digit",
    });
  }

  // Rule: at least one special character
  const hasSpecial = [...password].some((ch) => SPECIAL_CHARS.has(ch));
  if (!hasSpecial) {
    errors.push({
      field: "password",
      rule: "specialChar",
      message: "Password must contain at least one special character",
    });
  }

  return { success: errors.length === 0, errors };
}
