// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.highlighter

import com.intellij.codeInsight.highlighting.HighlightUsagesHandler
import com.intellij.openapi.editor.colors.EditorColors
import com.intellij.openapi.editor.colors.EditorColorsManager
import org.jetbrains.kotlin.idea.test.KotlinLightCodeInsightFixtureTestCase
import org.jetbrains.kotlin.idea.test.InTextDirectivesUtils

abstract class AbstractHighlightExitPointsTest : KotlinLightCodeInsightFixtureTestCase() {
    fun doTest(unused: String) {
        myFixture.configureByFile(fileName())
        HighlightUsagesHandler.invoke(myFixture.project, myFixture.editor, myFixture.file)

        val text = myFixture.file.text
        val expectedToBeHighlighted = InTextDirectivesUtils.findLinesWithPrefixesRemoved(text, "//HIGHLIGHTED:")
        val searchResultsTextAttributes =
            EditorColorsManager.getInstance().globalScheme.getAttributes(EditorColors.SEARCH_RESULT_ATTRIBUTES)
        val highlighters = myFixture.editor.markupModel.allHighlighters
            .filter { it.textAttributes == searchResultsTextAttributes }
        val actual = highlighters.map { text.substring(it.startOffset, it.endOffset) }
        assertEquals(expectedToBeHighlighted, actual)
    }
}