package com.moyz.adi.common.rag;

import com.moyz.adi.common.cosntant.AdiConstant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the recall-size policy, because that number is the ceiling of every later ranking step.
 * <p>
 * Vector search returns a fixed number of candidates; fusion, rerank and the neighbour window can
 * only reorder or widen what was already returned. A pool that is too small cannot be rescued by any
 * ranker, which is exactly the failure mode migration 027 fixed (the default was 3, while the pool
 * had to be 10 for every answer to be reachable; measured on the kb-eval golden set, see that file
 * and {@code AdiConstant#RAG_RETRIEVE_NUMBER_MAX}).
 * <p>
 * The assertions below are about the <em>shape</em> of the rule, not about one magic number: the
 * budget must be able to shrink the pool, the cap must be reachable, and the cap must not be set
 * below what the benchmark needs. Changing the cap is then a deliberate act that has to face these.
 */
class RetrieveSizePolicyTest {

    /**
     * Pool size at which the 73-question Chinese golden set puts the answer inside the pool for every
     * question (kb-eval RANKDIAG: 86.3% at 3, 95.9% at 5, 100.0% at 10). Not a tuning parameter --
     * a measured requirement.
     */
    private static final int POOL_NEEDED_FOR_FULL_RECALL = 10;

    @BeforeEach
    void useOpenAiEstimator() {
        TokenEstimatorThreadLocal.setTokenEstimator(AdiConstant.TokenEstimator.OPENAI);
    }

    @AfterEach
    void clearEstimator() {
        TokenEstimatorThreadLocal.clearTokenEstimator();
    }

    @Test
    void aRoomyWindowReachesTheDeclaredMaximum() {
        assertEquals(AdiConstant.RAG_RETRIEVE_NUMBER_MAX,
                EmbeddingRag.getRetrieveMaxResults("年假有几天", 32768, 0),
                "a large context window must reach the cap, otherwise 'auto' silently under-retrieves");
    }

    @Test
    void aTightWindowShrinksThePoolBelowTheCap() {
        int got = EmbeddingRag.getRetrieveMaxResults("年假有几天", 2000, 0);
        assertEquals(1, got, "2000 tokens minus the question buys one 1000-token segment");
        assertTrue(got < AdiConstant.RAG_RETRIEVE_NUMBER_MAX,
                "the cap must be a ceiling, not a floor -- a small window has to reduce the pool");
    }

    @Test
    void noConfiguredTokenLimitStillRetrievesTheMaximum() {
        assertEquals(AdiConstant.RAG_RETRIEVE_NUMBER_MAX,
                EmbeddingRag.getRetrieveMaxResults("年假有几天", 0, 0),
                "no maxInputTokens means the budget is unknown, so fall back to the cap");
    }

    @Test
    void historyAndSystemMessageAreDeductedFromTheBudget() {
        assertEquals(0, EmbeddingRag.getRetrieveMaxResults("年假有几天", 3000, 2950),
                "reserved tokens come off the top; a chat that eats the window retrieves nothing");
    }

    @Test
    void anOverlongQuestionRetrievesNothingInsteadOfTruncatingSilently() {
        String tooLong = "年假有几天年假有几天年假有几天年假有几天年假有几天";
        assertEquals(0, EmbeddingRag.getRetrieveMaxResults(tooLong, 5, 0),
                "returning 0 lets the strict-mode check upstream stop the request instead of answering "
                        + "with an arbitrary half-context");
    }

    /**
     * The guard that would have caught the pre-027 setting: a cap below the measured requirement
     * means some questions cannot be answered no matter how good the ranking is. Failing here is a
     * signal to re-read {@code AdiConstant#RAG_RETRIEVE_NUMBER_MAX} and migration 027, not to relax
     * the assertion.
     */
    @Test
    void theCapMustBeAbleToReachTheMeasuredRequirement() {
        assertTrue(AdiConstant.RAG_RETRIEVE_NUMBER_MAX >= POOL_NEEDED_FOR_FULL_RECALL,
                "cap=" + AdiConstant.RAG_RETRIEVE_NUMBER_MAX + " < " + POOL_NEEDED_FOR_FULL_RECALL
                        + " leaves part of the golden set with its answer outside the pool; "
                        + "no reranker can recover what was never retrieved");
    }
}
