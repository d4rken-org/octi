package eu.darken.octi.modules.power.ui.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.Editable
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.TextWatcher
import android.text.style.StyleSpan
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.widget.GridLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import eu.darken.octi.R
import eu.darken.octi.common.EdgeToEdgeHelper
import eu.darken.octi.modules.power.R as PowerR

class BatteryWidgetConfigActivity : AppCompatActivity() {

    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    private var selectedBgColor: Int? = null
    private var selectedAccentColor: Int? = null
    private var selectedPreset: WidgetTheme? = null
    private var isMaterialYou = true
    private var isCustomMode = false

    private var bgSwatchViews = mutableListOf<Pair<Int, View>>()
    private var accentSwatchViews = mutableListOf<Pair<Int, View>>()

    private lateinit var toolbar: com.google.android.material.appbar.MaterialToolbar
    private lateinit var scrollView: ScrollView
    private lateinit var previewContainer: LinearLayout
    private lateinit var chipGroup: ChipGroup
    private lateinit var customChip: Chip
    private lateinit var customColorsContainer: LinearLayout
    private lateinit var applyButton: MaterialButton
    private lateinit var applyContainer: LinearLayout
    private lateinit var bgHexInput: TextInputEditText
    private lateinit var accentHexInput: TextInputEditText
    private lateinit var bgHexLayout: TextInputLayout
    private lateinit var accentHexLayout: TextInputLayout
    private lateinit var bgGrid: GridLayout
    private lateinit var accentGrid: GridLayout

    private var updatingHexProgrammatically = false

    private val defaultStrokeWidth by lazy { (1 * resources.displayMetrics.density).toInt() }
    private val selectedStrokeWidth by lazy { (3 * resources.displayMetrics.density).toInt() }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        setResult(RESULT_CANCELED)

        appWidgetId = intent?.extras?.getInt(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID,
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID

        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }

        setContentView(R.layout.module_power_widget_config_activity)

        toolbar = findViewById(R.id.toolbar)
        scrollView = findViewById(R.id.scroll_view)
        previewContainer = findViewById(R.id.preview_container)
        chipGroup = findViewById(R.id.presets_chip_group)
        customColorsContainer = findViewById(R.id.custom_colors_container)
        applyButton = findViewById(R.id.apply_button)
        applyContainer = findViewById(R.id.apply_container)
        bgHexInput = findViewById(R.id.background_hex_input)
        accentHexInput = findViewById(R.id.accent_hex_input)
        bgHexLayout = findViewById(R.id.background_hex_layout)
        accentHexLayout = findViewById(R.id.accent_hex_layout)
        bgGrid = findViewById(R.id.background_grid)
        accentGrid = findViewById(R.id.accent_grid)

        setupEdgeToEdge()
        setupToolbar()
        setupPresets()
        setupColorGrid(bgGrid, bgSwatchViews, isBackground = true)
        setupColorGrid(accentGrid, accentSwatchViews, isBackground = false)
        setupHexInputs()

        applyButton.setOnClickListener { applyAndFinish() }

        if (savedInstanceState != null) {
            restoreState(savedInstanceState)
        } else {
            loadCurrentTheme()
        }
    }

    private fun setupEdgeToEdge() {
        val e2e = EdgeToEdgeHelper(this)
        e2e.insetsPadding(toolbar, top = true, left = true, right = true)
        e2e.insetsPadding(scrollView, left = true, right = true)

        ViewCompat.setOnApplyWindowInsetsListener(applyContainer) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
            val displayCutout = insets.getInsets(WindowInsetsCompat.Type.displayCutout())
            v.setPadding(
                maxOf(systemBars.left, displayCutout.left),
                v.paddingTop,
                maxOf(systemBars.right, displayCutout.right),
                maxOf(systemBars.bottom, ime.bottom),
            )
            insets
        }
    }

    private fun setupToolbar() {
        toolbar.setNavigationOnClickListener { finish() }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(STATE_IS_MATERIAL_YOU, isMaterialYou)
        outState.putBoolean(STATE_IS_CUSTOM, isCustomMode)
        selectedBgColor?.let { outState.putInt(STATE_BG_COLOR, it) }
        selectedAccentColor?.let { outState.putInt(STATE_ACCENT_COLOR, it) }
        selectedPreset?.let { outState.putString(STATE_PRESET, it.name) }
    }

    private fun restoreState(savedInstanceState: Bundle) {
        isMaterialYou = savedInstanceState.getBoolean(STATE_IS_MATERIAL_YOU, true)
        isCustomMode = savedInstanceState.getBoolean(STATE_IS_CUSTOM, false)
        if (savedInstanceState.containsKey(STATE_BG_COLOR)) {
            selectedBgColor = savedInstanceState.getInt(STATE_BG_COLOR)
        }
        if (savedInstanceState.containsKey(STATE_ACCENT_COLOR)) {
            selectedAccentColor = savedInstanceState.getInt(STATE_ACCENT_COLOR)
        }
        selectedPreset = savedInstanceState.getString(STATE_PRESET)?.let { WidgetTheme.fromName(it) }

        when {
            isMaterialYou -> selectMaterialYou()
            isCustomMode -> {
                selectCustom()
                updateHexFields()
                updateSwatchSelections()
                updatePreview()
                updateApplyButton()
            }
            else -> {
                updateHexFields()
                updateSwatchSelections()
                updatePreview()
                updateApplyButton()
                highlightPreset(selectedPreset)
            }
        }
    }

    private fun loadCurrentTheme() {
        val options = AppWidgetManager.getInstance(this).getAppWidgetOptions(appWidgetId)
        val mode = options.getString(WidgetTheme.KEY_THEME_MODE)
        val presetName = options.getString(WidgetTheme.KEY_THEME_PRESET)

        if (mode == WidgetTheme.MODE_CUSTOM) {
            isMaterialYou = false
            selectedBgColor = if (options.containsKey(WidgetTheme.KEY_CUSTOM_BG)) options.getInt(WidgetTheme.KEY_CUSTOM_BG) else null
            selectedAccentColor = if (options.containsKey(WidgetTheme.KEY_CUSTOM_ACCENT)) options.getInt(WidgetTheme.KEY_CUSTOM_ACCENT) else null
            selectedPreset = WidgetTheme.fromName(presetName)

            if (selectedPreset != null) {
                highlightPreset(selectedPreset)
            } else {
                selectCustom()
            }
            updateHexFields()
            updateSwatchSelections()
            updatePreview()
            updateApplyButton()
        } else {
            selectMaterialYou()
        }
    }

    private fun setupPresets() {
        for (theme in WidgetTheme.entries) {
            val chip = LayoutInflater.from(this)
                .inflate(R.layout.widget_preset_chip, chipGroup, false) as Chip

            chip.text = getString(theme.labelRes)
            chip.id = View.generateViewId()
            chip.tag = theme

            if (theme.presetBg != null) {
                val dot = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setSize(
                        (12 * resources.displayMetrics.density).toInt(),
                        (12 * resources.displayMetrics.density).toInt(),
                    )
                    setColor(theme.presetBg)
                }
                chip.chipIcon = dot
                chip.isChipIconVisible = true
            } else {
                chip.isChipIconVisible = false
            }

            chip.setOnCheckedChangeListener { _, isChecked ->
                if (!isChecked) return@setOnCheckedChangeListener
                if (theme == WidgetTheme.MATERIAL_YOU) {
                    selectMaterialYou()
                } else {
                    isCustomMode = false
                    selectedPreset = theme
                    isMaterialYou = false
                    selectedBgColor = theme.presetBg
                    selectedAccentColor = theme.presetAccent
                    customColorsContainer.isVisible = false
                    updateHexFields()
                    updateSwatchSelections()
                    updatePreview()
                    updateApplyButton()
                }
            }

            chipGroup.addView(chip)
        }

        // Custom chip
        customChip = LayoutInflater.from(this)
            .inflate(R.layout.widget_preset_chip, chipGroup, false) as Chip
        customChip.text = getString(PowerR.string.module_power_widget_config_custom_label)
        customChip.id = View.generateViewId()
        customChip.tag = TAG_CUSTOM
        customChip.isChipIconVisible = false

        customChip.setOnCheckedChangeListener { _, isChecked ->
            if (!isChecked) return@setOnCheckedChangeListener
            selectCustom()
        }

        chipGroup.addView(customChip)
    }

    private fun highlightPreset(active: WidgetTheme?) {
        if (active == null) {
            chipGroup.clearCheck()
            return
        }
        for (i in 0 until chipGroup.childCount) {
            val chip = chipGroup.getChildAt(i) as? Chip ?: continue
            if (chip.tag == active) {
                chip.isChecked = true
                return
            }
        }
        chipGroup.clearCheck()
    }

    private fun selectMaterialYou() {
        isMaterialYou = true
        isCustomMode = false
        selectedPreset = WidgetTheme.MATERIAL_YOU
        customColorsContainer.isVisible = false
        highlightPreset(WidgetTheme.MATERIAL_YOU)
        updatePreview()
        applyButton.isEnabled = true
    }

    private fun selectCustom() {
        isMaterialYou = false
        isCustomMode = true
        selectedPreset = null
        customColorsContainer.isVisible = true
        customChip.isChecked = true
        updatePreview()
        updateApplyButton()
    }

    private fun setupColorGrid(
        grid: GridLayout,
        swatchList: MutableList<Pair<Int, View>>,
        isBackground: Boolean,
    ) {
        for (color in SWATCH_COLORS) {
            val swatchView = layoutInflater.inflate(R.layout.widget_color_swatch_item, grid, false)
            val colorView = swatchView.findViewById<View>(R.id.color_swatch)
            val bg = colorView.background.mutate() as GradientDrawable
            bg.setColor(color)
            colorView.background = bg

            swatchView.setOnClickListener {
                if (isBackground) {
                    selectedBgColor = color
                } else {
                    selectedAccentColor = color
                }
                updateHexFields()
                updateSwatchSelections()
                updatePreview()
                updateApplyButton()
            }

            val dp44 = (44 * resources.displayMetrics.density).toInt()
            val lp = GridLayout.LayoutParams(
                GridLayout.spec(GridLayout.UNDEFINED),
                GridLayout.spec(GridLayout.UNDEFINED, 1f),
            ).apply {
                width = dp44
                height = dp44
                setGravity(Gravity.CENTER)
            }
            grid.addView(swatchView, lp)
            swatchList.add(color to swatchView)
        }
    }

    private fun setupHexInputs() {
        bgHexInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                if (updatingHexProgrammatically) return
                val color = parseHexColor(s?.toString())
                if (color != null) {
                    selectedBgColor = color
                    updateSwatchSelections()
                    updatePreview()
                }
                updateApplyButton()
            }
        })

        accentHexInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                if (updatingHexProgrammatically) return
                val color = parseHexColor(s?.toString())
                if (color != null) {
                    selectedAccentColor = color
                    updateSwatchSelections()
                    updatePreview()
                }
                updateApplyButton()
            }
        })

        bgHexLayout.setEndIconOnClickListener {
            bgHexInput.text?.clear()
            selectedBgColor = null
            updateSwatchSelections()
            updatePreview()
            updateApplyButton()
        }

        accentHexLayout.setEndIconOnClickListener {
            accentHexInput.text?.clear()
            selectedAccentColor = null
            updateSwatchSelections()
            updatePreview()
            updateApplyButton()
        }
    }

    private fun updateHexFields() {
        updatingHexProgrammatically = true
        bgHexInput.setText(selectedBgColor?.let { String.format("%06X", it and 0xFFFFFF) } ?: "")
        accentHexInput.setText(selectedAccentColor?.let { String.format("%06X", it and 0xFFFFFF) } ?: "")
        updatingHexProgrammatically = false
    }

    private fun updateSwatchSelections() {
        updateSwatchList(bgSwatchViews, selectedBgColor)
        updateSwatchList(accentSwatchViews, selectedAccentColor)
    }

    private fun updateSwatchList(swatchList: List<Pair<Int, View>>, selectedColor: Int?) {
        for ((color, view) in swatchList) {
            val colorView = view.findViewById<View>(R.id.color_swatch)
            val check = view.findViewById<ImageView>(R.id.check_icon)
            val bg = colorView.background.mutate() as GradientDrawable
            val isSelected = selectedColor == color

            check.isVisible = isSelected
            if (isSelected) {
                val contrast = WidgetTheme.bestContrast(color)
                check.colorFilter = PorterDuffColorFilter(contrast, PorterDuff.Mode.SRC_IN)
                bg.setStroke(selectedStrokeWidth, contrast)
            } else {
                bg.setStroke(defaultStrokeWidth, 0x33000000)
            }
        }
    }

    private fun updatePreview() {
        previewContainer.removeAllViews()

        if (isMaterialYou) {
            previewContainer.setBackgroundResource(R.drawable.widget_background)
            val previewRow = layoutInflater.inflate(R.layout.module_power_widget_row, previewContainer, false)
            val label = previewRow.findViewById<TextView>(R.id.device_label)
            label.text = buildPreviewLabel()
            previewRow.findViewById<TextView>(R.id.charge_percent).text = "75%"
            previewRow.findViewById<ProgressBar>(R.id.battery_progressbar).progress = 75
            previewContainer.addView(previewRow)
            return
        }

        val bg = selectedBgColor ?: return
        val accent = selectedAccentColor ?: return
        val colors = WidgetTheme.deriveColors(bg, accent)

        previewContainer.setBackgroundColor(colors.containerBg)

        val previewRow = layoutInflater.inflate(R.layout.module_power_widget_row, previewContainer, false)

        val label = previewRow.findViewById<TextView>(R.id.device_label)
        label.text = buildPreviewLabel()
        label.setTextColor(colors.icon)

        val icon = previewRow.findViewById<ImageView>(R.id.battery_icon)
        icon.setColorFilter(colors.icon, PorterDuff.Mode.SRC_IN)

        val percentText = previewRow.findViewById<TextView>(R.id.charge_percent)
        percentText.text = "75%"
        percentText.setTextColor(colors.onContainer)

        val progressBar = previewRow.findViewById<ProgressBar>(R.id.battery_progressbar)
        progressBar.progress = 75
        progressBar.progressTintList = ColorStateList.valueOf(colors.barFill)
        progressBar.progressBackgroundTintList = ColorStateList.valueOf(colors.barTrack)

        previewContainer.addView(previewRow)
    }

    private fun buildPreviewLabel(): SpannableStringBuilder = SpannableStringBuilder().apply {
        val name = "Pixel 8"
        append(name)
        setSpan(StyleSpan(Typeface.BOLD), 0, name.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        append(" \u00b7 5 min ago")
    }

    private fun updateApplyButton() {
        applyButton.isEnabled = when {
            isMaterialYou -> true
            isCustomMode -> selectedBgColor != null && selectedAccentColor != null &&
                isHexValid(bgHexInput.text?.toString()) && isHexValid(accentHexInput.text?.toString())
            else -> selectedBgColor != null && selectedAccentColor != null
        }
    }

    private fun applyAndFinish() {
        val widgetManager = AppWidgetManager.getInstance(this)
        val options = widgetManager.getAppWidgetOptions(appWidgetId)

        if (isMaterialYou) {
            options.putString(WidgetTheme.KEY_THEME_MODE, WidgetTheme.MODE_MATERIAL_YOU)
            options.putString(WidgetTheme.KEY_THEME_PRESET, WidgetTheme.MATERIAL_YOU.name)
            options.remove(WidgetTheme.KEY_CUSTOM_BG)
            options.remove(WidgetTheme.KEY_CUSTOM_ACCENT)
        } else {
            options.putString(WidgetTheme.KEY_THEME_MODE, WidgetTheme.MODE_CUSTOM)
            options.putString(WidgetTheme.KEY_THEME_PRESET, selectedPreset?.name ?: "")
            val bg = selectedBgColor ?: return
            val accent = selectedAccentColor ?: return
            options.putInt(WidgetTheme.KEY_CUSTOM_BG, bg)
            options.putInt(WidgetTheme.KEY_CUSTOM_ACCENT, accent)
        }

        widgetManager.updateAppWidgetOptions(appWidgetId, options)

        sendBroadcast(Intent(AppWidgetManager.ACTION_APPWIDGET_UPDATE).apply {
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, intArrayOf(appWidgetId))
            component = ComponentName(this@BatteryWidgetConfigActivity, BatteryWidgetProvider::class.java)
        })

        setResult(
            RESULT_OK,
            Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId),
        )
        finish()
    }

    companion object {
        private const val TAG_CUSTOM = "custom"
        private const val STATE_IS_MATERIAL_YOU = "state_is_material_you"
        private const val STATE_IS_CUSTOM = "state_is_custom"
        private const val STATE_BG_COLOR = "state_bg_color"
        private const val STATE_ACCENT_COLOR = "state_accent_color"
        private const val STATE_PRESET = "state_preset"

        fun parseHexColor(input: String?): Int? {
            if (input.isNullOrBlank()) return null
            val cleaned = input.trim().removePrefix("#").uppercase()
            if (cleaned.length != 6) return null
            if (!cleaned.matches(Regex("[0-9A-F]{6}"))) return null
            return try {
                (0xFF000000 or cleaned.toLong(16)).toInt()
            } catch (_: NumberFormatException) {
                null
            }
        }

        private fun isHexValid(input: String?): Boolean = parseHexColor(input) != null

        val SWATCH_COLORS = intArrayOf(
            0xFFF44336.toInt(), // Red
            0xFFE91E63.toInt(), // Pink
            0xFF9C27B0.toInt(), // Purple
            0xFF673AB7.toInt(), // Deep Purple
            0xFF3F51B5.toInt(), // Indigo
            0xFF2196F3.toInt(), // Blue
            0xFF03A9F4.toInt(), // Light Blue
            0xFF00BCD4.toInt(), // Cyan
            0xFF009688.toInt(), // Teal
            0xFF4CAF50.toInt(), // Green
            0xFF8BC34A.toInt(), // Light Green
            0xFFCDDC39.toInt(), // Lime
            0xFFFFEB3B.toInt(), // Yellow
            0xFFFFC107.toInt(), // Amber
            0xFFFF9800.toInt(), // Orange
            0xFFFF5722.toInt(), // Deep Orange
            0xFF795548.toInt(), // Brown
            0xFF9E9E9E.toInt(), // Grey
            0xFF607D8B.toInt(), // Blue Grey
            0xFFFFFFFF.toInt(), // White
            0xFF1E1E1E.toInt(), // Near Black
            0xFF263238.toInt(), // Dark Blue Grey
            0xFF1B5E20.toInt(), // Dark Green
            0xFF0D47A1.toInt(), // Dark Blue
        )
    }
}
