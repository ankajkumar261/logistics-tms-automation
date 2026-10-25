package com.portfolio.logistics.healing.core.ai;

import com.portfolio.logistics.healing.core.ActionKind;
import com.portfolio.logistics.healing.core.ElementSnapshot;

import java.util.List;
import java.util.Optional;

/**
 * Last-resort second opinion, consulted only when the deterministic score is close but below the
 * threshold. It may only pick one of the candidates it is shown — it never invents a locator — and
 * its answer is blended with the deterministic score, so it can't rescue a weak match.
 */
public interface AiLocatorAdvisor {

    /**
     * @param reference  what the element looked like (baseline or selector intent)
     * @param candidates the top-scoring live elements; indexes in the answer refer to this list
     * @return empty when the advisor is unavailable or failed — the heal then proceeds without it
     */
    Optional<AiVerdict> choose(String originalSelector, ActionKind action, ElementSnapshot reference,
                               List<ElementSnapshot> candidates);
}
