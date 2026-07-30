import { strict as assert } from "node:assert";
import { createHash, generateKeyPairSync, sign } from "node:crypto";
import { HttpsError } from "firebase-functions/v2/https";
import {
  buildNextCreateSurveyQuota,
  buildPayload,
  buildVotingKeyRegistrationPayload,
  CALLABLE_OPTIONS,
  createSurvey,
  createSurveyRequestHash,
  parseCreateSurvey,
  parseRegisterVotingKey,
  parseRevokeVotingKey,
  parseStoredResult,
  parseStoredSurvey,
  parseStoredVotingDevice,
  parseStoredVotingKey,
  parseSubmitVote,
  parseUserProfile,
  registerVotingKey,
  revokeVotingKey,
  submitVote,
  submitVoteRequestHash,
} from "../src";

const NOW = 1_800_000_000_000;
const OPERATION_ID = "01234567-89ab-4cde-8fab-0123456789ab";
const { privateKey, publicKey } = generateKeyPairSync("ec", {
  namedCurve: "prime256v1",
});
const PUBLIC_KEY_BASE64 = publicKey
  .export({ format: "der", type: "spki" })
  .toString("base64");
const KEY_ID = createHash("sha256")
  .update(publicKey.export({ format: "der", type: "spki" }))
  .digest("hex");
const DEVICE_ID = "b".repeat(64);

function expectHttpsError(
  code: HttpsError["code"],
  block: () => unknown,
): void {
  assert.throws(block, (error: unknown) => {
    return error instanceof HttpsError && error.code === code;
  });
}

async function expectAsyncHttpsError(
  code: HttpsError["code"],
  block: () => Promise<unknown>,
): Promise<void> {
  await assert.rejects(block, (error: unknown) => {
    return error instanceof HttpsError && error.code === code;
  });
}

function validCreateSurveyInput() {
  return {
    operationId: OPERATION_ID,
    title: "Community garden",
    description: "Choose the next improvement for our community garden.",
    allowMultipleChoices: false,
    options: ["More benches", "Native flowers"],
    expiresAt: NOW + 60_000,
  };
}

function validVoteInput() {
  const payload = buildPayload("survey-1", "user-1", KEY_ID, ["option-1"], NOW);
  const signature = sign("SHA256", Buffer.from(payload), privateKey).toString("base64");
  return {
    operationId: OPERATION_ID,
    surveyId: "survey-1",
    keyId: KEY_ID,
    optionIds: ["option-1"],
    signedAt: NOW,
    publicKey: PUBLIC_KEY_BASE64,
    signature,
    commitmentHash: createHash("sha256")
      .update(`${payload}|${signature}|${PUBLIC_KEY_BASE64}`)
      .digest("base64"),
  };
}

function validKeyRegistrationInput() {
  const unsigned = {
    deviceId: DEVICE_ID,
    keyId: KEY_ID,
    signedAt: NOW,
  };
  const payload = buildVotingKeyRegistrationPayload("user-1", unsigned);
  return {
    ...unsigned,
    publicKey: PUBLIC_KEY_BASE64,
    signature: sign("SHA256", Buffer.from(payload), privateKey).toString("base64"),
  };
}

describe("callable request contracts", () => {
  it("keeps every mutation on the bounded App Check callable configuration", () => {
    assert.deepEqual(CALLABLE_OPTIONS, {
      region: "us-central1",
      enforceAppCheck: true,
      consumeAppCheckToken: true,
      timeoutSeconds: 30,
      memory: "256MiB",
      maxInstances: 5,
    });
  });

  it("rejects unauthenticated calls before parsing or touching Firestore", async () => {
    await expectAsyncHttpsError(
      "unauthenticated",
      () => createSurvey.run({ data: null } as never),
    );
    await expectAsyncHttpsError(
      "unauthenticated",
      () => registerVotingKey.run({ data: null } as never),
    );
    await expectAsyncHttpsError(
      "unauthenticated",
      () => revokeVotingKey.run({ data: null } as never),
    );
    await expectAsyncHttpsError(
      "unauthenticated",
      () => submitVote.run({ data: null } as never),
    );
  });

  it("normalizes bounded survey text while preserving exact request shape", () => {
    assert.deepEqual(
      parseCreateSurvey(
        {
          ...validCreateSurveyInput(),
          title: "  Community garden  ",
          options: [" More benches ", "Native flowers"],
        },
        NOW,
      ),
      validCreateSurveyInput(),
    );
  });

  it("rejects null, extra fields, wrong booleans, non-integers, and excessive lifetime", () => {
    expectHttpsError("invalid-argument", () => parseCreateSurvey(null, NOW));
    expectHttpsError("invalid-argument", () => parseCreateSurvey({
      ...validCreateSurveyInput(),
      unexpected: true,
    }, NOW));
    expectHttpsError("invalid-argument", () => parseCreateSurvey({
      ...validCreateSurveyInput(),
      allowMultipleChoices: "false",
    }, NOW));
    expectHttpsError("invalid-argument", () => parseCreateSurvey({
      ...validCreateSurveyInput(),
      expiresAt: NOW + 0.5,
    }, NOW));
    expectHttpsError("invalid-argument", () => parseCreateSurvey({
      ...validCreateSurveyInput(),
      expiresAt: NOW + 32 * 24 * 60 * 60 * 1_000,
    }, NOW));
    expectHttpsError("invalid-argument", () => parseCreateSurvey({
      ...validCreateSurveyInput(),
      operationId: "not-a-uuid",
    }, NOW));
  });

  it("accepts canonical P-256 vote material and sorts option identifiers", () => {
    const input = validVoteInput();
    assert.deepEqual(
      parseSubmitVote({
        ...input,
        optionIds: ["option-2", "option-1"],
      }),
      {
        ...input,
        optionIds: ["option-1", "option-2"],
      },
    );
  });

  it("rejects malformed identifiers and non-canonical cryptographic encodings", () => {
    const input = validVoteInput();
    const p384PublicKey = generateKeyPairSync("ec", {
      namedCurve: "secp384r1",
    }).publicKey.export({ format: "der", type: "spki" }).toString("base64");
    expectHttpsError("invalid-argument", () => parseSubmitVote({
      ...input,
      unexpected: true,
    }));
    expectHttpsError("invalid-argument", () => parseSubmitVote({
      ...input,
      surveyId: "surveys/survey-1",
    }));
    expectHttpsError("invalid-argument", () => parseSubmitVote({
      ...input,
      optionIds: ["option-1", "option-1"],
    }));
    expectHttpsError("invalid-argument", () => parseSubmitVote({
      ...input,
      publicKey: Buffer.from("not a key").toString("base64"),
    }));
    expectHttpsError("invalid-argument", () => parseSubmitVote({
      ...input,
      publicKey: p384PublicKey,
    }));
    expectHttpsError("invalid-argument", () => parseSubmitVote({
      ...input,
      signature: `${input.signature}=`,
    }));
    expectHttpsError("invalid-argument", () => parseSubmitVote({
      ...input,
      commitmentHash: Buffer.alloc(31).toString("base64"),
    }));
  });

  it("accepts a proof-of-possession key registration and rejects mismatched IDs", () => {
    const registration = validKeyRegistrationInput();
    assert.deepEqual(
      parseRegisterVotingKey(registration),
      registration,
    );
    expectHttpsError("invalid-argument", () => parseRegisterVotingKey({
      ...validKeyRegistrationInput(),
      keyId: "a".repeat(64),
    }));
    expectHttpsError("invalid-argument", () => parseRegisterVotingKey({
      ...validKeyRegistrationInput(),
      unexpected: true,
    }));
  });

  it("accepts only the exact revoke-key request shape", () => {
    assert.deepEqual(
      parseRevokeVotingKey({ keyId: KEY_ID }),
      { keyId: KEY_ID },
    );
    expectHttpsError("invalid-argument", () => parseRevokeVotingKey({
      keyId: KEY_ID,
      unexpected: true,
    }));
    expectHttpsError("invalid-argument", () => parseRevokeVotingKey({
      keyId: "A".repeat(64),
    }));
  });

  it("hashes normalized request content but excludes only the operation identifier", () => {
    const firstCreate = parseCreateSurvey(validCreateSurveyInput(), NOW);
    const secondCreate = {
      ...firstCreate,
      operationId: "fedcba98-7654-4321-8abc-fedcba987654",
    };
    assert.equal(createSurveyRequestHash(firstCreate), createSurveyRequestHash(secondCreate));

    const firstVote = parseSubmitVote(validVoteInput());
    const secondVote = {
      ...firstVote,
      operationId: "fedcba98-7654-4321-8abc-fedcba987654",
    };
    assert.equal(submitVoteRequestHash(firstVote), submitVoteRequestHash(secondVote));
    assert.notEqual(
      submitVoteRequestHash(firstVote),
      submitVoteRequestHash({ ...firstVote, signedAt: firstVote.signedAt + 1 }),
    );
  });
});

describe("survey creation quota", () => {
  it("initializes, increments, and resets a bounded rolling window", () => {
    assert.deepEqual(buildNextCreateSurveyQuota(undefined, NOW), {
      windowStartedAt: NOW,
      count: 1,
    });
    assert.deepEqual(
      buildNextCreateSurveyQuota({ windowStartedAt: NOW - 1_000, count: 1 }, NOW),
      { windowStartedAt: NOW - 1_000, count: 2 },
    );
    assert.deepEqual(
      buildNextCreateSurveyQuota(
        { windowStartedAt: NOW - 24 * 60 * 60 * 1_000, count: 10 },
        NOW,
      ),
      { windowStartedAt: NOW, count: 1 },
    );
  });

  it("rejects exhausted and poisoned quota state", () => {
    expectHttpsError("resource-exhausted", () => buildNextCreateSurveyQuota(
      { windowStartedAt: NOW - 1_000, count: 10 },
      NOW,
    ));
    expectHttpsError("failed-precondition", () => buildNextCreateSurveyQuota(
      { windowStartedAt: NOW + 1, count: 1 },
      NOW,
    ));
    expectHttpsError("failed-precondition", () => buildNextCreateSurveyQuota(
      { windowStartedAt: NOW, count: "one" },
      NOW,
    ));
  });
});

describe("stored document contracts", () => {
  const survey = {
    id: "survey-1",
    title: "Community garden",
    description: "Choose the next improvement for our community garden.",
    creatorId: "user-1",
    creatorName: "Alice",
    createdAt: NOW,
    expiresAt: NOW + 60_000,
    isActive: true,
    resultsVisibility: "after_close",
    allowMultipleChoices: false,
    options: [
      { id: "option-1", title: "More benches" },
      { id: "option-2", title: "Native flowers" },
    ],
  };

  it("accepts the complete profile shape and rejects poisoned identity data", () => {
    assert.equal(
      parseUserProfile({
        id: "user-1",
        displayName: "Alice",
        createdSurveyIds: ["survey-1"],
        votedSurveyIds: [],
      }, "user-1").displayName,
      "Alice",
    );
    assert.equal(
      parseUserProfile({
        id: "user-1",
        displayName: "Alice",
        publicKey: PUBLIC_KEY_BASE64,
      }, "user-1").publicKey,
      PUBLIC_KEY_BASE64,
    );
    expectHttpsError("failed-precondition", () => parseUserProfile({
      id: "user-2",
      displayName: "Mallory",
    }, "user-1"));
    expectHttpsError("failed-precondition", () => parseUserProfile({
      id: "user-1",
      displayName: " Alice ",
    }, "user-1"));
    expectHttpsError("failed-precondition", () => parseUserProfile({
      id: "user-1",
      displayName: "Alice",
      role: "admin",
    }, "user-1"));
    expectHttpsError("failed-precondition", () => parseUserProfile({
      id: "user-1",
      displayName: "Alice",
      publicKey: "legacy-but-malformed",
    }, "user-1"));
  });

  it("validates active, rotated, and revoked key history plus device state", () => {
    const activeKey = {
      keyId: KEY_ID,
      deviceId: DEVICE_ID,
      publicKey: PUBLIC_KEY_BASE64,
      status: "active",
      registeredAt: NOW,
    };
    assert.equal(parseStoredVotingKey(activeKey, KEY_ID).status, "active");
    assert.equal(
      parseStoredVotingKey(
        { ...activeKey, status: "rotated", rotatedAt: NOW + 1 },
        KEY_ID,
      ).status,
      "rotated",
    );
    assert.equal(
      parseStoredVotingKey(
        { ...activeKey, status: "revoked", revokedAt: NOW + 1 },
        KEY_ID,
      ).status,
      "revoked",
    );
    assert.deepEqual(
      parseStoredVotingDevice({
        deviceId: DEVICE_ID,
        activeKeyId: KEY_ID,
        registeredAt: NOW,
        updatedAt: NOW,
      }, DEVICE_ID).activeKeyId,
      KEY_ID,
    );
    expectHttpsError("failed-precondition", () => parseStoredVotingKey(
      { ...activeKey, status: "active", rotatedAt: NOW + 1 },
      KEY_ID,
    ));
  });

  it("revalidates survey and result shape before applying a vote", () => {
    const parsedSurvey = parseStoredSurvey(survey, "survey-1");
    assert.equal(parsedSurvey.options.length, 2);
    assert.deepEqual(
      parseStoredResult(
        {
          surveyId: "survey-1",
          totalVotes: 1,
          optionVotes: {
            "option-1": 1,
            "option-2": 0,
          },
        },
        "survey-1",
        new Set(["option-1", "option-2"]),
      ),
      {
        surveyId: "survey-1",
        totalVotes: 1,
        optionVotes: {
          "option-1": 1,
          "option-2": 0,
        },
      },
    );
    expectHttpsError("failed-precondition", () => parseStoredSurvey({
      ...survey,
      unexpected: true,
    }, "survey-1"));
    expectHttpsError("failed-precondition", () => parseStoredResult(
      {
        surveyId: "survey-1",
        totalVotes: 1,
        optionVotes: {
          "option-1": 1,
          "option-injected": 0,
        },
      },
      "survey-1",
      new Set(["option-1", "option-2"]),
    ));
  });
});
