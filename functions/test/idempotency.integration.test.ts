import { strict as assert } from "node:assert";
import { createHash, generateKeyPairSync, randomUUID, sign } from "node:crypto";
import { Firestore } from "@google-cloud/firestore";
import { HttpsError } from "firebase-functions/v2/https";
import {
  buildVotingKeyRegistrationPayload,
  buildPayload,
  executeCreateSurvey,
  executeRegisterVotingKey,
  executeRevokeVotingKey,
  executeSubmitVote,
} from "../src";

const describeWithEmulator = process.env.FIRESTORE_EMULATOR_HOST ? describe : describe.skip;

describeWithEmulator("transactional idempotency", function () {
  this.timeout(15_000);

  const emulatorPrivateKey = generateKeyPairSync("rsa", { modulusLength: 2_048 })
    .privateKey
    .export({ format: "pem", type: "pkcs8" })
    .toString();
  const database = new Firestore({
    projectId: "dvote-test",
    credentials: {
      client_email: "firestore-emulator@dvote-test.iam.gserviceaccount.com",
      private_key: emulatorPrivateKey,
    },
  });
  const now = 1_900_000_000_000;
  const uid = "user-idempotency";
  const createOperationId = "01234567-89ab-4cde-8fab-0123456789ab";
  const voteOperationId = "11111111-2222-4333-8444-555555555555";
  const { privateKey, publicKey } = generateKeyPairSync("ec", {
    namedCurve: "prime256v1",
  });
  const publicKeyBase64 = publicKey
    .export({ format: "der", type: "spki" })
    .toString("base64");
  const keyId = createHash("sha256")
    .update(publicKey.export({ format: "der", type: "spki" }))
    .digest("hex");
  const deviceId = "b".repeat(64);
  const dependencies = {
    database,
    now: () => now,
    randomId: randomUUID,
  };

  async function createTestSurvey(
    operationId: string,
    allowMultipleChoices = false,
  ) {
    const response = await executeCreateSurvey(uid, {
      operationId,
      title: "Community garden",
      description: "Choose the next improvement for our community garden.",
      allowMultipleChoices,
      options: ["More benches", "Native flowers"],
      expiresAt: now + 60_000,
    }, dependencies);
    const snapshot = await database.doc(`surveys/${response.surveyId}`).get();
    return {
      surveyId: response.surveyId,
      optionIds: (snapshot.get("options") as Array<{ id: string }>).map(
        (option) => option.id,
      ),
    };
  }

  function signedVoteRequest(
    surveyId: string,
    optionIds: string[],
    operationId: string,
    signedAt = now,
  ) {
    const payload = buildPayload(surveyId, uid, keyId, optionIds, signedAt);
    const signature = sign("SHA256", Buffer.from(payload), privateKey).toString("base64");
    return {
      operationId,
      surveyId,
      keyId,
      optionIds,
      signedAt,
      publicKey: publicKeyBase64,
      signature,
      commitmentHash: createHash("sha256")
        .update(`${payload}|${signature}|${publicKeyBase64}`)
        .digest("base64"),
    };
  }

  beforeEach(async () => {
    await Promise.all([
      database.recursiveDelete(database.collection("users")),
      database.recursiveDelete(database.collection("surveys")),
    ]);
    await database.collection("users").doc(uid).set({
      id: uid,
      displayName: "Alice",
    });
    await database.doc(`users/${uid}/keys/${keyId}`).set({
      keyId,
      deviceId,
      publicKey: publicKeyBase64,
      status: "active",
      registeredAt: now,
    });
    await database.doc(`users/${uid}/devices/${deviceId}`).set({
      deviceId,
      activeKeyId: keyId,
      registeredAt: now,
      updatedAt: now,
    });
  });

  after(async () => {
    await database.terminate();
  });

  it("returns one create result for sequential and concurrent identical retries", async () => {
    const request = {
      operationId: createOperationId,
      title: "Community garden",
      description: "Choose the next improvement for our community garden.",
      allowMultipleChoices: false,
      options: ["More benches", "Native flowers"],
      expiresAt: now + 60_000,
    };

    const [first, concurrentRetry] = await Promise.all([
      executeCreateSurvey(uid, request, dependencies),
      executeCreateSurvey(uid, request, dependencies),
    ]);
    const sequentialRetry = await executeCreateSurvey(uid, request, dependencies);

    assert.deepEqual(concurrentRetry, first);
    assert.deepEqual(sequentialRetry, first);
    assert.equal(
      (await database.collection("surveys").where("creatorId", "==", uid).get()).size,
      1,
    );
    assert.equal(
      (await database.doc(`users/${uid}/limits/createSurvey`).get()).get("count"),
      1,
    );
    await expectHttpsError("already-exists", () => executeCreateSurvey(
      uid,
      { ...request, title: "A changed survey title" },
      dependencies,
    ));
  });

  it("enforces the rolling create quota without partially creating a survey", async () => {
    const quotaRef = database.doc(`users/${uid}/limits/createSurvey`);
    await quotaRef.set({
      windowStartedAt: now - 1,
      count: 10,
    });

    await expectHttpsError("resource-exhausted", () => executeCreateSurvey(
      uid,
      {
        operationId: createOperationId,
        title: "Community garden",
        description: "Choose the next improvement for our community garden.",
        allowMultipleChoices: false,
        options: ["More benches", "Native flowers"],
        expiresAt: now + 60_000,
      },
      dependencies,
    ));
    assert.equal((await database.collection("surveys").get()).size, 0);
    assert.equal((await quotaRef.get()).get("count"), 10);

    await quotaRef.set({
      windowStartedAt: now - 24 * 60 * 60 * 1_000,
      count: 10,
    });
    await createTestSurvey(createOperationId);
    assert.deepEqual((await quotaRef.get()).data(), {
      windowStartedAt: now,
      count: 1,
    });
    assert.equal((await database.collection("surveys").get()).size, 1);
  });

  it("counts one ballot for concurrent retries and rejects a genuine second ballot", async () => {
    const createResponse = await executeCreateSurvey(uid, {
      operationId: createOperationId,
      title: "Community garden",
      description: "Choose the next improvement for our community garden.",
      allowMultipleChoices: false,
      options: ["More benches", "Native flowers"],
      expiresAt: now + 60_000,
    }, dependencies);
    const surveySnapshot = await database.doc(`surveys/${createResponse.surveyId}`).get();
    const optionId = surveySnapshot.get("options")[0].id as string;
    const payload = buildPayload(createResponse.surveyId, uid, keyId, [optionId], now);
    const signature = sign("SHA256", Buffer.from(payload), privateKey).toString("base64");
    const commitmentHash = createHash("sha256")
      .update(`${payload}|${signature}|${publicKeyBase64}`)
      .digest("base64");
    const request = {
      operationId: voteOperationId,
      surveyId: createResponse.surveyId,
      keyId,
      optionIds: [optionId],
      signedAt: now,
      publicKey: publicKeyBase64,
      signature,
      commitmentHash,
    };

    const [first, concurrentRetry] = await Promise.all([
      executeSubmitVote(uid, request, dependencies),
      executeSubmitVote(uid, request, dependencies),
    ]);
    const lateRetry = await executeSubmitVote(uid, request, {
      ...dependencies,
      now: () => now + 10 * 60 * 1_000,
    });

    assert.deepEqual(concurrentRetry, first);
    assert.deepEqual(lateRetry, first);
    assert.equal(first.canonicalPayload, payload);
    assert.equal(first.publicKey, publicKeyBase64);
    assert.equal(first.signature, signature);
    const receipt = (
      await database.doc(
        `surveys/${createResponse.surveyId}/receipts/${first.receiptId}`,
      ).get()
    ).data();
    assert.deepEqual(receipt, {
      surveyId: createResponse.surveyId,
      receiptId: first.receiptId,
      commitmentHash,
      acceptedAt: now,
    });
    assert.equal("signature" in (receipt ?? {}), false);
    assert.equal("publicKey" in (receipt ?? {}), false);
    assert.equal(
      (await database.doc(`surveys/${createResponse.surveyId}/results/final`).get())
        .get("totalVotes"),
      1,
    );
    await expectHttpsError("already-exists", () => executeSubmitVote(
      uid,
      {
        ...request,
        operationId: "aaaaaaaa-bbbb-4ccc-8ddd-eeeeeeeeeeee",
      },
      dependencies,
    ));
    await expectHttpsError("invalid-argument", () => executeSubmitVote(
      uid,
      {
        ...request,
        operationId: "99999999-8888-4777-8666-555555555555",
      },
      {
        ...dependencies,
        now: () => now + 10 * 60 * 1_000,
      },
    ));
    await expectHttpsError("invalid-argument", () => executeSubmitVote(
      uid,
      signedVoteRequest(
        createResponse.surveyId,
        [optionId],
        "77777777-8888-4999-8aaa-bbbbbbbbbbbb",
        now + 10 * 60 * 1_000,
      ),
      dependencies,
    ));
  });

  it("serializes concurrent ballots from different users into one exact result", async () => {
    const survey = await createTestSurvey(createOperationId);
    const otherUid = "other-voter";
    const otherDeviceId = "c".repeat(64);
    const other = generateKeyPairSync("ec", { namedCurve: "prime256v1" });
    const otherPublicKey = other.publicKey
      .export({ format: "der", type: "spki" })
      .toString("base64");
    const otherKeyId = createHash("sha256")
      .update(other.publicKey.export({ format: "der", type: "spki" }))
      .digest("hex");
    await database.doc(`users/${otherUid}`).set({
      id: otherUid,
      displayName: "Bob",
    });
    await database.doc(`users/${otherUid}/keys/${otherKeyId}`).set({
      keyId: otherKeyId,
      deviceId: otherDeviceId,
      publicKey: otherPublicKey,
      status: "active",
      registeredAt: now,
    });
    await database.doc(`users/${otherUid}/devices/${otherDeviceId}`).set({
      deviceId: otherDeviceId,
      activeKeyId: otherKeyId,
      registeredAt: now,
      updatedAt: now,
    });

    const optionId = survey.optionIds[0];
    const otherPayload = buildPayload(
      survey.surveyId,
      otherUid,
      otherKeyId,
      [optionId],
      now,
    );
    const otherSignature = sign(
      "SHA256",
      Buffer.from(otherPayload),
      other.privateKey,
    ).toString("base64");
    await Promise.all([
      executeSubmitVote(
        uid,
        signedVoteRequest(survey.surveyId, [optionId], voteOperationId),
        dependencies,
      ),
      executeSubmitVote(otherUid, {
        operationId: "88888888-9999-4aaa-8bbb-cccccccccccc",
        surveyId: survey.surveyId,
        keyId: otherKeyId,
        optionIds: [optionId],
        signedAt: now,
        publicKey: otherPublicKey,
        signature: otherSignature,
        commitmentHash: createHash("sha256")
          .update(`${otherPayload}|${otherSignature}|${otherPublicKey}`)
          .digest("base64"),
      }, dependencies),
    ]);

    const result = (
      await database.doc(`surveys/${survey.surveyId}/results/final`).get()
    ).data();
    assert.equal(result?.totalVotes, 2);
    assert.equal(result?.optionVotes[optionId], 2);
    assert.equal(
      (await database.collection(`surveys/${survey.surveyId}/votes`).get()).size,
      2,
    );
  });

  it("rejects invalid vote state without writing a ballot, receipt, or aggregate", async () => {
    const survey = await createTestSurvey(createOperationId);
    const resultRef = database.doc(`surveys/${survey.surveyId}/results/final`);
    const initialResult = (await resultRef.get()).data();

    await expectHttpsError("invalid-argument", () => executeSubmitVote(
      uid,
      signedVoteRequest(
        survey.surveyId,
        survey.optionIds,
        "22222222-3333-4444-8555-666666666666",
      ),
      dependencies,
    ));
    await expectHttpsError("invalid-argument", () => executeSubmitVote(
      uid,
      signedVoteRequest(
        survey.surveyId,
        ["unknown-option"],
        "33333333-4444-4555-8666-777777777777",
      ),
      dependencies,
    ));
    await expectHttpsError("not-found", () => executeSubmitVote(
      uid,
      signedVoteRequest(
        "missing-survey",
        [survey.optionIds[0]],
        "44444444-5555-4666-8777-888888888888",
      ),
      dependencies,
    ));

    const validRequest = signedVoteRequest(
      survey.surveyId,
      [survey.optionIds[0]],
      voteOperationId,
    );
    const validPayload = buildPayload(
      survey.surveyId,
      uid,
      keyId,
      [survey.optionIds[0]],
      now,
    );
    const attacker = generateKeyPairSync("ec", { namedCurve: "prime256v1" });
    const invalidSignature = sign(
      "SHA256",
      Buffer.from(validPayload),
      attacker.privateKey,
    ).toString("base64");
    await expectHttpsError("permission-denied", () => executeSubmitVote(
      uid,
      {
        ...validRequest,
        signature: invalidSignature,
        commitmentHash: createHash("sha256")
          .update(`${validPayload}|${invalidSignature}|${publicKeyBase64}`)
          .digest("base64"),
      },
      dependencies,
    ));
    await expectHttpsError("invalid-argument", () => executeSubmitVote(
      uid,
      {
        ...validRequest,
        commitmentHash: Buffer.alloc(32, 1).toString("base64"),
      },
      dependencies,
    ));

    await database.doc(`surveys/${survey.surveyId}`).update({ isActive: false });
    await expectHttpsError("failed-precondition", () => executeSubmitVote(
      uid,
      validRequest,
      dependencies,
    ));
    await database.doc(`surveys/${survey.surveyId}`).update({
      isActive: true,
      expiresAt: now,
    });
    await expectHttpsError("failed-precondition", () => executeSubmitVote(
      uid,
      validRequest,
      dependencies,
    ));

    assert.equal(
      (await database.doc(`surveys/${survey.surveyId}/votes/${uid}`).get()).exists,
      false,
    );
    assert.equal(
      (await database.collection(`surveys/${survey.surveyId}/receipts`).get()).size,
      0,
    );
    assert.deepEqual((await resultRef.get()).data(), initialResult);
    assert.equal(
      (await database.collection(`users/${uid}/operations`).get()).size,
      1,
    );
  });

  it("rotates one device without invalidating another and retains revoked history", async () => {
    const historicalSurvey = await executeCreateSurvey(uid, {
      operationId: createOperationId,
      title: "Community garden",
      description: "Choose the next improvement for our community garden.",
      allowMultipleChoices: false,
      options: ["More benches", "Native flowers"],
      expiresAt: now + 60_000,
    }, dependencies);
    const historicalSurveySnapshot = await database
      .doc(`surveys/${historicalSurvey.surveyId}`)
      .get();
    const historicalOptionId = historicalSurveySnapshot.get("options")[0].id as string;
    const historicalPayload = buildPayload(
      historicalSurvey.surveyId,
      uid,
      keyId,
      [historicalOptionId],
      now,
    );
    const historicalSignature = sign(
      "SHA256",
      Buffer.from(historicalPayload),
      privateKey,
    ).toString("base64");
    const historicalRequest = {
      operationId: voteOperationId,
      surveyId: historicalSurvey.surveyId,
      keyId,
      optionIds: [historicalOptionId],
      signedAt: now,
      publicKey: publicKeyBase64,
      signature: historicalSignature,
      commitmentHash: createHash("sha256")
        .update(`${historicalPayload}|${historicalSignature}|${publicKeyBase64}`)
        .digest("base64"),
    };
    const historicalReceipt = await executeSubmitVote(
      uid,
      historicalRequest,
      dependencies,
    );

    const replacement = generateKeyPairSync("ec", { namedCurve: "prime256v1" });
    const replacementPublicKey = replacement.publicKey
      .export({ format: "der", type: "spki" })
      .toString("base64");
    const replacementKeyId = createHash("sha256")
      .update(replacement.publicKey.export({ format: "der", type: "spki" }))
      .digest("hex");
    const replacementProof = {
      deviceId,
      keyId: replacementKeyId,
      signedAt: now,
    };
    await expectHttpsError("invalid-argument", () => executeRegisterVotingKey(uid, {
      ...replacementProof,
      publicKey: replacementPublicKey,
      signature: sign(
        "SHA256",
        Buffer.from(buildVotingKeyRegistrationPayload(uid, replacementProof)),
        replacement.privateKey,
      ).toString("base64"),
    }, {
      ...dependencies,
      now: () => now + 10 * 60 * 1_000,
    }));
    await expectHttpsError("permission-denied", () => executeRegisterVotingKey(uid, {
      ...replacementProof,
      publicKey: replacementPublicKey,
      signature: sign(
        "SHA256",
        Buffer.from(buildVotingKeyRegistrationPayload(uid, replacementProof)),
        privateKey,
      ).toString("base64"),
    }, dependencies));
    await executeRegisterVotingKey(uid, {
      ...replacementProof,
      publicKey: replacementPublicKey,
      signature: sign(
        "SHA256",
        Buffer.from(buildVotingKeyRegistrationPayload(uid, replacementProof)),
        replacement.privateKey,
      ).toString("base64"),
    }, dependencies);

    assert.equal(
      (await database.doc(`users/${uid}/keys/${keyId}`).get()).get("status"),
      "rotated",
    );
    assert.equal(
      (await database.doc(`users/${uid}/keys/${replacementKeyId}`).get()).get("status"),
      "active",
    );
    assert.deepEqual(
      await executeSubmitVote(uid, historicalRequest, dependencies),
      historicalReceipt,
    );

    const otherDevice = "c".repeat(64);
    const other = generateKeyPairSync("ec", { namedCurve: "prime256v1" });
    const otherPublicKey = other.publicKey
      .export({ format: "der", type: "spki" })
      .toString("base64");
    const otherKeyId = createHash("sha256")
      .update(other.publicKey.export({ format: "der", type: "spki" }))
      .digest("hex");
    const otherProof = {
      deviceId: otherDevice,
      keyId: otherKeyId,
      signedAt: now,
    };
    await executeRegisterVotingKey(uid, {
      ...otherProof,
      publicKey: otherPublicKey,
      signature: sign(
        "SHA256",
        Buffer.from(buildVotingKeyRegistrationPayload(uid, otherProof)),
        other.privateKey,
      ).toString("base64"),
    }, dependencies);

    assert.equal(
      (await database.doc(`users/${uid}/keys/${replacementKeyId}`).get()).get("status"),
      "active",
    );
    assert.equal(
      (await database.doc(`users/${uid}/keys/${otherKeyId}`).get()).get("status"),
      "active",
    );

    assert.deepEqual(
      await executeRevokeVotingKey(
        uid,
        { keyId: replacementKeyId },
        dependencies,
      ),
      { keyId: replacementKeyId, status: "revoked" },
    );
    assert.deepEqual(
      await executeRevokeVotingKey(
        uid,
        { keyId: replacementKeyId },
        dependencies,
      ),
      { keyId: replacementKeyId, status: "revoked" },
    );
    assert.equal(
      (await database.doc(`users/${uid}/keys/${replacementKeyId}`).get()).get("status"),
      "revoked",
    );
    assert.equal(
      (await database.doc(`users/${uid}/keys/${otherKeyId}`).get()).get("status"),
      "active",
    );
    const survey = await executeCreateSurvey(uid, {
      operationId: "22222222-3333-4444-8555-666666666666",
      title: "Community garden",
      description: "Choose the next improvement for our community garden.",
      allowMultipleChoices: false,
      options: ["More benches", "Native flowers"],
      expiresAt: now + 60_000,
    }, dependencies);
    const surveySnapshot = await database.doc(`surveys/${survey.surveyId}`).get();
    const optionId = surveySnapshot.get("options")[0].id as string;
    const revokedPayload = buildPayload(
      survey.surveyId,
      uid,
      replacementKeyId,
      [optionId],
      now,
    );
    const revokedSignature = sign(
      "SHA256",
      Buffer.from(revokedPayload),
      replacement.privateKey,
    ).toString("base64");
    await expectHttpsError("permission-denied", () => executeSubmitVote(
      uid,
      {
        operationId: "33333333-4444-4555-8666-777777777777",
        surveyId: survey.surveyId,
        keyId: replacementKeyId,
        optionIds: [optionId],
        signedAt: now,
        publicKey: replacementPublicKey,
        signature: revokedSignature,
        commitmentHash: createHash("sha256")
          .update(`${revokedPayload}|${revokedSignature}|${replacementPublicKey}`)
          .digest("base64"),
      },
      dependencies,
    ));
    await expectHttpsError("failed-precondition", () => executeRegisterVotingKey(
      uid,
      {
        ...replacementProof,
        publicKey: replacementPublicKey,
        signature: sign(
          "SHA256",
          Buffer.from(buildVotingKeyRegistrationPayload(uid, replacementProof)),
          replacement.privateKey,
        ).toString("base64"),
      },
      dependencies,
    ));
  });
});

async function expectHttpsError(
  code: HttpsError["code"],
  block: () => Promise<unknown>,
): Promise<void> {
  await assert.rejects(block, (error: unknown) => {
    return error instanceof HttpsError && error.code === code;
  });
}
