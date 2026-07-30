import { strict as assert } from "node:assert";
import { readFileSync } from "node:fs";
import { resolve } from "node:path";
import {
  applyVoteToResult,
  buildInitialResult,
  buildPayload,
  buildPublicSurveyDocument,
  RESULTS_VISIBILITY_AFTER_CLOSE,
} from "../src";

type CanonicalPayloadVector = {
  name: string;
  surveyId: string;
  userId: string;
  keyId: string;
  optionIds: string[];
  signedAt: number;
  canonicalPayload: string;
};

const canonicalPayloadVectors = JSON.parse(
  readFileSync(
    resolve(__dirname, "../../../test-vectors/canonical-vote-payload-v1.json"),
    "utf8",
  ),
) as CanonicalPayloadVector[];

describe("buildPayload", () => {
  for (const vector of canonicalPayloadVectors) {
    it(vector.name, () => {
      assert.equal(
        buildPayload(
          vector.surveyId,
          vector.userId,
          vector.keyId,
          vector.optionIds,
          vector.signedAt,
        ),
        vector.canonicalPayload,
      );
    });
  }
});

describe("survey result privacy contract", () => {
  const options = [
    { id: "option-a", title: "A" },
    { id: "option-b", title: "B" },
  ];

  it("keeps aggregate results out of the public survey document", () => {
    const survey = buildPublicSurveyDocument({
      id: "survey-1",
      title: "Survey",
      description: "Survey description",
      creatorId: "creator-1",
      creatorName: "Creator",
      createdAt: 1,
      expiresAt: 2,
      isActive: true,
      allowMultipleChoices: false,
      options,
    });

    assert.equal(survey.resultsVisibility, RESULTS_VISIBILITY_AFTER_CLOSE);
    assert.equal("result" in survey, false);
  });

  it("updates the separated result without mutating the previous snapshot", () => {
    const initial = buildInitialResult("survey-1", options);
    const updated = applyVoteToResult(initial, ["option-a"]);

    assert.deepEqual(initial, {
      surveyId: "survey-1",
      totalVotes: 0,
      optionVotes: {
        "option-a": 0,
        "option-b": 0,
      },
    });
    assert.deepEqual(updated, {
      surveyId: "survey-1",
      totalVotes: 1,
      optionVotes: {
        "option-a": 1,
        "option-b": 0,
      },
    });
  });
});
