# Flutter Untranslated

Flutter Untranslated is a powerful IntelliJ-based plugin designed to streamline the detection of hardcoded text strings in Flutter projects. Its primary purpose is to identify untranslated texts and help developers localize their applications efficiently.

---

## Features

- **Smart Detection**: Analyze your Flutter source code to find hardcoded text strings.
- **Interactive Panel**: Easily view and filter detected strings in a user-friendly panel.
- **ARB File Generation**: Automatically create `.arb` files for easy localization (e.g., `app_en.arb`, `app_fr.arb`).
- **Contextual Navigation**: Click on detected strings to jump directly to their location in the code.
- **Custom API Integration**: Allows setting your HuggingFace API key to leverage text translation services seamlessly.
- **Configurable Shortcuts**: Quickly access the plugin with a keyboard shortcut (`Ctrl+Alt+U` on Windows/Linux or `Cmd+Alt+U` on macOS).

---

## Installation

1. Open IntelliJ IDEA or Android Studio.
2. Navigate to `Settings` > `Plugins`.
3. Search for **Flutter Untranslated** in the marketplace.
4. Click **Install** and restart your IDE.

---

## Usage

1. Open any Flutter project in your IntelliJ-based IDE.
2. Use the shortcut `Ctrl+Alt+U` (Windows/Linux) or `Cmd+Alt+U` (macOS) to start analyzing your code.  
   Alternatively, access the plugin via `Tools > Flutter Untranslated`.
3. View the results in the **Flutter Untranslated** tool window at the bottom of the IDE.
4. Use the search bar in the tool window to filter results by text strings.
5. Set a custom HuggingFace API key by entering it in the right-side input field and clicking the "Set API Key" button.
6. Select relevant strings and generate ARB files by clicking the **Generate ARB Files** button.

---

## How It Works

- **Text Analysis**: The plugin scans your project's source code for any hardcoded strings.
- **HuggingFace API Support**: Provide an API key to translate identified strings directly into multiple languages.
- **Code Navigation**: Hyperlinks allow direct access to code locations for editing.

---

## Contributing

Contributions are welcome! If you'd like to enhance this plugin, fork the repository and submit a pull request.

---

## Feedback and Support

Found a bug or have a feature request? Reach out to us:

- **GitHub Issues**: [Report issues here](https://github.com/ebenjs/flutter_untranslated/issues)
- **Email**: nikaboue10@gmail.com

---

## License

This project is licensed under the MIT License. See the [LICENSE](LICENSE) file for details.
