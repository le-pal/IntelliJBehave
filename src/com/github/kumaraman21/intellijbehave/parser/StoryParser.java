/*
 * Copyright 2011-12 Aman Kumar
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.github.kumaraman21.intellijbehave.parser;

import org.apache.commons.lang.StringUtils;
import org.jetbrains.annotations.NotNull;

import com.github.kumaraman21.intellijbehave.highlighter.StoryTokenType;
import com.github.kumaraman21.intellijbehave.utility.LocalizedStorySupport;
import com.intellij.lang.ASTNode;
import com.intellij.lang.PsiBuilder;
import com.intellij.lang.PsiParser;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;

public class StoryParser implements PsiParser {

	private static final boolean DEBUG = false;

	@NotNull
	@Override
	public ASTNode parse(final IElementType root, final PsiBuilder builder) {
		final PsiBuilder.Marker rootMarker = builder.mark();
		builder.setDebugMode(true);
		parseStory(builder);
		rootMarker.done(root);
		return builder.getTreeBuilt();
	}

	@SuppressWarnings("UnnecessaryLabelOnContinueStatement")
	private void parseStory(final PsiBuilder builder) {
		final PsiBuilder.Marker storyMarker = builder.mark();

		final ParserState state = new ParserState(builder);

		whileLoop:
		while (!builder.eof()) {
			final IElementType tokenType = builder.getTokenType();

			// Comment and whitespace are not returned by default

			if (isComment(tokenType)) {
				state.enterComment();
				builder.advanceLexer();
				continue whileLoop;
			} else {
				state.leaveComment();
			}

			if (isWhitespace(tokenType)) {
				if (isCrlf(builder.getTokenText())) {
					// this is never called unfortunately
					state.leaveTableRow();
				}

				state.enterWhitespace();
				builder.advanceLexer();
				continue whileLoop;
			} else {
				state.leaveWhitespace();
			}


			if (isStoryDescription(tokenType)) {
				state.enterStoryDescription();
				builder.advanceLexer();
				continue whileLoop;
			} else {
				state.leaveStoryDescription();
			}

			if (isScenario(tokenType)) {
				state.enterScenario();
				builder.advanceLexer();
				continue whileLoop;
			} else if (!belongsToScenario(tokenType)) {
				state.leaveScenario();
			}

			if (isScenarioText(tokenType)) {
				builder.advanceLexer();
				continue whileLoop;
			}

			if (isMeta(tokenType)) {
				state.enterMeta();
				builder.advanceLexer();
				continue whileLoop;
			}

			if (isStepType(tokenType)) {
				state.enterStepType(tokenType);
				builder.advanceLexer();
				continue whileLoop;
			}

			if (isStepText(tokenType)) {
				builder.advanceLexer();
				continue whileLoop;
			}

			if (isExampleTable(tokenType)) {
				state.enterExampleTable();
				builder.advanceLexer();
				continue whileLoop;
			} else if (!belongsToTable(tokenType)) {
				state.leaveExampleTable();
			}

			if (isTableRow(tokenType)) {
				state.enterTableRow();
				builder.advanceLexer();
				continue whileLoop;
			}

			// unknown
			final PsiBuilder.Marker unknwonMark = builder.mark();
			builder.advanceLexer();
			unknwonMark.done(StoryElementType.UNKNOWN_FRAGMENT);
		}
		state.leaveRemainings();
		storyMarker.done(StoryElementType.STORY);
	}

	private static boolean isCrlf(final String text) {
		return text.contains("\n") || text.contains("\r");
	}

	private static class MarkerData {
		private final PsiBuilder.Marker marker;
		private final IElementType elementType;

		private MarkerData(final PsiBuilder.Marker marker, final IElementType elementType) {
			this.marker = marker;
			this.elementType = elementType;
		}

		public boolean matches(final IElementType elementType) {
			return this.elementType == elementType;
		}

		public boolean matches(final TokenSet tokenSet) {
			return tokenSet.contains(this.elementType);
		}

		public void applyMark() {
			marker.done(elementType);
		}
	}

	private static class ParserState {
		private final PsiBuilder builder;
		private final MarkerData[] markers = new MarkerData[10];
		private int markerIndex = -1;
		private StoryElementType previousStepElementType = null;


		public ParserState(final PsiBuilder builder) {
			this.builder = builder;
		}

		private void matchesHeadOrPush(final StoryElementType elementType) {
			if (markerIndex >= 0 && markers[markerIndex].matches(elementType)) {
				return;
			}
			markers[++markerIndex] = new MarkerData(builder.mark(), elementType);
			if (DEBUG)
				System.out.println("StoryParser$ParserState: PUSH>> " + StringUtils.repeat("..", markerIndex) + elementType);
		}

		private void popUntilOnlyIfPresent(final StoryElementType elementType) {
			int newMarkerIndex = markerIndex;
			for (int i = markerIndex; i >= 0; i--) {
				if (markers[i].matches(elementType)) {
					newMarkerIndex = i - 1;
					break;
				}
			}

			for (int i = newMarkerIndex + 1; i <= markerIndex; i++) {
				if (DEBUG)
					System.out.println("StoryParser$ParserState: POP >> " + StringUtils.repeat("..", i) + markers[i].elementType);
				markers[i].applyMark();
			}

			markerIndex = newMarkerIndex;
		}

		public void leaveRemainings() {
			while (markerIndex >= 0) {
				if (DEBUG)
					System.out.println("StoryParser$ParserState: POP >> " + StringUtils.repeat("..", markerIndex) + markers[markerIndex].elementType);
				markers[markerIndex--].applyMark();
			}
		}


		private void popUntilOnlyIfPresent(final TokenSet tokenSet) {
			int newMarkerIndex = markerIndex;
			for (int i = markerIndex; i >= 0; i--) {
				if (markers[i].matches(tokenSet)) {
					newMarkerIndex = i - 1;
					break;
				}
			}

			for (int i = newMarkerIndex + 1; i <= markerIndex; i++) {
				if (DEBUG)
					System.out.println("StoryParser$ParserState: POP >> " + StringUtils.repeat("..", i) + markers[i].elementType);
				markers[i].applyMark();
			}

			markerIndex = newMarkerIndex;
		}


		public void enterComment() {
			matchesHeadOrPush(StoryElementType.COMMENT);
		}

		private void leaveComment() {
			popUntilOnlyIfPresent(StoryElementType.COMMENT);
		}

		public void enterWhitespace() {
		}

		public void leaveWhitespace() {
		}

		public void enterStoryDescription() {
			leaveRemainings();
			matchesHeadOrPush(StoryElementType.STORY_DESCRIPTION);
		}

		private void leaveStoryDescription() {
			popUntilOnlyIfPresent(StoryElementType.STORY_DESCRIPTION);
		}

		public void enterScenario() {
			leaveRemainings();
			previousStepElementType = null;
			matchesHeadOrPush(StoryElementType.SCENARIO);
		}

		private void leaveScenario() {
			leaveRemainings();
			previousStepElementType = null;
			popUntilOnlyIfPresent(StoryElementType.SCENARIO);
		}

		public void enterMeta() {
			leaveStoryDescription();
			leaveExampleTable();
			leaveTableRow();
			leaveStep();
			matchesHeadOrPush(StoryElementType.META);
		}

		private void leaveMeta() {
			popUntilOnlyIfPresent(StoryElementType.META);
		}

		public void enterStepType(final IElementType tokenType) {
			leaveExampleTable();
			leaveMeta();
			leaveStep();
			leaveStoryDescription();
			leaveTableRow();

			StoryElementType elementType = previousStepElementType;
			if (tokenType == StoryTokenType.STEP_TYPE_GIVEN) {
				elementType = StoryElementType.GIVEN_STEP;
			} else if (tokenType == StoryTokenType.STEP_TYPE_WHEN) {
				elementType = StoryElementType.WHEN_STEP;
			} else if (tokenType == StoryTokenType.STEP_TYPE_THEN) {
				elementType = StoryElementType.THEN_STEP;
			} else {
				// should be the AND
				if (tokenType != StoryTokenType.STEP_TYPE_AND) {
					// throw...
				}
			}

			if (elementType == null) { // yuk!
				elementType = StoryElementType.GIVEN_STEP;
			}
			previousStepElementType = elementType;
			matchesHeadOrPush(elementType);
		}

		private void leaveStep() {
			leaveTableRow();
			popUntilOnlyIfPresent(StoryElementType.STEPS_TOKEN_SET);
		}

		public void enterExampleTable() {
			leaveTableRow();
			leaveStep();
			leaveMeta();
			leaveExampleTable();
			matchesHeadOrPush(StoryElementType.EXAMPLES);
		}

		public void leaveExampleTable() {
			leaveTableRow();
			popUntilOnlyIfPresent(StoryElementType.EXAMPLES);
		}

		public void enterTableRow() {
			matchesHeadOrPush(StoryElementType.TABLE_ROW);
		}

		public void leaveTableRow() {
			popUntilOnlyIfPresent(StoryElementType.TABLE_ROW);
		}
	}

	private static boolean belongsToScenario(final IElementType tokenType) {
		return isWhitespace(tokenType)
				|| isComment(tokenType)
				|| isScenarioText(tokenType)
				|| isStoryDescription(tokenType)
				|| isStepType(tokenType)
				|| isStepText(tokenType)
				|| isExampleTable(tokenType)
				|| isTableRow(tokenType)
				|| isMeta(tokenType);
	}

	private boolean belongsToTable(final IElementType tokenType) {
		return isWhitespace(tokenType)
				|| isComment(tokenType)
				|| isTableRow(tokenType);
	}

	private static boolean isMeta(final IElementType tokenType) {
		return tokenType == StoryTokenType.META
				|| tokenType == StoryTokenType.META_KEY
				|| tokenType == StoryTokenType.META_TEXT;
	}

	private static boolean isGivenStories(final IElementType tokenType) {
		return tokenType == StoryTokenType.GIVEN_STORIES;
	}

	private static boolean isExampleTable(final IElementType tokenType) {
		return tokenType == StoryTokenType.EXAMPLE_TYPE;
	}

	private static boolean isTableRow(final IElementType tokenType) {
		return tokenType == StoryTokenType.TABLE_CELL
				|| tokenType == StoryTokenType.TABLE_DELIM;
	}

	private static boolean isStepType(final IElementType tokenType) {
		return tokenType == StoryTokenType.STEP_TYPE_GIVEN
				|| tokenType == StoryTokenType.STEP_TYPE_WHEN
				|| tokenType == StoryTokenType.STEP_TYPE_THEN
				|| tokenType == StoryTokenType.STEP_TYPE_AND;
	}

	private static boolean isStepText(final IElementType tokenType) {
		return tokenType == StoryTokenType.STEP_TEXT;
	}

	private static boolean isScenario(final IElementType tokenType) {
		return tokenType == StoryTokenType.SCENARIO_TYPE;
	}

	private static boolean isScenarioText(final IElementType tokenType) {
		return tokenType == StoryTokenType.SCENARIO_TEXT;
	}

	private static boolean isWhitespace(final IElementType tokenType) {
		return tokenType == StoryTokenType.WHITE_SPACE;
	}

	private static boolean isComment(final IElementType tokenType) {
		return tokenType == StoryTokenType.COMMENT
				|| tokenType == StoryTokenType.COMMENT_WITH_LOCALE;
	}

	private static boolean isStoryDescription(final IElementType tokenType) {
		return tokenType == StoryTokenType.STORY_DESCRIPTION
				|| tokenType == StoryTokenType.NARRATIVE_TYPE
				|| tokenType == StoryTokenType.NARRATIVE_TEXT
				;
	}

	private void skipWhitespacesOrComments(final PsiBuilder builder) {
		skipWhitespacesOrComments(builder, false);
	}

	private String skipWhitespacesOrComments(final PsiBuilder builder, final boolean checkForLocale) {
		String storyLocale = "en";

		while (isWhitespace(builder.getTokenType())
				|| builder.getTokenType() == StoryTokenType.COMMENT) {
			if (builder.getTokenType() == StoryTokenType.COMMENT) {
				final PsiBuilder.Marker commentMark = builder.mark();
				while (builder.getTokenType() == StoryTokenType.COMMENT) {
					if (checkForLocale) {
						final String commentText = builder.getTokenText();
						final String locale = LocalizedStorySupport.checkForLanguageDefinition(commentText);
						if (locale != null) {
							storyLocale = locale;
						}
					}
					builder.advanceLexer();
				}
				commentMark.done(StoryElementType.COMMENT);
			} else {
				builder.advanceLexer();
			}
		}

		return storyLocale;
	}

	private void parseStoryDescriptionOrNarrativesLinesIfPresent(final PsiBuilder builder) {
		if (builder.getTokenType() != StoryTokenType.STORY_DESCRIPTION
				&& builder.getTokenType() != StoryTokenType.NARRATIVE_TYPE
				&& builder.getTokenType() != StoryTokenType.NARRATIVE_TEXT) {
			return;
		}

		final PsiBuilder.Marker marker = builder.mark();
		while (builder.getTokenType() == StoryTokenType.STORY_DESCRIPTION
				|| builder.getTokenType() == StoryTokenType.NARRATIVE_TYPE
				|| builder.getTokenType() == StoryTokenType.NARRATIVE_TEXT) {
			parseStoryDescriptionLine(builder);
			skipWhitespacesOrComments(builder);
		}
		marker.done(StoryElementType.STORY_DESCRIPTION);
	}

	private void parseStoryDescriptionLine(final PsiBuilder builder) {
		builder.advanceLexer();
	}

	private void parseScenarios(final PsiBuilder builder) {
		if (builder.getTokenType() == StoryTokenType.SCENARIO_TYPE) {
			while (builder.getTokenType() == StoryTokenType.SCENARIO_TYPE) {
				parseScenario(builder);
				skipWhitespacesOrComments(builder);
			}
		} else {
			builder.advanceLexer();
			builder.error("Scenario expected");
		}
	}

	private void parseScenario(final PsiBuilder builder) {
		final PsiBuilder.Marker stepMarker = builder.mark();
		builder.advanceLexer();
		skipWhitespacesOrComments(builder);
		while (builder.getTokenType() == StoryTokenType.SCENARIO_TEXT) {
			parseScenarioText(builder);
			skipWhitespacesOrComments(builder);
		}
		parseMeta(builder);
		parseSteps(builder);
		skipWhitespacesOrComments(builder);
		parseStoryDescriptionOrNarrativesLinesIfPresent(builder);
		skipWhitespacesOrComments(builder);
		parseExamples(builder);
		stepMarker.done(StoryElementType.SCENARIO);
	}

	private void parseScenarioText(final PsiBuilder builder) {
		builder.advanceLexer();
	}

	private void parseMeta(final PsiBuilder builder) {
		if (builder.getTokenType() == StoryTokenType.META) {
			final PsiBuilder.Marker stepMarker = builder.mark();
			while (builder.getTokenType() == StoryTokenType.META
					|| builder.getTokenType() == StoryTokenType.META_TEXT
					|| builder.getTokenType() == StoryTokenType.META_KEY) {
				builder.advanceLexer();
				skipWhitespacesOrComments(builder);
			}
			stepMarker.done(StoryElementType.META);
		}
	}

	private void parseSteps(final PsiBuilder builder) {
		parseStoryDescriptionOrNarrativesLinesIfPresent(builder);
		if (isStepType(builder.getTokenType())) {
			StoryElementType previousStepElementType = null;
			while (isStepType(builder.getTokenType())) {
				previousStepElementType = parseStep(builder, previousStepElementType);
				skipWhitespacesOrComments(builder);
				parseStoryDescriptionOrNarrativesLinesIfPresent(builder);
			}
		} else {
			builder.error("At least one step expected");
		}
	}


	private StoryElementType parseStep(final PsiBuilder builder, final StoryElementType previousStepElementType) {
		final PsiBuilder.Marker stepMarker = builder.mark();
		StoryElementType currentStepElementType;

		// TODO find a nicer way to perform the switch
		final IElementType tokenType = builder.getTokenType();
		if (tokenType == StoryTokenType.STEP_TYPE_GIVEN) {
			currentStepElementType = StoryElementType.GIVEN_STEP;
		} else if (tokenType == StoryTokenType.STEP_TYPE_WHEN) {
			currentStepElementType = StoryElementType.WHEN_STEP;
		} else if (tokenType == StoryTokenType.STEP_TYPE_THEN) {
			currentStepElementType = StoryElementType.THEN_STEP;
		} else {
			// should be the AND
			if (tokenType != StoryTokenType.STEP_TYPE_AND) {
				// throw...
			}
			currentStepElementType = previousStepElementType;
		}

		if (currentStepElementType == null) {
			// yuk!
			currentStepElementType = StoryElementType.GIVEN_STEP;
		}

		parseStepType(builder);
		parseStepText(builder);
		skipWhitespacesOrComments(builder);
		parseTableIfPresent(builder);
		skipWhitespacesOrComments(builder);
		stepMarker.done(currentStepElementType);

		return currentStepElementType;
	}

	private void parseStepType(final PsiBuilder builder) {
		builder.advanceLexer();
	}

	private void parseStepText(final PsiBuilder builder) {
		if (builder.getTokenType() == StoryTokenType.STEP_TEXT) {
			while (builder.getTokenType() == StoryTokenType.STEP_TEXT) {
				builder.advanceLexer();
				skipWhitespacesOrComments(builder);
			}
		} else {
			builder.error("Step text expected");
		}
	}

	private void parseExamples(final PsiBuilder builder) {
		if (builder.getTokenType() == StoryTokenType.EXAMPLE_TYPE) {
			final PsiBuilder.Marker stepMarker = builder.mark();
			builder.advanceLexer();
			skipWhitespacesOrComments(builder);
			if (builder.getTokenType() == StoryTokenType.TABLE_DELIM) {
				parseTableIfPresent(builder);
			} else {
				builder.error("Table row expected");
			}

			stepMarker.done(StoryElementType.EXAMPLES);
		}
	}

	private void parseTableIfPresent(final PsiBuilder builder) {
		if (builder.getTokenType() == StoryTokenType.TABLE_DELIM) {
			while (builder.getTokenType() == StoryTokenType.TABLE_DELIM
					|| builder.getTokenType() == StoryTokenType.TABLE_CELL) {
				parseTableToken(builder);
				skipWhitespacesOrComments(builder);
			}
		}
	}

	private void parseTableToken(final PsiBuilder builder) {
		builder.advanceLexer();
	}
}
