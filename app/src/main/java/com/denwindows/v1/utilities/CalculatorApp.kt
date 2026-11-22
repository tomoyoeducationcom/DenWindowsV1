package com.denwindows.v1.utilities

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.denwindows.v1.theme.LocalDenColors
import com.denwindows.v1.theme.MonoFontFamily
import java.math.BigDecimal
import java.math.MathContext

class CalculatorEngine {
    var display by mutableStateOf("0")
        private set
    var expressionPreview by mutableStateOf("")
        private set
    val history = mutableStateListOf<String>()

    private var accumulator: BigDecimal? = null
    private var pendingOp: String? = null
    private var resetOnNextDigit = false

    fun inputDigit(digit: String) {
        if (resetOnNextDigit || display == "Error") {
            display = if (digit == ".") "0." else digit
            resetOnNextDigit = false
            return
        }
        if (digit == "." && display.contains(".")) return
        display = if (display == "0" && digit != ".") digit else display + digit
    }

    fun backspace() {
        if (resetOnNextDigit || display == "Error") {
            display = "0"
            return
        }
        display = if (display.length <= 1 || (display.length == 2 && display.startsWith("-"))) "0" else display.dropLast(1)
    }

    fun clearAll() {
        display = "0"
        expressionPreview = ""
        accumulator = null
        pendingOp = null
        resetOnNextDigit = false
    }

    fun toggleSign() {
        if (display == "0" || display == "Error") return
        display = if (display.startsWith("-")) display.drop(1) else "-$display"
    }

    fun applyPercent() {
        val current = display.toBigDecimalOrNull() ?: return
        val base = accumulator
        val result = if (base != null && (pendingOp == "+" || pendingOp == "-")) {
            base.multiply(current, MathContext.DECIMAL64).divide(BigDecimal(100), MathContext.DECIMAL64)
        } else {
            current.divide(BigDecimal(100), MathContext.DECIMAL64)
        }
        display = formatDecimal(result)
        resetOnNextDigit = true
    }

    fun applyOperator(op: String) {
        val current = display.toBigDecimalOrNull() ?: return
        if (accumulator != null && pendingOp != null && !resetOnNextDigit) {
            val res = evaluate(accumulator!!, current, pendingOp!!)
            if (res == null) {
                display = "Error"
                accumulator = null
                pendingOp = null
                expressionPreview = ""
                resetOnNextDigit = true
                return
            }
            accumulator = res
            display = formatDecimal(res)
        } else {
            accumulator = current
        }
        pendingOp = op
        expressionPreview = "${formatDecimal(accumulator!!)} $op"
        resetOnNextDigit = true
    }

    fun evaluateEquals() {
        val left = accumulator ?: return
        val op = pendingOp ?: return
        val right = display.toBigDecimalOrNull() ?: return
        val res = evaluate(left, right, op)
        if (res == null) {
            display = "Error"
        } else {
            val formatted = formatDecimal(res)
            history.add(0, "${formatDecimal(left)} $op ${formatDecimal(right)} = $formatted")
            if (history.size > 20) history.removeAt(history.lastIndex)
            display = formatted
        }
        expressionPreview = ""
        accumulator = null
        pendingOp = null
        resetOnNextDigit = true
    }

    private fun evaluate(a: BigDecimal, b: BigDecimal, op: String): BigDecimal? {
        return when (op) {
            "+" -> a.add(b, MathContext.DECIMAL64)
            "-" -> a.subtract(b, MathContext.DECIMAL64)
            "×" -> a.multiply(b, MathContext.DECIMAL64)
            "÷" -> if (b.compareTo(BigDecimal.ZERO) == 0) null else a.divide(b, MathContext.DECIMAL64)
            else -> b
        }
    }

    private fun formatDecimal(value: BigDecimal): String {
        val stripped = value.stripTrailingZeros()
        return if (stripped.scale() <= 0) stripped.toPlainString() else stripped.toPlainString()
    }
}

@Composable
fun CalculatorApp() {
    val denColors = LocalDenColors.current
    val engine = remember { CalculatorEngine() }
    var showHistory by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(denColors.windowSurface)
            .padding(10.dp)
    ) {
        // Header + History Toggle
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = engine.expressionPreview,
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = MonoFontFamily),
                color = denColors.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = if (showHistory) "Keypad" else "History (${engine.history.size})",
                style = MaterialTheme.typography.labelSmall,
                color = denColors.accent,
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .clickable { showHistory = !showHistory }
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            )
        }

        // Display
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            contentAlignment = Alignment.CenterEnd
        ) {
            Text(
                text = engine.display,
                style = MaterialTheme.typography.headlineLarge.copy(
                    fontFamily = MonoFontFamily,
                    fontSize = 30.sp,
                    fontWeight = FontWeight.SemiBold
                ),
                color = denColors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.End,
                modifier = Modifier.testTag("calculator_display")
            )
        }

        if (showHistory) {
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (engine.history.isEmpty()) {
                    item {
                        Text(
                            "No calculations yet.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = denColors.textSecondary
                        )
                    }
                } else {
                    items(engine.history) { row ->
                        Text(
                            text = row,
                            style = MaterialTheme.typography.bodyMedium.copy(fontFamily = MonoFontFamily),
                            color = denColors.textPrimary,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(denColors.secondarySurface)
                                .padding(10.dp)
                        )
                    }
                }
            }
        } else {
            val rows = listOf(
                listOf("C", "±", "%", "÷"),
                listOf("7", "8", "9", "×"),
                listOf("4", "5", "6", "-"),
                listOf("1", "2", "3", "+"),
                listOf("⌫", "0", ".", "=")
            )
            Column(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                rows.forEach { row ->
                    Row(
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        row.forEach { key ->
                            val isEquals = key == "="
                            val isOperator = key in setOf("÷", "×", "-", "+")
                            val bg = when {
                                isEquals -> denColors.accent
                                isOperator -> denColors.accentContainer
                                key in setOf("C", "±", "%", "⌫") -> denColors.secondarySurface.copy(alpha = 0.7f)
                                else -> denColors.secondarySurface
                            }
                            val fg = when {
                                isEquals -> Color.White
                                isOperator -> denColors.accent
                                else -> denColors.textPrimary
                            }
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxSize()
                                    .clip(RoundedCornerShape(9.dp))
                                    .background(bg)
                                    .clickable {
                                        when (key) {
                                            "C" -> engine.clearAll()
                                            "⌫" -> engine.backspace()
                                            "±" -> engine.toggleSign()
                                            "%" -> engine.applyPercent()
                                            "÷", "×", "-", "+" -> engine.applyOperator(key)
                                            "=" -> engine.evaluateEquals()
                                            else -> engine.inputDigit(key)
                                        }
                                    }
                                    .testTag("calc_btn_$key"),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = key,
                                    style = MaterialTheme.typography.titleMedium.copy(fontSize = 17.sp),
                                    color = fg
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
