package net.quedale.andronvif.adddevice

import android.text.Editable
import android.text.TextWatcher
import android.widget.EditText

class PortNumberTextWatcher(private val editText: EditText) : TextWatcher {

    private var isUpdating = false
    private var originalCursorPosition = 0

    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
        if (!isUpdating) {
            originalCursorPosition = editText.selectionStart
        }
    }

    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
        // Not used here, operations happen after text changes
    }

    override fun afterTextChanged(s: Editable?) {
        if (isUpdating) return

        isUpdating = true

        // 1. Filter to only digits and parse as integer if possible
        val digitsOnly = s.toString().filter { it.isDigit() }
        val inputNumber = digitsOnly.toIntOrNull()

        // 2. Validate the port number (0 to 65535, or typically 1 to 65535 for application ports)
        if (originalCursorPosition > 1 && (inputNumber == null || inputNumber < 1 || inputNumber > 65535)) {
            // Invalid input: remove the invalid character(s)
            val newText = digitsOnly.removeRange(originalCursorPosition - 1, originalCursorPosition)
            editText.setText(newText)
            // Restore cursor position if possible
            editText.setSelection(newText.length.coerceAtMost(originalCursorPosition))
        } else {
            // Valid port number
            editText.setText(digitsOnly) // Update EditText to contain only valid digits
            editText.setSelection(digitsOnly.length) // Move cursor to the end
        }

        isUpdating = false
    }
}