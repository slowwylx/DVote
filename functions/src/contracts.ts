import { createHash, createPublicKey, createVerify } from "node:crypto";
import { HttpsError } from "firebase-functions/v2/https";

export const RESULTS_VISIBILITY_AFTER_CLOSE = "after_close";

const MAX_PROFILE_AGGREGATE_IDS = 2_000;
const MAX_SURVEY_LIFETIME_MILLIS = 31 * 24 * 60 * 60 * 1_000;
const DOCUMENT_ID_PATTERN = /^[A-Za-z0-9_-]{1,128}$/;
const OPERATION_ID_PATTERN =
  /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/;
const CANONICAL_BASE64_PATTERN =
  /^(?:[A-Za-z0-9+/]{4})*(?:[A-Za-z0-9+/]{2}==|[A-Za-z0-9+/]{3}=)?$/;
const SHA256_HEX_PATTERN = /^[0-9a-f]{64}$/;

export type CreateSurveyInput = {
  operationId: string;
  title: string;
  description: string;
  allowMultipleChoices: boolean;
  options: string[];
  expiresAt: number;
};

export type SubmitVoteInput = {
  operationId: string;
  surveyId: string;
  keyId: string;
  optionIds: string[];
  signedAt: number;
  publicKey: string;
  signature: string;
  commitmentHash: string;
};

export type UserProfileData = {
  id: string;
  displayName: string;
  publicKey?: string;
  createdSurveyIds?: string[];
  votedSurveyIds?: string[];
};

export type RegisterVotingKeyInput = {
  deviceId: string;
  keyId: string;
  publicKey: string;
  signedAt: number;
  signature: string;
};

export type RevokeVotingKeyInput = {
  keyId: string;
};

export type VotingKeyStatus = "active" | "rotated" | "revoked";

export type VotingKeyData = {
  keyId: string;
  deviceId: string;
  publicKey: string;
  status: VotingKeyStatus;
  registeredAt: number;
  rotatedAt?: number;
  revokedAt?: number;
};

export type VotingDeviceData = {
  deviceId: string;
  activeKeyId: string | null;
  registeredAt: number;
  updatedAt: number;
};

export type SurveyOptionData = {
  id: string;
  title: string;
};

export type StoredSurveyData = {
  id: string;
  title: string;
  description: string;
  creatorId: string;
  creatorName: string;
  createdAt: number;
  expiresAt: number;
  isActive: boolean;
  resultsVisibility: typeof RESULTS_VISIBILITY_AFTER_CLOSE;
  allowMultipleChoices: boolean;
  options: SurveyOptionData[];
};

export type SurveyResultData = {
  surveyId: string;
  totalVotes: number;
  optionVotes: Record<string, number>;
};

export function parseCreateSurvey(
  data: unknown,
  nowMillis = Date.now(),
): CreateSurveyInput {
  const input = requireExactObject(
    data,
    [
      "operationId",
      "title",
      "description",
      "allowMultipleChoices",
      "options",
      "expiresAt",
    ],
    "createSurvey request",
  );
  const title = requireNormalizedString(input.title, "title", 3, 80);
  const description = requireNormalizedString(input.description, "description", 10, 500);
  const options = requireStringArray(input.options, "options", 2, 10)
    .map((option) => requireNormalizedString(option, "option", 1, 80));
  if (new Set(options).size !== options.length) {
    throw new HttpsError("invalid-argument", "Survey options must be unique.");
  }

  const expiresAt = requireSafeInteger(input.expiresAt, "expiresAt");
  if (expiresAt <= nowMillis || expiresAt > nowMillis + MAX_SURVEY_LIFETIME_MILLIS) {
    throw new HttpsError("invalid-argument", "Expiration is outside the accepted range.");
  }

  return {
    operationId: requireOperationId(input.operationId),
    title,
    description,
    allowMultipleChoices: requireBoolean(
      input.allowMultipleChoices,
      "allowMultipleChoices",
    ),
    options,
    expiresAt,
  };
}

export function parseSubmitVote(data: unknown): SubmitVoteInput {
  const input = requireExactObject(
    data,
    [
      "operationId",
      "surveyId",
      "keyId",
      "optionIds",
      "signedAt",
      "publicKey",
      "signature",
      "commitmentHash",
    ],
    "submitVote request",
  );
  const optionIds = requireStringArray(input.optionIds, "optionIds", 1, 10)
    .map((optionId) => requireDocumentId(optionId, "optionId"))
    .sort();
  if (new Set(optionIds).size !== optionIds.length) {
    throw new HttpsError("invalid-argument", "Duplicate options are not allowed.");
  }

  return {
    operationId: requireOperationId(input.operationId),
    surveyId: requireDocumentId(input.surveyId, "surveyId"),
    keyId: requireSha256Hex(input.keyId, "keyId"),
    optionIds,
    signedAt: requireSafeInteger(input.signedAt, "signedAt"),
    publicKey: requireP256PublicKeyBase64(input.publicKey, "publicKey"),
    signature: requireDerSignatureBase64(input.signature, "signature"),
    commitmentHash: requireFixedBase64(input.commitmentHash, "commitmentHash", 32),
  };
}

export function parseRegisterVotingKey(data: unknown): RegisterVotingKeyInput {
  const input = requireExactObject(
    data,
    ["deviceId", "keyId", "publicKey", "signedAt", "signature"],
    "registerVotingKey request",
  );
  const publicKey = requireP256PublicKeyBase64(input.publicKey, "publicKey");
  const keyId = requireSha256Hex(input.keyId, "keyId");
  if (keyId !== votingKeyId(publicKey)) {
    throw new HttpsError("invalid-argument", "keyId does not match publicKey.");
  }
  return {
    deviceId: requireSha256Hex(input.deviceId, "deviceId"),
    keyId,
    publicKey,
    signedAt: requireSafeInteger(input.signedAt, "signedAt"),
    signature: requireDerSignatureBase64(input.signature, "signature"),
  };
}

export function parseRevokeVotingKey(data: unknown): RevokeVotingKeyInput {
  const input = requireExactObject(data, ["keyId"], "revokeVotingKey request");
  return {
    keyId: requireSha256Hex(input.keyId, "keyId"),
  };
}

export function buildVotingKeyRegistrationPayload(
  uid: string,
  input: Pick<RegisterVotingKeyInput, "deviceId" | "keyId" | "signedAt">,
): string {
  return [
    "action=registerVotingKey",
    `user=${uid}`,
    `device=${input.deviceId}`,
    `key=${input.keyId}`,
    `signedAt=${input.signedAt}`,
  ].join("|");
}

export function votingKeyId(publicKeyBase64: string): string {
  return createHash("sha256")
    .update(Buffer.from(publicKeyBase64, "base64"))
    .digest("hex");
}

export function createSurveyRequestHash(input: CreateSurveyInput): string {
  return sha256Hex(JSON.stringify({
    title: input.title,
    description: input.description,
    allowMultipleChoices: input.allowMultipleChoices,
    options: input.options,
    expiresAt: input.expiresAt,
  }));
}

export function submitVoteRequestHash(input: SubmitVoteInput): string {
  return sha256Hex(JSON.stringify({
    surveyId: input.surveyId,
    keyId: input.keyId,
    optionIds: input.optionIds,
    signedAt: input.signedAt,
    publicKey: input.publicKey,
    signature: input.signature,
    commitmentHash: input.commitmentHash,
  }));
}

export function parseUserProfile(data: unknown, expectedUserId: string): UserProfileData {
  const profile = requireStoredExactObject(
    data,
    ["id", "displayName"],
    ["publicKey", "createdSurveyIds", "votedSurveyIds"],
    "user profile",
  );
  const id = requireStoredString(profile.id, "user profile id", 1, 128);
  if (id !== expectedUserId) {
    throw invalidStoredData("user profile");
  }

  const result: UserProfileData = {
    id,
    displayName: requireStoredNormalizedString(
      profile.displayName,
      "user profile display name",
      1,
      80,
    ),
  };

  if (profile.publicKey !== undefined) {
    result.publicKey = requireStoredP256PublicKey(
      profile.publicKey,
      "user profile public key",
    );
  }
  if (profile.createdSurveyIds !== undefined) {
    result.createdSurveyIds = requireStoredDocumentIdArray(
      profile.createdSurveyIds,
      "created survey ids",
    );
  }
  if (profile.votedSurveyIds !== undefined) {
    result.votedSurveyIds = requireStoredDocumentIdArray(
      profile.votedSurveyIds,
      "voted survey ids",
    );
  }
  return result;
}

export function parseStoredVotingKey(
  data: unknown,
  expectedKeyId: string,
): VotingKeyData {
  const key = requireStoredExactObject(
    data,
    ["keyId", "deviceId", "publicKey", "status", "registeredAt"],
    ["rotatedAt", "revokedAt"],
    "voting key",
  );
  const keyId = requireStoredSha256Hex(key.keyId, "voting key id");
  const publicKey = requireStoredP256PublicKey(key.publicKey, "voting key public key");
  const status = requireStoredString(key.status, "voting key status", 6, 7);
  if (
    keyId !== expectedKeyId ||
    keyId !== votingKeyId(publicKey) ||
    (status !== "active" && status !== "rotated" && status !== "revoked")
  ) {
    throw invalidStoredData("voting key");
  }

  const result: VotingKeyData = {
    keyId,
    deviceId: requireStoredSha256Hex(key.deviceId, "voting key device id"),
    publicKey,
    status,
    registeredAt: requireStoredNonNegativeInteger(
      key.registeredAt,
      "voting key registration time",
    ),
  };
  if (status === "active") {
    if (key.rotatedAt !== undefined || key.revokedAt !== undefined) {
      throw invalidStoredData("voting key");
    }
  } else if (status === "rotated") {
    if (key.rotatedAt === undefined || key.revokedAt !== undefined) {
      throw invalidStoredData("voting key");
    }
    result.rotatedAt = requireStoredNonNegativeInteger(
      key.rotatedAt,
      "voting key rotation time",
    );
  } else {
    if (key.revokedAt === undefined || key.rotatedAt !== undefined) {
      throw invalidStoredData("voting key");
    }
    result.revokedAt = requireStoredNonNegativeInteger(
      key.revokedAt,
      "voting key revocation time",
    );
  }
  return result;
}

export function parseStoredVotingDevice(
  data: unknown,
  expectedDeviceId: string,
): VotingDeviceData {
  const device = requireStoredExactObject(
    data,
    ["deviceId", "activeKeyId", "registeredAt", "updatedAt"],
    [],
    "voting device",
  );
  const deviceId = requireStoredSha256Hex(device.deviceId, "voting device id");
  const activeKeyId = device.activeKeyId === null ?
    null :
    requireStoredSha256Hex(device.activeKeyId, "active voting key id");
  if (deviceId !== expectedDeviceId) {
    throw invalidStoredData("voting device");
  }
  return {
    deviceId,
    activeKeyId,
    registeredAt: requireStoredNonNegativeInteger(
      device.registeredAt,
      "voting device registration time",
    ),
    updatedAt: requireStoredNonNegativeInteger(
      device.updatedAt,
      "voting device update time",
    ),
  };
}

export function parseStoredSurvey(data: unknown, expectedSurveyId: string): StoredSurveyData {
  const survey = requireStoredExactObject(
    data,
    [
      "id",
      "title",
      "description",
      "creatorId",
      "creatorName",
      "createdAt",
      "expiresAt",
      "isActive",
      "resultsVisibility",
      "allowMultipleChoices",
      "options",
    ],
    [],
    "survey",
  );
  const id = requireStoredDocumentId(survey.id, "survey id");
  if (id !== expectedSurveyId) {
    throw invalidStoredData("survey");
  }

  const rawOptions = requireStoredArray(survey.options, "survey options", 2, 10);
  const options = rawOptions.map((rawOption) => {
    const option = requireStoredExactObject(rawOption, ["id", "title"], [], "survey option");
    return {
      id: requireStoredDocumentId(option.id, "survey option id"),
      title: requireStoredNormalizedString(option.title, "survey option title", 1, 80),
    };
  });
  if (new Set(options.map((option) => option.id)).size !== options.length) {
    throw invalidStoredData("survey");
  }

  const resultsVisibility = requireStoredString(
    survey.resultsVisibility,
    "results visibility",
    RESULTS_VISIBILITY_AFTER_CLOSE.length,
    RESULTS_VISIBILITY_AFTER_CLOSE.length,
  );
  if (resultsVisibility !== RESULTS_VISIBILITY_AFTER_CLOSE) {
    throw invalidStoredData("survey");
  }

  return {
    id,
    title: requireStoredNormalizedString(survey.title, "survey title", 3, 80),
    description: requireStoredNormalizedString(
      survey.description,
      "survey description",
      10,
      500,
    ),
    creatorId: requireStoredString(survey.creatorId, "survey creator id", 1, 128),
    creatorName: requireStoredNormalizedString(
      survey.creatorName,
      "survey creator name",
      1,
      80,
    ),
    createdAt: requireStoredSafeInteger(survey.createdAt, "survey created time"),
    expiresAt: requireStoredSafeInteger(survey.expiresAt, "survey expiry time"),
    isActive: requireStoredBoolean(survey.isActive, "survey active state"),
    resultsVisibility: RESULTS_VISIBILITY_AFTER_CLOSE,
    allowMultipleChoices: requireStoredBoolean(
      survey.allowMultipleChoices,
      "survey multiple-choice state",
    ),
    options,
  };
}

export function parseStoredResult(
  data: unknown,
  expectedSurveyId: string,
  allowedOptionIds: ReadonlySet<string>,
): SurveyResultData {
  const result = requireStoredExactObject(
    data,
    ["surveyId", "totalVotes", "optionVotes"],
    [],
    "survey result",
  );
  if (result.surveyId !== expectedSurveyId) {
    throw invalidStoredData("survey result");
  }
  const optionVotes = requireStoredRecord(result.optionVotes, "survey option votes");
  if (
    Object.keys(optionVotes).length !== allowedOptionIds.size ||
    Object.keys(optionVotes).some((optionId) => !allowedOptionIds.has(optionId))
  ) {
    throw invalidStoredData("survey result");
  }

  const validatedVotes = Object.fromEntries(
    Object.entries(optionVotes).map(([optionId, count]) => [
      optionId,
      requireStoredNonNegativeInteger(count, "survey option vote count"),
    ]),
  );
  const totalVotes = requireStoredNonNegativeInteger(result.totalVotes, "survey total votes");
  if (Object.values(validatedVotes).some((count) => count > totalVotes)) {
    throw invalidStoredData("survey result");
  }

  return {
    surveyId: expectedSurveyId,
    totalVotes,
    optionVotes: validatedVotes,
  };
}

export function verifySignature(
  payload: string,
  publicKeyBase64: string,
  signatureBase64: string,
): boolean {
  try {
    const publicKey = createPublicKey({
      key: Buffer.from(publicKeyBase64, "base64"),
      format: "der",
      type: "spki",
    });
    const verifier = createVerify("SHA256");
    verifier.update(payload);
    verifier.end();
    return verifier.verify(publicKey, Buffer.from(signatureBase64, "base64"));
  } catch {
    return false;
  }
}

function requireExactObject(
  value: unknown,
  expectedKeys: string[],
  name: string,
): Record<string, unknown> {
  const object = requirePlainObject(value, name);
  const actualKeys = Object.keys(object).sort();
  const sortedExpectedKeys = [...expectedKeys].sort();
  if (
    actualKeys.length !== sortedExpectedKeys.length ||
    actualKeys.some((key, index) => key !== sortedExpectedKeys[index])
  ) {
    throw new HttpsError("invalid-argument", `${name} has an invalid shape.`);
  }
  return object;
}

function requireStoredExactObject(
  value: unknown,
  requiredKeys: string[],
  optionalKeys: string[],
  name: string,
): Record<string, unknown> {
  const object = requireStoredRecord(value, name);
  const keys = Object.keys(object);
  if (
    requiredKeys.some((key) => !keys.includes(key)) ||
    keys.some((key) => !requiredKeys.includes(key) && !optionalKeys.includes(key))
  ) {
    throw invalidStoredData(name);
  }
  return object;
}

function requirePlainObject(value: unknown, name: string): Record<string, unknown> {
  if (value === null || typeof value !== "object" || Array.isArray(value)) {
    throw new HttpsError("invalid-argument", `${name} must be an object.`);
  }
  const prototype = Object.getPrototypeOf(value);
  if (prototype !== Object.prototype && prototype !== null) {
    throw new HttpsError("invalid-argument", `${name} must be a plain object.`);
  }
  return value as Record<string, unknown>;
}

function requireNormalizedString(
  value: unknown,
  name: string,
  minimumLength: number,
  maximumLength: number,
): string {
  if (typeof value !== "string") {
    throw new HttpsError("invalid-argument", `${name} must be a string.`);
  }
  const normalized = value.trim().normalize("NFC");
  if (normalized.length < minimumLength || normalized.length > maximumLength) {
    throw new HttpsError("invalid-argument", `${name} has an invalid length.`);
  }
  return normalized;
}

function requireStringArray(
  value: unknown,
  name: string,
  minimumLength: number,
  maximumLength: number,
): string[] {
  if (
    !Array.isArray(value) ||
    value.length < minimumLength ||
    value.length > maximumLength ||
    value.some((item) => typeof item !== "string")
  ) {
    throw new HttpsError("invalid-argument", `${name} must be a bounded string array.`);
  }
  return value as string[];
}

function requireDocumentId(value: unknown, name: string): string {
  if (typeof value !== "string" || !DOCUMENT_ID_PATTERN.test(value)) {
    throw new HttpsError("invalid-argument", `${name} is invalid.`);
  }
  return value;
}

function requireSha256Hex(value: unknown, name: string): string {
  if (typeof value !== "string" || !SHA256_HEX_PATTERN.test(value)) {
    throw new HttpsError("invalid-argument", `${name} must be a SHA-256 identifier.`);
  }
  return value;
}

function requireOperationId(value: unknown): string {
  if (typeof value !== "string" || !OPERATION_ID_PATTERN.test(value)) {
    throw new HttpsError("invalid-argument", "operationId must be a canonical UUID.");
  }
  return value;
}

function requireSafeInteger(value: unknown, name: string): number {
  if (typeof value !== "number" || !Number.isSafeInteger(value)) {
    throw new HttpsError("invalid-argument", `${name} must be an integer.`);
  }
  return value;
}

function requireBoolean(value: unknown, name: string): boolean {
  if (typeof value !== "boolean") {
    throw new HttpsError("invalid-argument", `${name} must be a boolean.`);
  }
  return value;
}

function requireP256PublicKeyBase64(value: unknown, name: string): string {
  const bytes = decodeCanonicalBase64(value, name, 64, 128);
  try {
    const publicKey = createPublicKey({
      key: bytes,
      format: "der",
      type: "spki",
    });
    const curve = publicKey.asymmetricKeyDetails?.namedCurve;
    const canonicalDer = publicKey.export({ format: "der", type: "spki" });
    if (
      publicKey.asymmetricKeyType !== "ec" ||
      (curve !== "prime256v1" && curve !== "P-256") ||
      !Buffer.isBuffer(canonicalDer) ||
      !canonicalDer.equals(bytes)
    ) {
      throw new Error("Unexpected key type.");
    }
    return value as string;
  } catch {
    throw new HttpsError("invalid-argument", `${name} must be a canonical P-256 SPKI key.`);
  }
}

function requireDerSignatureBase64(value: unknown, name: string): string {
  const bytes = decodeCanonicalBase64(value, name, 8, 72);
  if (!isCanonicalEcdsaDerSignature(bytes)) {
    throw new HttpsError("invalid-argument", `${name} must be a canonical DER signature.`);
  }
  return value as string;
}

function requireFixedBase64(value: unknown, name: string, byteLength: number): string {
  decodeCanonicalBase64(value, name, byteLength, byteLength);
  return value as string;
}

function decodeCanonicalBase64(
  value: unknown,
  name: string,
  minimumBytes: number,
  maximumBytes: number,
): Buffer {
  if (
    typeof value !== "string" ||
    !CANONICAL_BASE64_PATTERN.test(value) ||
    value.length > Math.ceil(maximumBytes / 3) * 4
  ) {
    throw new HttpsError("invalid-argument", `${name} must be canonical base64.`);
  }
  const bytes = Buffer.from(value, "base64");
  if (
    bytes.length < minimumBytes ||
    bytes.length > maximumBytes ||
    bytes.toString("base64") !== value
  ) {
    throw new HttpsError("invalid-argument", `${name} has an invalid encoded length.`);
  }
  return bytes;
}

function isCanonicalEcdsaDerSignature(signature: Buffer): boolean {
  if (signature.length < 8 || signature[0] !== 0x30 || signature[1] !== signature.length - 2) {
    return false;
  }
  const rLength = signature[3];
  if (signature[2] !== 0x02 || rLength < 1 || rLength > 33) return false;
  const sTagIndex = 4 + rLength;
  if (sTagIndex + 2 > signature.length || signature[sTagIndex] !== 0x02) return false;
  const sLength = signature[sTagIndex + 1];
  if (sLength < 1 || sLength > 33 || sTagIndex + 2 + sLength !== signature.length) return false;
  return isCanonicalPositiveDerInteger(signature.subarray(4, sTagIndex)) &&
    isCanonicalPositiveDerInteger(signature.subarray(sTagIndex + 2));
}

function isCanonicalPositiveDerInteger(value: Buffer): boolean {
  if (value.length === 0 || (value[0] & 0x80) !== 0) return false;
  if (value.length > 1 && value[0] === 0 && (value[1] & 0x80) === 0) return false;
  return value.some((byte) => byte !== 0);
}

function requireStoredRecord(value: unknown, name: string): Record<string, unknown> {
  if (value === null || typeof value !== "object" || Array.isArray(value)) {
    throw invalidStoredData(name);
  }
  return value as Record<string, unknown>;
}

function requireStoredArray(
  value: unknown,
  name: string,
  minimumLength: number,
  maximumLength: number,
): unknown[] {
  if (!Array.isArray(value) || value.length < minimumLength || value.length > maximumLength) {
    throw invalidStoredData(name);
  }
  return value;
}

function requireStoredString(
  value: unknown,
  name: string,
  minimumLength: number,
  maximumLength: number,
): string {
  if (
    typeof value !== "string" ||
    value.length < minimumLength ||
    value.length > maximumLength
  ) {
    throw invalidStoredData(name);
  }
  return value;
}

function requireStoredNormalizedString(
  value: unknown,
  name: string,
  minimumLength: number,
  maximumLength: number,
): string {
  const stringValue = requireStoredString(value, name, minimumLength, maximumLength);
  if (stringValue !== stringValue.trim().normalize("NFC")) {
    throw invalidStoredData(name);
  }
  return stringValue;
}

function requireStoredDocumentId(value: unknown, name: string): string {
  if (typeof value !== "string" || !DOCUMENT_ID_PATTERN.test(value)) {
    throw invalidStoredData(name);
  }
  return value;
}

function requireStoredSha256Hex(value: unknown, name: string): string {
  if (typeof value !== "string" || !SHA256_HEX_PATTERN.test(value)) {
    throw invalidStoredData(name);
  }
  return value;
}

function requireStoredSafeInteger(value: unknown, name: string): number {
  if (typeof value !== "number" || !Number.isSafeInteger(value)) {
    throw invalidStoredData(name);
  }
  return value;
}

function requireStoredNonNegativeInteger(value: unknown, name: string): number {
  const integer = requireStoredSafeInteger(value, name);
  if (integer < 0) {
    throw invalidStoredData(name);
  }
  return integer;
}

function requireStoredBoolean(value: unknown, name: string): boolean {
  if (typeof value !== "boolean") {
    throw invalidStoredData(name);
  }
  return value;
}

function requireStoredP256PublicKey(value: unknown, name: string): string {
  try {
    return requireP256PublicKeyBase64(value, name);
  } catch {
    throw invalidStoredData(name);
  }
}

function requireStoredDocumentIdArray(value: unknown, name: string): string[] {
  const values = requireStoredArray(value, name, 0, MAX_PROFILE_AGGREGATE_IDS);
  if (values.some((item) => typeof item !== "string" || !DOCUMENT_ID_PATTERN.test(item))) {
    throw invalidStoredData(name);
  }
  return values as string[];
}

function invalidStoredData(name: string): HttpsError {
  return new HttpsError("failed-precondition", `Stored ${name} is invalid.`);
}

function sha256Hex(payload: string): string {
  return createHash("sha256").update(payload).digest("hex");
}
