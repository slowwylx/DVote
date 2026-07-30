import { strict as assert } from "node:assert";
import { generateKeyPairSync } from "node:crypto";
import { Firestore } from "@google-cloud/firestore";

const describeWithFunctionsEmulator =
  process.env.FUNCTIONS_EMULATOR_HOST ? describe : describe.skip;

describeWithFunctionsEmulator("callable transport security", function () {
  this.timeout(15_000);

  const projectId = "dvote-test";
  const uid = "callable-user";
  const emulatorPrivateKey = generateKeyPairSync("rsa", { modulusLength: 2_048 })
    .privateKey
    .export({ format: "pem", type: "pkcs8" })
    .toString();
  const database = new Firestore({
    projectId,
    credentials: {
      client_email: "functions-emulator@dvote-test.iam.gserviceaccount.com",
      private_key: emulatorPrivateKey,
    },
  });
  const authToken = unsignedEmulatorToken(uid);
  const appCheckToken = unsignedEmulatorToken(
    "1:1234567890:android:0123456789abcdef",
  );

  beforeEach(async () => {
    await Promise.all([
      database.recursiveDelete(database.collection("users")),
      database.recursiveDelete(database.collection("surveys")),
    ]);
    await database.doc(`users/${uid}`).set({
      id: uid,
      displayName: "Alice",
    });
  });

  after(async () => {
    await database.terminate();
  });

  it("accepts an authenticated, App Check-protected callable request", async () => {
    const response = await callCreateSurvey({
      operationId: "01234567-89ab-4cde-8fab-0123456789ab",
      title: "Community garden",
      description: "Choose the next improvement for our community garden.",
      allowMultipleChoices: false,
      options: ["More benches", "Native flowers"],
      expiresAt: Date.now() + 60_000,
    });

    assert.equal(response.status, 200);
    assert.match(response.body.result.surveyId, /^[A-Za-z0-9_-]{1,128}$/);
    assert.equal(
      (await database.collection("surveys").where("creatorId", "==", uid).get()).size,
      1,
    );
  });

  it("rejects missing Auth or App Check before a mutation", async () => {
    const request = {
      operationId: "01234567-89ab-4cde-8fab-0123456789ab",
      title: "Community garden",
      description: "Choose the next improvement for our community garden.",
      allowMultipleChoices: false,
      options: ["More benches", "Native flowers"],
      expiresAt: Date.now() + 60_000,
    };

    const missingAuth = await callCreateSurvey(request, { auth: false });
    const missingAppCheck = await callCreateSurvey(request, { appCheck: false });

    assert.equal(missingAuth.status, 401);
    assert.equal(missingAuth.body.error.status, "UNAUTHENTICATED");
    assert.equal(missingAppCheck.status, 401);
    assert.equal(missingAppCheck.body.error.status, "UNAUTHENTICATED");
    assert.equal((await database.collection("surveys").get()).size, 0);
  });

  it("preserves strict request decoding through the callable transport", async () => {
    const response = await callCreateSurvey({
      operationId: "01234567-89ab-4cde-8fab-0123456789ab",
      title: "Community garden",
      description: "Choose the next improvement for our community garden.",
      allowMultipleChoices: false,
      options: ["More benches", "Native flowers"],
      expiresAt: Date.now() + 60_000,
      unexpected: true,
    });

    assert.equal(response.status, 400);
    assert.equal(response.body.error.status, "INVALID_ARGUMENT");
    assert.equal((await database.collection("surveys").get()).size, 0);
  });

  async function callCreateSurvey(
    data: unknown,
    tokens: { auth?: boolean; appCheck?: boolean } = {},
  ): Promise<{
    status: number;
    body: {
      result: { surveyId: string };
      error: { status: string };
    };
  }> {
    const headers: Record<string, string> = {
      "Content-Type": "application/json",
    };
    if (tokens.auth !== false) {
      headers.Authorization = `Bearer ${authToken}`;
    }
    if (tokens.appCheck !== false) {
      headers["X-Firebase-AppCheck"] = appCheckToken;
    }
    const response = await fetch(
      `http://${process.env.FUNCTIONS_EMULATOR_HOST}/${projectId}/us-central1/createSurvey`,
      {
        method: "POST",
        headers,
        body: JSON.stringify({ data }),
      },
    );
    return {
      status: response.status,
      body: await response.json() as {
        result: { surveyId: string };
        error: { status: string };
      },
    };
  }
});

function unsignedEmulatorToken(subject: string): string {
  const encode = (value: object) =>
    Buffer.from(JSON.stringify(value)).toString("base64url");
  return `${encode({ alg: "none", typ: "JWT" })}.${encode({ sub: subject })}.emulator`;
}
