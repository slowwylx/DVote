import { initializeApp } from "firebase-admin/app";
import {
  DocumentReference,
  FieldValue,
  Firestore,
  getFirestore,
} from "firebase-admin/firestore";
import { CallableOptions, HttpsError, onCall } from "firebase-functions/v2/https";
import { createHash, randomUUID } from "node:crypto";
import {
  buildVotingKeyRegistrationPayload,
  createSurveyRequestHash,
  CreateSurveyInput,
  parseCreateSurvey,
  parseRegisterVotingKey,
  parseRevokeVotingKey,
  parseStoredResult,
  parseStoredSurvey,
  parseStoredVotingDevice,
  parseStoredVotingKey,
  parseSubmitVote,
  parseUserProfile,
  RegisterVotingKeyInput,
  RESULTS_VISIBILITY_AFTER_CLOSE,
  submitVoteRequestHash,
  SubmitVoteInput,
  SurveyOptionData,
  SurveyResultData,
  verifySignature,
} from "./contracts";

export {
  buildVotingKeyRegistrationPayload,
  parseCreateSurvey,
  parseRegisterVotingKey,
  parseRevokeVotingKey,
  parseStoredResult,
  parseStoredSurvey,
  parseStoredVotingDevice,
  parseStoredVotingKey,
  parseSubmitVote,
  parseUserProfile,
  RESULTS_VISIBILITY_AFTER_CLOSE,
  createSurveyRequestHash,
  submitVoteRequestHash,
  verifySignature,
} from "./contracts";
export type {
  CreateSurveyInput,
  RegisterVotingKeyInput,
  StoredSurveyData,
  SubmitVoteInput,
  SurveyOptionData,
  SurveyResultData,
  UserProfileData,
} from "./contracts";

initializeApp();

export const CALLABLE_OPTIONS: CallableOptions = {
  region: "us-central1",
  enforceAppCheck: true,
  consumeAppCheckToken: true,
  timeoutSeconds: 30,
  memory: "256MiB",
  maxInstances: 5,
};
const CREATE_SURVEY_LIMIT = 10;
const CREATE_SURVEY_WINDOW_MILLIS = 24 * 60 * 60 * 1_000;
const VOTE_TIMESTAMP_WINDOW_MILLIS = 5 * 60 * 1_000;
const KEY_PROOF_TIMESTAMP_WINDOW_MILLIS = 5 * 60 * 1_000;
const DOCUMENT_ID_PATTERN = /^[A-Za-z0-9_-]{1,128}$/;
const SHA256_HEX_PATTERN = /^[0-9a-f]{64}$/;

export type BackendDependencies = {
  database: Firestore;
  now: () => number;
  randomId: () => string;
};

type CreateSurveyResponse = {
  surveyId: string;
};

type SubmitVoteResponse = {
  surveyId: string;
  receiptId: string;
  keyId: string;
  commitmentHash: string;
  acceptedAt: number;
  canonicalPayload: string;
  publicKey: string;
  signature: string;
};

type RegisterVotingKeyResponse = {
  keyId: string;
  deviceId: string;
  status: "active";
  registeredAt: number;
};

type RevokeVotingKeyResponse = {
  keyId: string;
  status: "rotated" | "revoked";
};

type PublicSurveyDocumentInput = {
  id: string;
  title: string;
  description: string;
  creatorId: string;
  creatorName: string;
  createdAt: number;
  expiresAt: number;
  isActive: boolean;
  allowMultipleChoices: boolean;
  options: SurveyOptionData[];
};

export const registerVotingKey = onCall(CALLABLE_OPTIONS, async (request) => {
  const uid = request.auth?.uid;
  if (!uid) throw new HttpsError("unauthenticated", "Sign in before registering a key.");
  return executeRegisterVotingKey(uid, request.data);
});

export async function executeRegisterVotingKey(
  uid: string,
  data: unknown,
  providedDependencies?: BackendDependencies,
): Promise<RegisterVotingKeyResponse> {
  const dependencies = providedDependencies ?? defaultDependencies();
  const input = parseRegisterVotingKey(data);
  const now = dependencies.now();
  if (Math.abs(now - input.signedAt) > KEY_PROOF_TIMESTAMP_WINDOW_MILLIS) {
    throw new HttpsError(
      "invalid-argument",
      "Key registration proof is outside the accepted time window.",
    );
  }
  const proof = buildVotingKeyRegistrationPayload(uid, input);
  if (!verifySignature(proof, input.publicKey, input.signature)) {
    throw new HttpsError("permission-denied", "Key registration proof is invalid.");
  }

  const database = dependencies.database;
  const userRef = database.collection("users").doc(uid);
  const keyRef = userRef.collection("keys").doc(input.keyId);
  const deviceRef = userRef.collection("devices").doc(input.deviceId);
  let response: RegisterVotingKeyResponse | undefined;

  await database.runTransaction(async (transaction) => {
    const [userSnap, keySnap, deviceSnap] = await Promise.all([
      transaction.get(userRef),
      transaction.get(keyRef),
      transaction.get(deviceRef),
    ]);
    if (!userSnap.exists) {
      throw new HttpsError("failed-precondition", "User profile was not found.");
    }
    parseUserProfile(userSnap.data(), uid);

    const device = deviceSnap.exists ?
      parseStoredVotingDevice(deviceSnap.data(), input.deviceId) :
      undefined;
    if (keySnap.exists) {
      const existingKey = parseStoredVotingKey(keySnap.data(), input.keyId);
      if (
        existingKey.deviceId !== input.deviceId ||
        existingKey.publicKey !== input.publicKey
      ) {
        throw new HttpsError("failed-precondition", "Stored voting key is invalid.");
      }
      if (
        existingKey.status !== "active" ||
        device?.activeKeyId !== input.keyId
      ) {
        throw new HttpsError(
          "failed-precondition",
          "This voting key is no longer active. Generate a replacement key.",
        );
      }
      response = {
        keyId: input.keyId,
        deviceId: input.deviceId,
        status: "active",
        registeredAt: existingKey.registeredAt,
      };
      return;
    }

    let previousKey:
      | { reference: DocumentReference; data: ReturnType<typeof parseStoredVotingKey> }
      | undefined;
    if (device?.activeKeyId) {
      const previousKeyRef = userRef.collection("keys").doc(device.activeKeyId);
      const previousKeySnap = await transaction.get(previousKeyRef);
      if (!previousKeySnap.exists) {
        throw new HttpsError("failed-precondition", "Stored voting device is invalid.");
      }
      const previousKeyData = parseStoredVotingKey(
        previousKeySnap.data(),
        device.activeKeyId,
      );
      if (
        previousKeyData.deviceId !== input.deviceId ||
        previousKeyData.status !== "active"
      ) {
        throw new HttpsError("failed-precondition", "Stored voting device is invalid.");
      }
      previousKey = {
        reference: previousKeyRef,
        data: previousKeyData,
      };
    }

    if (previousKey) {
      transaction.set(previousKey.reference, {
        keyId: previousKey.data.keyId,
        deviceId: previousKey.data.deviceId,
        publicKey: previousKey.data.publicKey,
        status: "rotated",
        registeredAt: previousKey.data.registeredAt,
        rotatedAt: now,
      });
    }
    transaction.set(keyRef, {
      keyId: input.keyId,
      deviceId: input.deviceId,
      publicKey: input.publicKey,
      status: "active",
      registeredAt: now,
    });
    transaction.set(deviceRef, {
      deviceId: input.deviceId,
      activeKeyId: input.keyId,
      registeredAt: device?.registeredAt ?? now,
      updatedAt: now,
    });
    response = {
      keyId: input.keyId,
      deviceId: input.deviceId,
      status: "active",
      registeredAt: now,
    };
  });

  if (!response) {
    throw new HttpsError("internal", "Key registration did not produce a response.");
  }
  return response;
}

export const revokeVotingKey = onCall(CALLABLE_OPTIONS, async (request) => {
  const uid = request.auth?.uid;
  if (!uid) throw new HttpsError("unauthenticated", "Sign in before revoking a key.");
  return executeRevokeVotingKey(uid, request.data);
});

export async function executeRevokeVotingKey(
  uid: string,
  data: unknown,
  providedDependencies?: BackendDependencies,
): Promise<RevokeVotingKeyResponse> {
  const dependencies = providedDependencies ?? defaultDependencies();
  const input = parseRevokeVotingKey(data);
  const database = dependencies.database;
  const now = dependencies.now();
  const keyRef = database.doc(`users/${uid}/keys/${input.keyId}`);
  let response: RevokeVotingKeyResponse | undefined;

  await database.runTransaction(async (transaction) => {
    const keySnap = await transaction.get(keyRef);
    if (!keySnap.exists) {
      throw new HttpsError("not-found", "Voting key was not found.");
    }
    const key = parseStoredVotingKey(keySnap.data(), input.keyId);
    if (key.status !== "active") {
      response = {
        keyId: input.keyId,
        status: key.status,
      };
      return;
    }

    const deviceRef = database.doc(`users/${uid}/devices/${key.deviceId}`);
    const deviceSnap = await transaction.get(deviceRef);
    if (!deviceSnap.exists) {
      throw new HttpsError("failed-precondition", "Stored voting device is invalid.");
    }
    const device = parseStoredVotingDevice(deviceSnap.data(), key.deviceId);
    if (device.activeKeyId !== input.keyId) {
      throw new HttpsError("failed-precondition", "Stored voting device is invalid.");
    }

    transaction.set(keyRef, {
      keyId: key.keyId,
      deviceId: key.deviceId,
      publicKey: key.publicKey,
      status: "revoked",
      registeredAt: key.registeredAt,
      revokedAt: now,
    });
    transaction.set(deviceRef, {
      ...device,
      activeKeyId: null,
      updatedAt: now,
    });
    response = {
      keyId: input.keyId,
      status: "revoked",
    };
  });

  if (!response) {
    throw new HttpsError("internal", "Key revocation did not produce a response.");
  }
  return response;
}

export const createSurvey = onCall(CALLABLE_OPTIONS, async (request) => {
  const uid = request.auth?.uid;
  if (!uid) throw new HttpsError("unauthenticated", "Sign in before creating a survey.");
  return executeCreateSurvey(uid, request.data);
});

export async function executeCreateSurvey(
  uid: string,
  data: unknown,
  providedDependencies?: BackendDependencies,
): Promise<CreateSurveyResponse> {
  const dependencies = providedDependencies ?? defaultDependencies();
  const database = dependencies.database;
  const now = dependencies.now();
  const input = parseCreateSurvey(data, now);
  const requestHash = createSurveyRequestHash(input);
  const userRef = database.collection("users").doc(uid);
  const operationRef = userRef.collection("operations").doc(input.operationId);
  const quotaRef = userRef.collection("limits").doc("createSurvey");
  const surveyRef = database.collection("surveys").doc();
  const options = input.options.map((title) => ({
    id: dependencies.randomId(),
    title,
  }));
  const resultRef = surveyRef.collection("results").doc("final");
  let response: CreateSurveyResponse | undefined;

  await database.runTransaction(async (transaction) => {
    const operationSnap = await transaction.get(operationRef);
    if (operationSnap.exists) {
      response = parseCreateSurveyOperation(operationSnap.data(), requestHash);
      return;
    }

    const [userSnap, quotaSnap] = await Promise.all([
      transaction.get(userRef),
      transaction.get(quotaRef),
    ]);
    if (!userSnap.exists) {
      throw new HttpsError("failed-precondition", "User profile was not found.");
    }
    const profile = parseUserProfile(userSnap.data(), uid);
    const nextQuota = buildNextCreateSurveyQuota(quotaSnap.data(), now);

    response = { surveyId: surveyRef.id };
    transaction.set(surveyRef, buildPublicSurveyDocument({
      id: surveyRef.id,
      title: input.title,
      description: input.description,
      creatorId: uid,
      creatorName: profile.displayName,
      createdAt: now,
      expiresAt: input.expiresAt,
      isActive: true,
      allowMultipleChoices: input.allowMultipleChoices,
      options,
    }));
    transaction.set(resultRef, buildInitialResult(surveyRef.id, options));
    transaction.set(
      userRef,
      { createdSurveyIds: FieldValue.arrayUnion(surveyRef.id) },
      { merge: true },
    );
    transaction.set(quotaRef, nextQuota);
    transaction.set(operationRef, {
      kind: "createSurvey",
      requestHash,
      response,
      createdAt: now,
    });
  });

  if (!response) {
    throw new HttpsError("internal", "Survey operation did not produce a response.");
  }
  return response;
}

export const submitVote = onCall(CALLABLE_OPTIONS, async (request) => {
  const uid = request.auth?.uid;
  if (!uid) throw new HttpsError("unauthenticated", "Sign in before voting.");
  return executeSubmitVote(uid, request.data);
});

export async function executeSubmitVote(
  uid: string,
  data: unknown,
  providedDependencies?: BackendDependencies,
): Promise<SubmitVoteResponse> {
  const dependencies = providedDependencies ?? defaultDependencies();
  const input = parseSubmitVote(data);
  const database = dependencies.database;
  const now = dependencies.now();
  const requestHash = submitVoteRequestHash(input);
  const surveyRef = database.collection("surveys").doc(input.surveyId);
  const userRef = database.collection("users").doc(uid);
  const keyRef = userRef.collection("keys").doc(input.keyId);
  const operationRef = userRef.collection("operations").doc(input.operationId);
  const voteRef = surveyRef.collection("votes").doc(uid);
  const resultRef = surveyRef.collection("results").doc("final");
  const receiptId = sha256Hex(input.commitmentHash);
  const receiptRef = surveyRef.collection("receipts").doc(receiptId);
  let response: SubmitVoteResponse | undefined;

  await database.runTransaction(async (transaction) => {
    const operationSnap = await transaction.get(operationRef);
    if (operationSnap.exists) {
      response = parseSubmitVoteOperation(operationSnap.data(), requestHash, input, uid);
      return;
    }
    if (Math.abs(now - input.signedAt) > VOTE_TIMESTAMP_WINDOW_MILLIS) {
      throw new HttpsError("invalid-argument", "Vote timestamp is outside the accepted window.");
    }

    const [surveySnap, voteSnap, userSnap, keySnap, resultSnap] = await Promise.all([
      transaction.get(surveyRef),
      transaction.get(voteRef),
      transaction.get(userRef),
      transaction.get(keyRef),
      transaction.get(resultRef),
    ]);

    if (!surveySnap.exists) throw new HttpsError("not-found", "Survey was not found.");
    if (voteSnap.exists) throw new HttpsError("already-exists", "User already voted.");
    if (!userSnap.exists) throw new HttpsError("failed-precondition", "User profile was not found.");
    if (!keySnap.exists) {
      throw new HttpsError("permission-denied", "Voting key is not registered.");
    }
    if (!resultSnap.exists) {
      throw new HttpsError("failed-precondition", "Survey result state was not found.");
    }
    parseUserProfile(userSnap.data(), uid);
    const votingKey = parseStoredVotingKey(keySnap.data(), input.keyId);
    if (votingKey.status !== "active" || votingKey.publicKey !== input.publicKey) {
      throw new HttpsError("permission-denied", "Voting key is not active for this user.");
    }

    const survey = parseStoredSurvey(surveySnap.data(), input.surveyId);
    if (!survey.isActive || survey.expiresAt <= now) {
      throw new HttpsError("failed-precondition", "Survey is closed.");
    }

    const allowedOptionIds = new Set(survey.options.map((option) => option.id));
    if (survey.allowMultipleChoices !== true && input.optionIds.length > 1) {
      throw new HttpsError("invalid-argument", "Survey allows only one option.");
    }
    if (input.optionIds.some((optionId) => !allowedOptionIds.has(optionId))) {
      throw new HttpsError("invalid-argument", "Selected option does not belong to this survey.");
    }

    const payload = buildPayload(
      input.surveyId,
      uid,
      input.keyId,
      input.optionIds,
      input.signedAt,
    );
    if (!verifySignature(payload, input.publicKey, input.signature)) {
      throw new HttpsError("permission-denied", "Vote signature is invalid.");
    }

    const expectedCommitment = sha256Base64(`${payload}|${input.signature}|${input.publicKey}`);
    if (expectedCommitment !== input.commitmentHash) {
      throw new HttpsError("invalid-argument", "Vote commitment does not match payload.");
    }

    const nextResult = applyVoteToResult(
      parseStoredResult(resultSnap.data(), input.surveyId, allowedOptionIds),
      input.optionIds,
    );

    response = {
      surveyId: input.surveyId,
      receiptId,
      keyId: input.keyId,
      commitmentHash: input.commitmentHash,
      acceptedAt: now,
      canonicalPayload: payload,
      publicKey: input.publicKey,
      signature: input.signature,
    };
    transaction.set(voteRef, {
      userId: uid,
      surveyId: input.surveyId,
      keyId: input.keyId,
      optionIds: input.optionIds,
      signedAt: input.signedAt,
      acceptedAt: now,
      publicKey: input.publicKey,
      signature: input.signature,
      commitmentHash: input.commitmentHash,
    });
    transaction.set(receiptRef, {
      surveyId: input.surveyId,
      receiptId,
      commitmentHash: input.commitmentHash,
      acceptedAt: now,
    });
    transaction.update(resultRef, nextResult);
    transaction.set(
      userRef,
      { votedSurveyIds: FieldValue.arrayUnion(input.surveyId) },
      { merge: true },
    );
    transaction.set(operationRef, {
      kind: "submitVote",
      requestHash,
      response,
      createdAt: now,
    });
  });

  if (!response) {
    throw new HttpsError("internal", "Vote operation did not produce a response.");
  }
  return response;
}

export function buildNextCreateSurveyQuota(
  data: unknown,
  now: number,
): { windowStartedAt: number; count: number } {
  if (data === undefined) {
    return { windowStartedAt: now, count: 1 };
  }
  const quota = requireStoredExactRecord(
    data,
    ["windowStartedAt", "count"],
    "survey creation quota",
  );
  const windowStartedAt = requireStoredNonNegativeInteger(
    quota.windowStartedAt,
    "survey creation quota",
  );
  const count = requireStoredNonNegativeInteger(quota.count, "survey creation quota");
  if (windowStartedAt > now || count > CREATE_SURVEY_LIMIT) {
    throw invalidStoredOperationState("survey creation quota");
  }
  if (now - windowStartedAt >= CREATE_SURVEY_WINDOW_MILLIS) {
    return { windowStartedAt: now, count: 1 };
  }
  if (count >= CREATE_SURVEY_LIMIT) {
    throw new HttpsError(
      "resource-exhausted",
      "Survey creation limit reached. Try again after the current window.",
    );
  }
  return { windowStartedAt, count: count + 1 };
}

function parseCreateSurveyOperation(
  data: unknown,
  expectedRequestHash: string,
): CreateSurveyResponse {
  const response = parseOperationEnvelope(data, "createSurvey", expectedRequestHash);
  const record = requireStoredExactRecord(response, ["surveyId"], "createSurvey response");
  if (typeof record.surveyId !== "string" || !DOCUMENT_ID_PATTERN.test(record.surveyId)) {
    throw invalidStoredOperationState("createSurvey response");
  }
  return { surveyId: record.surveyId };
}

function parseSubmitVoteOperation(
  data: unknown,
  expectedRequestHash: string,
  input: SubmitVoteInput,
  uid: string,
): SubmitVoteResponse {
  const response = parseOperationEnvelope(data, "submitVote", expectedRequestHash);
  const record = requireStoredExactRecord(
    response,
    [
      "surveyId",
      "receiptId",
      "keyId",
      "commitmentHash",
      "acceptedAt",
      "canonicalPayload",
      "publicKey",
      "signature",
    ],
    "submitVote response",
  );
  const expectedPayload = buildPayload(
    input.surveyId,
    uid,
    input.keyId,
    input.optionIds,
    input.signedAt,
  );
  if (
    record.surveyId !== input.surveyId ||
    record.keyId !== input.keyId ||
    typeof record.receiptId !== "string" ||
    record.receiptId !== sha256Hex(input.commitmentHash) ||
    record.commitmentHash !== input.commitmentHash ||
    record.canonicalPayload !== expectedPayload ||
    record.publicKey !== input.publicKey ||
    record.signature !== input.signature
  ) {
    throw invalidStoredOperationState("submitVote response");
  }
  return {
    surveyId: record.surveyId,
    receiptId: record.receiptId,
    keyId: input.keyId,
    commitmentHash: input.commitmentHash,
    acceptedAt: requireStoredNonNegativeInteger(record.acceptedAt, "submitVote response"),
    canonicalPayload: expectedPayload,
    publicKey: input.publicKey,
    signature: input.signature,
  };
}

function parseOperationEnvelope(
  data: unknown,
  expectedKind: "createSurvey" | "submitVote",
  expectedRequestHash: string,
): unknown {
  const operation = requireStoredExactRecord(
    data,
    ["kind", "requestHash", "response", "createdAt"],
    "operation",
  );
  if (
    (operation.kind !== "createSurvey" && operation.kind !== "submitVote") ||
    typeof operation.requestHash !== "string" ||
    !SHA256_HEX_PATTERN.test(operation.requestHash)
  ) {
    throw invalidStoredOperationState("operation");
  }
  requireStoredNonNegativeInteger(operation.createdAt, "operation");
  if (operation.kind !== expectedKind || operation.requestHash !== expectedRequestHash) {
    throw new HttpsError(
      "already-exists",
      "Operation ID was already used for a different request.",
    );
  }
  return operation.response;
}

function requireStoredExactRecord(
  data: unknown,
  keys: string[],
  name: string,
): Record<string, unknown> {
  if (data === null || typeof data !== "object" || Array.isArray(data)) {
    throw invalidStoredOperationState(name);
  }
  const record = data as Record<string, unknown>;
  const actualKeys = Object.keys(record).sort();
  const expectedKeys = [...keys].sort();
  if (
    actualKeys.length !== expectedKeys.length ||
    actualKeys.some((key, index) => key !== expectedKeys[index])
  ) {
    throw invalidStoredOperationState(name);
  }
  return record;
}

function requireStoredNonNegativeInteger(value: unknown, name: string): number {
  if (typeof value !== "number" || !Number.isSafeInteger(value) || value < 0) {
    throw invalidStoredOperationState(name);
  }
  return value;
}

function invalidStoredOperationState(name: string): HttpsError {
  return new HttpsError("failed-precondition", `Stored ${name} is invalid.`);
}

function defaultDependencies(): BackendDependencies {
  return {
    database: getFirestore(),
    now: Date.now,
    randomId: randomUUID,
  };
}

export function buildPublicSurveyDocument(input: PublicSurveyDocumentInput) {
  return {
    ...input,
    resultsVisibility: RESULTS_VISIBILITY_AFTER_CLOSE,
  };
}

export function buildInitialResult(
  surveyId: string,
  options: SurveyOptionData[],
): SurveyResultData {
  return {
    surveyId,
    totalVotes: 0,
    optionVotes: Object.fromEntries(options.map((option) => [option.id, 0])),
  };
}

export function applyVoteToResult(
  currentResult: SurveyResultData,
  optionIds: string[],
): SurveyResultData {
  const optionVotes = { ...currentResult.optionVotes };
  for (const optionId of optionIds) {
    optionVotes[optionId] = Number(optionVotes[optionId] ?? 0) + 1;
  }
  return {
    surveyId: currentResult.surveyId,
    totalVotes: Number(currentResult.totalVotes) + 1,
    optionVotes,
  };
}

export function buildPayload(
  surveyId: string,
  userId: string,
  keyId: string,
  optionIds: string[],
  signedAt: number,
): string {
  return [
    `survey=${surveyId}`,
    `user=${userId}`,
    `key=${keyId}`,
    `options=${[...optionIds].sort().join(",")}`,
    `signedAt=${signedAt}`,
  ].join("|");
}

function sha256Base64(payload: string): string {
  return createHash("sha256").update(payload).digest("base64");
}

function sha256Hex(payload: string): string {
  return createHash("sha256").update(payload).digest("hex");
}
