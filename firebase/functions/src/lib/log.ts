import { logger } from "firebase-functions/v2";

/**
 * Structured logs without personal data: only the function name and an outcome code. Never log uids, emails, tokens,
 * request payloads or error messages that may echo them.
 */
export type LogFields = { fn: string; outcome: string; code?: string };

export function logWarn(message: string, fields: LogFields): void {
  logger.warn(message, fields);
}

export function logError(message: string, fields: LogFields): void {
  logger.error(message, fields);
}
