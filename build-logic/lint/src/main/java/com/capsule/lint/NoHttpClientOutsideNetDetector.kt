package com.capsule.lint

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UClass

/**
 * Lint detector that prevents HTTP client usage outside `com.capsule.app.net.*`.
 *
 * Principle VI — privilege separation: only the :net process should touch the network.
 * This detector flags constructor calls to OkHttpClient, HttpURLConnection, java.net.Socket,
 * and Ktor HttpClient outside the allowed net package.
 */
class NoHttpClientOutsideNetDetector : Detector(), SourceCodeScanner {

    companion object {
        val ISSUE: Issue = Issue.create(
            id = "OrbitNoHttpClientOutsideNet",
            briefDescription = "HTTP client used outside :net process package",
            explanation = """
                Orbit's privilege-separation model (Principle VI) requires all network I/O \
                to flow through the :net process. HTTP clients must only be instantiated in \
                `com.capsule.app.net.*`. Move this code into the net package or use the \
                INetworkGateway AIDL interface.
            """.trimIndent(),
            category = Category.SECURITY,
            priority = 8,
            severity = Severity.ERROR,
            implementation = Implementation(
                NoHttpClientOutsideNetDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        )

        private val BANNED_CONSTRUCTORS = setOf(
            "okhttp3.OkHttpClient",
            "okhttp3.OkHttpClient.Builder",
            "java.net.HttpURLConnection",
            "javax.net.ssl.HttpsURLConnection",
            "java.net.Socket",
            "io.ktor.client.HttpClient"
        )

        private const val ALLOWED_PACKAGE = "com.capsule.app.net"
    }

    override fun getApplicableConstructorTypes(): List<String> =
        BANNED_CONSTRUCTORS.toList()

    override fun visitConstructor(
        context: JavaContext,
        node: UCallExpression,
        constructor: PsiMethod
    ) {
        val containingClass = constructor.containingClass ?: return
        val fqn = containingClass.qualifiedName ?: return

        // Check if the call site is inside the allowed net package
        val sourceFile = context.uastFile ?: return
        val classes = sourceFile.classes
        val callerPackage = classes.firstOrNull()?.let { getPackageName(it) } ?: ""

        if (!callerPackage.startsWith(ALLOWED_PACKAGE)) {
            context.report(
                ISSUE,
                node,
                context.getLocation(node),
                "HTTP client `$fqn` used outside `$ALLOWED_PACKAGE`. " +
                    "Use INetworkGateway AIDL to request network I/O."
            )
        }
    }

    private fun getPackageName(cls: UClass): String {
        val qualifiedName = cls.qualifiedName ?: return ""
        val simpleName = cls.name ?: return ""
        return if (qualifiedName.endsWith(simpleName)) {
            qualifiedName.dropLast(simpleName.length).trimEnd('.')
        } else {
            qualifiedName
        }
    }
}
