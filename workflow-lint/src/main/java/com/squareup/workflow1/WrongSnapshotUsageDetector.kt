@file:Suppress("UnstableApiUsage")

package com.squareup.workflow1

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity.INFORMATIONAL
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.intellij.psi.PsiElement
import org.jetbrains.uast.UReferenceExpression

/**
 * TODO write documentation
 */
class WrongSnapshotUsageDetector : Detector(), SourceCodeScanner {

//  override fun visitMethod(
//    context: JavaContext,
//    visitor: JavaElementVisitor?,
//    call: PsiMethodCallExpression,
//    method: PsiMethod
//  ) {
//    if (method)
//  }

  override fun getApplicableReferenceNames(): List<String>? =
    listOf("EMPTY")

  override fun visitReference(
    context: JavaContext,
    reference: UReferenceExpression,
    referenced: PsiElement
  ) {
    // TODO check for Snapshot.EMPTY vs EMPTY, provide quick fix
    context.report(
        ISSUE_SNAPSHOT_STATE_EMPTY,
        scope = reference,
        location = context.getLocation(reference),
        message = "Return null instead of Snapshot.EMPTY."
    )
  }

  companion object {
    val issues: List<Issue> get() = listOf(ISSUE_SNAPSHOT_STATE_EMPTY)

    internal val ISSUE_SNAPSHOT_STATE_EMPTY = Issue.create(
        id = "SnapshotStateEmpty",
        briefDescription = "This is the brief description TODO",
        explanation = "This is the explanation TODO",
        category = Category.CORRECTNESS,
        severity = INFORMATIONAL,
        implementation = Implementation(
            WrongSnapshotUsageDetector::class.java,
            Scope.JAVA_FILE_SCOPE
        )
    )
  }
}
