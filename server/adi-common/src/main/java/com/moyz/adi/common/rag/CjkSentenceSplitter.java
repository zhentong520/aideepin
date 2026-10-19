package com.moyz.adi.common.rag;

import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.document.splitter.DocumentBySentenceSplitter;
import dev.langchain4j.data.document.splitter.HierarchicalDocumentSplitter;
import dev.langchain4j.model.TokenCountEstimator;

import java.util.Arrays;
import java.util.regex.Pattern;

/**
 * Sentence-aware splitter for Chinese text.
 *
 * <p>langchain4j's built-in splitter chain only recognises {@code "\n\n"}, {@code "\n"}, {@code "."} and
 * {@code " "} as boundaries. Chinese prose uses none of them: a regulation clause or a manual section is
 * one long run of text with no ASCII punctuation at all, so the recursive splitter degenerates into
 * cutting by raw characters and the resulting segments straddle clause boundaries — the "…但下列情形
 * 除外" tail of a rule can end up in the next segment. {@code overlap} fails for the same reason: the
 * unit it repeats is the entire paragraph, so a small overlap does nothing and a large one duplicates
 * the previous segment whole.
 *
 * <p>This splitter cuts the text at CJK sentence terminators and at line breaks first, then lets
 * {@link HierarchicalDocumentSplitter} pack those sentences up to the configured size. Because the
 * packing unit is now a sentence, a segment ends where a sentence ends, and overlap repeats whole
 * sentences instead of duplicating a paragraph.
 *
 * <p>Text that has no CJK punctuation (English prose, source code, markdown tables) still works: line
 * breaks and blank lines remain boundaries, and pieces that are still too long are handed to the
 * inherited ASCII sentence sub-splitter.
 */
public class CjkSentenceSplitter extends HierarchicalDocumentSplitter {

    /**
     * Break after a CJK sentence terminator, or before a line break. The delimiter stays attached to the
     * text, so joining the pieces with an empty delimiter reproduces the source exactly.
     */
    private static final Pattern BOUNDARY = Pattern.compile("(?<=[。！？；])|(?=\\R)");

    private final int maxSegmentSize;
    private final int maxOverlapSize;
    private final TokenCountEstimator estimator;

    public CjkSentenceSplitter(int maxSegmentSize,
                               int maxOverlapSize,
                               TokenCountEstimator estimator) {
        super(maxSegmentSize, maxOverlapSize, estimator,
                new DocumentBySentenceSplitter(maxSegmentSize, maxOverlapSize, estimator));
        this.maxSegmentSize = maxSegmentSize;
        this.maxOverlapSize = maxOverlapSize;
        this.estimator = estimator;
    }

    @Override
    public String[] split(String text) {
        return Arrays.stream(BOUNDARY.split(text))
                .filter(piece -> !piece.isBlank())
                .toArray(String[]::new);
    }

    /**
     * The terminators are kept inside each piece, so nothing needs to be re-inserted between them.
     */
    @Override
    public String joinDelimiter() {
        return "";
    }

    @Override
    protected DocumentSplitter defaultSubSplitter() {
        return new DocumentBySentenceSplitter(maxSegmentSize, maxOverlapSize, estimator);
    }
}
