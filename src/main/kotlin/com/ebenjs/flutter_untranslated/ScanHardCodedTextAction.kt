package com.ebenjs.flutterhardcodetextscanner

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.RegisterToolWindowTask
import com.intellij.openapi.wm.ToolWindowAnchor
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.content.ContentFactory
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import kotlinx.coroutines.delay
import kotlinx.serialization.json.Json
import java.awt.Color
import java.awt.FlowLayout
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.io.File
import java.security.cert.X509Certificate
import java.util.prefs.Preferences
import javax.net.ssl.X509TrustManager
import javax.swing.*

class ScanHardCodedTextAction : AnAction() {

    private val groupedResults = HashMap<String, String>()

    override fun actionPerformed(event: AnActionEvent) {
        val project: Project = event.project ?: return
        val baseDir: VirtualFile = project.baseDir ?: return
        val libDir: VirtualFile = baseDir.findChild("lib") ?: return
        val hardCodedTexts = mutableListOf<HashMap<String, String>>()

        scanDartFiles(libDir, project, hardCodedTexts)

        if (hardCodedTexts.isEmpty()) {
            println("Aucun texte codé en dur trouvé.")
        } else {
            showResults(project, hardCodedTexts)
        }
    }

    private fun scanDartFiles(directory: VirtualFile, project: Project, results: MutableList<HashMap<String, String>>) {

        directory.children.forEach { file ->
            if (file.isDirectory) {

                scanDartFiles(file, project, results)
            } else if (file.extension == "dart") {

                val psiFile = PsiManager.getInstance(project).findFile(file)
                psiFile?.let {
                    results.add(HashMap<String, String>().apply {
                        put("file", "File: ${file.path}")
                        put("text", findHardCodedTexts(it))
                    })
                }
            }
        }
    }

    private fun findHardCodedTexts(psiFile: PsiFile): String {
        val regex = Regex("""Text\(\s*["']([^"']+)["'](?:\s*,.*)?\)(?!.*\bloc\S*\()""")
        val content = psiFile.text
        var count = 0
        var details = ""

        regex.findAll(content).forEach { match ->
            count++
            val hardcodedText = "Hardcoded Text: ${match.groupValues[1]}"
            details += "\n$hardcodedText"
        }

        return details;
    }

    private fun showResults(project: Project, results: List<HashMap<String, String>>) {
        val toolWindowManager = ToolWindowManager.getInstance(project)
        var toolWindow = toolWindowManager.getToolWindow("Hardcoded Text Finder")

        if (toolWindow == null) {
            toolWindow = toolWindowManager.registerToolWindow(
                RegisterToolWindowTask(
                    id = "Flutter Untranslated",
                    anchor = ToolWindowAnchor.BOTTOM,
                    canCloseContent = true
                )
            )
        }

        val contentFactory = ContentFactory.getInstance()

        val resultTextArea = JTextPane()
        resultTextArea.isEditable = false
        resultTextArea.contentType = "text/html"
        resultTextArea.border = null
        resultTextArea.text = """
        <html>
            <body style="font-family: JetBrains Mono; font-size: 10px">
                ${generateTextWithLinks(results, project)}
            </body>
        </html>
        """.trimIndent()

        resultTextArea.addHyperlinkListener { e ->
            if (e.eventType == javax.swing.event.HyperlinkEvent.EventType.ACTIVATED) {
                val clickedLink = e.description
                if (clickedLink != null && clickedLink.startsWith("file://")) {
                    val filePath = clickedLink.removePrefix("file://")
                    openFileInEditor(project, filePath, results)
                }
            }
        }

        val searchField = JTextField(20)
        val filterButton = JButton("Filter")
        val resetButton = JButton("Reset")
        val generateButton = JButton("Generate ARB Files")

        val apiKeyLabel = JLabel("API Key: ")
        val apiKeyField = JTextField(20)
        val apiKeyButton = JButton("Set API Key")

        val panel = JPanel()
        panel.layout = BoxLayout(panel, BoxLayout.Y_AXIS)

        val searchPanel = JPanel()
        searchPanel.layout = FlowLayout(FlowLayout.LEFT)
        searchPanel.add(JLabel("Search: "))
        searchPanel.add(searchField)
        searchPanel.add(filterButton)
        searchPanel.add(resetButton)
        searchPanel.add(generateButton)

        val apiKeyPanel = JPanel()
        apiKeyPanel.layout = FlowLayout(FlowLayout.RIGHT)
        apiKeyPanel.add(apiKeyLabel)
        apiKeyPanel.add(apiKeyField)
        apiKeyPanel.add(apiKeyButton)

        searchPanel.add(apiKeyPanel)

        panel.add(searchPanel)

        val scrollPane = JBScrollPane(resultTextArea)
        scrollPane.border = null
        panel.add(scrollPane)

        val popupMenu = JPopupMenu()
        addContextualMenu(popupMenu, resultTextArea, results, project)
        resultTextArea.componentPopupMenu = popupMenu

        filterButton.addActionListener {
            val query = searchField.text.lowercase()
            val filteredResults = results.filter { it["text"]!!.lowercase().contains(query) }
            resultTextArea.text = generateTextWithLinks(filteredResults, project)
        }

        resetButton.addActionListener {
            searchField.text = ""
            resultTextArea.text = """
        <html>
            <body style="font-family: JetBrains Mono; font-size: 10px">
                ${generateTextWithLinks(results, project)}
            </body>
        </html>
        """.trimIndent()
        }

        generateButton.addActionListener {
            val projectBasePath = project.basePath ?: return@addActionListener
            val arbDirectoryPath = "$projectBasePath/lib/l10n_generated"
            val arbDirectory = File(arbDirectoryPath)
            if (!arbDirectory.exists()) {
                arbDirectory.mkdirs()
            }

            val enArbFile = File("$arbDirectoryPath/app_en.arb")
            val frArbFile = File("$arbDirectoryPath/app_fr.arb")

            runBlocking {
                if (writeArbFile(enArbFile, "en", results)) {
                    JOptionPane.showMessageDialog(null, "app_en.arb generated successfully!")
                }
                if (writeArbFile(frArbFile, "fr", results)) {
                    JOptionPane.showMessageDialog(null, "app_fr.arb generated successfully!")
                }
            }

            LocalFileSystem.getInstance().refreshAndFindFileByIoFile(File(project.basePath))
                ?.refresh(true, true)
        }

        apiKeyButton.addActionListener {
            val apiKey = apiKeyField.text.trim()
            if (apiKey.isNotEmpty()) {
                setApiKey(apiKey)
                println("API Key Set")
            } else {
                JOptionPane.showMessageDialog(null, "Please enter a valid API key")
            }
        }

        val content = contentFactory.createContent(panel, "Results", false)
        toolWindow.contentManager.removeAllContents(true)
        toolWindow.contentManager.addContent(content)
        toolWindow.show(null)
    }

    private fun generateTextWithLinks(results: List<HashMap<String, String>>, project: Project): String {
        val sb = StringBuilder()
        results.forEach() {
            val currentFile = it["file"]!!
            val currentText = it["text"]!!
            if (currentText.isNotEmpty()) {
                groupedResults.putIfAbsent(currentFile, currentText)
            }
        }
        sb.append(
            "🔍 Found ${groupedResults.size} file(s) with hardcoded text(s) and ${
                groupedResults.values.sumBy {
                    it.split(
                        "Hardcoded Text: "
                    ).size - 1
                }
            } hardcoded text(s) " +
                    "in your project.<br/><br/>"
        )

        groupedResults.forEach { (key, currentText) ->
            val currentTextList = currentText.split("Hardcoded Text: ")
            sb.append("""<a href="file://$key" style="color: #299999;">File: ${safeFileName(key)}(${currentTextList.size - 1})</a><br/>""")
            currentTextList.forEach { text ->
                if (text.trim().isNotEmpty()) {
                    sb.append("""&nbsp;&nbsp;&nbsp;&nbsp;• $text<br/>""")
                }
            }
            sb.append("<br/>")
        }
        return sb.toString()
    }

    private fun openFileInEditor(project: Project, selectedText: String, results: List<HashMap<String, String>>) {
        val filePath = selectedText.removePrefix("File: ")
        val virtualFile = LocalFileSystem.getInstance().findFileByPath(filePath)
        if (virtualFile != null) {
            FileEditorManager.getInstance(project).openFile(virtualFile, true)
        } else {
            println("File not found at path: $filePath")
        }
    }

    private fun addContextualMenu(
        popupMenu: JPopupMenu,
        resultTextArea: JTextPane,
        results: List<HashMap<String, String>>,
        project: Project
    ) {
        val openAction = JMenuItem("Open in Editor")
        openAction.addActionListener {
            val selectedText = resultTextArea.selectedText
            if (selectedText != null) {

                openFileInEditor(project, selectedText, results)
            }
        }
        val copyTextAction = JMenuItem("Copy Text")
        copyTextAction.addActionListener {
            val selectedText = resultTextArea.selectedText
            if (selectedText != null) {
                val clipboard = Toolkit.getDefaultToolkit().systemClipboard
                clipboard.setContents(StringSelection(selectedText), null)
            }
        }
        popupMenu.add(openAction)
        popupMenu.add(copyTextAction)
    }

    private fun safeFileName(filePath: String): String {
        val parts = filePath.split("/")
        return parts[parts.size - 1]
    }

    private suspend fun writeArbFile(file: File, language: String, results: List<HashMap<String, String>>): Boolean {
        file.writeText("{\n")
        groupedResults.forEach { (key, currentText) ->
            val currentTextList = currentText.split("Hardcoded Text: ")
            val currentSafeText = currentTextList[currentTextList.size - 1]
            if (currentSafeText.trim().isNotEmpty()) {
                val translation = translateTextWithHuggingFace(currentSafeText, language)
                if (translation != "Null") {
                    file.appendText("\"${createMeaningFullKeyFrom(key, currentSafeText)}\": \"$translation\",\n")
                }
            }
        }
        file.appendText("}")
        return true
    }

    private fun createMeaningFullKeyFrom(key: String, text: String): String {
        var safeFileName = safeFileName(key)
        safeFileName = safeFileName.substring(0, safeFileName.lastIndexOf('.'))
        safeFileName = safeFileName.replace("_", "").replace(" ", "").replace(":", "").replace("!", "").replace("?", "")
            .lowercase()
        val word = text.split(" ")[0]
        return "${safeFileName}${word.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }}"
    }

    private suspend fun translateTextWithHuggingFace(text: String, language: String): String {
        val apiUrl =
            if (language == "en") "https://api-inference.huggingface.co/models/Helsinki-NLP/opus-mt-fr-en" else "https://api-inference.huggingface.co/models/Helsinki-NLP/opus-mt-en-fr"
        val apiToken = getApiKey() ?: return "Null"

        val client = HttpClient(CIO) {
            install(ContentNegotiation) {
                json(Json { prettyPrint = true })
            }
            engine {
                https {
                    trustManager = object : X509TrustManager {
                        override fun checkClientTrusted(p0: Array<out X509Certificate>?, p1: String?) {}

                        override fun checkServerTrusted(p0: Array<out X509Certificate>?, p1: String?) {}

                        override fun getAcceptedIssuers(): Array<X509Certificate>? = null
                    }
                }
            }
        }

        val parameters = """
        {
            "inputs": "$text"
        }
        """.trimIndent()

        try {
            val response = client.post(apiUrl) {
                contentType(ContentType.Application.Json)
                header("Authorization", "Bearer $apiToken")
                setBody(parameters)
            }

            if (response.status.value == 503) {
                delay(20000)
                return translateTextWithHuggingFace(text, language);
            }
            return parseTranslationResponse(response);
        } catch (e: Exception) {
            println("Erreur: ${e.localizedMessage}")
            return "Null"
        } finally {
            client.close()
        }
    }

    private suspend fun parseTranslationResponse(response: HttpResponse): String {
        val body = response.body<String>().toString()
        val translation = body.substring(body.indexOf(":") + 2, body.length - 3)
        return translation
    }

    fun setApiKey(apiKey: String) {
        val prefs = Preferences.userRoot().node("com/ebenjs/flutter_untranslated")
        prefs.put("huggingFaceApiKey", apiKey)
    }

    fun getApiKey(): String? {
        val prefs = Preferences.userRoot().node("com/ebenjs/flutter_untranslated")
        return prefs.get("huggingFaceApiKey", null)
    }
}
