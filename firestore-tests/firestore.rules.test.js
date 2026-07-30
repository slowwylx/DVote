const fs = require("node:fs");
const path = require("node:path");
const assert = require("node:assert/strict");
const {
  assertFails,
  assertSucceeds,
  initializeTestEnvironment,
} = require("@firebase/rules-unit-testing");

const VALID_PUBLIC_KEY = "A".repeat(124);

describe("DVote Firestore rules", () => {
  let testEnv;

  before(async () => {
    testEnv = await initializeTestEnvironment({
      projectId: "dvote-test",
      firestore: {
        rules: fs.readFileSync(path.resolve(__dirname, "../firestore.rules"), "utf8"),
      },
    });
  });

  after(async () => {
    await testEnv.cleanup();
  });

  beforeEach(async () => {
    await testEnv.clearFirestore();
  });

  async function seedSurvey({
    surveyId = "survey-1",
    expiresAt = Date.now() + 60_000,
    isActive = true,
    resultsVisibility = "after_close",
  } = {}) {
    await testEnv.withSecurityRulesDisabled(async (context) => {
      const db = context.firestore();
      await db.doc(`surveys/${surveyId}`).set({
        id: surveyId,
        title: "Survey",
        createdAt: 1,
        expiresAt,
        isActive,
        resultsVisibility,
      });
      await db.doc(`surveys/${surveyId}/results/final`).set({
        surveyId,
        totalVotes: 1,
        optionVotes: { "option-1": 1 },
      });
    });
  }

  it("denies public raw vote reads", async () => {
    await testEnv.withSecurityRulesDisabled(async (context) => {
      await context.firestore()
        .doc("surveys/survey-1/votes/user-1")
        .set({ optionIds: ["option-1"] });
    });

    const publicDb = testEnv.unauthenticatedContext().firestore();
    await assertFails(publicDb.doc("surveys/survey-1/votes/user-1").get());
  });

  it("denies every client raw vote mutation and collection listing", async () => {
    const db = testEnv.authenticatedContext("user-1").firestore();
    const voteRef = db.doc("surveys/survey-1/votes/user-1");
    await assertFails(voteRef.set({
      optionIds: ["option-1"],
      userId: "user-1",
    }));
    await testEnv.withSecurityRulesDisabled(async (context) => {
      await context.firestore().doc("surveys/survey-1/votes/user-1").set({
        optionIds: ["option-1"],
        userId: "user-1",
      });
    });
    await assertFails(voteRef.update({ optionIds: ["option-2"] }));
    await assertFails(voteRef.delete());
    await assertFails(db.collection("surveys/survey-1/votes").get());
  });

  it("allows a voter to read only their own raw vote", async () => {
    await testEnv.withSecurityRulesDisabled(async (context) => {
      await context.firestore()
        .doc("surveys/survey-1/votes/user-1")
        .set({ optionIds: ["option-1"] });
    });

    const ownerDb = testEnv.authenticatedContext("user-1").firestore();
    const otherDb = testEnv.authenticatedContext("user-2").firestore();

    await assertSucceeds(ownerDb.doc("surveys/survey-1/votes/user-1").get());
    await assertFails(otherDb.doc("surveys/survey-1/votes/user-1").get());
  });

  it("keeps profiles private and blocks spoofed user IDs", async () => {
    const ownerDb = testEnv.authenticatedContext("user-1").firestore();
    const otherDb = testEnv.authenticatedContext("user-2").firestore();
    const publicDb = testEnv.unauthenticatedContext().firestore();

    await assertSucceeds(
      ownerDb.doc("users/user-1").set({
        id: "user-1",
        displayName: "Alice",
        publicKey: VALID_PUBLIC_KEY,
      }),
    );
    await assertSucceeds(ownerDb.doc("users/user-1").get());
    await assertFails(publicDb.doc("users/user-1").get());
    await assertFails(otherDb.doc("users/user-1").get());
    await assertFails(ownerDb.collection("users").get());
    await assertFails(
      otherDb.doc("users/user-1").set({
        id: "user-1",
        displayName: "Mallory",
        publicKey: "B".repeat(124),
      }),
    );
  });

  it("denies client-side profile aggregate manipulation", async () => {
    const db = testEnv.authenticatedContext("user-1").firestore();
    await db.doc("users/user-1").set({
      id: "user-1",
      displayName: "Alice",
      publicKey: VALID_PUBLIC_KEY,
    });

    await assertFails(
      db.doc("users/user-1").set(
        { createdSurveyIds: ["survey-1"] },
        { merge: true },
      ),
    );
  });

  it("rejects malformed and oversized profile creates", async () => {
    const db = testEnv.authenticatedContext("user-1").firestore();
    const profile = {
      id: "user-1",
      displayName: "Alice",
      publicKey: VALID_PUBLIC_KEY,
    };

    await assertFails(db.doc("users/user-1").set({ ...profile, unexpected: true }));
    await assertFails(db.doc("users/user-1").set({ ...profile, displayName: "" }));
    await assertFails(db.doc("users/user-1").set({ ...profile, displayName: "A".repeat(81) }));
    await assertFails(db.doc("users/user-1").set({ ...profile, publicKey: 42 }));
    await assertFails(db.doc("users/user-1").set({ ...profile, publicKey: "short" }));
    await assertFails(db.doc("users/user-1").set({
      displayName: "Alice",
      publicKey: VALID_PUBLIC_KEY,
    }));
    await assertSucceeds(db.doc("users/user-1").set({
      id: "user-1",
      displayName: "Alice",
    }));
  });

  it("allows a bounded display-name update but keeps identity and key immutable", async () => {
    const db = testEnv.authenticatedContext("user-1").firestore();
    await db.doc("users/user-1").set({
      id: "user-1",
      displayName: "Alice",
      publicKey: VALID_PUBLIC_KEY,
    });

    await assertSucceeds(db.doc("users/user-1").update({ displayName: "Alice Cooper" }));
    await assertFails(db.doc("users/user-1").update({ displayName: "A".repeat(81) }));
    await assertFails(db.doc("users/user-1").update({ publicKey: "B".repeat(124) }));
    await assertFails(db.doc("users/user-1").update({ id: "user-2" }));
    await assertFails(db.doc("users/user-1").update({ unexpected: true }));
    await assertFails(db.doc("users/user-1").delete());
  });

  it("keeps operation and quota state Functions-owned", async () => {
    const db = testEnv.authenticatedContext("user-1").firestore();

    await assertFails(db.doc("users/user-1/operations/operation-1").get());
    await assertFails(db.doc("users/user-1/operations/operation-1").set({
      kind: "createSurvey",
    }));
    await assertFails(db.collection("users/user-1/operations").get());
    await assertFails(db.doc("users/user-1/limits/createSurvey").get());
    await assertFails(db.doc("users/user-1/limits/createSurvey").set({
      count: 0,
    }));
    await assertFails(db.collection("users/user-1/limits").get());
    await testEnv.withSecurityRulesDisabled(async (context) => {
      await context.firestore().doc("users/user-1/operations/operation-1").set({
        kind: "createSurvey",
      });
      await context.firestore().doc("users/user-1/limits/createSurvey").set({
        count: 1,
      });
    });
    await assertFails(db.doc("users/user-1/operations/operation-1").update({
      kind: "submitVote",
    }));
    await assertFails(db.doc("users/user-1/operations/operation-1").delete());
    await assertFails(db.doc("users/user-1/limits/createSurvey").update({
      count: 2,
    }));
    await assertFails(db.doc("users/user-1/limits/createSurvey").delete());
  });

  it("lets owners read registered key history but keeps key and device writes Functions-owned", async () => {
    await testEnv.withSecurityRulesDisabled(async (context) => {
      const db = context.firestore();
      await db.doc(`users/user-1/keys/${"a".repeat(64)}`).set({
        keyId: "a".repeat(64),
        status: "active",
      });
      await db.doc(`users/user-1/devices/${"b".repeat(64)}`).set({
        deviceId: "b".repeat(64),
        activeKeyId: "a".repeat(64),
      });
    });

    const ownerDb = testEnv.authenticatedContext("user-1").firestore();
    const otherDb = testEnv.authenticatedContext("user-2").firestore();
    const publicDb = testEnv.unauthenticatedContext().firestore();
    const keyPath = `users/user-1/keys/${"a".repeat(64)}`;
    const devicePath = `users/user-1/devices/${"b".repeat(64)}`;

    await assertSucceeds(ownerDb.doc(keyPath).get());
    await assertSucceeds(ownerDb.collection("users/user-1/keys").get());
    await assertSucceeds(ownerDb.doc(devicePath).get());
    await assertSucceeds(ownerDb.collection("users/user-1/devices").get());
    await assertFails(otherDb.doc(keyPath).get());
    await assertFails(otherDb.doc(devicePath).get());
    await assertFails(publicDb.doc(keyPath).get());
    await assertFails(otherDb.collection("users/user-1/keys").get());
    await assertFails(otherDb.collection("users/user-1/devices").get());
    await assertFails(ownerDb.doc(`users/user-1/keys/${"c".repeat(64)}`).set({
      status: "active",
    }));
    await assertFails(ownerDb.doc(keyPath).set({ status: "revoked" }, { merge: true }));
    await assertFails(ownerDb.doc(keyPath).delete());
    await assertFails(ownerDb.doc(`users/user-1/devices/${"d".repeat(64)}`).set({
      activeKeyId: null,
    }));
    await assertFails(ownerDb.doc(devicePath).set({ activeKeyId: null }, { merge: true }));
    await assertFails(ownerDb.doc(devicePath).delete());
  });

  it("denies every client survey mutation", async () => {
    const db = testEnv.authenticatedContext("user-1").firestore();
    const surveyRef = db.doc("surveys/survey-1");
    await assertFails(surveyRef.set({ title: "Blocked" }));
    await seedSurvey();
    await assertFails(surveyRef.update({ title: "Blocked" }));
    await assertFails(surveyRef.delete());
  });

  it("allows public survey queries only for the after-close result contract", async () => {
    const now = Date.now();
    await seedSurvey({ expiresAt: now + 60_000 });
    await seedSurvey({
      surveyId: "expired-survey",
      expiresAt: now - 60_000,
    });

    const publicDb = testEnv.unauthenticatedContext().firestore();
    await assertSucceeds(publicDb.doc("surveys/survey-1").get());
    const activeSnapshot = await assertSucceeds(
      publicDb.collection("surveys")
        .where("resultsVisibility", "==", "after_close")
        .where("isActive", "==", true)
        .where("expiresAt", ">", now)
        .orderBy("expiresAt", "asc")
        .orderBy("createdAt", "desc")
        .limit(50)
        .get(),
    );
    assert.deepEqual(activeSnapshot.docs.map((document) => document.id), ["survey-1"]);
    await assertFails(
      publicDb.collection("surveys")
        .where("isActive", "==", true)
        .get(),
    );
  });

  it("denies public reads of legacy surveys without the privacy contract", async () => {
    await testEnv.withSecurityRulesDisabled(async (context) => {
      await context.firestore().doc("surveys/legacy-survey").set({
        id: "legacy-survey",
        title: "Legacy survey",
        isActive: true,
        result: { totalVotes: 1 },
      });
    });

    const publicDb = testEnv.unauthenticatedContext().firestore();
    await assertFails(publicDb.doc("surveys/legacy-survey").get());
  });

  it("keeps active aggregate results private", async () => {
    await seedSurvey({
      expiresAt: Date.now() + 60_000,
      isActive: true,
    });

    const publicDb = testEnv.unauthenticatedContext().firestore();
    const voterDb = testEnv.authenticatedContext("user-1").firestore();
    await assertFails(publicDb.doc("surveys/survey-1/results/final").get());
    await assertFails(voterDb.doc("surveys/survey-1/results/final").get());
  });

  it("publishes the exact final result after server-confirmed expiry", async () => {
    await seedSurvey({
      expiresAt: Date.now() - 60_000,
      isActive: true,
    });

    const publicDb = testEnv.unauthenticatedContext().firestore();
    const snapshot = await assertSucceeds(
      publicDb.doc("surveys/survey-1/results/final").get(),
    );
    assert.equal(snapshot.get("totalVotes"), 1);
  });

  it("publishes the exact final result when a survey is explicitly inactive", async () => {
    await seedSurvey({
      expiresAt: Date.now() + 60_000,
      isActive: false,
    });

    const publicDb = testEnv.unauthenticatedContext().firestore();
    await assertSucceeds(publicDb.doc("surveys/survey-1/results/final").get());
  });

  it("denies final-result collection listing", async () => {
    await seedSurvey({
      expiresAt: Date.now() - 60_000,
    });

    const publicDb = testEnv.unauthenticatedContext().firestore();
    await assertFails(publicDb.collection("surveys/survey-1/results").get());
    await testEnv.withSecurityRulesDisabled(async (context) => {
      await context.firestore().doc("surveys/survey-1/results/draft").set({
        surveyId: "survey-1",
        totalVotes: 1,
      });
    });
    await assertFails(publicDb.doc("surveys/survey-1/results/draft").get());
  });

  it("denies every client result and receipt mutation", async () => {
    const db = testEnv.authenticatedContext("user-1").firestore();
    const resultRef = db.doc("surveys/survey-1/results/final");
    const receiptRef = db.doc("surveys/survey-1/receipts/forged");
    await assertFails(resultRef.set({
      surveyId: "survey-1",
      totalVotes: 999,
    }));
    await assertFails(receiptRef.set({
      commitmentHash: "forged",
    }));
    await testEnv.withSecurityRulesDisabled(async (context) => {
      await context.firestore().doc("surveys/survey-1").set({
        expiresAt: Date.now() - 1,
        isActive: false,
        resultsVisibility: "after_close",
      });
      await context.firestore().doc("surveys/survey-1/results/final").set({
        surveyId: "survey-1",
        totalVotes: 1,
      });
      await context.firestore().doc("surveys/survey-1/receipts/forged").set({
        commitmentHash: "hash",
      });
    });
    await assertFails(resultRef.update({ totalVotes: 999 }));
    await assertFails(resultRef.delete());
    await assertFails(receiptRef.update({ commitmentHash: "forged" }));
    await assertFails(receiptRef.delete());
  });

  it("allows direct public receipt reads but denies receipt listing", async () => {
    await testEnv.withSecurityRulesDisabled(async (context) => {
      await context.firestore()
        .doc("surveys/survey-1/receipts/receipt-1")
        .set({ commitmentHash: "hash" });
    });

    const publicDb = testEnv.unauthenticatedContext().firestore();
    const snapshot = await assertSucceeds(publicDb.doc("surveys/survey-1/receipts/receipt-1").get());
    assert.equal(snapshot.get("commitmentHash"), "hash");
    await assertFails(publicDb.collection("surveys/survey-1/receipts").get());
  });
});
